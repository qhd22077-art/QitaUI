package com.qita.ui.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Controller
import com.qita.ui.LAYOUTS
import com.qita.ui.Settings
import com.qita.ui.THEMES
import kotlin.math.roundToInt

private val Cyan = Color(0xFF40E0E0)
private val DeepGreen = Color(0xFF0B6A14)

/**
 * Settings in the Vita's own style: a green sky, a centred title over a thin rule, big white rows
 * with round icons, a cyan band on the selected row, glowing checkboxes and a round back button.
 * The top level is a menu; each entry opens a page of settings.
 */
@Composable
fun SettingsPage(
    settings: Settings,
    hasWallpaper: Boolean,
    wallpaper: ImageBitmap?,
    onChange: (Settings) -> Unit,
    onWallpaper: (Uri) -> Unit,
    onClearWallpaper: () -> Unit,
    onClearHome: () -> Unit,
    onShowTutorial: () -> Unit,
    onClose: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onWallpaper(uri)
    }
    var page by remember { mutableStateOf<String?>(null) }
    // Back steps out of a page first, then closes Settings (this handler is registered after Home's, so it wins).
    BackHandler(enabled = page != null) { page = null }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    val title = when (page) {
        "theme" -> "Theme & Background"
        "home" -> "Home Screen"
        "status" -> "Date & Time"
        "controller" -> "Controller"
        "system" -> "System"
        else -> "Settings"
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        VitaGreen()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, showHome = false)
            Text(
                title,
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            )
            Box(Modifier.fillMaxWidth().padding(horizontal = 60.dp).height(1.dp).background(Color.White.copy(alpha = 0.5f)))
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(160)))
                    }
                },
                label = "settingsPage",
            ) { current ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padScroller { scroll.animateScrollBy(it) }
                        .verticalScroll(scroll)
                        .padding(start = 96.dp, end = 72.dp, bottom = 96.dp),
                ) {
                    when (current) {
                        "theme" -> {
                            THEMES.forEachIndexed { i, t ->
                                MenuRow("set:theme:$i", "◐", t.name, trailing = {
                                    Box(Modifier.size(22.dp).background(Brush.verticalGradient(listOf(t.top, t.bottom)), CircleShape).border(1.5.dp, Color.White, CircleShape))
                                    if (i == settings.themeIndex) Text("✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }) { onChange(settings.copy(themeIndex = i)) }
                            }
                            MenuRow("set:wallpaper", "▣", "Choose wallpaper image") { picker.launch("image/*") }
                            if (hasWallpaper) MenuRow("set:wallpaper:remove", "✕", "Remove wallpaper image") { onClearWallpaper() }
                            CheckRow("set:particles", "✦", "Floating particles", settings.particles) { onChange(settings.copy(particles = it)) }
                            if (settings.particles) {
                                SliderRow("set:particleCount", "✦", "Particle amount", settings.particleCount.toFloat(), 5f..60f, 5f) {
                                    onChange(settings.copy(particleCount = it.roundToInt()))
                                }
                            }
                            SliderRow("set:dim", "◑", "Dim background", settings.dim, 0f..0.6f, 0.05f) { onChange(settings.copy(dim = it)) }
                        }
                        "home" -> {
                            LAYOUTS.forEachIndexed { i, l ->
                                MenuRow("set:layout:$i", "⌂", "Layout: ${l.name}", trailing = {
                                    if (i == settings.layoutIndex) Text("✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }) { onChange(settings.copy(layoutIndex = i)) }
                            }
                            CheckRow("set:rounded", "▢", "Rounded square bubbles", settings.roundedBubbles) { onChange(settings.copy(roundedBubbles = it)) }
                            CheckRow("set:labels", "A", "Show app names", settings.showLabels) { onChange(settings.copy(showLabels = it)) }
                            CheckRow("set:dots", "•", "Show page dots", settings.showDots) { onChange(settings.copy(showDots = it)) }
                            SliderRow("set:size", "●", "Bubble size", settings.bubbleScale, 0.7f..1.1f, 0.05f) { onChange(settings.copy(bubbleScale = it)) }
                            CheckRow("set:newest", "↓", "Sort newest apps first", settings.sortNewest) { onChange(settings.copy(sortNewest = it)) }
                            CheckRow("set:autoadd", "+", "Add newly installed apps to home", settings.autoAdd) { onChange(settings.copy(autoAdd = it)) }
                            MenuRow("set:clearhome", "✕", "Remove all apps from home") { onClearHome() }
                        }
                        "status" -> {
                            CheckRow("set:24h", "◷", "24-hour clock", settings.use24h) { onChange(settings.copy(use24h = it)) }
                            CheckRow("set:battery", "⚡", "Show battery level", settings.showBattery) { onChange(settings.copy(showBattery = it)) }
                        }
                        "controller" -> {
                            CheckRow("set:cursor", "↖", "Cursor mode", settings.cursorMode) { onChange(settings.copy(cursorMode = it)) }
                            SliderRow("set:cursorSpeed", "↖", "Cursor speed", settings.cursorSpeed, 0.5f..2.5f, 0.1f) { onChange(settings.copy(cursorSpeed = it)) }
                            CheckRow("set:ps", "✕", "PlayStation button symbols", settings.psLabels) { onChange(settings.copy(psLabels = it)) }
                            CheckRow("set:swap", "⇄", "Swap A/B and X/Y (Nintendo layout)", settings.swapAB) { onChange(settings.copy(swapAB = it)) }
                            CheckRow("set:debug", "?", "Show live input readout on screen", settings.debugInput) { onChange(settings.copy(debugInput = it)) }
                            InfoBox("Last input: ${Controller.lastInput}", mono = true)
                            InfoBox(
                                "A select   B back   X options   Y move (home) or add/remove (desktop)\n" +
                                    "Start settings   Select cursor mode   L1/R1 page or folder\n" +
                                    "L2 desktop   R2 search   L3 recenter cursor   R3 precision cursor\n" +
                                    "Right stick left/right changes page.\n" +
                                    "Move mode: D-pad carries the bubble, A drops it, B cancels.\n" +
                                    "Cursor mode: left stick moves the pointer, A clicks (hold to drag), right stick scrolls, D-pad nudges.",
                            )
                        }
                        "system" -> {
                            CheckRow("set:haptics", "∷", "Vibrate on long-press and highlight", settings.haptics) { onChange(settings.copy(haptics = it)) }
                            CheckRow("set:lock", "▭", "Lock screen when the launcher starts or the screen wakes", settings.lockScreen) { onChange(settings.copy(lockScreen = it)) }
                            MenuRow("set:tutorial", "i", "Show tutorial") { onShowTutorial() }
                            MenuRow("set:reset", "↺", "Reset all settings") { onChange(Settings()) }
                        }
                        else -> {
                            NavRow("set:nav:theme", "◐", "Theme & Background") { page = "theme" }
                            NavRow("set:nav:home", "⌂", "Home Screen") { page = "home" }
                            NavRow("set:nav:status", "◷", "Date & Time") { page = "status" }
                            NavRow("set:nav:controller", "✦", "Controller") { page = "controller" }
                            NavRow("set:nav:system", "⚙", "System") { page = "system" }
                        }
                    }
                }
            }
        }
        BackButton(
            onClick = { if (page != null) page = null else onClose() },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
        )
    }
}

/** The green Vita settings sky: dark at the top, glowing lime at the bottom, with a faint toolbox watermark. */
@Composable
private fun VitaGreen() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF0A5A12), 0.55f to Color(0xFF1F9B2A), 0.85f to Color(0xFF3FD84A), 1f to Color(0xFF8DFF6B),
            ),
        )
        // Watermark: a big translucent toolbox on the right.
        val w = size.width * 0.36f
        val h = size.height * 0.40f
        val left = size.width * 0.62f
        val top = size.height * 0.58f
        val mark = Color.White.copy(alpha = 0.10f)
        drawRoundRect(mark, Offset(left, top), Size(w, h), CornerRadius(w * 0.06f))
        drawRoundRect(mark, Offset(left + w * 0.27f, top - h * 0.24f), Size(w * 0.46f, h * 0.30f), CornerRadius(w * 0.08f), style = Stroke(width = w * 0.07f))
        drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(left + w * 0.16f, top + h * 0.32f), Size(w * 0.14f, h * 0.30f), CornerRadius(w * 0.03f))
        drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(left + w * 0.70f, top + h * 0.32f), Size(w * 0.14f, h * 0.30f), CornerRadius(w * 0.03f))
        // Extra glow along the bottom edge.
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f)), startY = size.height * 0.8f, endY = size.height),
        )
    }
}

