package com.qita.ui.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val x: Float, val y: Float, val r: Float, val speed: Float, val sway: Float, val alpha: Float)

/**
 * The Vita home wallpaper: a deep sky gradient that brightens toward the bottom, with soft white
 * swooshes drifting slowly across it. A custom [wallpaper] image replaces it. Floating particles
 * are optional.
 */
@Composable
fun BubbleBackground(
    modifier: Modifier = Modifier,
    top: Color = Color(0xFF0A2C9A),
    mid: Color = Color(0xFF1B5BD8),
    bottom: Color = Color(0xFFB4D8FF),
    particles: Boolean = false,
    wallpaper: ImageBitmap? = null,
    particleCount: Int = 28,
    dim: Float = 0f,
) {
    val topColor by animateColorAsState(top, tween(600), label = "top")
    val midColor by animateColorAsState(mid, tween(600), label = "mid")
    val bottomColor by animateColorAsState(bottom, tween(600), label = "bottom")
    Box(modifier.fillMaxSize()) {
        if (wallpaper != null) {
            Image(wallpaper, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Swooshes(topColor, midColor, bottomColor)
        }
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        if (particles) Particles(particleCount)
    }
}

@Composable
private fun Swooshes(top: Color, mid: Color, bottom: Color) {
    val phase by rememberInfiniteTransition(label = "swoosh").animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(0f to top, 0.55f to mid, 1f to bottom))
        // Extra glow along the bottom edge.
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f)), startY = size.height * 0.62f, endY = size.height),
        )
        swoosh(0.74f, 0.040f, 0.16f, 0.55f, phase)
        swoosh(0.83f, 0.050f, 0.20f, 0.28f, phase * 0.7f + 1.5f)
        swoosh(0.63f, 0.035f, 0.10f, 0.16f, phase * 1.2f + 3f)
    }
}

/** One soft curved band of light that bobs gently up and down. */
private fun DrawScope.swoosh(yBase: Float, amp: Float, thickness: Float, alpha: Float, phase: Float) {
    val w = size.width
    val h = size.height
    val y0 = h * yBase + sin(phase) * amp * h
    val t = h * thickness
    val path = Path().apply {
        moveTo(-w * 0.05f, y0 + t * 0.6f)
        cubicTo(w * 0.30f, y0 - t * 0.9f, w * 0.62f, y0 + t * 1.1f, w * 1.05f, y0 - t * 0.7f)
        lineTo(w * 1.05f, y0 - t * 0.7f + t)
        cubicTo(w * 0.62f, y0 + t * 2.4f, w * 0.30f, y0 + t * 0.3f, -w * 0.05f, y0 + t * 1.4f)
        close()
    }
    drawPath(
        path,
        Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0f),
                Color.White.copy(alpha = alpha),
                Color.White.copy(alpha = alpha * 0.5f),
                Color.White.copy(alpha = 0f),
            ),
        ),
    )
}

@Composable
private fun Particles(count: Int) {
    val list = remember(count) {
        val rnd = Random(7)
        List(count) {
            Particle(rnd.nextFloat(), rnd.nextFloat(), 0.008f + rnd.nextFloat() * 0.03f, 0.3f + rnd.nextFloat(), rnd.nextFloat() * 0.03f, 0.08f + rnd.nextFloat() * 0.2f)
        }
    }
    val t by rememberInfiniteTransition(label = "particles").animateFloat(
        0f, 1f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(Modifier.fillMaxSize()) {
        list.forEach { p ->
            val progress = (p.y - t * p.speed * 2f).mod(1f)
            val cx = (p.x + sin(progress * 6.28f) * p.sway) * size.width
            drawCircle(Color.White.copy(alpha = p.alpha), p.r * size.width, Offset(cx, progress * size.height))
        }
    }
}
