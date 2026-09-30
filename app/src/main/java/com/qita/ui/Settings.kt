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
import com.qita.ui.ui.Scene

/** Marks a home page whose background is its own photo (see [SettingsStore.loadPageBg]). */
const val PAGE_PHOTO = -2

/** A wallpaper palette: the sky colour at the top, the middle band and the bright glow at the bottom. */
data class Theme(val name: String, val top: Color, val mid: Color, val bottom: Color, val scene: Scene = Scene.WAVES)

val THEMES = listOf(
    Theme("Vita Blue", Color(0xFF0A2C9A), Color(0xFF1B5BD8), Color(0xFFB4D8FF)),
    Theme("Cloud", Color(0xFF9DB4E8), Color(0xFFC5D2F2), Color(0xFFF1F5FF)),
    Theme("Emerald", Color(0xFF0B6B2B), Color(0xFF2FB344), Color(0xFFB8F5B0)),
    Theme("Violet", Color(0xFF3A1C8F), Color(0xFF6B44D6), Color(0xFFD8C8FF)),
    Theme("Sunset", Color(0xFFB8321A), Color(0xFFF0782C), Color(0xFFFFE0B0)),
    Theme("Midnight", Color(0xFF060B2A), Color(0xFF14246E), Color(0xFF5A78D0)),
    // Backgrounds with their own scenery, animated in layers at different depths.
    Theme("Aurora", Color(0xFF03081C), Color(0xFF0B2A4A), Color(0xFF0E4A5A), Scene.AURORA),
    Theme("Deep Sea", Color(0xFF52D0D8), Color(0xFF12708F), Color(0xFF031F3A), Scene.OCEAN),
    Theme("Crystals", Color(0xFF0A0620), Color(0xFF231052), Color(0xFF4A1A6E), Scene.CRYSTAL),
    Theme("Nebula", Color(0xFF02010A), Color(0xFF0B0724), Color(0xFF1A0F3A), Scene.SPACE),
    Theme("Neon Grid", Color(0xFF120033), Color(0xFFC2185B), Color(0xFFFF7043), Scene.GRID),
    Theme("Dunes", Color(0xFF3B1552), Color(0xFFE0567A), Color(0xFFFFB25B), Scene.DUNES),
)

/**
 * Where bubbles sit on a page, as fractions (x, y) of the page area, in reading order. The bubble
 * at index i on page p is app number p * size + i.
 */
class PageLayout(val name: String, val slots: List<Pair<Float, Float>>) {
    val size: Int get() = slots.size

    /**
     * The slot reached by moving one step from [from] within the page: left/right walk the reading
     * order, up/down jump to the nearest slot in the next row. Null means "off the page".
     */
    fun step(from: Int, dx: Int, dy: Int): Int? {
        if (dx != 0) return (from + dx).takeIf { it in slots.indices }
        val (x, y) = slots[from]
        return slots.indices
            .filter { if (dy > 0) slots[it].second > y + 0.05f else slots[it].second < y - 0.05f }
            .minByOrNull { i ->
                val (sx, sy) = slots[i]
                kotlin.math.abs(sy - y) * 2f + kotlin.math.abs(sx - x)
            }
    }
}

private fun grid(cols: Int, rows: Int): List<Pair<Float, Float>> {
    val ys = if (rows == 3) listOf(0.2f, 0.5f, 0.8f) else listOf(0.3f, 0.7f)
    return ys.flatMap { y -> (0 until cols).map { c -> (c + 0.5f) / cols to y } }
}

val LAYOUTS = listOf(
    // Measured from the real Vita home screen: three, four, then two bubbles.
    PageLayout(
        "Vita (3-4-2)",
        listOf(
            0.266f to 0.22f, 0.500f to 0.22f, 0.734f to 0.22f,
            0.165f to 0.50f, 0.385f to 0.50f, 0.615f to 0.50f, 0.842f to 0.50f,
            0.266f to 0.80f, 0.742f to 0.80f,
        ),
    ),
    PageLayout("Grid (4x3)", grid(4, 3)),
    PageLayout("Wide (5x2)", grid(5, 2)),
)

