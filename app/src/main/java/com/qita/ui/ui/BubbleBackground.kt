package com.qita.ui.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val x: Float, val y: Float, val r: Float, val speed: Float, val sway: Float, val alpha: Float)

/** Vita-style background: a smooth blue gradient with soft particles drifting upward. */
@Composable
fun BubbleBackground(modifier: Modifier = Modifier, top: Color = Color(0xFF1466C8), bottom: Color = Color(0xFF4FB6F0)) {
    val particles = remember {
        val rnd = Random(7)
        List(28) {
            Particle(rnd.nextFloat(), rnd.nextFloat(), 0.008f + rnd.nextFloat() * 0.03f, 0.3f + rnd.nextFloat(), rnd.nextFloat() * 0.03f, 0.08f + rnd.nextFloat() * 0.2f)
        }
    }
    val t by rememberInfiniteTransition(label = "particles").animateFloat(
        0f, 1f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        particles.forEach { p ->
            val progress = (p.y - t * p.speed * 2f).mod(1f)
            val cx = (p.x + sin(progress * 6.28f) * p.sway) * size.width
            drawCircle(Color.White.copy(alpha = p.alpha), p.r * size.width, Offset(cx, progress * size.height))
        }
    }
}
