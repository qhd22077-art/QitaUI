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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
    // The real Vita wallpapers: glossy silk ribbons, and glass symbols over deep blue.
    Theme("Vita Silk", Color(0xFF1B6FCB), Color(0xFF1850A8), Color(0xFF0C1F6B), Scene.SILK),
    Theme("Vita Symbols", Color(0xFF021238), Color(0xFF063488), Color(0xFF0A4FA8), Scene.SYMBOLS),
)

/** Index of the Vita Silk theme in [THEMES], the look of PS Vita mode. */
const val VITA_SILK = 12

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

/** The page layout used when the screen is held upright: three bubbles across, four rows. */
val PORTRAIT_LAYOUT = PageLayout("Portrait (3x4)", listOf(0.14f, 0.38f, 0.62f, 0.86f).flatMap { y -> (0 until 3).map { c -> (c + 0.5f) / 3 to y } })

/** The Android screen orientation for the setting: 0 landscape, 1 portrait, anything else follows the sensor. */
fun orientationFlag(orientation: Int): Int = when (orientation) {
    0 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
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
    val themeIndex: Int = VITA_SILK,
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
    val vitaMode: Boolean = true,
    val lockTapPeel: Boolean = false,
    val lockClockSize: Float = 1f,
    val lockClockColor: Int = 0xFFFFFFFF.toInt(),
    val lockFont: Int = 0,
    val lockShowDate: Boolean = true,
    val lockPanelTint: Float = 0.06f,
    val lockBorder: Float = 0.5f,
    val lockFrame: Boolean = true,
    val lockBgMode: Int = 0,
    val lockTheme: Int = 0,
    val lockNotifs: Boolean = false,
    val lockNotifCount: Int = 3,
    val gameCovers: Boolean = true,
    val gamesOnHome: Boolean = true,
    val bgCustom: Boolean = false,
    val bgTop: Int = 0xFF1B6FCB.toInt(),
    val bgMid: Int = 0xFF1850A8.toInt(),
    val bgBottom: Int = 0xFF0C1F6B.toInt(),
    val lockClockPos: Int = 0,
    val symbolCount: Int = 45,
    val notifColor: Int = 0xFF1D3E8F.toInt(),
    /** 0 landscape, 1 portrait, 2 follows how the device is held. */
    val orientation: Int = 0,
    /** The charge at which the battery icon turns amber (low) and red and blinking (nearly dead). */
    val batteryLow: Int = 20,
    val batteryCritical: Int = 8,
    /** Saves memory and battery: 30 fps backgrounds, fewer symbols, no blur, smaller caches, no 120 Hz. */
    val lightMode: Boolean = false,
    val glass: Float = 0.85f,
    val glassBubbles: Boolean = true,
    val prevTheme: Int = 0,
    val bodyMode: Int = 0,
    val bodyColor: Int = 0xFF4A78D0.toInt(),
    val accent: Int = 0xFF40E0E0.toInt(),
    val iconSat: Float = 1f,
    val iconBright: Float = 1f,
    val iconScale: Float = 1f,
    val rimWidth: Float = 1f,
    val highlight: Float = 1f,
    val thickness: Float = 1f,
    val dome: Float = 1f,
    val sway: Float = 1f,
    val tapAnim: Int = 0,
    val nameSize: Float = 1f,
    val nameWeight: Int = 1,
    val nameColor: Int = 0xFFFFFFFF.toInt(),
    val namePill: Boolean = false,
    val nameFont: Int = 0,
    val uiFontChoice: Int = 0,
    val sceneSpeed: Float = 1f,
    val reduceMotion: Boolean = false,
    val showClock: Boolean = true,
    val barOpacity: Float = 1f,
    val clockSize: Float = 1f,
) {
    /** The chosen theme; with custom background colours on, its sky colours are replaced by the user's. */
    val theme: Theme get() {
        val base = THEMES[themeIndex.coerceIn(THEMES.indices)]
        return if (bgCustom) base.copy(top = Color(bgTop), mid = Color(bgMid), bottom = Color(bgBottom)) else base
    }
}

