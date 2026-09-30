package com.qita.ui.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** The kinds of animated 3D-looking backgrounds. Each one is drawn from layers at different depths. */
enum class Scene { WAVES, AURORA, OCEAN, CRYSTAL, SPACE, GRID, DUNES, SILK, SYMBOLS }

typealias Sky = Triple<Color, Color, Color>

/** What the wallpaper shows: scene [a], or a cross-fade to scene [b] by [t] while swiping between pages. */
class SceneMix(val a: Scene, val b: Scene, val t: Float, val ca: Sky, val cb: Sky)

/** A thing placed in a scene. [z] is depth (0 far, 1 near); [s] and [seed] vary speed and phase. */
class Dot(val x: Float, val y: Float, val z: Float, val s: Float, val seed: Float)

/** Shapes and lists reused every frame so drawing allocates nothing. */
class SceneCache {
    val p1 = Path()
    val p2 = Path()
    // Sorted from far to near, so drawing them in order layers them correctly.
    val dots: List<Dot> = Random(23).let { rnd ->
        List(120) { Dot(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat().let { it * it * 0.4f + it * 0.6f }, 0.5f + rnd.nextFloat(), rnd.nextFloat() * 6.28f) }
    }.sortedBy { it.z }
    val shards: List<Dot> = Random(5).let { rnd ->
        List(20) { Dot(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), 0.4f + rnd.nextFloat(), rnd.nextFloat() * 6.28f) }
    }.sortedBy { it.z }
}

private val TAU = (2 * PI).toFloat()

fun lerpSky(a: Sky, b: Sky, t: Float): Sky = Triple(lerp(a.first, b.first, t), lerp(a.second, b.second, t), lerp(a.third, b.third, t))

fun DrawScope.drawScene(scene: Scene, c: Sky, phase: Float, page: Float, k: SceneCache) {
    when (scene) {
        Scene.WAVES -> drawWaves(c, phase, page, k)
        Scene.AURORA -> drawAurora(c, phase, page, k)
        Scene.OCEAN -> drawOcean(c, phase, page, k)
        Scene.CRYSTAL -> drawCrystal(c, phase, page, k)
        Scene.SPACE -> drawSpace(c, phase, page, k)
        Scene.GRID -> drawGrid(c, phase, page, k)
        Scene.DUNES -> drawDunes(c, phase, page, k)
        Scene.SILK -> drawSilk(c, phase, page, k)
        Scene.SYMBOLS -> drawSymbols(c, phase, page, k)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.sky(c: Sky) {
    drawRect(Brush.verticalGradient(0f to c.first, 0.5f to c.second, 1f to c.third))
}

private fun DrawScope.glow(cx: Float, cy: Float, r: Float, color: Color, alpha: Float) {
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = Offset(cx, cy), radius = r),
        radius = r, center = Offset(cx, cy),
    )
}

private fun DrawScope.vignette(strength: Float) {
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = strength),
            center = Offset(size.width / 2f, size.height / 2f), radius = hypot(size.width, size.height) / 2f,
        ),
    )
}

/** Stars in [from, to) of the shared dots, twinkling; nearer stars are bigger, brighter and slide more with the page. */
private fun DrawScope.stars(k: SceneCache, from: Int, to: Int, phase: Float, page: Float, alpha: Float, maxY: Float = 0.8f) {
    val w = size.width
    val h = size.height
    val px = w / 1000f
    val turn = phase / TAU
    for (i in from until to) {
        val d = k.dots[i]
        val x = (d.x - page * 0.02f * (0.2f + d.z) + turn * 0.02f * (0.3f + d.z)).mod(1f) * w
        val y = d.y * h * maxY
        val tw = 0.55f + 0.45f * sin(phase * 3f * (0.5f + d.s) + d.seed)
        drawCircle(Color.White.copy(alpha = ((0.20f + 0.6f * d.z) * tw * alpha).coerceIn(0f, 1f)), px * (0.8f + 2.6f * d.z), Offset(x, y))
    }
}