/** Cyan band behind the selected row, fading out at both ends, plus the thin separator line under every row. */
private fun Modifier.rowBand(lit: Boolean): Modifier = composed {
    val band by animateFloatAsState(if (lit) 1f else 0f, tween(140), label = "band")
    drawBehind {
        if (band > 0.01f) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(Cyan.copy(alpha = 0f), Cyan.copy(alpha = 0.55f * band), Cyan.copy(alpha = 0.55f * band), Cyan.copy(alpha = 0f)),
                ),
            )
        }
        drawLine(Color.White.copy(alpha = 0.28f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }
}

@Composable
private fun GlyphBadge(glyph: String) {
    Box(
        Modifier
            .size(34.dp)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFCFE8CF))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = DeepGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MenuRow(
    key: String,
    glyph: String,
    label: String,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val lit = padHighlighted(key) || padHovered(key)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padClickable(key, corner = 0.dp, ring = false, onClick = onClick)
            .rowBand(lit)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlyphBadge(glyph)
        Text(label, Modifier.weight(1f), color = Color.White, fontSize = 20.sp)
        trailing()
    }
}

@Composable
private fun NavRow(key: String, glyph: String, label: String, onClick: () -> Unit) {
    MenuRow(key, glyph, label, trailing = { Text("▶", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp) }, onClick = onClick)
}

