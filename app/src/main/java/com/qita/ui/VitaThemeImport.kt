package com.qita.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads a Vita custom theme (the folder of `theme.xml` and pictures that the Vita Theme Builder makes, given as a zip) into a theme
 * of our own. The `theme.xml` files in the wild are not always valid XML (text before the first tag, stray characters), so it is read
 * with a forgiving tag scanner rather than an XML parser. Anything it does not understand is left out and named in the report.
 */
object VitaThemeImport {
    fun import(context: Context, input: InputStream): ThemeImportResult {
        // Read the pictures and the XML out of the zip; the music is never read (Android cannot play the Vita's ATRAC9).
        val files = HashMap<String, ByteArray>()
        var music: String? = null
        try {
            ZipInputStream(input.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val base = e.name.substringAfterLast('/').substringAfterLast('\\').lowercase()
                    when {
                        base.endsWith(".png") || base.endsWith(".jpg") || base.endsWith(".jpeg") || base.endsWith(".xml") -> {
                            val data = z.readBytes()
                            if (data.size <= 12 * 1024 * 1024) files[base] = data
                        }
                        base.endsWith(".at9") || base.endsWith(".ogg") || base.endsWith(".mp3") || base.endsWith(".wav") || base.endsWith(".m4a") -> music = base
                    }
                }
            }
        } catch (e: Exception) {
            return ThemeImportResult(null, "That file could not be opened as a zip: ${e.message}")
        }
        val xmlBytes = files["theme.xml"] ?: return ThemeImportResult(null, "There is no theme.xml in that file, so it is not a Vita theme.")
        val raw = xmlBytes.decodeToString()
        val start = raw.indexOf("<theme")
        if (start < 0) return ThemeImportResult(null, "theme.xml does not look like a Vita theme.")
        val xml = raw.substring(start).replace("`", "")

        val info = block(xml, "InfomationProperty").orEmpty()
        val startScreen = block(xml, "StartScreenProperty").orEmpty()
        val bar = block(xml, "InfomationBarProperty").orEmpty()
        val home = block(xml, "HomeProperty").orEmpty()
        val name = block(info, "m_title")?.let { value(it, "m_default") } ?: "Imported Vita theme"
        val author = block(info, "m_provider")?.let { value(it, "m_default") }.orEmpty()

        val id = ThemePacks.newId()
        val dir = ThemePacks.folder(context, id).apply { mkdirs() }
        val notes = ArrayList<String>()
        fun picture(fileName: String?, out: String, maxWidth: Int): String? {
            val bytes = fileName?.let { files[it.substringAfterLast('/').lowercase()] } ?: return null
            val bmp = decode(bytes, maxWidth) ?: return null
            File(dir, out).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            return out
        }

        // Page backgrounds, in order.
        val params = Regex("<BackgroundParam>(.*?)</BackgroundParam>", RegexOption.DOT_MATCHES_ALL).findAll(home).map { it.groupValues[1] }.toList()
        val pages = ArrayList<String>()
        params.take(10).forEachIndexed { i, p ->
            picture(value(p, "m_imageFilePath"), "page$i.jpg", 1920)?.let { pages.add(it) }
        }
        if (params.size > pages.size) notes.add("${params.size - pages.size} page background(s) could not be read")
        val nameColor = params.firstNotNullOfOrNull { color(value(it, "m_fontColor")) }

        val lock = picture(value(startScreen, "m_filePath"), "lock.jpg", 1920)
        val preview = picture(value(info, "m_homePreviewFilePath"), "preview.jpg", 480)
        val dateColor = color(value(startScreen, "m_dateColor"))
        val barColor = color(value(bar, "m_barColor"))
        val indicatorColor = color(value(bar, "m_indicatorColor"))

        // The icons for the system apps: <m_settings><m_iconFilePath>icon_settings.png</m_iconFilePath></m_settings> and so on.
        val icons = LinkedHashMap<String, String>()
        for (m in Regex("<m_([A-Za-z0-9]+)>\\s*<m_iconFilePath>\\s*([^<]*?)\\s*</m_iconFilePath>").findAll(home)) {
            val key = m.groupValues[1]
            val bytes = files[m.groupValues[2].substringAfterLast('/').lowercase()] ?: continue
            val out = "icon_$key.png"
            File(dir, out).writeBytes(bytes)
            icons[key] = out
        }

        if (pages.isEmpty() && lock == null) {
            dir.deleteRecursively()
            return ThemeImportResult(null, "That theme has no page backgrounds or lock screen picture that could be read.")
        }
        ThemePacks.save(context, ThemePack(id, name, author, pages, lock, preview, nameColor, dateColor, barColor, indicatorColor, icons))

        val used = buildList {
            add("${pages.size} page background${if (pages.size == 1) "" else "s"}")
            if (lock != null) add("lock screen picture")
            val colours = listOfNotNull(nameColor, dateColor, barColor, indicatorColor).size
            if (colours > 0) add("$colours colour${if (colours == 1) "" else "s"}")
            val mapped = icons.keys.count { it in setOf("settings", "browser", "hostCollabo") }
            if (mapped > 0) add("$mapped icon${if (mapped == 1) "" else "s"} for built-in bubbles")
        }
        if (music != null) notes.add(if (music!!.endsWith(".at9")) "background music ($music) is in the Vita's ATRAC9 format, which Android cannot play" else "background music ($music) is not used yet")
        notes.add("the Vita's animated waves over the wallpaper are not reproduced")
        return ThemeImportResult(id, "Imported “$name”: " + used.joinToString(", ") + ". Left out: " + notes.joinToString("; ") + ".")
    }

    private fun block(text: String, tag: String): String? {
        val s = text.indexOf("<$tag>")
        if (s < 0) return null
        val e = text.indexOf("</$tag>", s)
        if (e < 0) return null
        return text.substring(s + tag.length + 2, e)
    }

    private fun value(text: String, tag: String): String? =
        Regex("<$tag>\\s*([^<]*?)\\s*</$tag>").find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /** A colour written as 8 hex digits (AARRGGBB), with or without 0x or #. */
    private fun color(s: String?): Int? {
        val t = s?.trim()?.removePrefix("0x")?.removePrefix("#") ?: return null
        if (t.length != 8 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return java.lang.Long.parseLong(t, 16).toInt()
    }

    private fun decode(bytes: ByteArray, maxWidth: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