/** A filled ridge line across the screen. */
private fun DrawScope.ridge(path: Path, baseY: Float, amp: Float, freq: Float, seed: Float, shift: Float, brush: Brush, steps: Int = 40) {
    val w = size.width
    val h = size.height
    path.rewind()
    for (i in 0..steps) {
        val x = w * i / steps
        val u = (x + shift) / w
        val y = baseY - amp * (0.55f + 0.45f * sin(u * TAU * freq + seed)) * (0.7f + 0.3f * sin(u * TAU * freq * 2.7f + seed * 1.9f))
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.lineTo(w, h); path.lineTo(0f, h); path.close()
    drawPath(path, brush)
}

// ---------------------------------------------------------------------------------------------------------------
// WAVES: the Vita's flowing sky, layered waves and glass bubbles
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawWaves(c: Sky, phase: Float, page: Float, k: SceneCache) {
    val (top, mid, bottom) = c
    val w = size.width
    val h = size.height
    val turn = phase / TAU
    val fill = k.p1
    val crest = k.p2

    drawRect(Brush.verticalGradient(0f to top, 0.50f to mid, 1f to lerp(bottom, Color.White, 0.25f)))
    glow(w * (0.72f - page * 0.010f), h * 0.30f, w * 0.45f, Color.White, 0.30f)
    val beam = 0.05f + 0.02f * sin(phase)
    for ((x0, x1) in listOf(0.05f to 0.36f, 0.40f to 0.60f)) {
        fill.rewind()
        fill.moveTo(w * (x0 - page * 0.006f), 0f); fill.lineTo(w * (x1 - page * 0.006f), 0f)
        fill.lineTo(w * (x1 + 0.35f - page * 0.006f), h); fill.lineTo(w * (x0 + 0.20f - page * 0.006f), h); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = beam), Color.Transparent), startY = 0f, endY = h * 0.9f))
    }

    fun motes(near: Boolean) {
        for (i in 0 until 44) {
            val m = k.dots[i * 2]
            if ((m.z >= 0.55f) != near) continue
            val cx = (m.x - page * 0.035f * (0.3f + m.z) + sin(phase * 0.7f + m.seed) * 0.02f).mod(1f) * w
            val cy = (m.y - turn * m.s * (0.5f + m.z)).mod(1f) * h
            val r = (0.005f + 0.034f * m.z * m.z) * w
            val a = 0.10f + 0.24f * m.z
            drawCircle(Color.White.copy(alpha = a * 0.30f), r * 1.9f, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = a), r, Offset(cx, cy))
            if (m.z > 0.6f) {
                drawCircle(Color.White.copy(alpha = 0.35f * m.z), r, Offset(cx, cy), style = Stroke(width = 1.5f))
                drawCircle(Color.White.copy(alpha = 0.55f), r * 0.22f, Offset(cx - r * 0.35f, cy - r * 0.38f))
            }
        }
    }
    fun wave(layer: Int) {
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
            val y = baseY + amp * sin(u * TAU * (1.3f + 0.25f * layer) + phase * speed + layer * 1.7f) +
                amp * 0.45f * sin(u * TAU * 3.1f - phase * speed * 1.6f + layer)
            if (i == 0) { fill.moveTo(x, y); crest.moveTo(x, y) } else { fill.lineTo(x, y); crest.lineTo(x, y) }
        }
        fill.lineTo(w * 1.05f, h); fill.lineTo(-w * 0.05f, h); fill.close()
        val tint = lerp(lerp(mid, Color.White, 0.25f), Color.White, z)
        drawPath(
            fill,
            Brush.verticalGradient(
                0f to tint.copy(alpha = 0.10f + 0.20f * z), 0.30f to tint.copy(alpha = 0.03f + 0.05f * z), 1f to Color.Black.copy(alpha = 0.05f * z),
                startY = baseY - amp, endY = h,
            ),
        )
        if (z > 0.2f) drawPath(crest, Color.White.copy(alpha = 0.10f + 0.34f * z), style = Stroke(width = (1f + 2.5f * z) * 1.4f, cap = StrokeCap.Round))
    }

    motes(false)
    wave(0); wave(1)
    val streakY = h * (0.50f - page * 0.02f) + sin(phase * 0.6f) * h * 0.012f
    rotate(-3f, Offset(w / 2f, streakY)) {
        for ((thick, a) in listOf(0.16f to 0.07f, 0.07f to 0.12f, 0.022f to 0.30f)) {
            drawOval(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = a), Color.White.copy(alpha = a * 0.55f), Color.Transparent)),
                topLeft = Offset(-w * 0.1f, streakY - h * thick / 2f), size = Size(w * 1.2f, h * thick),
            )
        }
    }
    wave(2); wave(3); wave(4)
    motes(true)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f)), startY = h * 0.70f, endY = h))
    vignette(0.34f)
}

