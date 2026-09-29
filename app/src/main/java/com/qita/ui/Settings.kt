package com.qita.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

data class Theme(val name: String, val top: Color, val bottom: Color)

val THEMES = listOf(
    Theme("Ocean", Color(0xFF1466C8), Color(0xFF4FB6F0)),
    Theme("Twilight", Color(0xFF4A1E9E), Color(0xFFB061E8)),
    Theme("Forest", Color(0xFF0F6B3A), Color(0xFF6BD08A)),
    Theme("Sunset", Color(0xFFD8452A), Color(0xFFFFB35C)),
    Theme("Sakura", Color(0xFFD8477F), Color(0xFFFFB6D0)),
    Theme("Midnight", Color(0xFF0A0F2C), Color(0xFF2A3A7A)),
)

/** Bubble rows per page. The page size is the sum of a layout's rows. */
val LAYOUTS = listOf(
    "Staggered" to listOf(4, 3, 3),
    "Grid" to listOf(4, 4, 4),
    "Wide" to listOf(5, 5),
)

data class Settings(
    val themeIndex: Int = 0,
    val particles: Boolean = true,
    val bubbleScale: Float = 1f,
    val use24h: Boolean = false,
    val showBattery: Boolean = true,
    val sortNewest: Boolean = false,
    val layoutIndex: Int = 0,
    val roundedBubbles: Boolean = false,
    val showLabels: Boolean = true,
    val showDots: Boolean = true,
    val particleCount: Int = 28,
    val dim: Float = 0f,
    val haptics: Boolean = true,
) {
    val theme: Theme get() = THEMES[themeIndex.coerceIn(THEMES.indices)]
}

/** Persists [Settings] in SharedPreferences and the custom wallpaper as a file in app storage. */
class SettingsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("qita_settings", Context.MODE_PRIVATE)
    private val wallpaperFile get() = File(context.filesDir, "wallpaper.jpg")

    fun load() = Settings(
        themeIndex = prefs.getInt("theme", 0),
        particles = prefs.getBoolean("particles", true),
        bubbleScale = prefs.getFloat("bubbleScale", 1f),
        use24h = prefs.getBoolean("use24h", false),
        showBattery = prefs.getBoolean("showBattery", true),
        sortNewest = prefs.getBoolean("sortNewest", false),
        layoutIndex = prefs.getInt("layout", 0),
        roundedBubbles = prefs.getBoolean("rounded", false),
        showLabels = prefs.getBoolean("labels", true),
        showDots = prefs.getBoolean("dots", true),
        particleCount = prefs.getInt("particleCount", 28),
        dim = prefs.getFloat("dim", 0f),
        haptics = prefs.getBoolean("haptics", true),
    )

    fun save(s: Settings) {
        prefs.edit()
            .putInt("theme", s.themeIndex)
            .putBoolean("particles", s.particles)
            .putFloat("bubbleScale", s.bubbleScale)
            .putBoolean("use24h", s.use24h)
            .putBoolean("showBattery", s.showBattery)
            .putBoolean("sortNewest", s.sortNewest)
            .putInt("layout", s.layoutIndex)
            .putBoolean("rounded", s.roundedBubbles)
            .putBoolean("labels", s.showLabels)
            .putBoolean("dots", s.showDots)
            .putInt("particleCount", s.particleCount)
            .putFloat("dim", s.dim)
            .putBoolean("haptics", s.haptics)
            .apply()
    }

    fun loadOrder(): List<String> =
        prefs.getString("order", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun saveOrder(order: List<String>) {
        prefs.edit().putString("order", order.joinToString("\n")).apply()
    }

    fun loadHidden(): Set<String> = prefs.getStringSet("hidden", emptySet()).orEmpty().toSet()

    fun saveHidden(hidden: Set<String>) {
        prefs.edit().putStringSet("hidden", hidden).apply()
    }

    fun loadWallpaper(): ImageBitmap? =
        if (wallpaperFile.exists()) BitmapFactory.decodeFile(wallpaperFile.path)?.asImageBitmap() else null

    /** Copies the picked image into app storage (downscaled) and returns it, or null on failure. */
    fun saveWallpaper(uri: Uri): ImageBitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2400) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        // Camera photos are often stored rotated with an EXIF tag; apply it so the wallpaper is upright.
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val upright = if (degrees == 0f) bmp else
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        wallpaperFile.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        upright.asImageBitmap()
    }.getOrNull()

    fun clearWallpaper() {
        wallpaperFile.delete()
    }
}
