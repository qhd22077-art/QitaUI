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
    Theme("Vita Blue", Color(0xFF031038), Color(0xFF0B2F8A), Color(0xFF5A8FE0)),
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
    Theme("Vita Silk", Color(0xFF04164A), Color(0xFF072C7A), Color(0xFF0A3A9A), Scene.SILK),
    Theme("Vita Symbols", Color(0xFF021238), Color(0xFF063488), Color(0xFF0A4FA8), Scene.SYMBOLS),
    // The real menu's deep PlayStation navy: nearly black at the top, a rich blue only at the bottom, with the pale silk ribbons over it.
    Theme("Vita Night", Color(0xFF010514), Color(0xFF04134A), Color(0xFF0A2F82), Scene.SILK),
)

/** Index of the Vita Silk theme in [THEMES], the look of PS Vita mode. */
const val VITA_SILK = 12

/** The darker PlayStation blue, the default look. */
const val VITA_NIGHT = 14

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
    val themeIndex: Int = VITA_NIGHT,
    val particles: Boolean = false,
    val bubbleScale: Float = 1f,
    /** Shrinks the home bubbles just enough that the names of the bottom row stay on the screen. */
    val fitNames: Boolean = true,
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
    /** The web search used by the Browser and the Store: 0 Google, 1 Bing (Edge), 2 DuckDuckGo. */
    val searchEngine: Int = 0,
    val cursorMode: Boolean = false,
    val cursorSpeed: Float = 1f,
    val psLabels: Boolean = true,
    val swapAB: Boolean = false,
    val autoAdd: Boolean = false,
    val debugInput: Boolean = false,
    val lockScreen: Boolean = true,
    val vitaMode: Boolean = true,
    val lockTapPeel: Boolean = false,
    /** The sheet can be peeled by a drag anywhere on it (down and left), not only from the corner. */
    val lockPeelAnywhere: Boolean = true,
    val lockClockSize: Float = 1f,
    val lockClockColor: Int = 0xFFFFFFFF.toInt(),
    val lockFont: Int = 0,
    val lockShowDate: Boolean = true,
    val lockPanelTint: Float = 0.06f,
    val lockBorder: Float = 0.5f,
    /** How deep the glass frame's bevel looks (0 flat .. 1 deep). */
    val lockBevel: Float = 0.7f,
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
    /** The top bar's colour and the colour of its clock (a Vita theme sets both). */
    val barColor: Int = 0xFF000000.toInt(),
    val indicatorColor: Int = 0xFFFFFFFF.toInt(),
    /** 0 landscape, 1 portrait, 2 follows how the device is held. */
    val orientation: Int = 0,
    /** The charge at which the battery icon turns amber (low) and red and blinking (nearly dead). */
    val batteryLow: Int = 20,
    val batteryCritical: Int = 8,
    /** Saves memory and battery: 30 fps backgrounds, fewer symbols, no blur, smaller caches, no 120 Hz. */
    val lightMode: Boolean = false,
    val glass: Float = 0.85f,
    val glassBubbles: Boolean = true,
    /** How solid every bubble is: 1 solid, lower lets the wallpaper show through (the bubble name stays solid). */
    val bubbleAlpha: Float = 1f,
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
    /** Long names slide across: 0 never, 1 only the selected or touched one, 2 always. */
    val scrollNames: Int = 2,
    /** Dropping one bubble on the middle of another makes a folder of them. */
    val dragMakesFolder: Boolean = true,
    /** A bubble can be put in any free slot of a page, leaving empty slots; off packs the bubbles in order. */
    val freePlacement: Boolean = true,
    /** Short interface sounds (see Sounds): on or off, how loud, and whether they follow the media volume (else the system sounds volume). */
    val soundOn: Boolean = true,
    val soundVolume: Float = 0.6f,
    val soundMedia: Boolean = true,
    /** New games from a scan go into a folder for their console instead of onto the home screen one by one. */
    val gameFoldersAuto: Boolean = true,
    val nameFont: Int = 0,
    val uiFontChoice: Int = 0,
    val sceneSpeed: Float = 1f,
    val reduceMotion: Boolean = false,
    /** Tilting the device shifts the wallpaper, bubbles and lock screen against each other; [parallaxStrength] scales it. */
    val parallax: Boolean = true,
    /** Battery saver: 0 off, 1 on automatically when the battery is low (and not charging), 2 always on. */
    val saverMode: Int = 0,
    /** A count of waiting notifications on each bubble (needs notification access). */
    val badges: Boolean = true,
    val parallaxStrength: Float = 1f,
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

    init {
        // Once: the old default blue (Vita Silk) becomes the darker default; a theme the user chose themselves is left alone.
        if (!prefs.getBoolean("nightSeeded", false)) {
            if (prefs.getInt("theme", VITA_SILK) == VITA_SILK) prefs.edit().putInt("theme", VITA_NIGHT).apply()
            prefs.edit().putBoolean("nightSeeded", true).apply()
        }
        // The icons of the theme in use are needed before the first bubble is drawn.
        BubbleStyles.load(context)
        ThemePacks.loadActive(context)
    }
    /** The global wallpaper (page == null) or the photo chosen for one home page. */
    private fun wallpaperFile(page: Int? = null) =
        File(context.filesDir, if (page == null) "wallpaper.jpg" else "wallpaper_page_$page.jpg")

    fun load() = Settings(
        themeIndex = prefs.getInt("theme", VITA_NIGHT),
        particles = prefs.getBoolean("particles", false),
        bubbleScale = prefs.getFloat("bubbleScale", 1f),
        fitNames = prefs.getBoolean("fitNames", true),
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
        searchEngine = prefs.getInt("searchEngine", 0),
        cursorMode = prefs.getBoolean("cursorMode", false),
        cursorSpeed = prefs.getFloat("cursorSpeed", 1f),
        psLabels = prefs.getBoolean("psLabels", true),
        swapAB = prefs.getBoolean("swapAB", false),
        autoAdd = prefs.getBoolean("autoAdd", false),
        debugInput = prefs.getBoolean("debugInput", false),
        lockScreen = prefs.getBoolean("lockScreen", true),
        vitaMode = prefs.getBoolean("vitaMode", true),
        lockTapPeel = prefs.getBoolean("lockTapPeel", false),
        lockPeelAnywhere = prefs.getBoolean("lockPeelAnywhere", true),
        lockClockSize = prefs.getFloat("lockClockSize", 1f),
        lockClockColor = prefs.getInt("lockClockColor", 0xFFFFFFFF.toInt()),
        lockFont = prefs.getInt("lockFont", 0),
        lockShowDate = prefs.getBoolean("lockShowDate", true),
        lockPanelTint = prefs.getFloat("lockPanelTint", 0.06f),
        lockBorder = prefs.getFloat("lockBorder", 0.5f),
        lockBevel = prefs.getFloat("lockBevel", 0.7f),
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
        barColor = prefs.getInt("barColor", 0xFF000000.toInt()),
        indicatorColor = prefs.getInt("indicatorColor", 0xFFFFFFFF.toInt()),
        orientation = prefs.getInt("orientation", 0),
        lightMode = prefs.getBoolean("lightMode", false),
        batteryLow = prefs.getInt("batteryLow", 20),
        batteryCritical = prefs.getInt("batteryCritical", 8),
        glass = prefs.getFloat("glass", 0.85f),
        glassBubbles = prefs.getBoolean("glassBubbles", true),
        bubbleAlpha = prefs.getFloat("bubbleAlpha", 1f),
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
        scrollNames = prefs.getInt("scrollNames", 2).coerceIn(0, 2),
        dragMakesFolder = prefs.getBoolean("dragMakesFolder", true),
        freePlacement = prefs.getBoolean("freePlacement", true),
        soundOn = prefs.getBoolean("soundOn", true),
        soundVolume = prefs.getFloat("soundVolume", 0.6f),
        soundMedia = prefs.getBoolean("soundMedia", true),
        gameFoldersAuto = prefs.getBoolean("gameFoldersAuto", true),
        nameFont = prefs.getInt("nameFont", 0),
        uiFontChoice = prefs.getInt("uiFontChoice", 0),
        sceneSpeed = prefs.getFloat("sceneSpeed", 1f),
        reduceMotion = prefs.getBoolean("reduceMotion", false),
        parallax = prefs.getBoolean("parallax", true),
        saverMode = prefs.getInt("saverMode", 0),
        badges = prefs.getBoolean("badges", true),
        parallaxStrength = prefs.getFloat("parallaxStrength", 1f),
        showClock = prefs.getBoolean("showClock", true),
        barOpacity = prefs.getFloat("barOpacity", 1f),
        clockSize = prefs.getFloat("clockSize", 1f),
    )

    fun save(s: Settings) {
        prefs.edit()
            .putInt("theme", s.themeIndex)
            .putBoolean("particles", s.particles)
            .putFloat("bubbleScale", s.bubbleScale)
            .putBoolean("fitNames", s.fitNames)
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
            .putInt("searchEngine", s.searchEngine)
            .putBoolean("cursorMode", s.cursorMode)
            .putFloat("cursorSpeed", s.cursorSpeed)
            .putBoolean("psLabels", s.psLabels)
            .putBoolean("swapAB", s.swapAB)
            .putBoolean("autoAdd", s.autoAdd)
            .putBoolean("debugInput", s.debugInput)
            .putBoolean("lockScreen", s.lockScreen)
            .putBoolean("vitaMode", s.vitaMode)
            .putBoolean("lockTapPeel", s.lockTapPeel)
            .putBoolean("lockPeelAnywhere", s.lockPeelAnywhere)
            .putFloat("lockClockSize", s.lockClockSize)
            .putInt("lockClockColor", s.lockClockColor)
            .putInt("lockFont", s.lockFont)
            .putBoolean("lockShowDate", s.lockShowDate)
            .putFloat("lockPanelTint", s.lockPanelTint)
            .putFloat("lockBorder", s.lockBorder)
            .putFloat("lockBevel", s.lockBevel)
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
            .putInt("barColor", s.barColor)
            .putInt("indicatorColor", s.indicatorColor)
            .putInt("orientation", s.orientation)
            .putBoolean("lightMode", s.lightMode)
            .putInt("batteryLow", s.batteryLow)
            .putInt("batteryCritical", s.batteryCritical)
            .putFloat("glass", s.glass)
            .putBoolean("glassBubbles", s.glassBubbles)
            .putFloat("bubbleAlpha", s.bubbleAlpha)
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
            .putInt("scrollNames", s.scrollNames)
            .putBoolean("dragMakesFolder", s.dragMakesFolder)
            .putBoolean("freePlacement", s.freePlacement)
            .putBoolean("soundOn", s.soundOn)
            .putFloat("soundVolume", s.soundVolume)
            .putBoolean("soundMedia", s.soundMedia)
            .putBoolean("gameFoldersAuto", s.gameFoldersAuto)
            .putInt("nameFont", s.nameFont)
            .putInt("uiFontChoice", s.uiFontChoice)
            .putFloat("sceneSpeed", s.sceneSpeed)
            .putBoolean("reduceMotion", s.reduceMotion)
            .putBoolean("parallax", s.parallax)
            .putInt("saverMode", s.saverMode)
            .putBoolean("badges", s.badges)
            .putFloat("parallaxStrength", s.parallaxStrength)
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
        var saved = prefs.getString("home", "").orEmpty().split('\n').filter { it.isNotBlank() }
        if (!prefs.getBoolean("systemSeeded", false)) {
            val seeded = SYSTEM_IDS.filter { it !in saved } + saved
            saveHome(seeded)
            prefs.edit().putBoolean("systemSeeded", true).putBoolean("gamesSeeded", true).putBoolean("browserFoldersSeeded", true).putBoolean("androidSettingsSeeded", true).putBoolean("trophiesSeeded", true).putBoolean("mediaSeeded", true).apply()
            return seeded
        }
        // The Games bubble arrived later: put it on the home screen once for installs that already had the others.
        if (!prefs.getBoolean("gamesSeeded", false)) {
            saved = if (SystemAction.GAMES.id in saved) saved else listOf(SystemAction.GAMES.id) + saved
            saveHome(saved)
            prefs.edit().putBoolean("gamesSeeded", true).apply()
        }
        // So did Browser and Folders.
        if (!prefs.getBoolean("browserFoldersSeeded", false)) {
            saved = listOf(SystemAction.BROWSER.id, SystemAction.FOLDERS.id).filter { it !in saved } + saved
            saveHome(saved)
            prefs.edit().putBoolean("browserFoldersSeeded", true).apply()
        }
        // And the System Settings bubble.
        if (!prefs.getBoolean("androidSettingsSeeded", false)) {
            saved = if (SystemAction.ANDROID.id in saved) saved else saved + SystemAction.ANDROID.id
            saveHome(saved)
            prefs.edit().putBoolean("androidSettingsSeeded", true).apply()
        }
        // And the Trophies bubble.
        if (!prefs.getBoolean("trophiesSeeded", false)) {
            saved = if (SystemAction.TROPHIES.id in saved) saved else saved + SystemAction.TROPHIES.id
            saveHome(saved)
            prefs.edit().putBoolean("trophiesSeeded", true).apply()
        }
        // And the Photos and Music bubbles.
        if (!prefs.getBoolean("mediaSeeded", false)) {
            saved = saved + listOf(SystemAction.PHOTOS.id, SystemAction.MUSIC.id).filter { it !in saved }
            saveHome(saved)
            prefs.edit().putBoolean("mediaSeeded", true).apply()
        }
        // And the Videos bubble.
        if (!prefs.getBoolean("videosSeeded", false)) {
            saved = if (SystemAction.VIDEOS.id in saved) saved else saved + SystemAction.VIDEOS.id
            saveHome(saved)
            prefs.edit().putBoolean("videosSeeded", true).apply()
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

    /** Uses a picture file that is already the right size (a theme's) as the wallpaper as it is, without a second lossy save. */
    fun useWallpaperFile(file: File, page: Int? = null): Boolean = runCatching {
        file.copyTo(wallpaperFile(page), overwrite = true)
        true
    }.getOrDefault(false)

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

    private val undoDir get() = File(context.filesDir, "wallpaper_undo").apply { mkdirs() }

    /** True while the look from before the first theme was applied is kept, so it can be put back. */
    fun hasLookBackup(): Boolean = prefs.getBoolean("undo_has", false)

    /**
     * Keeps how the home pages and the lock screen look now (their pictures, which pages use a photo, the lock mode and the name, clock
     * and bar colours) before a theme replaces them. Only the first call counts, so the look from before any theme is what comes back.
     */
    fun backupLook(pageBg: Map<Int, Int>, s: Settings) {
        if (hasLookBackup()) return
        val dir = undoDir
        dir.listFiles()?.forEach { it.delete() }
        for (p in listOf(-5) + (0..9)) {
            val f = wallpaperFile(p)
            if (f.exists()) runCatching { f.copyTo(File(dir, "w${p + 5}.jpg"), overwrite = true) }
        }
        prefs.edit()
            .putString("undo_pageBg", pageBg.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putInt("undo_lockBgMode", s.lockBgMode).putInt("undo_nameColor", s.nameColor).putInt("undo_lockClockColor", s.lockClockColor)
            .putInt("undo_barColor", s.barColor).putInt("undo_indicatorColor", s.indicatorColor)
            .putBoolean("undo_has", true).apply()
    }

    /** Puts back what [backupLook] kept: returns [s] with the saved colours and lock mode, and the saved page backgrounds; null if nothing was kept. */
    fun restoreLook(s: Settings): Pair<Settings, Map<Int, Int>>? {
        if (!hasLookBackup()) return null
        val dir = undoDir
        for (p in listOf(-5) + (0..9)) {
            val f = wallpaperFile(p)
            val b = File(dir, "w${p + 5}.jpg")
            if (b.exists()) runCatching { b.copyTo(f, overwrite = true) } else f.delete()
        }
        val pageBg = prefs.getString("undo_pageBg", "").orEmpty().split(',').mapNotNull {
            val parts = it.split(':')
            val page = parts.getOrNull(0)?.toIntOrNull()
            val value = parts.getOrNull(1)?.toIntOrNull()
            if (page != null && value != null) page to value else null
        }.toMap()
        savePageBg(pageBg)
        prefs.edit().putBoolean("undo_has", false).apply()
        return s.copy(
            lockBgMode = prefs.getInt("undo_lockBgMode", s.lockBgMode), nameColor = prefs.getInt("undo_nameColor", s.nameColor),
            lockClockColor = prefs.getInt("undo_lockClockColor", s.lockClockColor), barColor = prefs.getInt("undo_barColor", s.barColor),
            indicatorColor = prefs.getInt("undo_indicatorColor", s.indicatorColor),
        ) to pageBg
    }
}