// ---------------------------------------------------------------------------------------------------------------
// AURORA: curtains of northern lights over a star field and two mountain ranges
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawAurora(c: Sky, phase: Float, page: Float, k: SceneCache) {
    val w = size.width
    val h = size.height
    sky(c)
    glow(w * 0.5f, h * 0.05f, w * 0.7f, Color(0xFF2EE6B0), 0.10f)
    stars(k, 0, 90, phase, page, 1f, 0.7f)
    val colors = listOf(Color(0xFF3DFFB0), Color(0xFF52B6FF), Color(0xFFB77BFF))
    for (i in 0..2) {
        val z = i / 2f
        val baseY = h * (0.20f + 0.09f * i)
        val amp = h * (0.045f + 0.03f * i)
        val bandH = h * (0.24f + 0.05f * i)
        val shift = page * w * (0.01f + 0.035f * z)
        val steps = 26
        fun top(x: Float): Float {
            val u = (x + shift) / w
            return baseY + amp * sin(u * TAU * (1.1f + 0.4f * i) + phase * (0.6f + 0.3f * i) + i * 2f) + amp * 0.4f * sin(u * TAU * 2.7f - phase * 0.9f)
        }
        fun height(x: Float): Float {
            val u = (x + shift) / w
            return bandH * (0.75f + 0.35f * sin(u * TAU * 3f + phase * 1.1f + i))
        }
        k.p1.rewind()
        for (j in 0..steps) {
            val x = w * j / steps
            if (j == 0) k.p1.moveTo(x, top(x)) else k.p1.lineTo(x, top(x))
        }
        for (j in steps downTo 0) {
            val x = w * j / steps
            k.p1.lineTo(x, top(x) + height(x))
        }
        k.p1.close()
        val col = colors[i]
        drawPath(
            k.p1,
            Brush.verticalGradient(
                0f to Color.Transparent, 0.18f to col.copy(alpha = 0.55f - 0.10f * i), 0.55f to col.copy(alpha = 0.16f), 1f to Color.Transparent,
                startY = baseY - amp, endY = baseY + amp + bandH * 1.2f,
            ),
        )
        // Fine vertical rays inside the curtain.
        for (j in 0 until 30) {
            val x = w * (j + 0.5f) / 30f
            val a = 0.05f + 0.06f * (0.5f + 0.5f * sin(phase * 1.3f + j * 1.7f + i))
            drawLine(col.copy(alpha = a), Offset(x, top(x)), Offset(x, top(x) + height(x) * 0.9f), strokeWidth = 1.5f + z)
        }
    }
    val far = lerp(c.second, Color.Black, 0.55f)
    ridge(k.p1, h * 0.86f, h * 0.22f, 1.6f, 0.7f, page * w * 0.02f, Brush.verticalGradient(listOf(far, lerp(far, Color.Black, 0.5f))), 70)
    ridge(k.p1, h * 0.98f, h * 0.20f, 2.3f, 3.1f, page * w * 0.06f, Brush.verticalGradient(listOf(Color(0xFF03060F), Color.Black)), 70)
    vignette(0.35f)
}

// ---------------------------------------------------------------------------------------------------------------
// OCEAN: light shafts, fish schools, jellyfish, rising bubbles and kelp at several depths
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawOcean(c: Sky, phase: Float, page: Float, k: SceneCache) {
    val w = size.width
    val h = size.height
    val px = w / 1000f
    val turn = phase / TAU
    sky(c)
    // Light shafts from the surface.
    for (i in 0 until 6) {
        val x0 = w * (0.05f + 0.17f * i) + sin(phase * 0.5f + i * 1.3f) * w * 0.03f - page * w * 0.01f
        k.p1.rewind()
        k.p1.moveTo(x0, 0f); k.p1.lineTo(x0 + w * 0.10f, 0f); k.p1.lineTo(x0 + w * 0.30f, h); k.p1.lineTo(x0 + w * 0.12f, h); k.p1.close()
        drawPath(k.p1, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f + 0.05f * sin(phase + i)), Color.Transparent), startY = 0f, endY = h * 0.85f))
    }
    val dark = Color(0xFF032338)
    // Fish, far to near.
    for (i in 20 until 44) {
        val d = k.dots[i]
        val x = ((d.x + turn * 0.5f * (0.3f + d.z) * (if (i % 2 == 0) 1f else -1f)).mod(1.2f) - 0.1f) * w
        val y = h * (0.18f + 0.55f * d.y) + sin(phase * 2f + d.seed) * h * 0.006f
        val len = px * (7f + 18f * d.z)
        val dir = if (i % 2 == 0) 1f else -1f
        val col = dark.copy(alpha = 0.25f + 0.45f * d.z)
        drawOval(col, Offset(x - len, y - len * 0.32f), Size(len * 2f, len * 0.64f))
        k.p2.rewind()
        k.p2.moveTo(x - dir * len * 0.9f, y); k.p2.lineTo(x - dir * len * 1.6f, y - len * 0.45f); k.p2.lineTo(x - dir * len * 1.6f, y + len * 0.45f); k.p2.close()
        drawPath(k.p2, col)
    }
    // Jellyfish: glowing bells with trailing tentacles.
    for (i in 0 until 3) {
        val z = 0.3f + 0.35f * i
        val cx = w * (0.18f + 0.32f * i) + sin(phase * 0.7f + i * 2f) * w * 0.04f - page * w * 0.03f * z
        val cy = h * (0.32f + 0.14f * i) + sin(phase * 0.9f + i) * h * 0.035f
        val r = w * (0.022f + 0.022f * z)
        glow(cx, cy, r * 3.5f, Color(0xFF7DFFF2), 0.22f)
        drawArc(
            Brush.verticalGradient(listOf(Color(0xFFCFFFF8).copy(alpha = 0.85f), Color(0xFF3FD9D0).copy(alpha = 0.35f)), startY = cy - r, endY = cy),
            180f, 180f, true, Offset(cx - r, cy - r), Size(r * 2f, r * 2f),
        )
        for (t in -2..2) {
            k.p2.rewind()
            k.p2.moveTo(cx + t * r * 0.36f, cy)
            for (s in 1..6) k.p2.lineTo(cx + t * r * 0.36f + sin(phase * 2f + s * 0.9f + t) * r * 0.28f, cy + s * r * 0.55f)
            drawPath(k.p2, Color(0xFFB8FFF6).copy(alpha = 0.40f), style = Stroke(width = 1.5f * (0.6f + z), cap = StrokeCap.Round))
        }
    }
    // Rising bubbles at several depths.
    for (i in 44 until 84) {
        val d = k.dots[i]
        val cx = (d.x + sin(phase * 0.8f + d.seed) * 0.02f - page * 0.03f * (0.3f + d.z)).mod(1f) * w
        val cy = (d.y - turn * d.s * (0.6f + d.z)).mod(1f) * h
        val r = px * (2f + 12f * d.z * d.z)
        drawCircle(Color.White.copy(alpha = 0.12f + 0.18f * d.z), r, Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = 0.30f + 0.3f * d.z), r, Offset(cx, cy), style = Stroke(width = 1.2f))
    }
    // Kelp and rocks: a far layer, then a nearer and darker one.
    for (layer in 0..1) {
        val z = layer.toFloat()
        val kelpColor = lerp(Color(0xFF0C5A5A), Color(0xFF021A22), z)
        val count = 9 + layer * 3
        for (i in 0 until count) {
            val bx = w * (i + 0.3f + 0.4f * ((i * 7) % 5) / 5f) / count - page * w * (0.02f + 0.05f * z)
            val hh = h * (0.16f + 0.10f * z + 0.05f * ((i * 3) % 4))
            k.p2.rewind()
            k.p2.moveTo(bx, h)
            for (s in 1..8) {
                val t = s / 8f
                k.p2.lineTo(bx + sin(phase * (0.9f + 0.3f * z) + i + t * 4f) * w * 0.012f * t * (1f + z), h - hh * t)
            }
            drawPath(k.p2, kelpColor.copy(alpha = 0.7f), style = Stroke(width = px * (5f + 5f * z), cap = StrokeCap.Round))
        }
        ridge(k.p1, h * (1.0f - 0.02f * (1f - z)), h * (0.10f + 0.06f * z), 2.2f + layer, 1.5f + layer * 2f, page * w * (0.03f + 0.06f * z),
            Brush.verticalGradient(listOf(lerp(Color(0xFF0A3C4E), Color(0xFF010E14), z), Color.Black)), 40)
    }
    vignette(0.40f)
}

