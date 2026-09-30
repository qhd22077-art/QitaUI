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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import kotlin.math.PI
import kotlin.math.hypot
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
    /** Current home page (fractional while swiping); the swooshes drift with it. Read while drawing. */
    scroll: () -> Float = { 0f },
    /** When set, the sky colours (top, middle, bottom) come from here, read while drawing, so they can follow a swipe. */
    palette: (() -> Triple<Color, Color, Color>)? = null,
) {
    val topColor by animateColorAsState(top, tween(600), label = "top")
    val midColor by animateColorAsState(mid, tween(600), label = "mid")
    val bottomColor by animateColorAsState(bottom, tween(600), label = "bottom")
    Box(modifier.fillMaxSize()) {
        if (wallpaper != null) {
            Image(wallpaper, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Swooshes(palette ?: { Triple(topColor, midColor, bottomColor) }, scroll)
        }
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        if (particles) Particles(particleCount)
    }
}

/** A soft floating bubble of light. [z] is its depth: 0 = far (small, dim, slow), 1 = near (big, bright, fast). */
private class Mote(val x: Float, val y: Float, val z: Float, val speed: Float, val seed: Float)

/**
 * The animated Vita-style scene, built from layers at different depths so the depth is obvious: hazy far
 * waves and dim small motes at the back, sharper brighter waves and big bubbles in front, each layer drifting at
 * its own speed and sliding by its own amount as the page changes (parallax). Everything is drawn in one pass
 * from reused paths, and the animation is only read while drawing.
 */
@Composable
private fun Swooshes(colors: () -> Triple<Color, Color, Color>, scroll: () -> Float) {
    val phase by rememberInfiniteTransition(label = "swoosh").animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val fill = remember { Path() }
    val crest = remember { Path() }
    val motes = remember {
        val rnd = Random(23)
        List(44) { Mote(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat().let { it * it * 0.4f + it * 0.6f }, 0.5f + rnd.nextFloat(), rnd.nextFloat() * 6.28f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val (top, mid, bottom) = colors()
        val w = size.width
        val h = size.height
        val page = scroll()
        val turn = phase / (2f * PI.toFloat())

        // Sky: deep at the top, bright at the horizon.
        drawRect(Brush.verticalGradient(0f to top, 0.50f to mid, 1f to lerp(bottom, Color.White, 0.25f)))
        // Far away: a big soft glow and two faint beams of light.
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(w * (0.72f - page * 0.010f), h * 0.30f), radius = w * 0.45f,
            ),
            radius = w * 0.45f, center = Offset(w * (0.72f - page * 0.010f), h * 0.30f),
        )
        val beam = 0.05f + 0.02f * sin(phase)
        for ((x0, x1) in listOf(0.05f to 0.36f, 0.40f to 0.60f)) {
            fill.rewind()
            fill.moveTo(w * (x0 - page * 0.006f), 0f); fill.lineTo(w * (x1 - page * 0.006f), 0f)
            fill.lineTo(w * (x1 + 0.35f - page * 0.006f), h); fill.lineTo(w * (x0 + 0.20f - page * 0.006f), h); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = beam), Color.Transparent), startY = 0f, endY = h * 0.9f))
        }

        // Motes and waves are drawn back to front: far motes, far waves, the light streak, near waves, near motes.
        fun drawMotes(near: Boolean) {
            for (m in motes) {
                if ((m.z >= 0.55f) != near) continue
                val cx = (m.x - page * 0.035f * (0.3f + m.z) + sin(phase * 0.7f + m.seed) * 0.02f).mod(1f) * w
                val cy = (m.y - turn * m.speed * (0.5f + m.z)).mod(1f) * h
                val r = (0.005f + 0.034f * m.z * m.z) * w
                val a = 0.10f + 0.24f * m.z
                drawCircle(Color.White.copy(alpha = a * 0.30f), r * 1.9f, Offset(cx, cy))
                drawCircle(Color.White.copy(alpha = a), r, Offset(cx, cy))
                if (m.z > 0.6f) {
                    // Near bubbles get a thin bright rim and a glint, so they read as glass.
                    drawCircle(Color.White.copy(alpha = 0.35f * m.z), r, Offset(cx, cy), style = Stroke(width = 1.5f))
                    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.22f, Offset(cx - r * 0.35f, cy - r * 0.38f))
                }
            }
        }
        fun drawWave(layer: Int) {
            val z = layer / 4f
            val baseY = h * (0.44f + 0.105f * layer) - page * h * (0.004f + 0.018f * z)
            val amp = h * (0.018f + 0.034f * z)
            val speed = 0.45f + 0.95f * z
            val shift = page * w * (0.012f + 0.07f * z)
            val steps = 28
            fill.rewind(); crest.rewind()
            for (i in 0..steps) {
                val x = -w * 0.05f + w * 1.1f * i / steps
                val u = (x + shift) / w
                val y = baseY + amp * sin(u * 6.28f * (1.3f + 0.25f * layer) + phase * speed + layer * 1.7f) +
                    amp * 0.45f * sin(u * 6.28f * 3.1f - phase * speed * 1.6f + layer)
                if (i == 0) { fill.moveTo(x, y); crest.moveTo(x, y) } else { fill.lineTo(x, y); crest.lineTo(x, y) }
            }
            fill.lineTo(w * 1.05f, h); fill.lineTo(-w * 0.05f, h); fill.close()
            // Far layers are hazy and blue, near ones bright and white, so distance reads as atmosphere.
            val tint = lerp(lerp(mid, Color.White, 0.25f), Color.White, z)
            drawPath(
                fill,
                Brush.verticalGradient(
                    0f to tint.copy(alpha = 0.10f + 0.20f * z),
                    0.30f to tint.copy(alpha = 0.03f + 0.05f * z),
                    1f to Color.Black.copy(alpha = 0.05f * z),
                    startY = baseY - amp, endY = h,
                ),
            )
            if (z > 0.2f) drawPath(crest, Color.White.copy(alpha = 0.10f + 0.34f * z), style = Stroke(width = (1f + 2.5f * z) * 1.4f, cap = StrokeCap.Round))
        }

        drawMotes(near = false)
        drawWave(0); drawWave(1)
        // The long bright streak of light through the middle.
        val streakY = h * (0.50f - page * 0.02f) + sin(phase * 0.6f) * h * 0.012f
        rotate(-3f, Offset(w / 2f, streakY)) {
            for ((thick, a) in listOf(0.16f to 0.07f, 0.07f to 0.12f, 0.022f to 0.30f)) {
                drawOval(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = a), Color.White.copy(alpha = a * 0.55f), Color.Transparent),
                    ),
                    topLeft = Offset(-w * 0.1f, streakY - h * thick / 2f),
                    size = Size(w * 1.2f, h * thick),
                )
            }
        }
        drawWave(2); drawWave(3); drawWave(4)
        drawMotes(near = true)

        // A bright glow along the horizon, then dark corners to pull the eye in and deepen the scene.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f)), startY = h * 0.70f, endY = h))
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.34f),
                center = Offset(w / 2f, h / 2f), radius = hypot(w, h) / 2f,
            ),
        )
    }
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
