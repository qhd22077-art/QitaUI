package com.qita.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** What the launcher knows about one game beyond its file: how often and how long it was played, the user's notes and tags, and the pictures they added. */
data class GameStat(
    val launches: Int = 0,
    val playMs: Long = 0,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    /** File names of the user's own screenshots (inside the game's pictures folder). */
    val shots: List<String> = emptyList(),
)

/**
 * Play counts, play time, notes, tags and pictures for each game, kept in the app's storage.
 * Play time is the time between starting a game from the launcher and coming back to it, so for an emulator it is an estimate
 * (it also counts menus); for Flash games, which are played inside the launcher, it is exact.
 */
object GameStats {
    /** Goes up whenever anything here changes; screens read it to redraw. */
    var rev by mutableIntStateOf(0)
        private set

    private var data: HashMap<String, GameStat>? = null

    private fun prefs(c: Context) = c.getSharedPreferences("qita_game_stats", Context.MODE_PRIVATE)

    @Synchronized private fun all(c: Context): HashMap<String, GameStat> {
        data?.let { return it }
        val map = HashMap<String, GameStat>()
        runCatching {
            val o = JSONObject(prefs(c).getString("stats", "{}").orEmpty())
            for (k in o.keys()) {
                val s = o.getJSONObject(k)
                fun list(name: String) = s.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                map[k] = GameStat(s.optInt("l"), s.optLong("t"), s.optString("n"), list("g"), list("s"))
            }
        }
        data = map
        return map
    }

    @Synchronized private fun put(c: Context, id: String, stat: GameStat) {
        val map = all(c)
        if (stat == GameStat()) map.remove(id) else map[id] = stat
        val o = JSONObject()
        for ((k, s) in map) {
            o.put(k, JSONObject().put("l", s.launches).put("t", s.playMs).put("n", s.notes).put("g", JSONArray(s.tags)).put("s", JSONArray(s.shots)))
        }
        prefs(c).edit().putString("stats", o.toString()).apply()
        rev++
    }

    fun get(c: Context, id: String): GameStat = all(c)[id] ?: GameStat()

    /** Every tag in use, most used first. */
    fun tagsInUse(c: Context): List<String> =
        all(c).values.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key }

    /** True if the user's notes or tags for [id] contain [query]. */
    fun matches(c: Context, id: String, query: String): Boolean {
        val s = all(c)[id] ?: return false
        return s.notes.contains(query, true) || s.tags.any { it.contains(query, true) }
    }

    fun setNotes(c: Context, id: String, notes: String) = put(c, id, get(c, id).copy(notes = notes.trim()))

    /** Sets the tags from a comma separated text: tidy, no repeats, at most 12. */
    fun setTags(c: Context, id: String, text: String) {
        val tags = text.split(',', ';').map { it.trim().take(24) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(12)
        put(c, id, get(c, id).copy(tags = tags))
    }

    // --- Play time ---

    /** A game was started from the launcher: counts it and notes the time, to be settled when the launcher is back on screen. */
    fun begin(c: Context, id: String) {
        settle(c)
        put(c, id, get(c, id).copy(launches = get(c, id).launches + 1))
        prefs(c).edit().putString("pending_id", id).putLong("pending_at", System.currentTimeMillis()).apply()
    }

    /** Adds the time since the last [begin] to that game, if it is long enough to be real play (not a game that failed to open) and under 12 hours. */
    fun settle(c: Context, minMs: Long = 30_000L) {
        val p = prefs(c)
        val id = p.getString("pending_id", null) ?: return
        val elapsed = System.currentTimeMillis() - p.getLong("pending_at", 0L)
        p.edit().remove("pending_id").remove("pending_at").apply()
        if (elapsed in minMs..(12L * 3600_000L)) put(c, id, get(c, id).copy(playMs = get(c, id).playMs + elapsed))
    }

    fun hasPending(c: Context): Boolean = prefs(c).getString("pending_id", null) != null

    /** "2 h 5 min", "12 min" or "under a minute". */
    fun duration(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> "under a minute"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }

    // --- Pictures ---

    private fun dir(c: Context, id: String) = File(File(c.filesDir, "shots"), id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifEmpty { "x" })

    /** The pictures of [id] in order: in-game, title screen (fetched), then the user's own. */
    fun pictures(c: Context, id: String): List<File> {
        val d = dir(c, id)
        val own = get(c, id).shots.map { File(d, it) }
        return (listOf(File(d, "snap.png"), File(d, "title.png")) + own).filter { it.exists() && it.length() > 0 }
    }

    /** True for a picture the user added (it can be deleted). */
    fun isOwn(c: Context, id: String, file: File): Boolean = file.name in get(c, id).shots

    /** Whether the online pictures were already looked for. */
    fun fetched(c: Context, id: String): Boolean = File(dir(c, id), ".tried").exists()

    /**
     * Gets an in-game picture and the title screen from the same server as the cover art, matching the name the same way.
     * Blocks, so call it off the main thread. Looks only once per game unless the server could not be reached.
     */
    fun fetchPictures(c: Context, game: Game) {
        runCatching {
            val system = systemById(game.systemId) ?: return
            val folder = system.thumbs ?: return
            val d = dir(c, game.id).apply { mkdirs() }
            var reached = false
            fun get(kind: String, name: String): Bitmap? = runCatching {
                val safe = name.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
                val enc = { s: String -> URLEncoder.encode(s, "UTF-8").replace("+", "%20") }
                val conn = URL("https://thumbnails.libretro.com/${enc(folder)}/$kind/${enc(safe)}.png").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                val code = conn.responseCode
                reached = true
                if (code != 200) { conn.disconnect(); return@runCatching null }
                val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                conn.disconnect()
                bmp
            }.getOrNull()
            val tidy = CoverMatch.tidy(game.raw)
            val match by lazy { CoverMatch.best(game.raw, CoverIndex.names(c, system)) }
            for ((kind, file) in listOf("Named_Snaps" to "snap.png", "Named_Titles" to "title.png")) {
                val bmp = get(kind, game.raw) ?: (if (tidy != game.raw) get(kind, tidy) else null) ?: match?.let { get(kind, it) }
                if (bmp != null) File(d, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (reached) File(d, ".tried").writeText("1")
            rev++
        }
    }

    /** Adds a picture from the device: upright and no bigger than 1280 px. Returns false if it could not be read. */
    fun addPicture(c: Context, id: String, uri: Uri): Boolean = runCatching {
        val r = c.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        val bmp = r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return false
        val degrees = r.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val upright = if (degrees == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        val d = dir(c, id).apply { mkdirs() }
        val name = "u${System.currentTimeMillis()}.jpg"
        File(d, name).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        put(c, id, get(c, id).copy(shots = get(c, id).shots + name))
        true
    }.getOrDefault(false)

    fun removePicture(c: Context, id: String, file: File) {
        if (!isOwn(c, id, file)) return
        file.delete()
        put(c, id, get(c, id).copy(shots = get(c, id).shots - file.name))
    }

    /** Forgets everything about a game that left the library. */
    fun forget(c: Context, id: String) {
        dir(c, id).deleteRecursively()
        put(c, id, GameStat())
    }
}
