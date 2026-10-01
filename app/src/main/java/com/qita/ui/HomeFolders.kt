package com.qita.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject

/** A folder bubble on the home screen: its name and the ids of the bubbles inside it. The home list holds the folder's id in their place. */
data class HomeFolder(val id: String, val name: String, val members: List<String>)

/** Where the folder bubbles are kept. */
object HomeFolders {
    const val PREFIX = "qita.folder."

    fun isFolder(id: String) = id.startsWith(PREFIX)

    /** The folder every game of one console goes into. */
    fun consoleId(systemId: String) = "${PREFIX}sys.$systemId"

    fun newId() = PREFIX + java.lang.Long.toHexString(System.nanoTime())

    private fun prefs(c: Context) = c.getSharedPreferences("qita_home_folders", Context.MODE_PRIVATE)

    fun load(c: Context): List<HomeFolder> = runCatching {
        val arr = JSONArray(prefs(c).getString("folders", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val m = o.getJSONArray("members")
            HomeFolder(o.getString("id"), o.getString("name"), (0 until m.length()).map { m.getString(it) })
        }
    }.getOrDefault(emptyList())

    fun save(c: Context, list: List<HomeFolder>) {
        val arr = JSONArray()
        list.forEach { f -> arr.put(JSONObject().put("id", f.id).put("name", f.name).put("members", JSONArray(f.members))) }
        prefs(c).edit().putString("folders", arr.toString()).apply()
    }
}

/** The little picture a folder bubble shows: up to six of its bubbles' icons arranged in a cluster, on clear glass. */
object FolderArt {
    private val cache = LruCache<String, ImageBitmap>(24)

    fun icon(id: String, members: List<LaunchableApp>): ImageBitmap {
        val shown = members.take(6)
        // The icons themselves are part of the key, so a cover that arrives later replaces the old picture.
        val key = id + shown.joinToString("|") { it.packageName + System.identityHashCode(it.icon) }
        cache.get(key)?.let { return it }
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val n = shown.size
        // Centres (as fractions of the picture) and the icon radius for each count, like the Vita's folders.
        val centres: List<Pair<Float, Float>> = when (n) {
            0 -> emptyList()
            1 -> listOf(0.5f to 0.5f)
            2 -> listOf(0.32f to 0.5f, 0.68f to 0.5f)
            3 -> listOf(0.5f to 0.30f, 0.30f to 0.66f, 0.70f to 0.66f)
            4 -> listOf(0.33f to 0.33f, 0.67f to 0.33f, 0.33f to 0.67f, 0.67f to 0.67f)
            5 -> listOf(0.5f to 0.22f, 0.24f to 0.43f, 0.76f to 0.43f, 0.34f to 0.74f, 0.66f to 0.74f)
            else -> listOf(0.5f to 0.20f, 0.25f to 0.35f, 0.75f to 0.35f, 0.25f to 0.65f, 0.75f to 0.65f, 0.5f to 0.80f)
        }
        val radius = when (n) { 1 -> 0.34f; 2, 3 -> 0.22f; 4 -> 0.20f; else -> 0.16f } * size
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xCCFFFFFF.toInt() }
        shown.forEachIndexed { i, app ->
            val (fx, fy) = centres[i]
            val cx = fx * size
            val cy = fy * size
            runCatching {
                var src = app.icon.asAndroidBitmap()
                // A hardware bitmap cannot be drawn onto this canvas.
                if (src.config == Bitmap.Config.HARDWARE) src = src.copy(Bitmap.Config.ARGB_8888, false)
                canvas.save()
                canvas.clipPath(Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) })
                canvas.drawBitmap(src, null, RectF(cx - radius, cy - radius, cx + radius, cy + radius), paint)
                canvas.restore()
                canvas.drawCircle(cx, cy, radius, ring)
            }
        }
        val out = bmp.asImageBitmap()
        cache.put(key, out)
        return out
    }
}