/** Persists [Settings] in SharedPreferences and the custom wallpaper as a file in app storage. */
class SettingsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("qita_settings", Context.MODE_PRIVATE)
    /** The global wallpaper (page == null) or the photo chosen for one home page. */
    private fun wallpaperFile(page: Int? = null) =
        File(context.filesDir, if (page == null) "wallpaper.jpg" else "wallpaper_page_$page.jpg")

    fun load() = Settings(
        themeIndex = prefs.getInt("theme", VITA_SILK),
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
        vitaMode = prefs.getBoolean("vitaMode", true),
        lockTapPeel = prefs.getBoolean("lockTapPeel", false),
        lockClockSize = prefs.getFloat("lockClockSize", 1f),
        lockClockColor = prefs.getInt("lockClockColor", 0xFFFFFFFF.toInt()),
        lockFont = prefs.getInt("lockFont", 0),
        lockShowDate = prefs.getBoolean("lockShowDate", true),
        lockPanelTint = prefs.getFloat("lockPanelTint", 0.06f),
        lockBorder = prefs.getFloat("lockBorder", 0.5f),
        lockFrame = prefs.getBoolean("lockFrame", true),
        lockBgMode = prefs.getInt("lockBgMode", 0),
        lockTheme = prefs.getInt("lockTheme", 0),
        lockNotifs = prefs.getBoolean("lockNotifs", false),
        lockNotifCount = prefs.getInt("lockNotifCount", 3),
        gameCovers = prefs.getBoolean("gameCovers", true),
        gamesOnHome = prefs.getBoolean("gamesOnHome", true),
        bgCustom = prefs.getBoolean("bgCustom", false),
        bgTop = prefs.getInt("bgTop", 0xFF1B6FCB.toInt()),
        bgMid = prefs.getInt("bgMid", 0xFF1850A8.toInt()),
        bgBottom = prefs.getInt("bgBottom", 0xFF0C1F6B.toInt()),
        lockClockPos = prefs.getInt("lockClockPos", 0),
        symbolCount = prefs.getInt("symbolCount", 45),
        notifColor = prefs.getInt("notifColor", 0xFF1D3E8F.toInt()),
        orientation = prefs.getInt("orientation", 0),
        lightMode = prefs.getBoolean("lightMode", false),
        batteryLow = prefs.getInt("batteryLow", 20),
        batteryCritical = prefs.getInt("batteryCritical", 8),
        glass = prefs.getFloat("glass", 0.85f),
        glassBubbles = prefs.getBoolean("glassBubbles", true),
        prevTheme = prefs.getInt("prevTheme", 0),
        bodyMode = prefs.getInt("bodyMode", 0),
        bodyColor = prefs.getInt("bodyColor", 0xFF4A78D0.toInt()),
        accent = prefs.getInt("accent", 0xFF40E0E0.toInt()),
        iconSat = prefs.getFloat("iconSat", 1f),
        iconBright = prefs.getFloat("iconBright", 1f),
        iconScale = prefs.getFloat("iconScale", 1f),
        rimWidth = prefs.getFloat("rimWidth", 1f),
        highlight = prefs.getFloat("highlight", 1f),
        thickness = prefs.getFloat("thickness", 1f),
        dome = prefs.getFloat("dome", 1f),
        sway = prefs.getFloat("sway", 1f),
        tapAnim = prefs.getInt("tapAnim", 0),
        nameSize = prefs.getFloat("nameSize", 1f),
        nameWeight = prefs.getInt("nameWeight", 1),
        nameColor = prefs.getInt("nameColor", 0xFFFFFFFF.toInt()),
        namePill = prefs.getBoolean("namePill", false),
        nameFont = prefs.getInt("nameFont", 0),
        uiFontChoice = prefs.getInt("uiFontChoice", 0),
        sceneSpeed = prefs.getFloat("sceneSpeed", 1f),
        reduceMotion = prefs.getBoolean("reduceMotion", false),
        showClock = prefs.getBoolean("showClock", true),
        barOpacity = prefs.getFloat("barOpacity", 1f),
        clockSize = prefs.getFloat("clockSize", 1f),
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
            .putBoolean("vitaMode", s.vitaMode)
            .putBoolean("lockTapPeel", s.lockTapPeel)
            .putFloat("lockClockSize", s.lockClockSize)
            .putInt("lockClockColor", s.lockClockColor)
            .putInt("lockFont", s.lockFont)
            .putBoolean("lockShowDate", s.lockShowDate)
            .putFloat("lockPanelTint", s.lockPanelTint)
            .putFloat("lockBorder", s.lockBorder)
            .putBoolean("lockFrame", s.lockFrame)
            .putInt("lockBgMode", s.lockBgMode)
            .putInt("lockTheme", s.lockTheme)
            .putBoolean("lockNotifs", s.lockNotifs)
            .putInt("lockNotifCount", s.lockNotifCount)
            .putBoolean("gameCovers", s.gameCovers)
            .putBoolean("gamesOnHome", s.gamesOnHome)
            .putBoolean("bgCustom", s.bgCustom)
            .putInt("bgTop", s.bgTop)
            .putInt("bgMid", s.bgMid)
            .putInt("bgBottom", s.bgBottom)
            .putInt("lockClockPos", s.lockClockPos)
            .putInt("symbolCount", s.symbolCount)
            .putInt("notifColor", s.notifColor)
            .putInt("orientation", s.orientation)
            .putBoolean("lightMode", s.lightMode)
            .putInt("batteryLow", s.batteryLow)
            .putInt("batteryCritical", s.batteryCritical)
            .putFloat("glass", s.glass)
            .putBoolean("glassBubbles", s.glassBubbles)
            .putInt("prevTheme", s.prevTheme)
            .putInt("bodyMode", s.bodyMode)
            .putInt("bodyColor", s.bodyColor)
            .putInt("accent", s.accent)
            .putFloat("iconSat", s.iconSat)
            .putFloat("iconBright", s.iconBright)
            .putFloat("iconScale", s.iconScale)
            .putFloat("rimWidth", s.rimWidth)
            .putFloat("highlight", s.highlight)
            .putFloat("thickness", s.thickness)
            .putFloat("dome", s.dome)
            .putFloat("sway", s.sway)
            .putInt("tapAnim", s.tapAnim)
            .putFloat("nameSize", s.nameSize)
            .putInt("nameWeight", s.nameWeight)
            .putInt("nameColor", s.nameColor)
            .putBoolean("namePill", s.namePill)
            .putInt("nameFont", s.nameFont)
            .putInt("uiFontChoice", s.uiFontChoice)
            .putFloat("sceneSpeed", s.sceneSpeed)
            .putBoolean("reduceMotion", s.reduceMotion)
            .putBoolean("showClock", s.showClock)
            .putFloat("barOpacity", s.barOpacity)
            .putFloat("clockSize", s.clockSize)
            .apply()
    }

    /**
     * Packages shown on the home screen, in display order. The built-in bubbles (Settings, Store,
     * Desktop) are put in front once, so existing installs get them too.
     */
    fun loadHome(): List<String> {
        val saved = prefs.getString("home", "").orEmpty().split('\n').filter { it.isNotBlank() }
        if (!prefs.getBoolean("systemSeeded", false)) {
            val seeded = SYSTEM_IDS.filter { it !in saved } + saved
            saveHome(seeded)
            prefs.edit().putBoolean("systemSeeded", true).putBoolean("gamesSeeded", true).apply()
            return seeded
        }
        // The Games bubble arrived later: put it on the home screen once for installs that already had the others.
        if (!prefs.getBoolean("gamesSeeded", false)) {
            val withGames = if (SystemAction.GAMES.id in saved) saved else listOf(SystemAction.GAMES.id) + saved
            saveHome(withGames)
            prefs.edit().putBoolean("gamesSeeded", true).apply()
            return withGames
        }
        return saved
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

    private val fontFile get() = File(context.filesDir, "custom_font.ttf")

    fun hasCustomFont(): Boolean = fontFile.exists()

    /** Copies a font file the user picked (for example their own copy of a system font) into app storage. */
    fun saveFont(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input -> fontFile.outputStream().use { input.copyTo(it) } }
        check(fontFile.length() > 1000)
        true
    }.getOrElse { fontFile.delete(); false }

    fun clearFont() { fontFile.delete() }

    /** The picked font as a family, or null if there is none or it cannot be read. */
    fun customFontFamily(): FontFamily? =
        if (fontFile.exists()) runCatching { FontFamily(Font(fontFile)) }.getOrNull() else null

    /** Every setting (not the home layout, launch counts or wallpapers) as JSON text, so a look can be backed up. */
    fun exportJson(): String {
        val json = org.json.JSONObject()
        prefs.all.forEach { (k, v) ->
            if (k.startsWith("launch_") || k == "known" || k == "home" || k == "systemSeeded" || k == "tutorialSeen") return@forEach
            when (v) {
                is Boolean, is Int, is String -> json.put(k, v)
                is Float -> json.put(k, v.toDouble())
                else -> {}
            }
        }
        return json.toString(2)
    }

    /** Applies settings written by [exportJson]. Returns false if the text is not valid. */
    fun importJson(text: String): Boolean = runCatching {
        val json = org.json.JSONObject(text)
        val edit = prefs.edit()
        json.keys().forEach { k ->
            when (val v = json.get(k)) {
                is Boolean -> edit.putBoolean(k, v)
                is Int -> edit.putInt(k, v)
                is Double -> edit.putFloat(k, v.toFloat())
                is String -> edit.putString(k, v)
                else -> {}
            }
        }
        edit.apply()
        true
    }.getOrDefault(false)

    fun clearWallpaper(page: Int? = null) {
        wallpaperFile(page).delete()
    }
}
