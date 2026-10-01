package com.qita.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/**
 * Reads a Vita custom theme (the folder of `theme.xml` and pictures that the Vita Theme Builder makes, given as a zip or .vpk) into a
 * theme of our own. The `theme.xml` files in the wild are not always valid XML (text before the first tag, stray characters, odd
 * encodings), so it is read with a forgiving tag scanner rather than an XML parser. Anything it does not understand is left out and
 * named in the report.
 */
object VitaThemeImport {
    private const val MAX_FILE = 12 * 1024 * 1024
    private const val MAX_TOTAL = 64L * 1024 * 1024
    private const val MAX_INNER = 64 * 1024 * 1024
    private val PICTURES = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
    private val MUSIC = setOf("at9", "ogg", "mp3", "wav", "m4a", "aac")

    /** The files of the theme, found by full path first and by file name second. */
    private class Pack {
        val byPath = LinkedHashMap<String, ByteArray>()
        val byBase = LinkedHashMap<String, ByteArray>()
        val inner = ArrayList<ByteArray>()
        var music: String? = null
        var total = 0L
        var skipped = 0

        fun take(name: String, size: Long, s: InputStream) {
            val n = norm(name)
            val base = n.substringAfterLast('/')
            val ext = base.substringAfterLast('.', "")
            when {
                ext in PICTURES || ext == "xml" -> {
                    if (size > MAX_FILE || total + maxOf(size, 0L) > MAX_TOTAL) { skipped++; return }
                    val d = readLimited(s, MAX_FILE) ?: run { skipped++; return }
                    total += d.size
                    byPath.putIfAbsent(n, d)
                    byBase.putIfAbsent(base, d)
                }
                ext in MUSIC -> if (music == null) music = base
                ext == "zip" || ext == "vpk" -> if (size <= MAX_INNER && inner.size < 3) readLimited(s, MAX_INNER)?.let { inner.add(it) }
            }
        }

        fun find(ref: String?): ByteArray? {
            if (ref.isNullOrBlank()) return null
            val n = norm(ref)
            return byPath[n] ?: byPath.entries.firstOrNull { it.key.endsWith("/$n") }?.value ?: byBase[n.substringAfterLast('/')]
        }
    }

