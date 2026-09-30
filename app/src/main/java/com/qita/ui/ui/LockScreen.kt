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
import androidx.compose.ui.draw.blur
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
 * The Vita lock screen, as on the real console: a bright blue sky with soft light streaks, a huge thin white clock with the
 * date under it, "Peel to unlock" at the bottom and a curled corner at the top right. Peel the corner away (or tap it, or
 * double-tap anywhere, or press the confirm button) to reveal the home screen. This is in-app only: Android does not let a
 * launcher replace the phone's real lock screen, so it is visual and not a security lock.
 */
@Composable
fun LockScreen(settings: Settings, onUnlock: () -> Unit) {
    val density = LocalDensity.current
    val peel = remember { Animatable(0f) }
    var pageWidth by remember { mutableStateOf(1) }
    val baseFold = with(density) { 56.dp.toPx() }
    val radius = with(density) { 14.dp.toPx() }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(5_000)
        }
    }
    // The clock and date ease in; the hint follows a little later.
    val enter = remember { Animatable(0f) }
    val hintIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { enter.animateTo(1f, tween(480, easing = VitaMotion.Ease)) }
        delay(260)
        hintIn.animateTo(1f, tween(400))
    }
    val nudge by rememberInfiniteTransition(label = "lockNudge").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "nudge",
    )

    Column(
        Modifier
            .fillMaxSize()
            // Nothing underneath should be touchable while locked; double-tapping is a fallback unlock.
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onUnlock() }) }
            .statusBarsPadding(),
    ) {
        StatusBar(settings.use24h, settings.showBattery, showHome = false)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The wallpaper stays put; only the clock sheet peels away.
            LockSky()
            Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { pageWidth = it.width }
                        .graphicsLayer {
                            shape = PeelShape(baseFold + peel.value, radius)
                            clip = true
                        },
                ) {
                    val clockSize = (maxHeight.value * 0.36f).sp
                    val dateSize = (maxHeight.value * 0.075f).sp
                    val hintSize = (maxHeight.value * 0.062f).sp
                    // The content fades as the sheet peels, so nothing is cut off abruptly.
                    val fade = { (1f - peel.value / (pageWidth * 0.35f)).coerceIn(0f, 1f) }
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .offset(y = (-maxHeight.value * 0.06f).dp)
                            .graphicsLayer {
                                alpha = enter.value * fade()
                                val sc = 0.94f + 0.06f * enter.value
                                scaleX = sc; scaleY = sc
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val time = SimpleDateFormat(if (settings.use24h) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
                        Box(contentAlignment = Alignment.Center) {
                            // A soft glow: the same text, wider and blurred (the blur shows on Android 12 and newer).
                            Text(
                                time, Modifier.blur(14.dp), color = Color.White.copy(alpha = 0.55f), fontSize = clockSize,
                                fontWeight = FontWeight.ExtraLight, style = TextStyle(letterSpacing = 4.sp),
                            )
                            Text(
                                time, color = Color.White, fontSize = clockSize, fontWeight = FontWeight.ExtraLight,
                                style = TextStyle(letterSpacing = 4.sp),
                            )
                        }
                        Text(
                            SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now),
                            color = Color.White, fontSize = dateSize, fontWeight = FontWeight.Light,
                        )
                    }
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .graphicsLayer { alpha = hintIn.value * fade() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text("◀", Modifier.offset(x = (nudge * 4f).dp), color = HintBlue, fontSize = hintSize)
                        Text("Peel to unlock", color = HintBlue, fontSize = hintSize, fontWeight = FontWeight.Light)
                        Text("▶", Modifier.offset(x = (-nudge * 4f).dp), color = HintBlue, fontSize = hintSize)
                    }
                }

                PeelBack(baseFold + peel.value, Color(0xFF0A3FD0), radius)

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

private val HintBlue = Color(0xFFA8DCFF)

/** The lock screen's sky: a bright blue gradient with long soft streaks of light that drift slowly. */
@Composable
private fun LockSky() {
    val phase by rememberInfiniteTransition(label = "lockSky").animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "skyPhase",
    )
    val streak = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF0030C8), 0.42f to Color(0xFF0A78F0), 0.72f to Color(0xFF40B4FF), 1f to Color(0xFFA0E2FF),
            ),
        )
        // (start y, control y1, control y2, end y, strength) as fractions of the height.
        val streaks = listOf(
            floatArrayOf(0.80f, 0.92f, 0.60f, 0.46f, 1.0f),
            floatArrayOf(0.88f, 0.86f, 0.80f, 0.66f, 0.7f),
            floatArrayOf(0.36f, 0.42f, 0.56f, 0.44f, 0.45f),
        )
        for ((i, s) in streaks.withIndex()) {
            val sway = 0.018f * kotlin.math.sin(phase + i * 1.7f)
            streak.rewind()
            streak.moveTo(-w * 0.05f, (s[0] + sway) * h)
            streak.cubicTo(w * 0.32f, (s[1] - sway) * h, w * 0.62f, (s[2] + sway) * h, w * 1.05f, (s[3] - sway) * h)
            val k = s[4]
            drawPath(streak, Color.White.copy(alpha = 0.10f * k), style = Stroke(width = h * 0.16f, cap = StrokeCap.Round))
            drawPath(streak, Color.White.copy(alpha = 0.18f * k), style = Stroke(width = h * 0.07f, cap = StrokeCap.Round))
            drawPath(streak, Color.White.copy(alpha = 0.55f * k), style = Stroke(width = h * 0.012f, cap = StrokeCap.Round))
        }
    }
}
