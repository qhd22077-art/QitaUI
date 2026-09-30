package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Settings
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Vita lock screen, as on the console: the wallpaper with a framed translucent panel inset from the edges, the date
 * and a big clock at the bottom left, the faint PlayStation symbols at the bottom right and a small curled corner at
 * the top right. Peel the corner away (or tap it, or double-tap anywhere, or press the confirm button) to reveal the
 * home screen. This is in-app only: Android does not let a launcher replace the phone's real lock screen, so it is
 * visual and not a security lock.
 */
@Composable
fun LockScreen(settings: Settings, wallpaper: ImageBitmap?, onUnlock: () -> Unit) {
    val density = LocalDensity.current
    val peel = remember { Animatable(0f) }
    var pageWidth by remember { mutableStateOf(1) }
    val baseFold = with(density) { 40.dp.toPx() }
    val radius = with(density) { 10.dp.toPx() }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(5_000)
        }
    }
    // The clock and date ease in.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(480, easing = VitaMotion.Ease)) }

    Column(
        Modifier
            .fillMaxSize()
            // Nothing underneath should be touchable while locked; double-tapping is a fallback unlock.
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onUnlock() }) }
            .statusBarsPadding(),
    ) {
        StatusBar(settings.use24h, settings.showBattery, showHome = false)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The wallpaper stays put; only the framed panel peels away.
            val theme = settings.theme
            val sky = Triple(theme.top, theme.mid, theme.bottom)
            BubbleBackground(
                top = theme.top, mid = theme.mid, bottom = theme.bottom, wallpaper = wallpaper,
                scene = { SceneMix(theme.scene, theme.scene, 0f, sky, sky) },
            )
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { pageWidth = it.width }
                        .graphicsLayer {
                            shape = PeelShape(baseFold + peel.value, radius)
                            clip = true
                        }
                        .background(Color.Black.copy(alpha = 0.16f))
                        .vitaPanel(10.dp, 0.30f),
                ) {
                    val clockSize = (maxHeight.value * 0.30f).sp
                    val dateSize = (maxHeight.value * 0.075f).sp
                    // The content fades as the sheet peels, so nothing is cut off abruptly.
                    val fade = { (1f - peel.value / (pageWidth * 0.35f)).coerceIn(0f, 1f) }
                    // The faint PlayStation symbols in the lower right corner.
                    Canvas(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(width = maxWidth * 0.52f, height = maxHeight * 0.26f)
                            .graphicsLayer { alpha = enter.value * fade() },
                    ) { drawSymbolRow() }
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 18.dp, bottom = 10.dp)
                            .graphicsLayer {
                                alpha = enter.value * fade()
                                translationY = (1f - enter.value) * 12.dp.toPx()
                            },
                    ) {
                        Text(
                            SimpleDateFormat("MMMM d (EEEE)", Locale.getDefault()).format(now),
                            color = Color.White, fontSize = dateSize,
                        )
                        Text(
                            SimpleDateFormat(if (settings.use24h) "HH:mm" else "h:mm", Locale.getDefault()).format(now),
                            color = Color.White, fontSize = clockSize, fontWeight = FontWeight.Normal,
                            style = TextStyle(letterSpacing = 1.sp),
                        )
                    }
                }

                PeelBack(baseFold + peel.value, Color(0xFF1E5AE0), radius)

                // Above everything else in the panel, so nothing can cover it.
                PeelCorner(
                    peel = peel,
                    pageWidth = pageWidth,
                    padKey = "lock:peel",
                    hint = true,
                    repeatHint = true,
                    tapToPeel = true,
                    onPeeled = onUnlock,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

/** △ ○ × □ side by side as thin pale outlines, cut off by the panel's lower edge as on the console. */
private fun DrawScope.drawSymbolRow() {
    val slot = size.width / 4f
    val r = minOf(slot * 0.42f, size.height * 0.9f)
    val stroke = Stroke(width = r * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val color = Color.White.copy(alpha = 0.28f)
    val cy = size.height * 0.62f
    // Triangle
    var cx = slot * 0.5f
    drawPath(
        Path().apply { moveTo(cx, cy - r); lineTo(cx + r * 0.95f, cy + r * 0.8f); lineTo(cx - r * 0.95f, cy + r * 0.8f); close() },
        color, style = stroke,
    )
    // Circle
    cx = slot * 1.5f
    drawCircle(color, radius = r * 0.92f, center = Offset(cx, cy), style = stroke)
    // Cross
    cx = slot * 2.5f
    drawLine(color, Offset(cx - r * 0.8f, cy - r * 0.8f), Offset(cx + r * 0.8f, cy + r * 0.8f), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(cx + r * 0.8f, cy - r * 0.8f), Offset(cx - r * 0.8f, cy + r * 0.8f), stroke.width, StrokeCap.Round)
    // Square
    cx = slot * 3.5f
    drawRoundRect(color, Offset(cx - r * 0.8f, cy - r * 0.8f), androidx.compose.ui.geometry.Size(r * 1.6f, r * 1.6f), androidx.compose.ui.geometry.CornerRadius(r * 0.08f), style = stroke)
}
