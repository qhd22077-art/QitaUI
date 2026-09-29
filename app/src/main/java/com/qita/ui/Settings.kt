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
    val cursorMode: Boolean = false,
    val cursorSpeed: Float = 1f,
    val psLabels: Boolean = true,
    val swapAB: Boolean = false,
    val autoAdd: Boolean = false,
    val debugInput: Boolean = false,
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
        cursorMode = prefs.getBoolean("cursorMode", false),
        cursorSpeed = prefs.getFloat("cursorSpeed", 1f),
        psLabels = prefs.getBoolean("psLabels", true),
        swapAB = prefs.getBoolean("swapAB", false),
        autoAdd = prefs.getBoolean("autoAdd", false),
        debugInput = prefs.getBoolean("debugInput", false),
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
            .putBoolean("cursorMode", s.cursorMode)
            .putFloat("cursorSpeed", s.cursorSpeed)
            .putBoolean("psLabels", s.psLabels)
            .putBoolean("swapAB", s.swapAB)
            .putBoolean("autoAdd", s.autoAdd)
            .putBoolean("debugInput", s.debugInput)
            .apply()
    }

    /** Packages shown on the home screen, in display order. Empty until the user adds apps. */
    fun loadHome(): List<String> =
        prefs.getString("home", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun saveHome(home: List<String>) {
        prefs.edit().putString("home", home.joinToString("\n")).apply()
    }

    fun tutorialSeen(): Boolean = prefs.getBoolean("tutorialSeen", false)

    fun setTutorialSeen() {
        prefs.edit().putBoolean("tutorialSeen", true).apply()
    }

    /** Packages the launcher has already seen, so newly installed apps can be detected. */
    fun loadKnown(): Set<String> = prefs.getStringSet("known", emptySet()).orEmpty().toSet()

    fun saveKnown(known: Set<String>) {
        prefs.edit().putStringSet("known", known).apply()
    }

    fun recordLaunch(packageName: String) {
        prefs.edit().putInt("launch_$packageName", prefs.getInt("launch_$packageName", 0) + 1).apply()
    }

    /** How many times each app was launched from this launcher. */
    fun loadLaunchCounts(): Map<String, Int> =
        prefs.all.filterKeys { it.startsWith("launch_") }
            .mapKeys { it.key.removePrefix("launch_") }
            .mapValues { (it.value as? Int) ?: 0 }

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
