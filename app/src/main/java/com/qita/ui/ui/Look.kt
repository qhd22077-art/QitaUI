package com.qita.ui.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.qita.ui.Settings

/** The appearance options of [Settings] in the form the drawing code reads them, handed down through [LocalLook]. */
data class Look(
    /** 0 icon colour, 1 one chosen colour, 2 black glass, 3 milky white. */
    val bodyMode: Int = 0,
    val bodyColor: Int = 0xFF4A78D0.toInt(),
    val accent: Color = Color(0xFF40E0E0),
    val iconSat: Float = 1f,
    val iconBright: Float = 1f,
    val iconScale: Float = 1f,
    val rimWidth: Float = 1f,
    val highlight: Float = 1f,
    val thickness: Float = 1f,
    val dome: Float = 1f,
    val sway: Float = 1f,
    /** 0 flip, 1 pulse, 2 none. */
    val tapAnim: Int = 0,
    val nameSize: Float = 1f,
    val nameWeight: FontWeight = FontWeight.Normal,
    val nameColor: Color = Color.White,
    val namePill: Boolean = false,
    /** Long names slide across: 0 never, 1 only the selected or touched one, 2 always. */
    val scrollNames: Int = 2,
    val sceneSpeed: Float = 1f,
    val reduceMotion: Boolean = false,
    val showClock: Boolean = true,
    val barOpacity: Float = 1f,
    val barColor: Color = Color.Black,
    val indicatorColor: Color = Color.White,
    val clockSize: Float = 1f,
    /** 0 opaque discs .. 1 clear glass (PS Vita mode). */
    val glass: Float = 0f,
    /** 1 solid .. lower: every bubble lets the wallpaper show through. */
    val bubbleAlpha: Float = 1f,
    val symbols: Int = 45,
    /** Light mode: the drawing code skips its costliest effects. */
    val light: Boolean = false,
    val batteryLow: Int = 20,
    val batteryCritical: Int = 8,
    /** How far a tilt shifts things: 0 is off (setting off, Light mode or Reduce motion). */
    val tilt: Float = 0f,
)

val LocalLook = compositionLocalOf { Look() }

/** The font of the bubble names; null keeps the screen's font. */
val LocalNameFont = compositionLocalOf<FontFamily?> { null }

fun Settings.look(saver: Boolean = false) = Look(
    bodyMode = bodyMode,
    bodyColor = bodyColor,
    accent = Color(accent),
    iconSat = iconSat,
    iconBright = iconBright,
    iconScale = iconScale,
    rimWidth = rimWidth,
    highlight = highlight,
    thickness = thickness,
    dome = dome,
    sway = if (reduceMotion || saver) 0f else sway,
    tapAnim = if (reduceMotion || saver) 2 else tapAnim,
    nameSize = nameSize,
    nameWeight = when (nameWeight) { 0 -> FontWeight.Light; 2 -> FontWeight.Bold; else -> FontWeight.Normal },
    nameColor = Color(nameColor),
    namePill = namePill,
    // Light mode keeps the battery: only the one you are on moves.
    scrollNames = if ((lightMode || saver) && scrollNames == 2) 1 else scrollNames,
    sceneSpeed = sceneSpeed,
    reduceMotion = reduceMotion || saver,
    showClock = showClock,
    barOpacity = barOpacity,
    barColor = Color(barColor),
    indicatorColor = Color(indicatorColor),
    clockSize = clockSize,
    glass = if (glassBubbles) glass else 0f,
    bubbleAlpha = bubbleAlpha,
    symbols = if (lightMode || saver) minOf(symbolCount, 18) else symbolCount,
    light = lightMode || saver,
    batteryLow = batteryLow,
    batteryCritical = batteryCritical,
    tilt = if (parallax && !lightMode && !reduceMotion && !saver) parallaxStrength else 0f,
)

/** The swatches offered wherever a colour is picked. */
/** Swatches for background colours: deep blues first, like the Vita's skies, then other moods. */
val BG_SWATCHES: List<Int> = listOf(
    0xFF061238, 0xFF0C1F6B, 0xFF1850A8, 0xFF1B6FCB, 0xFF2F9BE8, 0xFF52C4EC, 0xFF6B44D6, 0xFF0B6B2B, 0xFFB8321A, 0xFF101216, 0xFFE8EEF8,
).map { it.toInt() }

val SWATCHES: List<Int> = listOf(
    0xFF1D3E8F, 0xFF40E0E0, 0xFF4A78D0, 0xFF5CC85A, 0xFFF0B030, 0xFFF0782C, 0xFFE0567A, 0xFFA060E0, 0xFFFFFFFF, 0xFF101216,
).map { it.toInt() }

/** The glass colour behind transparent parts of [app]'s art, for the chosen colour mode. */
fun bodyFor(app: com.qita.ui.LaunchableApp, look: Look): Int = when {
    app.action != null -> com.qita.ui.systemBody(app.action)
    look.bodyMode == 1 -> com.qita.ui.lightBody(Color(look.bodyColor))
    look.bodyMode == 2 -> 0xFF0A0B0D.toInt()
    look.bodyMode == 3 -> 0xFFE6EBF2.toInt()
    else -> com.qita.ui.lightBody(app.tint)
}
