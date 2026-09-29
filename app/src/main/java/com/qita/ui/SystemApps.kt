package com.qita.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

/** What a built-in bubble does when started. */
enum class SystemAction(val id: String, val label: String, val blurb: String) {
    SETTINGS("qita.sys.settings", "Settings", "Change the theme, home screen layout, controller and system options."),
    STORE("qita.sys.store", "Store", "The store is not built yet. It will be added later."),
    DESKTOP("qita.sys.desktop", "Desktop", "A desktop with every app on this device, for anything not on the home screen."),
}

/** Ids of the built-in bubbles, in the order they start on the home screen. */
val SYSTEM_IDS: List<String> = SystemAction.values().map { it.id }

/** The three built-in bubbles. Their icons are drawn in code, so no image assets are needed. */
val SYSTEM_APPS: List<LaunchableApp> by lazy {
    listOf(
        systemApp(SystemAction.SETTINGS, Color(0xFF3FAA55)) { drawToolbox(it) },
        systemApp(SystemAction.STORE, Color(0xFF2E7DD7)) { drawBag(it) },
        systemApp(SystemAction.DESKTOP, Color(0xFFE2761B)) { drawMonitor(it) },
    )
}

private fun systemApp(action: SystemAction, tint: Color, glyph: DrawScope.(Color) -> Unit) = LaunchableApp(
    label = action.label,
    packageName = action.id,
    icon = drawIcon { glyph(lerp(tint, Color.Black, 0.45f)) },
    tint = tint,
    action = action,
)

private const val ICON = 256

private fun drawIcon(block: DrawScope.() -> Unit): ImageBitmap {
    val image = ImageBitmap(ICON, ICON)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ComposeCanvas(image), Size(ICON.toFloat(), ICON.toFloat()), block)
    return image
}

private val Glyph = Color.White

/** A toolbox: handle, body, lid seam and latch. */
private fun DrawScope.drawToolbox(dark: Color) {
    drawRoundRect(Glyph, Offset(92f, 62f), Size(72f, 64f), CornerRadius(16f), style = Stroke(width = 13f))
    drawRoundRect(Glyph, Offset(42f, 98f), Size(172f, 104f), CornerRadius(18f))
    drawRect(dark.copy(alpha = 0.35f), Offset(42f, 136f), Size(172f, 7f))
    drawRoundRect(dark, Offset(108f, 124f), Size(40f, 32f), CornerRadius(7f))
    drawCircle(Glyph, 5.5f, Offset(128f, 140f))
}

/** A shopping bag with a star on it. */
private fun DrawScope.drawBag(dark: Color) {
    drawArc(Glyph, 180f, 180f, false, Offset(94f, 54f), Size(68f, 76f), style = Stroke(width = 11f, cap = StrokeCap.Round))
    val bag = Path().apply {
        moveTo(68f, 94f); lineTo(188f, 94f); lineTo(200f, 202f); lineTo(56f, 202f); close()
    }
    drawPath(bag, Glyph)
    drawPath(bag, Glyph, style = Stroke(width = 12f, join = StrokeJoin.Round))
    val star = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) 32f else 14f
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = 128f + r * cos(a).toFloat()
        val y = 152f + r * sin(a).toFloat()
        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    drawPath(star, dark)
}

/** A monitor showing a terminal prompt. */
private fun DrawScope.drawMonitor(dark: Color) {
    drawRoundRect(Glyph, Offset(44f, 66f), Size(168f, 110f), CornerRadius(14f))
    drawRoundRect(dark, Offset(56f, 78f), Size(144f, 86f), CornerRadius(8f))
    drawRect(Glyph, Offset(116f, 176f), Size(24f, 20f))
    drawRoundRect(Glyph, Offset(88f, 192f), Size(80f, 12f), CornerRadius(6f))
    val prompt = Path().apply { moveTo(80f, 100f); lineTo(104f, 121f); lineTo(80f, 142f) }
    drawPath(prompt, Glyph, style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawLine(Glyph, Offset(116f, 144f), Offset(146f, 144f), strokeWidth = 9f, cap = StrokeCap.Round)
}
