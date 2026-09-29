package com.qita.ui.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qita.ui.LaunchableApp

/** Whether bubbles show the app's art edge to edge (the Vita look) or a small icon on a glossy sphere. */
val LocalFullArt = compositionLocalOf { true }

/**
 * A Vita-style bubble. By default the app's own art fills the whole circle, with a thin white rim, soft
 * edge shading for depth and a highlight across the top, as on the real home screen. With full art off
 * it is a tinted glossy sphere holding a smaller icon. Extra [modifier] parts (such as a gamepad target)
 * are applied after the size so they see the bubble's own bounds.
 */
@Composable
fun Sphere(
    app: LaunchableApp,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    elevation: Dp = 7.dp,
    spot: Color = Color(0xFF0A2A6A),
    rim: Brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f))),
    rimWidth: Dp = 2.5.dp,
    /** 0..1 selection glow, read while drawing: tints the bubble cyan like the Vita's selected bubble. */
    glow: () -> Float = { 0f },
) {
    val full = LocalFullArt.current
    val light = lerp(app.tint, Color.White, 0.42f)
    val dark = lerp(app.tint, Color.Black, 0.38f)
    Box(
        Modifier
            .size(size)
            .then(modifier)
            // Soft shadow under the bubble and a faint white halo around it, both drawn cheaply.
            .drawBehind {
                val r = this.size.minDimension / 2f
                val lift = elevation.toPx()
                for (i in 4 downTo 1) {
                    drawCircle(
                        spot.copy(alpha = 0.10f),
                        radius = r + lift * 0.10f * i,
                        center = Offset(this.size.width / 2f, this.size.height / 2f + lift * 0.28f),
                    )
                }
                // A flat shadow on the "floor" under the bubble makes it read as a ball.
                for (i in 3 downTo 1) {
                    drawOval(
                        Color.Black.copy(alpha = 0.10f),
                        Offset(this.size.width / 2f - r * (0.55f + 0.12f * i), this.size.height / 2f + r * (1.02f + 0.03f * i)),
                        Size(r * (1.10f + 0.24f * i), r * (0.20f + 0.05f * i)),
                    )
                }
                drawCircle(Color.White.copy(alpha = 0.10f), radius = r + 8.dp.toPx())
                drawCircle(Color.White.copy(alpha = 0.16f), radius = r + 4.dp.toPx())
            }
            .clip(shape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(light, app.tint, dark),
                        center = Offset(this.size.width * 0.42f, this.size.height * 0.30f),
                        radius = this.size.width * 0.85f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (full) {
            // The art fills the bubble, then a glass dome goes over it so it reads as a solid ball.
            Image(app.icon, app.label, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val w = this.size.width
                    val h = this.size.height
                    val c = Offset(w / 2f, h / 2f)
                    val radius = minOf(w, h) / 2f
                    // Dark toward the rim, so the middle bulges toward you.
                    val edge = Brush.radialGradient(
                        0f to Color.Transparent, 0.50f to Color.Transparent, 0.85f to Color.Black.copy(alpha = 0.22f),
                        1f to Color.Black.copy(alpha = 0.55f), center = c, radius = radius,
                    )
                    // Light bounced up from the floor, near the bottom.
                    val bounce = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.42f), Color.Transparent),
                        center = Offset(w * 0.5f, h * 1.02f), radius = w * 0.58f,
                    )
                    // A big soft highlight across the top.
                    val soft = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.06f)),
                        startY = h * 0.04f, endY = h * 0.46f,
                    )
                    val spec = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0f)),
                        center = Offset(w * 0.30f, h * 0.20f), radius = w * 0.11f,
                    )
                    val cyan = Color(0xFF16E0FF)
                    onDrawBehind {
                        drawRect(edge)
                        drawRect(bounce)
                        drawOval(soft, topLeft = Offset(w * 0.14f, h * 0.045f), size = Size(w * 0.72f, h * 0.42f))
                        // The sharp little glint of a window's reflection.
                        drawOval(spec, topLeft = Offset(w * 0.19f, h * 0.12f), size = Size(w * 0.22f, h * 0.15f))
                        val g = glow()
                        if (g > 0.01f) drawRect(cyan.copy(alpha = 0.32f * g))
                    }
                },
            )
        } else {
            Image(app.icon, app.label, Modifier.size(size * 0.66f).clip(CircleShape))
            // Glossy highlight across the top, and a soft bounce of light along the bottom.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = size * 0.045f)
                    .size(size * 0.80f, size * 0.44f)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.04f)))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = size * 0.03f)
                    .size(size * 0.74f, size * 0.30f)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.30f)))),
            )
        }
        // A fine dark ring inside the white rim gives the edge some depth. Both go on last so art never covers them.
        Box(Modifier.fillMaxSize().padding(rimWidth).border(1.dp, Color.Black.copy(alpha = 0.22f), shape))
        Box(Modifier.fillMaxSize().border(rimWidth, rim, shape))
    }
}
