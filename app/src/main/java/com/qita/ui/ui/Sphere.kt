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
            // The art fills the bubble.
            Image(app.icon, app.label, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val w = this.size.width
                    val h = this.size.height
                    val edge = Brush.radialGradient(
                        0f to Color.Transparent, 0.66f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.32f),
                        center = Offset(w / 2f, h / 2f), radius = minOf(w, h) / 2f,
                    )
                    // A curved crescent of light along the top-left, like the Vita's glossy bubbles.
                    val outer = Path().apply { addOval(Rect(w * 0.06f, h * 0.03f, w * 0.94f, h * 0.70f)) }
                    val inner = Path().apply { addOval(Rect(w * 0.02f, h * 0.15f, w * 0.98f, h * 0.84f)) }
                    val crescent = Path.combine(PathOperation.Difference, outer, inner)
                    val gloss = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.10f)),
                        Offset(w * 0.25f, h * 0.05f), Offset(w * 0.75f, h * 0.30f),
                    )
                    // A soft sheen over the upper half.
                    val sheen = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        startY = 0f, endY = h * 0.55f,
                    )
                    val bounce = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                    )
                    val bounceStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    val inset = 4.dp.toPx()
                    onDrawBehind {
                        drawRect(edge)
                        drawRect(sheen)
                        drawPath(crescent, gloss)
                        // Light bouncing along the bottom rim.
                        drawArc(
                            bounce, startAngle = 45f, sweepAngle = 90f, useCenter = false,
                            topLeft = Offset(inset, inset), size = Size(w - 2 * inset, h - 2 * inset), style = bounceStroke,
                        )
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
