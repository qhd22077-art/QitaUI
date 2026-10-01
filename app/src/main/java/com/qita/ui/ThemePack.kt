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

    fun newId() = "t" + System.currentTimeMillis().toString(36)

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

    /** Puts the theme's icons on the built-in bubbles that have a matching Vita icon. */
    fun applyIcons(c: Context, pack: ThemePack?) {
        if (pack == null) { SystemIcons.overrides = emptyMap(); return }
        val dir = folder(c, pack.id)
        val map = HashMap<SystemAction, androidx.compose.ui.graphics.ImageBitmap>()
        for ((key, action) in listOf("settings" to SystemAction.SETTINGS, "browser" to SystemAction.BROWSER, "hostCollabo" to SystemAction.FOLDERS)) {
            val file = pack.icons[key]?.let { File(dir, it) } ?: continue
            runCatching { android.graphics.BitmapFactory.decodeFile(file.path) }.getOrNull()?.let { map[action] = it.asImageBitmap() }
        }
        SystemIcons.overrides = map
    }

    /** Reads the theme in use at start-up, so its icons are there before the first bubble is drawn. */
    fun loadActive(c: Context) {
        val id = active(c) ?: return
        applyIcons(c, load(c, id))
    }

    /** One `.qtheme` file: a zip of the theme's folder. */
    fun export(c: Context, id: String, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { z ->
            (folder(c, id).listFiles() ?: emptyArray()).filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
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
                    if (ext !in setOf("json", "jpg", "jpeg", "png")) continue
                    File(dir, name).outputStream().use { o -> z.copyTo(o) }
                    count++
                }
            }
            val json = File(dir, "theme.json")
            if (!json.exists()) { dir.deleteRecursively(); return ThemeImportResult(null, "That file is not a QitaUI theme (no theme.json inside).") }
            val pack = ThemePack.fromJson(id, JSONObject(json.readText()))
            save(c, pack)
            return ThemeImportResult(id, "Imported “${pack.name}”: ${pack.pages.size} page backgrounds" + (if (pack.lock != null) ", a lock screen picture" else "") + ", ${pack.icons.size} icons.")
        } catch (e: Exception) {
            dir.deleteRecursively()
            return ThemeImportResult(null, "That file could not be read as a theme: ${e.message}")
        }
    }
}
