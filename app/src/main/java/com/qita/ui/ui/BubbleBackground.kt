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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
    /** Current home page (fractional while swiping); the swooshes drift with it. Read while drawing. */
    scroll: () -> Float = { 0f },
    /** When set, the scene and its colours come from here, read while drawing, so they can follow a swipe. */
    scene: (() -> SceneMix)? = null,
) {
    val topColor by animateColorAsState(top, tween(600), label = "top")
    val midColor by animateColorAsState(mid, tween(600), label = "mid")
    val bottomColor by animateColorAsState(bottom, tween(600), label = "bottom")
    Box(modifier.fillMaxSize()) {
        if (wallpaper != null) {
            Image(wallpaper, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            SceneCanvas(
                scene ?: { Triple(topColor, midColor, bottomColor).let { SceneMix(Scene.WAVES, Scene.WAVES, 0f, it, it) } },
                scroll,
            )
        }
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        if (particles) Particles(particleCount)
    }
}

/** Draws the animated scene (or a cross-fade of two while swiping) from [mix], read only while drawing. */
@Composable
private fun SceneCanvas(mix: () -> SceneMix, scroll: () -> Float) {
    val look = LocalLook.current
    val running by rememberInfiniteTransition(label = "scene").animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween((26_000 / look.sceneSpeed.coerceIn(0.2f, 3f)).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    // With reduced motion the scene stands still.
    val phase = if (look.reduceMotion) 1.9f else running
    val cache = remember { SceneCache() }
    val layerPaint = remember { Paint() }
    Canvas(Modifier.fillMaxSize()) {
        val m = mix()
        val page = scroll()
        if (m.a == m.b || m.t < 0.02f) {
            drawScene(m.a, if (m.a == m.b) lerpSky(m.ca, m.cb, m.t) else m.ca, phase, page, cache)
        } else {
            drawScene(m.a, m.ca, phase, page, cache)
            layerPaint.alpha = m.t.coerceIn(0f, 1f)
            drawIntoCanvas { it.saveLayer(Rect(0f, 0f, size.width, size.height), layerPaint) }
            drawScene(m.b, m.cb, phase, page, cache)
            drawIntoCanvas { it.restore() }
        }
    }
}

/** A still preview of a background, for pickers. */
@Composable
fun SceneThumb(scene: Scene, colors: Triple<Color, Color, Color>, modifier: Modifier = Modifier) {
    val cache = remember { SceneCache() }
    Canvas(modifier) { drawScene(scene, colors, 1.9f, 0f, cache) }
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
