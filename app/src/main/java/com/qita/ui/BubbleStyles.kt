package com.qita.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.File

/** How one built-in bubble looks beyond its default: how see-through it is, a clear-glass look, a tint and a picture of its own. */
data class BubbleStyle(
    /** 1 = solid, lower = more translucent. */
    val alpha: Float = 1f,
    val glass: Boolean = false,
    /** ARGB tint for the bubble's background (or, with [glass], its glass), or null for the usual look. */
    val tint: Int? = null,
    /** True when the user chose a picture (kept in a file, see [BubbleStyles.picture]). */
    val picture: Boolean = false,
) {
    val isDefault: Boolean get() = alpha >= 0.999f && !glass && tint == null && !picture
}

/** The looks the user gave the built-in bubbles, kept in the app's storage. */
object BubbleStyles {
    /** By built-in id. A state, so a bubble reads its translucency while it draws and follows a slider without being rebuilt. */
    var all by mutableStateOf<Map<String, BubbleStyle>>(emptyMap())
        private set

    /** Goes up whenever the art of a bubble has to be redrawn (everything but translucency); the home screen reads it. */
    var artRev by mutableIntStateOf(0)
        private set

    private var prefs: SharedPreferences? = null
    private var dir: File? = null

    fun load(c: Context) {
        val p = c.getSharedPreferences("qita_bubble_styles", Context.MODE_PRIVATE)
        prefs = p
        dir = File(c.filesDir, "bubbles").apply { mkdirs() }
        all = runCatching {
            val o = JSONObject(p.getString("styles", "{}") ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val s = o.getJSONObject(k)
                BubbleStyle(s.optDouble("a", 1.0).toFloat(), s.optBoolean("g"), if (s.has("t")) s.getInt("t") else null, s.optBoolean("p"))
            }
        }.getOrDefault(emptyMap())
    }

    fun of(id: String): BubbleStyle = all[id] ?: BubbleStyle()

    fun set(id: String, style: BubbleStyle) {
        val old = of(id)
        all = if (style.isDefault) all - id else all + (id to style)
        val o = JSONObject()
        for ((k, s) in all) {
            val j = JSONObject().put("a", s.alpha.toDouble()).put("g", s.glass).put("p", s.picture)
            if (s.tint != null) j.put("t", s.tint)
            o.put(k, j)
        }
        prefs?.edit()?.putString("styles", o.toString())?.apply()
        // Translucency is applied while drawing; anything else is baked into the bubble's art.
        if (old.copy(alpha = style.alpha) != style) {
            SystemIcons.cache = null
            artRev++
        }
    }

    /** Back to the built-in look, deleting the picture. */
    fun reset(id: String) {
        pictureFile(id)?.delete()
        set(id, BubbleStyle())
    }

    private fun pictureFile(id: String): File? = dir?.let { File(it, id.filter { ch -> ch.isLetterOrDigit() } + ".jpg") }

    fun picture(id: String): ImageBitmap? = runCatching {
        pictureFile(id)?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
    }.getOrNull()

    /** Saves a picked image as the picture for [id]: upright, cut to a square and no bigger than 512 px. */
    fun savePicture(c: Context, id: String, uri: Uri): Boolean = runCatching {
        val file = pictureFile(id) ?: return false
        val r = c.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
        val bmp = r.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val degrees = r.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val upright = if (degrees == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        val side = minOf(upright.width, upright.height)
        val square = Bitmap.createBitmap(upright, (upright.width - side) / 2, (upright.height - side) / 2, side, side)
        val out = if (side > 512) Bitmap.createScaledBitmap(square, 512, 512, true) else square
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    }.getOrDefault(false)
}