data class Settings(
    val themeIndex: Int = 0,
    val particles: Boolean = false,
    val bubbleScale: Float = 1f,
    val use24h: Boolean = false,
    val showBattery: Boolean = true,
    val sortNewest: Boolean = false,
    val layoutIndex: Int = 0,
    val roundedBubbles: Boolean = false,
    val fullArt: Boolean = true,
    val bubble3d: Boolean = true,
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
    val lockScreen: Boolean = true,
) {
    val theme: Theme get() = THEMES[themeIndex.coerceIn(THEMES.indices)]
}

/** Persists [Settings] in SharedPreferences and the custom wallpaper as a file in app storage. */
class SettingsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("qita_settings", Context.MODE_PRIVATE)
    /** The global wallpaper (page == null) or the photo chosen for one home page. */
    private fun wallpaperFile(page: Int? = null) =
        File(context.filesDir, if (page == null) "wallpaper.jpg" else "wallpaper_page_$page.jpg")

    fun load() = Settings(
        themeIndex = prefs.getInt("theme", 0),
        particles = prefs.getBoolean("particles", false),
        bubbleScale = prefs.getFloat("bubbleScale", 1f),
        use24h = prefs.getBoolean("use24h", false),
        showBattery = prefs.getBoolean("showBattery", true),
        sortNewest = prefs.getBoolean("sortNewest", false),
        layoutIndex = prefs.getInt("layout", 0),
        roundedBubbles = prefs.getBoolean("rounded", false),
        fullArt = prefs.getBoolean("fullArt", true),
        bubble3d = prefs.getBoolean("bubble3d", true),
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
        lockScreen = prefs.getBoolean("lockScreen", true),
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
            .putBoolean("fullArt", s.fullArt)
            .putBoolean("bubble3d", s.bubble3d)
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
            .putBoolean("lockScreen", s.lockScreen)
            .apply()
    }

    /**
     * Packages shown on the home screen, in display order. The built-in bubbles (Settings, Store,
     * Desktop) are put in front once, so existing installs get them too.
     */
    fun loadHome(): List<String> {
        val saved = prefs.getString("home", "").orEmpty().split('\n').filter { it.isNotBlank() }
        if (prefs.getBoolean("systemSeeded", false)) return saved
        val seeded = SYSTEM_IDS.filter { it !in saved } + saved
        saveHome(seeded)
        prefs.edit().putBoolean("systemSeeded", true).apply()
        return seeded
    }

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

    fun loadWallpaper(page: Int? = null): ImageBitmap? {
        val file = wallpaperFile(page)
        return if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
    }

    /** Each home page's own background: a theme index, or [PAGE_PHOTO]. Pages not listed use the global one. */
    fun loadPageBg(): Map<Int, Int> =
        prefs.getString("pageBg", "").orEmpty().split(',').mapNotNull {
            val parts = it.split(':')
            val page = parts.getOrNull(0)?.toIntOrNull()
            val value = parts.getOrNull(1)?.toIntOrNull()
            if (page != null && value != null) page to value else null
        }.toMap()

    fun savePageBg(map: Map<Int, Int>) {
        prefs.edit().putString("pageBg", map.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }

    /** The photos of every page that uses one. */
    fun loadPageWallpapers(pageBg: Map<Int, Int>): Map<Int, ImageBitmap> =
        pageBg.filterValues { it == PAGE_PHOTO }.keys.mapNotNull { page -> loadWallpaper(page)?.let { page to it } }.toMap()

    /** Copies the picked image into app storage (downscaled) and returns it, or null on failure. */
    fun saveWallpaper(uri: Uri, page: Int? = null): ImageBitmap? = runCatching {
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
        wallpaperFile(page).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        upright.asImageBitmap()
    }.getOrNull()

    fun clearWallpaper(page: Int? = null) {
        wallpaperFile(page).delete()
    }
}
