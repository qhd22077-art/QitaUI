package com.qita.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

data class Settings(
    val themeIndex: Int = 0,
    val particles: Boolean = true,
    val bubbleScale: Float = 1f,
    val use24h: Boolean = false,
    val showBattery: Boolean = true,
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
    )

    fun save(s: Settings) {
        prefs.edit()
            .putInt("theme", s.themeIndex)
            .putBoolean("particles", s.particles)
            .putFloat("bubbleScale", s.bubbleScale)
            .putBoolean("use24h", s.use24h)
            .putBoolean("showBattery", s.showBattery)
            .apply()
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
        wallpaperFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.asImageBitmap()
    }.getOrNull()

    fun clearWallpaper() {
        wallpaperFile.delete()
    }
}
