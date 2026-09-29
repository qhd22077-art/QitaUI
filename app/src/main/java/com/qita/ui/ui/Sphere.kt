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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    rimWidth: Dp = 2.dp,
) {
    val full = LocalFullArt.current
    val light = lerp(app.tint, Color.White, 0.42f)
    val dark = lerp(app.tint, Color.Black, 0.38f)
    Box(
        Modifier
            .size(size)
            .then(modifier)
            // Soft white halo just outside the rim.
            .drawBehind { drawCircle(Color.White.copy(alpha = 0.20f), radius = this.size.minDimension / 2f + 5.dp.toPx()) }
            .shadow(elevation, shape, ambientColor = Color(0xFF0A2A6A), spotColor = spot)
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
            // The art fills the bubble; a touch larger so the corners of squircle icons are hidden.
            Image(app.icon, app.label, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.05f; scaleY = 1.05f })
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val w = this.size.width
                    val h = this.size.height
                    val edge = Brush.radialGradient(
                        0f to Color.Transparent, 0.70f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.26f),
                        center = Offset(w / 2f, h / 2f), radius = minOf(w, h) / 2f,
                    )
                    val gloss = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.03f)),
                        startY = 0f, endY = h * 0.48f,
                    )
                    onDrawBehind {
                        drawRect(edge)
                        drawOval(gloss, topLeft = Offset(w * 0.10f, h * 0.035f), size = Size(w * 0.80f, h * 0.44f))
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
        // The rim goes on last so the art never covers it.
        Box(Modifier.fillMaxSize().border(rimWidth, rim, shape))
    }
}
