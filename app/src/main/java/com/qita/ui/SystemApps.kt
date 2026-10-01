package com.qita.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

/** What a built-in bubble does when started. */
enum class SystemAction(val id: String, val label: String, val blurb: String) {
    SETTINGS("qita.sys.settings", "Settings", "Change the theme, home screen layout, controller and system options."),
    STORE("qita.sys.store", "Store", "Emulators and free games to download, a browser for any page, and your downloads."),
    DESKTOP("qita.sys.desktop", "Desktop", "A desktop with every app on this device, for anything not on the home screen."),
    GAMES("qita.sys.games", "Games", "Your games from every console, and the emulators that play them."),
    BROWSER("qita.sys.browser", "Browser", "Browse the web with tabs, bookmarks and history. Downloads go through the launcher."),
    FOLDERS("qita.sys.folders", "Folders", "Look through the files on this device, and copy, move, unpack or send them to a game folder."),
    ANDROID("qita.sys.android", "System Settings", "Open Android's own settings: Wi-Fi, Bluetooth, sound, display, apps and storage."),
}

/** Ids of the built-in bubbles, in the order they start on the home screen. */
val SYSTEM_IDS: List<String> = SystemAction.values().map { it.id }

/**
 * The three built-in bubbles. Like the Vita's own system icons they are glossy black spheres with a white
 * pictogram. The art is drawn in code; [tint] only colours the LiveArea page of each one.
 */
val SYSTEM_APPS: List<LaunchableApp> by lazy {
    listOf(
        // Every built-in bubble is black glass with a white pictogram.
        systemApp(SystemAction.SETTINGS, Color(0xFF3F9A5C)) { drawToolbox() },
        systemApp(SystemAction.STORE, Color(0xFF2E7DD7)) { drawBag() },
        systemApp(SystemAction.DESKTOP, Color(0xFFD9691E)) { drawMonitor() },
        systemApp(SystemAction.GAMES, Color(0xFF7A4FD0)) { drawGamepad() },
        systemApp(SystemAction.BROWSER, Color(0xFF1FA3C9)) { drawGlobe() },
        systemApp(SystemAction.FOLDERS, Color(0xFFD9A21E)) { drawFolder() },
        systemApp(SystemAction.ANDROID, Color(0xFF3DA35D)) { drawGear() },
    )
}

private val Ink = Color(0xFF14161A)

private fun systemApp(
    action: SystemAction,
    tint: Color,
    backTop: Color = Color(0xFF30343C),
    backBottom: Color = Color(0xFF07080A),
    art: DrawScope.() -> Unit,
): LaunchableApp {
    val icon = drawIcon {
        // A dark backdrop with a faint light in the upper left.
        drawRect(Brush.linearGradient(listOf(backTop, backBottom), Offset.Zero, Offset(ICON.toFloat(), ICON.toFloat())))
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), Offset(ICON * 0.3f, ICON * 0.25f), ICON * 0.55f),
            radius = ICON * 0.55f, center = Offset(ICON * 0.3f, ICON * 0.25f),
        )
        art()
    }
    return LaunchableApp(
        label = action.label,
        packageName = action.id,
        icon = icon,
        tint = tint,
        action = action,
        ball = SphereRenderer.render(icon.asAndroidBitmap(), 224, systemBody(action), 0.55f).asImageBitmap(),
    )
}

private const val ICON = 256

private fun drawIcon(block: DrawScope.() -> Unit): ImageBitmap {
    val image = ImageBitmap(ICON, ICON)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ComposeCanvas(image), Size(ICON.toFloat(), ICON.toFloat()), block)
    return image
}

private fun white(y0: Float, y1: Float) = Brush.verticalGradient(listOf(Color.White, Color(0xFFC4CAD2)), startY = y0, endY = y1)

/** A toolbox pictogram: handle, lid, body, a dark seam and latch. */
private fun DrawScope.drawToolbox(ink: Color = Ink) {
    drawRoundRect(white(48f, 120f), Offset(90f, 48f), Size(76f, 70f), CornerRadius(20f), style = Stroke(width = 13f))
    drawRoundRect(white(92f, 208f), Offset(40f, 92f), Size(176f, 116f), CornerRadius(16f))
    drawRect(ink, Offset(40f, 142f), Size(176f, 7f))
    drawRoundRect(Color.White, Offset(104f, 128f), Size(48f, 40f), CornerRadius(9f))
    drawRoundRect(ink, Offset(104f, 128f), Size(48f, 40f), CornerRadius(9f), style = Stroke(width = 5f))
    drawCircle(ink, 6f, Offset(128f, 146f))
    drawRect(ink, Offset(126f, 148f), Size(4f, 12f))
}