    fun import(context: Context, input: InputStream): ThemeImportResult {
        // The zip goes to a cache file first, so it can be read from its table of contents (more tolerant than reading it as a stream).
        val tmp = File.createTempFile("vita", ".zip", context.cacheDir)
        val pack = Pack()
        try {
            try {
                tmp.outputStream().use { input.copyTo(it) }
            } catch (e: Exception) {
                return ThemeImportResult(null, "That file could not be read: ${e.message ?: e.javaClass.simpleName}")
            }
            if (!readZip(tmp, pack)) return ThemeImportResult(null, "That file could not be opened as a zip.")
        } finally {
            tmp.delete()
        }
        // A theme packed inside another zip or .vpk.
        if (pack.find("theme.xml") == null) {
            for (b in pack.inner) {
                runCatching {
                    ZipInputStream(ByteArrayInputStream(b)).use { z ->
                        while (true) {
                            val e = z.nextEntry ?: break
                            if (!e.isDirectory) pack.take(e.name, e.size, z)
                        }
                    }
                }
                if (pack.find("theme.xml") != null) break
            }
        }
        val xmlBytes = pack.find("theme.xml") ?: return ThemeImportResult(null, "There is no theme.xml in that file, so it is not a Vita theme.")
        val raw = VitaThemeXml.decode(xmlBytes)
        var start = raw.indexOf("<theme", ignoreCase = true)
        if (start < 0) start = raw.indexOf('<')
        if (start < 0) return ThemeImportResult(null, "theme.xml does not look like a Vita theme.")
        val xml = raw.substring(start)

        val info = VitaThemeXml.block(xml, "InfomationProperty") ?: xml
        val startScreen = VitaThemeXml.block(xml, "StartScreenProperty") ?: xml
        val bar = VitaThemeXml.block(xml, "InfomationBarProperty") ?: xml
        val home = VitaThemeXml.block(xml, "HomeProperty") ?: xml
        val name = VitaThemeXml.text(info, "m_title") ?: "Imported Vita theme"
        val author = VitaThemeXml.text(info, "m_provider").orEmpty()

        val id = ThemePacks.newId()
        val dir = ThemePacks.folder(context, id).apply { mkdirs() }
        val notes = ArrayList<String>()
        val bad = ArrayList<String>()
        // Saves the first of the given pictures that can be read, as a JPEG at the screen's size (not as a big bitmap).
        fun picture(refs: List<String?>, out: String, maxWidth: Int, enlarge: Boolean = false): String? {
            val named = refs.filter { !it.isNullOrBlank() }
            for (ref in named) {
                val bmp = decode(pack.find(ref) ?: continue, maxWidth) ?: continue
                // Small pictures are enlarged once, properly, so they are not soft when drawn over a big screen.
                val big = if (enlarge) enlarged(bmp) else bmp
                File(dir, out).outputStream().use { big.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                if (big !== bmp) big.recycle()
                bmp.recycle()
                return out
            }
            named.firstOrNull()?.let { bad.add(it!!.substringAfterLast('/').substringAfterLast('\\')) }
            return null
        }
        fun namedLike(vararg prefixes: String): List<String> =
            pack.byBase.keys.filter { k ->
                k.substringAfterLast('.', "") in PICTURES && prefixes.any { k.startsWith(it) } &&
                    listOf("thumb", "preview", "icon", "page").none { it in k }
            }.sortedWith(compareBy({ it.length }, { it }))

        // Page backgrounds, in order; with no list in the theme, the pictures named like backgrounds.
        val params = VitaThemeXml.all(home, "BackgroundParam")
        val pages = ArrayList<String>()
        params.take(10).forEach { p ->
            picture(listOf(VitaThemeXml.value(p, "m_imageFilePath"), VitaThemeXml.value(p, "m_thumbnailFilePath")), "page${pages.size}.jpg", 1920, enlarge = true)
                ?.let { pages.add(it) }
        }
        if (params.size > pages.size) notes.add("${params.size - pages.size} page background(s) could not be read")
        if (pages.isEmpty()) {
            namedLike("bg", "background", "wallpaper").take(10).forEach { n ->
                picture(listOf(n), "page${pages.size}.jpg", 1920, enlarge = true)?.let { pages.add(it) }
            }
        }
        val nameColor = params.firstNotNullOfOrNull { VitaThemeXml.color(VitaThemeXml.value(it, "m_fontColor")) }
            ?: VitaThemeXml.color(VitaThemeXml.value(home, "m_fontColor"))

        val lock = picture(
            listOf(VitaThemeXml.value(startScreen, "m_filePath")) + namedLike("lock", "start").take(1),
            "lock.jpg", 1920, enlarge = true,
        )
        val preview = picture(listOf(VitaThemeXml.value(info, "m_homePreviewFilePath")) + pack.byBase.keys.filter { it.startsWith("preview") }.take(1), "preview.jpg", 480)
        val dateColor = VitaThemeXml.color(VitaThemeXml.value(startScreen, "m_dateColor"))
        val barColor = VitaThemeXml.color(VitaThemeXml.value(bar, "m_barColor"))
        val indicatorColor = VitaThemeXml.color(VitaThemeXml.value(bar, "m_indicatorColor"))

        // The icons for the system apps: <m_settings><m_iconFilePath>icon_settings.png</m_iconFilePath></m_settings> and so on.
        val icons = LinkedHashMap<String, String>()
        for (m in ICON.findAll(home)) {
            val key = m.groupValues[1]
            val bytes = pack.find(m.groupValues[2]) ?: continue
            val out = "icon_$key.png"
            File(dir, out).writeBytes(bytes)
            icons[key] = out
        }

        if (pages.isEmpty() && lock == null) {
            dir.deleteRecursively()
            val why = if (bad.isEmpty()) "" else " (could not read: ${bad.take(5).joinToString(", ")})"
            return ThemeImportResult(null, "That theme has no page backgrounds or lock screen picture that could be read$why.")
        }
        ThemePacks.save(context, ThemePack(id, name, author, pages, lock, preview, nameColor, dateColor, barColor, indicatorColor, icons))

        val used = buildList {
            add("${pages.size} page background${if (pages.size == 1) "" else "s"}")
            if (lock != null) add("lock screen picture")
            val colours = listOfNotNull(nameColor, dateColor, barColor, indicatorColor).size
            if (colours > 0) add("$colours colour${if (colours == 1) "" else "s"}")
            val mapped = icons.keys.count { it.lowercase() in setOf("settings", "browser", "hostcollabo") }
            if (mapped > 0) add("$mapped icon${if (mapped == 1) "" else "s"} for built-in bubbles (off until you switch them on in Settings, Theme)")
        }
        if (bad.isNotEmpty()) notes.add("pictures that could not be read: ${bad.take(5).joinToString(", ")}")
        if (pack.skipped > 0) notes.add("${pack.skipped} file(s) skipped for size")
        pack.music?.let { m -> notes.add(if (m.endsWith(".at9")) "background music ($m) is in the Vita's ATRAC9 format, which Android cannot play" else "background music ($m) is not used yet") }
        notes.add("the Vita's animated waves over the wallpaper are not reproduced")
        return ThemeImportResult(id, "Imported “$name”: " + used.joinToString(", ") + ". Left out: " + notes.joinToString("; ") + ".")
    }

    private val ICON = Regex(
        "<m_([A-Za-z0-9_]+)(?:[\\s/][^>]*)?>\\s*<m_iconFilePath(?:\\s[^>]*)?>\\s*([^<]*?)\\s*</m_iconFilePath\\s*>",
        RegexOption.IGNORE_CASE,
    )

    private fun norm(path: String): String {
        var n = path.trim().replace('\\', '/').trimStart('/')
        while (n.startsWith("./")) n = n.substring(2)
        return n.lowercase()
    }

    /** Reads the zip's table of contents (names as UTF-8, then as Latin-1), then, failing that, as a plain stream. */
    private fun readZip(file: File, pack: Pack): Boolean {
        for (cs in listOf(Charsets.UTF_8, Charsets.ISO_8859_1)) {
            try {
                ZipFile(file, cs).use { zf ->
                    val en = zf.entries()
                    while (en.hasMoreElements()) {
                        val e = en.nextElement()
                        if (!e.isDirectory) zf.getInputStream(e).use { pack.take(e.name, e.size, it) }
                    }
                }
                return true
            } catch (e: Exception) {
                // try the next way
            }
        }
        return try {
            ZipInputStream(file.inputStream().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory) pack.take(e.name, e.size, z)
                }
            }
            pack.byPath.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun readLimited(s: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            if (out.size() + n > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** The picture at up to twice its size (the long edge at most 2400), smoothly, or as it is if that is not worth it or fails. */
    private fun enlarged(bmp: Bitmap): Bitmap {
        val s = minOf(2.0, 2400.0 / maxOf(bmp.width, bmp.height))
        if (s < 1.15) return bmp
        return runCatching { Resample.scaleTo(bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), sharpen = 0.4f) }.getOrDefault(bmp)
    }

    private fun decode(bytes: ByteArray, maxWidth: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

/** The forgiving reading of a Vita `theme.xml`: no Android classes, so it can be checked on its own. */
object VitaThemeXml {
    /** The text of the file: UTF-16 or UTF-8 with a byte-order mark, UTF-16 without one, else UTF-8; stray characters removed. */
    fun decode(bytes: ByteArray): String {
        fun b(i: Int) = bytes[i].toInt() and 0xFF
        val s = when {
            bytes.size >= 2 && b(0) == 0xFF && b(1) == 0xFE -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && b(0) == 0xFE && b(1) == 0xFF -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            bytes.size >= 3 && b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 4 && b(0) == '<'.code && b(1) == 0 -> String(bytes, Charsets.UTF_16LE)
            bytes.size >= 4 && b(0) == 0 && b(1) == '<'.code -> String(bytes, Charsets.UTF_16BE)
            else -> bytes.decodeToString()
        }
        return s.filter { it == '\n' || it == '\r' || it == '\t' || it >= ' ' }.replace("`", "")
    }

    private fun open(tag: String) = Regex("<" + Regex.escape(tag) + "(?:[\\s/][^>]*)?>", RegexOption.IGNORE_CASE)
    private fun close(tag: String) = Regex("</" + Regex.escape(tag) + "\\s*>", RegexOption.IGNORE_CASE)

    /** Everything inside the first `<tag ...>` ... `</tag>` (empty for a self-closing tag), or null if the tag is not there. */
    fun block(text: String, tag: String): String? = blockAt(text, tag, 0)?.first

    private fun blockAt(text: String, tag: String, from: Int): Pair<String, Int>? {
        val m = open(tag).find(text, from) ?: return null
        if (m.value.endsWith("/>")) return "" to (m.range.last + 1)
        val e = close(tag).find(text, m.range.last + 1) ?: return null
        return text.substring(m.range.last + 1, e.range.first) to (e.range.last + 1)
    }

    /** Every `<tag>` ... `</tag>` block, in order. */
    fun all(text: String, tag: String): List<String> {
        val out = ArrayList<String>()
        var at = 0
        while (true) {
            val (body, next) = blockAt(text, tag, at) ?: break
            out.add(body)
            at = next
        }
        return out
    }

    /** The plain text of the first `<tag>`, without CDATA wrapping, or null if it is empty or has tags inside. */
    fun value(text: String, tag: String): String? {
        val b = block(text, tag)?.trim() ?: return null
        val t = if (b.startsWith("<![CDATA[") && b.endsWith("]]>")) b.substring(9, b.length - 3).trim() else b
        return t.takeIf { it.isNotEmpty() && '<' !in it }
    }

    /** A name such as `<m_title><m_default>Name</m_default><m_en>...</m_en></m_title>`: m_default, else the first text inside. */
    fun text(text: String, tag: String): String? {
        val b = block(text, tag) ?: return null
        value(b, "m_default")?.let { return it }
        value(text, tag)?.let { return it }
        return Regex("<[^>]*>").replace(b, "\n").replace("<![CDATA[", "").replace("]]>", "")
            .lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }
    }

    /** A colour as AARRGGBB, RRGGBB or RGB hex digits, with or without # or 0x; an alpha of 00 on a visible colour counts as opaque. */
    fun color(s: String?): Int? {
        var t = s?.trim() ?: return null
        t = t.removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (t.isEmpty() || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        val v = when (t.length) {
            8 -> java.lang.Long.parseLong(t, 16).toInt()
            6 -> (0xFF000000L or java.lang.Long.parseLong(t, 16)).toInt()
            else -> return null
        }
        return if (t.length == 8 && (v ushr 24) == 0 && (v and 0xFFFFFF) != 0) v or (0xFF shl 24) else v
    }
}
