package com.qita.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup of what playing builds up: play time, play counts, notes and tags, the user's own game pictures, favourites, the order of
 * last played, which emulator plays what, and the trophies. One zip file. Not included, on purpose: the RetroAchievements key (a secret),
 * the game folders (Android's access to them cannot be moved), the home layout and themes (themes have their own export), and the
 * settings (those have theirs).
 */
object ProgressBackup {
    /** The preference files that are saved, with the keys that are left out. */
    private val FILES = mapOf(
        "qita_game_stats" to setOf("pending_id", "pending_at"),
        "qita_trophies" to emptySet(),
        "qita_games" to setOf("folders"),
    )

    private fun dump(c: Context, name: String, skip: Set<String>): JSONObject {
        val out = JSONObject()
        c.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (k in skip) return@forEach
            val e = JSONObject()
            when (v) {
                is Boolean -> e.put("t", "b").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                is String -> e.put("t", "s").put("v", v)
                is Set<*> -> e.put("t", "S").put("v", JSONArray(v.filterIsInstance<String>()))
                else -> return@forEach
            }
            out.put(k, e)
        }
        return out
    }

    private fun apply(c: Context, name: String, data: JSONObject, skip: Set<String>) {
        val edit = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
        for (k in data.keys()) {
            if (k in skip) continue
            val e = data.getJSONObject(k)
            when (e.getString("t")) {
                "b" -> edit.putBoolean(k, e.getBoolean("v"))
                "i" -> edit.putInt(k, e.getInt("v"))
                "l" -> edit.putLong(k, e.getLong("v"))
                "f" -> edit.putFloat(k, e.getDouble("v").toFloat())
                "s" -> edit.putString(k, e.getString("v"))
                "S" -> edit.putStringSet(k, e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
            }
        }
        edit.apply()
    }

    private fun shotsDir(c: Context) = File(c.filesDir, "shots")

    /** Pictures fetched from the internet are not saved (they can be fetched again); the user's own ones are. */
    private fun isOwnPicture(f: File) = f.isFile && f.name.startsWith("u") && f.name.endsWith(".jpg")

    /** Writes the backup to [out] (and closes it). Blocks. */
    fun write(c: Context, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            val root = JSONObject().put("version", 1)
            val prefs = JSONObject()
            for ((name, skip) in FILES) prefs.put(name, dump(c, name, skip))
            root.put("prefs", prefs)
            zip.putNextEntry(ZipEntry("progress.json"))
            zip.write(root.toString().toByteArray())
            zip.closeEntry()
            val base = shotsDir(c)
            base.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                dir.listFiles()?.filter { isOwnPicture(it) }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("shots/${dir.name}/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Applies a backup written by [write]. Returns false if the file is not one. Blocks; call [reloadAll] afterwards on the main thread. */
    fun read(c: Context, input: InputStream): Boolean {
        var json: JSONObject? = null
        val base = shotsDir(c).apply { mkdirs() }
        val baseCanonical = base.canonicalPath + File.separator
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "progress.json" -> json = runCatching { JSONObject(zip.readBytes().decodeToString()) }.getOrNull()
                    entry.name.startsWith("shots/") && !entry.isDirectory -> {
                        // Only plain "game folder / picture" paths inside the pictures folder are written.
                        val target = File(base, entry.name.removePrefix("shots/"))
                        if (target.canonicalPath.startsWith(baseCanonical) && isOwnPicture(target) && target.parentFile?.parentFile == base) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
        }
        val prefs = json?.optJSONObject("prefs") ?: return false
        for ((name, skip) in FILES) prefs.optJSONObject(name)?.let { apply(c, name, it, skip) }
        return true
    }

    /** Makes the screens and counters read what was just loaded. Main thread. */
    fun reloadAll(c: Context) {
        GameStats.reload()
        Trophies.reload(c)
    }
}
