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
    val sceneSpeed: Float = 1f,
    val reduceMotion: Boolean = false,
    val showClock: Boolean = true,
    val barOpacity: Float = 1f,
    val clockSize: Float = 1f,
    /** 0 opaque discs .. 1 clear glass (PS Vita mode). */
    val glass: Float = 0f,
)

val LocalLook = compositionLocalOf { Look() }

/** The font of the bubble names; null keeps the screen's font. */
val LocalNameFont = compositionLocalOf<FontFamily?> { null }

fun Settings.look() = Look(
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
    sway = if (reduceMotion) 0f else sway,
    tapAnim = if (reduceMotion) 2 else tapAnim,
    nameSize = nameSize,
    nameWeight = when (nameWeight) { 0 -> FontWeight.Light; 2 -> FontWeight.Bold; else -> FontWeight.Normal },
    nameColor = Color(nameColor),
    namePill = namePill,
    sceneSpeed = sceneSpeed,
    reduceMotion = reduceMotion,
    showClock = showClock,
    barOpacity = barOpacity,
    clockSize = clockSize,
    glass = if (vitaMode) glass else 0f,
)

/** The swatches offered wherever a colour is picked. */
val SWATCHES: List<Int> = listOf(
    0xFF40E0E0, 0xFF4A78D0, 0xFF5CC85A, 0xFFF0B030, 0xFFF0782C, 0xFFE0567A, 0xFFA060E0, 0xFFFFFFFF, 0xFF101216,
).map { it.toInt() }

/** The glass colour behind transparent parts of [app]'s art, for the chosen colour mode. */
fun bodyFor(app: com.qita.ui.LaunchableApp, look: Look): Int = when {
    app.action != null -> com.qita.ui.systemBody(app.action)
    look.bodyMode == 1 -> com.qita.ui.lightBody(Color(look.bodyColor))
    look.bodyMode == 2 -> 0xFF0A0B0D.toInt()
    look.bodyMode == 3 -> 0xFFE6EBF2.toInt()
    else -> com.qita.ui.lightBody(app.tint)
}
