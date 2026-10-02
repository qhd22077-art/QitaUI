package com.qita.ui

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One saved theme, as listed in Settings. */
data class ThemePackInfo(val id: String, val name: String, val author: String)

/** What an import did: the new theme's id (null if nothing could be used) and a short report for the user. */
class ThemeImportResult(val id: String?, val report: String)

/**
 * A saved theme: page wallpapers, a lock screen picture, colours and icons, kept as files in the app's storage (not in memory).
 * The names in [pages], [lock], [preview] and [icons] are files inside the theme's own folder.
 */
class ThemePack(
    val id: String,
    val name: String,
    val author: String,
    val pages: List<String>,
    val lock: String?,
    val preview: String?,
    val nameColor: Int?,
    val dateColor: Int?,
    val barColor: Int?,
    val indicatorColor: Int?,
    /** The Vita's icon names (settings, browser, hostCollabo, trophy...) and the picture file for each. */
    val icons: Map<String, String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("author", author)
        .put("pages", org.json.JSONArray(pages)).put("lock", lock ?: "").put("preview", preview ?: "")
        .put("nameColor", nameColor ?: JSONObject.NULL).put("dateColor", dateColor ?: JSONObject.NULL)
        .put("barColor", barColor ?: JSONObject.NULL).put("indicatorColor", indicatorColor ?: JSONObject.NULL)
        .put("icons", JSONObject(icons))

    companion object {
        fun fromJson(id: String, o: JSONObject): ThemePack {
            fun colour(k: String) = if (o.isNull(k) || !o.has(k)) null else o.getInt(k)
            val p = o.optJSONArray("pages")
            val ic = o.optJSONObject("icons")
            return ThemePack(
                id, o.optString("name", "Theme"), o.optString("author"),
                (0 until (p?.length() ?: 0)).map { p!!.getString(it) },
                o.optString("lock").ifEmpty { null }, o.optString("preview").ifEmpty { null },
                colour("nameColor"), colour("dateColor"), colour("barColor"), colour("indicatorColor"),
                ic?.keys()?.asSequence()?.associateWith { ic.getString(it) } ?: emptyMap(),
            )
        }
    }
}

/** Where saved themes live, and the one that is in use. */
object ThemePacks {
    private fun root(c: Context) = File(c.filesDir, "themes").apply { mkdirs() }
    private fun prefs(c: Context) = c.getSharedPreferences("qita_theme_pack", Context.MODE_PRIVATE)

    private val seq = java.util.concurrent.atomic.AtomicInteger()

    fun newId() = "t" + System.currentTimeMillis().toString(36) + seq.incrementAndGet().toString(36)

    fun folder(c: Context, id: String) = File(root(c), id.filter { it.isLetterOrDigit() })