// ---------------------------------------------------------------------------------------------------------------
// CRYSTAL: spinning faceted gems floating at different depths
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawCrystal(c: Sky, phase: Float, page: Float, k: SceneCache) {
    val w = size.width
    val h = size.height
    sky(c)
    glow(w * 0.5f, h * 0.55f, w * 0.55f, Color(0xFFB040FF), 0.20f)
    glow(w * (0.20f - page * 0.01f), h * 0.30f, w * 0.40f, Color(0xFF30D8FF), 0.16f)
    stars(k, 0, 50, phase, page, 0.6f, 0.9f)
    val palette = listOf(Color(0xFF33E0FF), Color(0xFFFF4FD8), Color(0xFF9B6BFF), Color(0xFF3DFFC0))
    for ((index, d) in k.shards.withIndex()) {
        val cx = (d.x + sin(phase * 0.4f + d.seed) * 0.03f - page * 0.06f * (0.3f + d.z)).mod(1.1f).let { it - 0.05f } * w
        val cy = d.y * h * 0.9f + h * 0.05f + sin(phase * 0.6f * (0.5f + d.s) + d.seed) * h * 0.035f
        val size = w * (0.010f + 0.052f * d.z * d.z)
        val ang = phase * (0.5f + d.s) + d.seed
        val cosA = cos(ang)
        val wid = size * (abs(cosA) * 0.9f + 0.14f)
        val base = palette[index % palette.size]
        val alpha = 0.30f + 0.60f * d.z
        val lightC = lerp(base, Color.White, 0.55f).copy(alpha = alpha)
        val darkC = lerp(base, Color.Black, 0.40f).copy(alpha = alpha)
        if (d.z > 0.5f) glow(cx, cy, size * 3.2f, base, 0.20f * d.z)
        val leftLit = cosA >= 0f
        // Two faces of a gem: the one turned toward the light is bright, the other dark; they swap as it spins.
        k.p1.rewind(); k.p1.moveTo(cx, cy - size * 1.5f); k.p1.lineTo(cx - wid, cy); k.p1.lineTo(cx, cy + size * 1.5f); k.p1.close()
        drawPath(k.p1, if (leftLit) lightC else darkC)
        k.p2.rewind(); k.p2.moveTo(cx, cy - size * 1.5f); k.p2.lineTo(cx + wid, cy); k.p2.lineTo(cx, cy + size * 1.5f); k.p2.close()
        drawPath(k.p2, if (leftLit) darkC else lightC)
        val edge = Color.White.copy(alpha = 0.30f * d.z + 0.05f)
        drawLine(edge, Offset(cx, cy - size * 1.5f), Offset(cx, cy + size * 1.5f), strokeWidth = 1.2f)
        drawLine(edge, Offset(cx - wid, cy), Offset(cx + wid, cy), strokeWidth = 1f)
    }
    vignette(0.38f)
}