@Composable
private fun CheckRow(key: String, glyph: String, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    MenuRow(key, glyph, label, trailing = { GlowCheck(checked) }) { onChecked(!checked) }
}

/** Rounded box with a soft glow and a check when on, like the Vita's checkbox. */
@Composable
private fun GlowCheck(checked: Boolean) {
    val glow by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "glow")
    Canvas(Modifier.size(34.dp)) {
        val r = CornerRadius(7.dp.toPx())
        if (glow > 0.01f) {
            drawRoundRect(Color.White.copy(alpha = 0.30f * glow), Offset(-3.dp.toPx(), -3.dp.toPx()), Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), CornerRadius(10.dp.toPx()))
        }
        drawRoundRect(Color.White.copy(alpha = 0.16f + 0.5f * glow), size = size, cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.9f), size = size, cornerRadius = r, style = Stroke(width = 2.dp.toPx()))
        if (checked) {
            val tick = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.74f)
                lineTo(size.width * 0.80f, size.height * 0.28f)
            }
            drawPath(tick, DeepGreen, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A row with a slider under its label; with it highlighted, left/right on the gamepad steps the value. */
@Composable
private fun SliderRow(
    key: String,
    glyph: String,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
) {
    val lit = padHighlighted(key) || padHovered(key)
    Column(
        Modifier
            .fillMaxWidth()
            .padTarget(key, corner = 0.dp, ring = false, onAdjust = { dir ->
                onChange((value + dir * step).coerceIn(range.start, range.endInclusive))
            })
            .rowBand(lit)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlyphBadge(glyph)
            Text(label, color = Color.White, fontSize = 20.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
        )
    }
}

@Composable
private fun InfoBox(text: String, mono: Boolean = false) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        color = if (mono) Color(0xFFD8FFB0) else Color.White.copy(alpha = 0.9f),
        fontSize = 12.sp,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
    )
}

/** Round glossy back button in the bottom-left corner, with a return arrow. */
@Composable
private fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lit = padHighlighted("set:back") || padHovered("set:back")
    Box(
        modifier
            .size(66.dp)
            .padClickable("set:back", corner = null, pad = 4.dp, onClick = onClick)
            .clip(CircleShape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFFA6FF8F), Color(0xFF3FD84A), Color(0xFF14902A)),
                        center = Offset(size.width * 0.4f, size.height * 0.32f),
                        radius = size.width * 0.85f,
                    ),
                )
            }
            .border(if (lit) 4.dp else 2.dp, if (lit) Cyan else Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            // Top run, the curve around the right, and the bottom run: a return arrow.
            drawLine(Color.White, Offset(w * 0.30f, h * 0.25f), Offset(w * 0.60f, h * 0.25f), stroke.width, StrokeCap.Round)
            drawArc(
                Color.White, startAngle = -90f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(w * 0.34f, h * 0.25f), size = Size(w * 0.52f, h * 0.50f), style = stroke,
            )
            drawLine(Color.White, Offset(w * 0.60f, h * 0.75f), Offset(w * 0.34f, h * 0.75f), stroke.width, StrokeCap.Round)
            val head = Path().apply {
                moveTo(w * 0.10f, h * 0.25f)
                lineTo(w * 0.32f, h * 0.08f)
                lineTo(w * 0.32f, h * 0.42f)
                close()
            }
            drawPath(head, Color.White)
        }
    }
}
