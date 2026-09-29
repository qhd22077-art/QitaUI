package com.qita.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Brush
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

/**
 * The three built-in bubbles. Their art is drawn in code, edge to edge like real app icons: a gradient
 * backdrop with a shaded illustration on it. The colours are single constants so they are easy to retint.
 */
val SYSTEM_APPS: List<LaunchableApp> by lazy {
    listOf(
        systemApp(SystemAction.SETTINGS, Color(0xFF6C8CA8), Color(0xFF2B4560)) { drawToolbox() },
        systemApp(SystemAction.STORE, Color(0xFF4FA0F0), Color(0xFF1B4FB0)) { drawBag() },
        systemApp(SystemAction.DESKTOP, Color(0xFFF59A3E), Color(0xFFC0501A)) { drawMonitor() },
    )
}

private fun systemApp(action: SystemAction, backTop: Color, backBottom: Color, art: DrawScope.() -> Unit) = LaunchableApp(
    label = action.label,
    packageName = action.id,
    icon = drawIcon {
        // Backdrop: a diagonal gradient with a soft light in the upper left.
        drawRect(Brush.linearGradient(listOf(backTop, backBottom), Offset.Zero, Offset(ICON.toFloat(), ICON.toFloat())))
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), Offset(ICON * 0.3f, ICON * 0.25f), ICON * 0.6f),
            radius = ICON * 0.6f, center = Offset(ICON * 0.3f, ICON * 0.25f),
        )
        art()
    },
    tint = lerp(backTop, backBottom, 0.5f),
    action = action,
)

private const val ICON = 256

private fun drawIcon(block: DrawScope.() -> Unit): ImageBitmap {
    val image = ImageBitmap(ICON, ICON)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ComposeCanvas(image), Size(ICON.toFloat(), ICON.toFloat()), block)
    return image
}

private fun vertical(top: Color, bottom: Color, y0: Float, y1: Float) = Brush.verticalGradient(listOf(top, bottom), startY = y0, endY = y1)

/** A soft dark ellipse under an object. */
private fun DrawScope.groundShadow(cx: Float, y: Float, w: Float) {
    for (i in 3 downTo 1) drawOval(Color.Black.copy(alpha = 0.10f), Offset(cx - w / 2f - i * 4f, y - i * 2f), Size(w + i * 8f, 14f + i * 5f))
}

/** A toolbox: steel handle, orange lid and body, a seam, rivets and a gold latch. */
private fun DrawScope.drawToolbox() {
    groundShadow(128f, 204f, 168f)
    // Handle, drawn first so the lid overlaps its ends.
    drawRoundRect(
        vertical(Color(0xFFEDF1F5), Color(0xFF8792A0), 50f, 120f),
        Offset(90f, 50f), Size(76f, 70f), CornerRadius(20f), style = Stroke(width = 14f),
    )
    // Body and lid.
    drawRoundRect(vertical(Color(0xFFF26B3D), Color(0xFFBD3A18), 110f, 204f), Offset(38f, 110f), Size(180f, 94f), CornerRadius(14f))
    drawRoundRect(vertical(Color(0xFFFF9160), Color(0xFFF26B3D), 88f, 144f), Offset(38f, 88f), Size(180f, 58f), CornerRadius(14f))
    drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(50f, 94f), Size(156f, 4f), CornerRadius(2f))
    // Seam between lid and body.
    drawRect(Color.Black.copy(alpha = 0.30f), Offset(38f, 143f), Size(180f, 5f))
    drawRect(Color.White.copy(alpha = 0.18f), Offset(38f, 148f), Size(180f, 2f))
    // Rivets and the latch.
    for (x in listOf(56f, 200f)) {
        drawCircle(Color(0xFFDDE3EA), 6f, Offset(x, 176f))
        drawCircle(Color.Black.copy(alpha = 0.25f), 6f, Offset(x, 176f), style = Stroke(width = 1.5f))
    }
    drawRoundRect(vertical(Color(0xFFFFE486), Color(0xFFE0A012), 126f, 164f), Offset(106f, 126f), Size(44f, 38f), CornerRadius(9f))
    drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(106f, 126f), Size(44f, 38f), CornerRadius(9f), style = Stroke(width = 1.5f))
    drawCircle(Color(0xFF5A3A00), 5f, Offset(128f, 142f))
    drawRect(Color(0xFF5A3A00), Offset(126f, 144f), Size(4f, 11f))
}

/** A shopping bag with a gold star. */
private fun DrawScope.drawBag() {
    groundShadow(128f, 210f, 150f)
    drawArc(
        Color.White, 180f, 180f, false, Offset(92f, 46f), Size(72f, 84f),
        style = Stroke(width = 12f, cap = StrokeCap.Round),
    )
    val bag = Path().apply { moveTo(64f, 92f); lineTo(192f, 92f); lineTo(204f, 206f); lineTo(52f, 206f); close() }
    drawPath(bag, vertical(Color.White, Color(0xFFC9D9EE), 92f, 206f))
    drawPath(bag, Color.White, style = Stroke(width = 10f, join = StrokeJoin.Round))
    drawPath(bag, Color.Black.copy(alpha = 0.10f), style = Stroke(width = 2f, join = StrokeJoin.Round))
    // Fold shading at the top of the bag.
    drawRect(Color.Black.copy(alpha = 0.10f), Offset(64f, 92f), Size(128f, 10f))
    val star = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) 34f else 15f
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = 128f + r * cos(a).toFloat()
        val y = 154f + r * sin(a).toFloat()
        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    drawPath(star, vertical(Color(0xFFFFE486), Color(0xFFE59A10), 120f, 190f))
    drawPath(star, Color(0xFF9A5A00).copy(alpha = 0.5f), style = Stroke(width = 2f, join = StrokeJoin.Round))
}

/** A monitor showing a terminal prompt. */
private fun DrawScope.drawMonitor() {
    groundShadow(128f, 208f, 140f)
    // Stand and base.
    drawRect(vertical(Color(0xFFCBD3DC), Color(0xFF7D8896), 172f, 198f), Offset(114f, 172f), Size(28f, 26f))
    drawRoundRect(vertical(Color(0xFFE6EBF0), Color(0xFF8792A0), 192f, 206f), Offset(84f, 192f), Size(88f, 14f), CornerRadius(7f))
    // Frame and screen.
    drawRoundRect(vertical(Color(0xFFF7F9FB), Color(0xFFB4BEC9), 58f, 178f), Offset(36f, 58f), Size(184f, 120f), CornerRadius(14f))
    drawRoundRect(vertical(Color(0xFF223046), Color(0xFF0A111B), 70f, 166f), Offset(48f, 70f), Size(160f, 96f), CornerRadius(8f))
    // Glare across the screen.
    val glare = Path().apply { moveTo(48f, 70f); lineTo(140f, 70f); lineTo(88f, 166f); lineTo(48f, 166f); close() }
    drawPath(glare, Color.White.copy(alpha = 0.07f))
    // Prompt.
    val green = Color(0xFF8AE234)
    val prompt = Path().apply { moveTo(72f, 98f); lineTo(98f, 120f); lineTo(72f, 142f) }
    drawPath(prompt, green, style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawLine(green, Offset(112f, 144f), Offset(150f, 144f), strokeWidth = 10f, cap = StrokeCap.Round)
}