// ---------------------------------------------------------------------------------------------------------------
// SPACE: nebula clouds, three layers of stars, a ringed planet, a moon and shooting stars
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawSpace(c: Sky, phase: Float, page: Float, k: SceneCache) {
    val w = size.width
    val h = size.height
    val turn = phase / TAU
    sky(c)
    glow(w * (0.25f + sin(phase * 0.3f) * 0.03f - page * 0.01f), h * 0.35f, w * 0.55f, Color(0xFFD03CC8), 0.24f)
    glow(w * (0.78f + sin(phase * 0.3f + 2f) * 0.03f - page * 0.015f), h * 0.62f, w * 0.50f, Color(0xFF3C6BFF), 0.24f)
    glow(w * 0.5f, h * 0.10f, w * 0.40f, Color(0xFF8A4CFF), 0.18f)
    stars(k, 0, 120, phase, page, 1f, 1f)
    // A shooting star now and then.
    val shoot = (turn * 3f).mod(1f)
    if (shoot < 0.16f) {
        val p = shoot / 0.16f
        val x = w * (0.95f - 0.5f * p)
        val y = h * (0.05f + 0.30f * p)
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * (1f - p))), Offset(x + w * 0.12f, y - h * 0.09f), Offset(x, y)),
            Offset(x + w * 0.12f, y - h * 0.09f), Offset(x, y), strokeWidth = 3f, cap = StrokeCap.Round,
        )
    }
    // A distant moon.
    val mx = w * (0.84f - page * 0.01f)
    val my = h * 0.20f
    val mr = w * 0.028f
    drawCircle(Brush.radialGradient(listOf(Color(0xFFEDEBFF), Color(0xFF7C78A0), Color(0xFF231F3E)), center = Offset(mx - mr * 0.4f, my - mr * 0.4f), radius = mr * 1.8f), mr, Offset(mx, my))
    // The planet, with its ring passing behind and in front.
    val pr = minOf(w, h) * 0.30f
    val px0 = w * (0.30f - page * 0.03f)
    val py0 = h * 0.60f + sin(phase * 0.5f) * h * 0.012f
    val ringW = pr * 2.5f
    val ringH = pr * 0.62f
    val ringStroke = Stroke(width = pr * 0.16f)
    val ringBrush = Brush.horizontalGradient(listOf(Color(0xFFD9B98A).copy(alpha = 0.15f), Color(0xFFF1D9AE).copy(alpha = 0.75f), Color(0xFFD9B98A).copy(alpha = 0.15f)), px0 - ringW / 2f, px0 + ringW / 2f)
    rotate(-16f, Offset(px0, py0)) {
        drawArc(ringBrush, 180f, 180f, false, Offset(px0 - ringW / 2f, py0 - ringH / 2f), Size(ringW, ringH), style = ringStroke)
    }
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFE2B8), Color(0xFFD08A5C), Color(0xFF6A2E3E), Color(0xFF12061C)),
            center = Offset(px0 - pr * 0.42f, py0 - pr * 0.42f), radius = pr * 1.55f,
        ),
        pr, Offset(px0, py0),
    )
    // Soft bands across the planet and a thin bright atmosphere edge.
    clipPath(k.p1.also { it.rewind(); it.addOval(Rect(px0 - pr, py0 - pr, px0 + pr, py0 + pr)) }) {
        for (i in 0 until 4) {
            drawRect(Color.Black.copy(alpha = 0.08f), Offset(px0 - pr, py0 - pr * 0.6f + i * pr * 0.32f), Size(pr * 2f, pr * 0.12f))
        }
    }
    drawCircle(Color(0xFFFFD9A8).copy(alpha = 0.30f), pr, Offset(px0, py0), style = Stroke(width = 2f))
    rotate(-16f, Offset(px0, py0)) {
        drawArc(ringBrush, 0f, 180f, false, Offset(px0 - ringW / 2f, py0 - ringH / 2f), Size(ringW, ringH), style = ringStroke)
    }
    vignette(0.42f)
}