    fun list(c: Context): List<ThemePackInfo> =
        (root(c).listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { d -> load(c, d.name)?.let { ThemePackInfo(it.id, it.name, it.author) } }
            .sortedBy { it.name.lowercase() }

    fun load(c: Context, id: String): ThemePack? = runCatching {
        ThemePack.fromJson(id, JSONObject(File(folder(c, id), "theme.json").readText()))
    }.getOrNull()

    fun save(c: Context, pack: ThemePack) {
        val dir = folder(c, pack.id).apply { mkdirs() }
        File(dir, "theme.json").writeText(pack.toJson().toString())
    }

    fun active(c: Context): String? = prefs(c).getString("active", null)?.ifEmpty { null }

    fun setActive(c: Context, id: String?) { prefs(c).edit().putString("active", id ?: "").apply() }

    fun delete(c: Context, id: String) {
        folder(c, id).deleteRecursively()
        if (active(c) == id) { setActive(c, null); SystemIcons.overrides = emptyMap() }
    }

    /** The Vita icon names (lower case) that have a matching built-in bubble. */
    val ICON_KEYS = listOf("settings", "browser", "hostcollabo")

    /** Which of the theme's icons the user has switched on (by Vita icon name, lower case). None by default: a theme leaves the built-in art alone. */
    fun iconsOn(c: Context): Set<String> = prefs(c).getStringSet("iconsOn", null)?.toSet().orEmpty()

    fun setIconsOn(c: Context, keys: Set<String>) { prefs(c).edit().putStringSet("iconsOn", HashSet(keys)).apply() }

    /** Puts the theme's icons on the built-in bubbles that have a matching Vita icon, for the ones switched on. */
    fun applyIcons(c: Context, pack: ThemePack?) {
        if (pack == null) { SystemIcons.overrides = emptyMap(); return }
        val dir = folder(c, pack.id)
        val map = HashMap<SystemAction, androidx.compose.ui.graphics.ImageBitmap>()
        val icons = pack.icons.mapKeys { it.key.lowercase() }
        val on = iconsOn(c)
        for ((key, action) in listOf("settings" to SystemAction.SETTINGS, "browser" to SystemAction.BROWSER, "hostcollabo" to SystemAction.FOLDERS)) {
            if (key !in on) continue
            val file = icons[key]?.let { File(dir, it) } ?: continue
            runCatching { android.graphics.BitmapFactory.decodeFile(file.path) }.getOrNull()?.let { map[action] = it.asImageBitmap() }
        }
        SystemIcons.overrides = map
    }

    /**
     * Adds the themes that come with the app (once): "PlayStation Games Vita Themes" by Lich_Kiingg, a free Vita theme, in a smaller
     * form (the pictures as JPEG, no music). It goes in the list like any imported theme; it is not applied by itself.
     */
    fun installBundled(c: Context) {
        val p = prefs(c)
        if (p.getBoolean("bundled2", false)) return
        // The first version (sharpened pictures came later) is replaced, keeping the choice of the active theme.
        val old = list(c).firstOrNull { it.author == "Lich_Kiingg" }?.id
        val result = runCatching {
            c.assets.open("themes/playstation-games.zip").use { VitaThemeImport.import(c, it) }
        }.getOrNull()
        if (result?.id != null && old != null) {
            val wasActive = active(c) == old
            delete(c, old)
            if (wasActive) setActive(c, result.id)
        }
        p.edit().putBoolean("bundled2", true).apply()
    }

    /** Reads the theme in use at start-up, so its icons are there before the first bubble is drawn. */
    fun loadActive(c: Context) {
        val id = active(c) ?: return
        applyIcons(c, load(c, id))
    }

    /** Whether themes carry the user's sounds and system bubble looks (when saved) and put a theme's own on (when applied). On by default. */
    fun extrasOn(c: Context): Boolean = prefs(c).getBoolean("extras", true)

    fun setExtrasOn(c: Context, on: Boolean) { prefs(c).edit().putBoolean("extras", on).apply() }

    fun hasExtras(c: Context, id: String): Boolean = File(folder(c, id), "extras.json").exists()

    /**
     * The user's own sounds and system bubble looks as a description (extras.json) and the files it names: the clips of sounds they
     * chose, the pictures of bubbles they gave one. Null if nothing is changed from the defaults.
     */
    private fun buildExtras(c: Context): Pair<String, Map<String, File>>? {
        val files = LinkedHashMap<String, File>()
        val bubbles = JSONObject()
        for ((bid, s) in BubbleStyles.all) {
            val j = JSONObject().put("a", s.alpha.toDouble()).put("gm", when (s.glass) { true -> 1; false -> 2; null -> 0 })
            if (s.tint != null) j.put("t", s.tint)
            if (s.picture) BubbleStyles.pictureFile(bid)?.takeIf { it.exists() }?.let { f ->
                val n = "bubble_${bid.filter { ch -> ch.isLetterOrDigit() }}.jpg"
                files[n] = f
                j.put("p", n)
            }
            bubbles.put(bid, j)
        }
        val sounds = JSONObject()
        for (s in Sound.values()) {
            val mode = Sounds.mode(c, s)
            if (mode == 0) continue
            val j = JSONObject().put("mode", mode)
            if (mode == 1) {
                val f = Sounds.userFile(c, s)
                if (!f.exists() || f.length() == 0L) continue
                val n = "sound_${s.id}.snd"
                files[n] = f
                j.put("file", n)
            }
            sounds.put(s.id, j)
        }
        if (bubbles.length() == 0 && sounds.length() == 0) return null
        return JSONObject().put("bubbles", bubbles).put("sounds", sounds).toString() to files
    }

    /** Makes a theme of its own that holds only the user's sounds and system bubble looks (no pictures). Returns its id, or null if there is nothing to hold. */
    fun makeStylePack(c: Context, name: String): String? {
        val (json, files) = buildExtras(c) ?: return null
        val id = newId()
        val dir = folder(c, id).apply { mkdirs() }
        File(dir, "extras.json").writeText(json)
        for ((n, f) in files) runCatching { f.copyTo(File(dir, n), overwrite = true) }
        save(c, ThemePack(id, name, "", emptyList(), null, null, null, null, null, null, emptyMap()))
        return id
    }

    /** Puts the theme's sounds and system bubble looks on, if it carries any. Returns what was set ("2 bubble looks, 3 sounds"), or null. */
    fun applyExtras(c: Context, id: String): String? = runCatching {
        val dir = folder(c, id)
        val o = JSONObject(File(dir, "extras.json").takeIf { it.exists() }?.readText() ?: return null)
        var nb = 0
        var ns = 0
        o.optJSONObject("bubbles")?.let { b ->
            for (bid in b.keys()) {
                if (bid !in SYSTEM_IDS) continue
                val j = b.getJSONObject(bid)
                val pic = j.optString("p").takeIf { it.isNotEmpty() }?.let { File(dir, File(it).name) }?.takeIf { it.exists() }
                if (pic != null) BubbleStyles.pictureFile(bid)?.let { target -> runCatching { pic.copyTo(target, overwrite = true) } }
                val glass: Boolean? = when (j.optInt("gm")) { 1 -> true; 2 -> false; else -> null }
                BubbleStyles.set(bid, BubbleStyle(j.optDouble("a", 1.0).toFloat(), glass, if (j.has("t")) j.getInt("t") else null, pic != null))
                nb++
            }
        }
        o.optJSONObject("sounds")?.let { sj ->
            for (s in Sound.values()) {
                val j = sj.optJSONObject(s.id) ?: continue
                when (j.optInt("mode")) {
                    2 -> { Sounds.setMode(c, s, 2); ns++ }
                    1 -> {
                        val f = j.optString("file").takeIf { it.isNotEmpty() }?.let { File(dir, File(it).name) }?.takeIf { it.exists() } ?: continue
                        f.copyTo(Sounds.userFile(c, s), overwrite = true)
                        Sounds.setMode(c, s, 1)
                        ns++
                    }
                }
            }
        }
        if (nb == 0 && ns == 0) null
        else listOfNotNull(if (nb > 0) "$nb bubble look${if (nb == 1) "" else "s"}" else null, if (ns > 0) "$ns sound${if (ns == 1) "" else "s"}" else null).joinToString(", ")
    }.getOrNull()

    /** One `.qtheme` file: a zip of the theme's folder, plus the user's sounds and bubble looks if that is switched on (and the theme has none of its own). */
    fun export(c: Context, id: String, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { z ->
            (folder(c, id).listFiles() ?: emptyArray()).filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
            if (extrasOn(c) && !hasExtras(c, id)) buildExtras(c)?.let { (json, files) ->
                z.putNextEntry(ZipEntry("extras.json")); z.write(json.toByteArray()); z.closeEntry()
                for ((n, f) in files) {
                    z.putNextEntry(ZipEntry(n))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    /** Reads a `.qtheme` file into a new theme of its own. */
    fun importPack(c: Context, input: InputStream): ThemeImportResult {
        val id = newId()
        val dir = folder(c, id).apply { mkdirs() }
        var count = 0
        try {
            ZipInputStream(input.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    // Only plain files in the theme's own folder: no paths, and only the kinds a theme holds.
                    val name = File(e.name).name
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in setOf("json", "jpg", "jpeg", "png", "snd")) continue
                    File(dir, name).outputStream().use { o -> z.copyTo(o) }
                    count++
                }
            }
            val json = File(dir, "theme.json")
            if (!json.exists()) { dir.deleteRecursively(); return ThemeImportResult(null, "That file is not a QitaUI theme (no theme.json inside).") }
            val pack = ThemePack.fromJson(id, JSONObject(json.readText()))
            save(c, pack)
            return ThemeImportResult(id, "Imported “${pack.name}”: ${pack.pages.size} page backgrounds" + (if (pack.lock != null) ", a lock screen picture" else "") + ", ${pack.icons.size} icons" + (if (hasExtras(c, id)) ", and its own sounds and bubble looks" else "") + ".")
        } catch (e: Exception) {
            dir.deleteRecursively()
            return ThemeImportResult(null, "That file could not be read as a theme: ${e.message}")
        }
    }
}