/** A shopping bag pictogram with a star cut out of it. */
private fun DrawScope.drawBag() {
    drawArc(Color.White, 180f, 180f, false, Offset(92f, 44f), Size(72f, 84f), style = Stroke(width = 12f, cap = StrokeCap.Round))
    val bag = Path().apply { moveTo(62f, 92f); lineTo(194f, 92f); lineTo(206f, 210f); lineTo(50f, 210f); close() }
    drawPath(bag, white(92f, 210f))
    drawPath(bag, Color.White, style = Stroke(width = 10f, join = StrokeJoin.Round))
    val star = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) 36f else 16f
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = 128f + r * cos(a).toFloat()
        val y = 156f + r * sin(a).toFloat()
        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    drawPath(star, Ink)
}

/** A monitor pictogram with a terminal prompt. */
private fun DrawScope.drawMonitor() {
    drawRect(white(172f, 198f), Offset(114f, 172f), Size(28f, 28f))
    drawRoundRect(Color.White, Offset(84f, 194f), Size(88f, 14f), CornerRadius(7f))
    drawRoundRect(white(56f, 180f), Offset(36f, 56f), Size(184f, 124f), CornerRadius(14f))
    drawRoundRect(Ink, Offset(50f, 70f), Size(156f, 96f), CornerRadius(8f))
    val prompt = Path().apply { moveTo(74f, 98f); lineTo(100f, 120f); lineTo(74f, 142f) }
    drawPath(prompt, Color.White, style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawLine(Color.White, Offset(114f, 144f), Offset(152f, 144f), strokeWidth = 10f, cap = StrokeCap.Round)
}

/** A game controller pictogram: a rounded body, a cross on the left, two buttons on the right. */
private fun DrawScope.drawGamepad() {
    val body = Path().apply {
        moveTo(74f, 84f); lineTo(182f, 84f)
        cubicTo(214f, 84f, 232f, 112f, 226f, 150f); cubicTo(222f, 180f, 204f, 192f, 186f, 184f)
        cubicTo(172f, 178f, 166f, 160f, 150f, 160f); lineTo(106f, 160f)
        cubicTo(90f, 160f, 84f, 178f, 70f, 184f); cubicTo(52f, 192f, 34f, 180f, 30f, 150f)
        cubicTo(24f, 112f, 42f, 84f, 74f, 84f); close()
    }
    drawPath(body, white(84f, 190f))
    // The cross.
    drawRoundRect(Ink, Offset(62f, 110f), Size(14f, 44f), CornerRadius(4f))
    drawRoundRect(Ink, Offset(47f, 125f), Size(44f, 14f), CornerRadius(4f))
    // The buttons.
    drawCircle(Ink, 9f, Offset(178f, 116f))
    drawCircle(Ink, 9f, Offset(200f, 138f))
}

/** A globe pictogram: a circle, two meridians and two parallels. */
private fun DrawScope.drawGlobe() {
    val c = Offset(128f, 128f)
    drawCircle(white(40f, 216f), 84f, c)
    drawCircle(Ink, 84f, c, style = Stroke(width = 4f))
    // Meridians and parallels in dark ink on the white disc.
    drawOval(Ink, Offset(88f, 44f), Size(80f, 168f), style = Stroke(width = 6f))
    drawLine(Ink, Offset(44f, 128f), Offset(212f, 128f), strokeWidth = 6f)
    drawArc(Ink, 200f, 140f, false, Offset(52f, 70f), Size(152f, 60f), style = Stroke(width = 5f, cap = StrokeCap.Round))
    drawArc(Ink, 20f, 140f, false, Offset(52f, 126f), Size(152f, 60f), style = Stroke(width = 5f, cap = StrokeCap.Round))
    drawLine(Ink, Offset(128f, 44f), Offset(128f, 212f), strokeWidth = 5f)
}

/** A folder pictogram: a tab at the back and a rounded front, with a dark seam. */
private fun DrawScope.drawFolder() {
    val back = Path().apply { moveTo(40f, 196f); lineTo(40f, 72f); lineTo(104f, 72f); lineTo(124f, 94f); lineTo(216f, 94f); lineTo(216f, 196f); close() }
    drawPath(back, Color(0xFFD7DCE3))
    drawRoundRect(white(110f, 210f), Offset(32f, 112f), Size(192f, 96f), CornerRadius(14f))
    drawRect(Ink, Offset(32f, 138f), Size(192f, 5f))
}

/** A cog: Android's own settings. */
private fun DrawScope.drawGear() {
    val c = Offset(128f, 128f)
    val metal = Color(0xFFD7DCE3)
    for (i in 0 until 8) rotate(i * 45f, c) { drawRoundRect(metal, Offset(112f, 34f), Size(32f, 50f), CornerRadius(7f)) }
    drawCircle(metal, radius = 66f, center = c)
    drawCircle(Ink, radius = 28f, center = c)
}

/** The ARGB colour of the glass body behind a built-in bubble's art. */
fun systemBody(action: SystemAction?): Int = 0xFF0A0B0D.toInt()