// ---------------------------------------------------------------------------------------------------------------
// GRID: a retro sun over a glowing perspective floor that rolls toward you
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawGrid(c: Sky, phase: Float, page: Float, k: SceneCache) {
    val w = size.width
    val h = size.height
    val turn = phase / TAU
    val horizon = h * 0.54f
    drawRect(Brush.verticalGradient(0f to c.first, 0.55f to c.second, 1f to c.third, startY = 0f, endY = horizon), size = Size(w, horizon))
    stars(k, 0, 60, phase, page, 0.7f, 0.5f)
    // The sun, banded with scan lines.
    val sunX = w * (0.5f - page * 0.02f)
    val r = h * 0.25f
    val sunY = horizon - r * 0.35f
    glow(sunX, sunY, r * 2.4f, Color(0xFFFF4FA0), 0.35f)
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE066), Color(0xFFFF4F8A), Color(0xFFB0209A)), startY = sunY - r, endY = sunY + r), r, Offset(sunX, sunY))
    k.p1.rewind(); k.p1.addOval(Rect(sunX - r, sunY - r, sunX + r, sunY + r))
    clipPath(k.p1) {
        for (i in 0 until 7) {
            drawRect(lerp(c.second, c.third, 0.55f), Offset(sunX - r, sunY + r * (0.05f + 0.13f * i)), Size(r * 2f, r * (0.015f + 0.014f * i)))
        }
    }
    // Neon mountains on the horizon.
    ridge(k.p2, horizon + 1f, h * 0.10f, 2.4f, 0.4f, page * w * 0.04f, Brush.verticalGradient(listOf(Color(0xFF2A0A4A), Color(0xFF12002A))), 60)
    drawPath(k.p2, Color(0xFF34E8FF).copy(alpha = 0.55f), style = Stroke(width = 1.6f))
    // The floor.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF200046), Color(0xFF06000F)), startY = horizon, endY = h), Offset(0f, horizon), Size(w, h - horizon))
    val vx = w * (0.5f - page * 0.03f)
    val lines = 14
    val scroll = turn * 6f
    for (i in 0 until lines) {
        val t = ((i + scroll).mod(lines.toFloat())) / lines
        val y = horizon + (h - horizon) * t * t
        val a = 0.10f + 0.70f * t
        drawLine(lerp(Color(0xFFFF3CC8), Color(0xFF34E8FF), t).copy(alpha = a), Offset(0f, y), Offset(w, y), strokeWidth = 1f + 3f * t)
    }
    for (j in -12..12) {
        val xb = vx + j * w * 0.15f
        drawLine(Color(0xFFFF3CC8).copy(alpha = 0.40f), Offset(vx + j * w * 0.004f, horizon), Offset(xb, h), strokeWidth = 1.6f)
    }
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFF4FA0).copy(alpha = 0.55f), Color.Transparent), startY = horizon, endY = horizon + h * 0.10f), Offset(0f, horizon), Size(w, h * 0.10f))
    vignette(0.35f)
}

// ---------------------------------------------------------------------------------------------------------------
// DUNES: a low sun and six ridges of desert fading into haze, with birds and drifting dust
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawDunes(c: Sky, phase: Float, page: Float, k: SceneCache) {
    val w = size.width
    val h = size.height
    val px = w / 1000f
    val turn = phase / TAU
    sky(c)
    val sunX = w * (0.66f - page * 0.015f)
    val sunY = h * 0.44f
    glow(sunX, sunY, w * 0.55f, Color(0xFFFFC46B), 0.45f)
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFF3C4), Color(0xFFFFB14D)), startY = sunY - w * 0.08f, endY = sunY + w * 0.08f), w * 0.08f, Offset(sunX, sunY))
    // Long streaks of cloud.
    for (i in 0 until 3) {
        val cx = ((0.2f + 0.35f * i + turn * 0.05f * (1 + i)).mod(1.4f) - 0.2f) * w
        drawOval(Color.White.copy(alpha = 0.16f), Offset(cx - w * 0.16f, h * (0.16f + 0.07f * i)), Size(w * 0.32f, h * 0.035f))
    }
    // Birds crossing the sky.
    for (i in 0 until 7) {
        val d = k.dots[i * 6]
        val x = ((d.x + turn * 0.6f * (0.5f + d.s)).mod(1.2f) - 0.1f) * w
        val y = h * (0.12f + 0.22f * d.y) + sin(phase * 2f + d.seed) * h * 0.01f
        val s = px * (5f + 9f * d.z)
        val flap = sin(phase * 14f * (0.6f + d.s) + d.seed) * s * 0.5f
        val col = Color(0xFF3A1230).copy(alpha = 0.55f)
        drawLine(col, Offset(x - s, y - flap), Offset(x, y), strokeWidth = 1.6f, cap = StrokeCap.Round)
        drawLine(col, Offset(x + s, y - flap), Offset(x, y), strokeWidth = 1.6f, cap = StrokeCap.Round)
    }
    val haze = c.third
    val dark = Color(0xFF2A0F2E)
    for (i in 0 until 6) {
        val z = i / 5f
        val col = lerp(lerp(haze, Color.White, 0.15f), dark, z * z)
        ridge(
            k.p1, h * (0.52f + 0.085f * i), h * (0.055f + 0.02f * i), 0.8f + 0.25f * i, i * 1.7f + sin(phase * 0.3f) * 0.05f,
            page * w * (0.01f + 0.06f * z),
            Brush.verticalGradient(listOf(col, lerp(col, dark, 0.35f))), 44,
        )
    }
    // Drifting dust in the foreground.
    for (i in 84 until 120) {
        val d = k.dots[i]
        val x = (d.x + turn * 0.10f * (0.3f + d.z) - page * 0.05f * d.z + sin(phase + d.seed) * 0.01f).mod(1f) * w
        val y = h * (0.5f + 0.5f * d.y)
        drawCircle(Color(0xFFFFE0A8).copy(alpha = 0.10f + 0.25f * d.z), px * (1.5f + 5f * d.z * d.z), Offset(x, y))
    }
    vignette(0.32f)
}

