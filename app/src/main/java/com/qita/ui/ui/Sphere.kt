package com.qita.ui.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qita.ui.LaunchableApp

/**
 * A glossy Vita-style sphere holding an app's icon: lit from the upper left, darker toward the
 * lower right, with a bright rim, a top highlight and a soft bounce of light along the bottom.
 * Extra [modifier] parts (such as a gamepad target) are applied after the size so they see the
 * sphere's own bounds.
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
            }
            .border(rimWidth, rim, shape),
        contentAlignment = Alignment.Center,
    ) {
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
}