// ---------------------------------------------------------------------------------------------------------------
// SILK: the Vita's glossy translucent ribbons, sweeping down across a blue sky and twisting as they drift
// ---------------------------------------------------------------------------------------------------------------

private class Ribbon(val base: Float, val amp: Float, val ph: Float, val k: Float, val width: Float, val alpha: Float, val speed: Float, val z: Float)

private val RIBBONS = listOf(
    Ribbon(0.22f, 0.050f, 0.0f, 0.9f, 0.16f, 0.70f, 0.6f, 0.2f),
    Ribbon(0.30f, 0.060f, 1.0f, 0.8f, 0.12f, 0.80f, 0.7f, 0.3f),
    Ribbon(0.40f, 0.070f, 2.1f, 0.7f, 0.14f, 1.00f, 0.9f, 0.5f),
    Ribbon(0.16f, 0.050f, 3.0f, 1.0f, 0.09f, 0.60f, 0.5f, 0.15f),
    Ribbon(0.46f, 0.055f, 4.0f, 0.9f, 0.11f, 0.85f, 1.0f, 0.7f),
    Ribbon(0.34f, 0.080f, 5.0f, 0.6f, 0.08f, 0.55f, 1.2f, 0.9f),
)

private fun DrawScope.drawSilk(c: Sky, phase: Float, page: Float, k: SceneCache) {
    val (top, mid, bottom) = c
    val w = size.width
    val h = size.height
    val fill = k.p1
    val edge = k.p2
    val px = w / 1000f
    drawRect(Brush.verticalGradient(0f to top, 0.5f to mid, 1f to bottom))
    // A soft bloom where the ribbons catch the light.
    glow(w * (0.25f - page * 0.01f), h * 0.35f, w * 0.6f, Color.White, 0.12f)
    val steps = 36
    for (r in RIBBONS) {
        val t = phase * r.speed
        val shift = page * 0.05f * (0.3f + r.z)
        fill.rewind(); edge.rewind()
        // Top curve left to right, then the bottom curve back: the ribbon is the band between them.
        for (pass in 0..1) {
            for (j in 0..steps) {
                val i = if (pass == 0) j else steps - j
                val u = i.toFloat() / steps
                val us = u + shift
                // The big sweep: the ribbons start high on the left and flow down toward the right.
                val sweep = 0.30f * (0.5f - 0.5f * cos(PI.toFloat() * u))
                val wave = r.amp * (sin(us * TAU * r.k + r.ph + t) + 0.5f * sin(us * TAU * r.k * 2.3f + r.ph * 1.7f - t * 1.3f))
                val twist = 0.55f + 0.45f * sin(us * TAU * 1.3f + r.ph + t * 0.5f)
                val yc = (r.base + sweep + wave) * h
                val half = h * r.width * twist / 2f
                val y = if (pass == 0) yc - half else yc + half
                val x = w * u
                if (pass == 0) {
                    if (j == 0) { fill.moveTo(x, y); edge.moveTo(x, y) } else { fill.lineTo(x, y); edge.lineTo(x, y) }
                } else fill.lineTo(x, y)
            }
        }
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(Color(0xFFD0EAFF).copy(alpha = 0.30f * r.alpha), Color(0xFF9CC8FF).copy(alpha = 0.10f * r.alpha)), startY = 0f, endY = h))
        // A soft glow under a fine bright edge along the top of the ribbon.
        drawPath(edge, Color.White.copy(alpha = 0.16f * r.alpha), style = Stroke(width = 6f * px * (1f + r.z), cap = StrokeCap.Round))
        drawPath(edge, Color(0xFFEAFAFF).copy(alpha = 0.85f * r.alpha), style = Stroke(width = 1.6f * px * (1f + r.z * 0.6f), cap = StrokeCap.Round))
    }
    vignette(0.12f)
}

// ---------------------------------------------------------------------------------------------------------------
// SYMBOLS: deep blue with a sweeping glass sheet, glowing filaments and tumbling PlayStation symbols
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawSymbols(c: Sky, phase: Float, page: Float, k: SceneCache) {
    val (top, mid, bottom) = c
    val w = size.width
    val h = size.height
    val px = w / 1000f
    val fill = k.p1
    val edge = k.p2
    drawRect(Brush.verticalGradient(0f to top, 0.55f to mid, 1f to bottom))
    // Light blooming from the lower right.
    glow(w * (0.95f - page * 0.01f), h * 0.85f, w * 0.65f, Color(0xFF9CD8FF), 0.45f)

    // The glass sheet: a wide translucent swell along the bottom with a bright crest.
    val sway = sin(phase) * 0.02f
    fill.rewind(); edge.rewind()
    val steps = 32
    for (i in 0..steps) {
        val u = i.toFloat() / steps
        val y = h * (0.95f - 0.27f * sin(PI.toFloat() * (u * 0.9f + 0.05f + sway)).coerceAtLeast(0f) - 0.04f * sin(u * TAU + phase))
        val x = w * u
        if (i == 0) { fill.moveTo(x, y); edge.moveTo(x, y) } else { fill.lineTo(x, y); edge.lineTo(x, y) }
    }
    fill.lineTo(w, h); fill.lineTo(0f, h); fill.close()
    drawPath(fill, Brush.verticalGradient(listOf(Color(0xFF8FCBFF).copy(alpha = 0.30f), Color(0xFF3A78E0).copy(alpha = 0.08f)), startY = h * 0.65f, endY = h))
    drawPath(edge, Color.White.copy(alpha = 0.18f), style = Stroke(width = 8f * px))
    drawPath(edge, Color(0xFFDDF4FF).copy(alpha = 0.85f), style = Stroke(width = 1.8f * px))

    // Fine glowing filaments sweeping up the right side.
    for (n in 0 until 7) {
        val f = n / 6f
        edge.rewind()
        val sx = w * (0.82f + 0.03f * n)
        val ex = w * (0.72f + 0.06f * n - page * 0.004f * n)
        edge.moveTo(sx + w * 0.10f, h * 1.02f)
        edge.cubicTo(
            sx + w * (0.03f + 0.01f * sin(phase + n)), h * 0.65f,
            ex + w * 0.02f, h * (0.40f - 0.03f * f),
            w * (0.96f - 0.02f * n + 0.01f * sin(phase * 0.7f + n)), h * (0.08f + 0.04f * n),
        )
        val a = 0.22f + 0.30f * (1f - f)
        drawPath(edge, Color(0xFF9CE0FF).copy(alpha = a * 0.35f), style = Stroke(width = 5f * px, cap = StrokeCap.Round))
        drawPath(edge, Color.White.copy(alpha = a), style = Stroke(width = 1.3f * px, cap = StrokeCap.Round))
    }

    // Floating glass symbols, far to near: each tumbles in its own 3D orientation.
    val turn = phase / TAU
    val shapes = k.shards
    for (i in 0 until 16) {
        val d = shapes[i]
        val z = 0.3f + 0.7f * d.z
        val cx = (d.x + (page * -0.03f * z) + 0.02f * sin(phase + d.seed)).mod(1.1f) * w
        val cy = (0.15f + 0.85f * ((d.y - turn * 0.1f * d.s).mod(1f))) * h
        val rad = px * (14f + 46f * z)
        val ax = d.seed + phase * d.s * 0.9f
        val ay = d.seed * 1.7f + phase * d.s * 0.6f
        val az = d.seed * 0.5f + phase * d.s * 0.4f
        symbolPath(edge, i % 4, cx, cy, rad, ax, ay, az)
        val alpha = (0.35f + 0.55f * z)
        val sw = rad * 0.16f
        // Thickness: a darker copy behind, then the lit face.
        translate(rad * 0.06f, rad * 0.08f) { drawPath(edge, Color(0xFF0A2A6A).copy(alpha = alpha * 0.8f), style = Stroke(width = sw * 1.15f, cap = StrokeCap.Round)) }
        drawPath(edge, Color(0xFF7DB8F0).copy(alpha = alpha * 0.8f), style = Stroke(width = sw, cap = StrokeCap.Round))
        drawPath(edge, Color.White.copy(alpha = alpha * 0.7f), style = Stroke(width = sw * 0.3f, cap = StrokeCap.Round))
    }
}

/** The path of symbol [kind] (0 circle, 1 cross, 2 triangle, 3 square) of radius [r], rotated in 3D and seen with some perspective. */
private fun symbolPath(path: Path, kind: Int, cx: Float, cy: Float, r: Float, ax: Float, ay: Float, az: Float) {
    path.rewind()
    val ca = cos(ax); val sa = sin(ax); val cb = cos(ay); val sb = sin(ay); val cg = cos(az); val sg = sin(az)
    var first = true
    fun pt(x: Float, y: Float, move: Boolean) {
        // Rotate about z, then x, then y, then a gentle perspective.
        val x1 = x * cg - y * sg; val y1 = x * sg + y * cg
        val y2 = y1 * ca; val z2 = y1 * sa
        val x3 = x1 * cb + z2 * sb; val z3 = -x1 * sb + z2 * cb
        val p = 1f / (1f + z3 * 0.25f)
        val sx = cx + x3 * r * p; val sy = cy + y2 * r * p
        if (move || first) path.moveTo(sx, sy) else path.lineTo(sx, sy)
        first = false
    }
    when (kind) {
        0 -> { for (i in 0..24) { val a = TAU * i / 24f; pt(cos(a) * 0.9f, sin(a) * 0.9f, false) }; path.close() }
        1 -> { pt(-0.8f, -0.8f, true); pt(0.8f, 0.8f, false); pt(0.8f, -0.8f, true); pt(-0.8f, 0.8f, false) }
        2 -> { pt(0f, -0.95f, false); pt(0.9f, 0.7f, false); pt(-0.9f, 0.7f, false); path.close() }
        else -> { pt(-0.75f, -0.75f, false); pt(0.75f, -0.75f, false); pt(0.75f, 0.75f, false); pt(-0.75f, 0.75f, false); path.close() }
    }
}
