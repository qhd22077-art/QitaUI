package com.qita.ui.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qita.ui.LaunchableApp

/**
 * A folder bubble: clear glass, like the Vita's. The sphere is almost see-through (the background shows through it, a little
 * bluer toward the rim), with a pale rim, a glare across the top, a thin light arc along the bottom and a bright speck, and its
 * apps sit inside as small round icons.
 */
@Composable
fun FolderGlass(
    app: LaunchableApp,
    size: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** What sits inside the glass: the folder's cluster, or for a glass built-in bubble its pictogram. */
    icon: ImageBitmap = app.icon,
    iconFraction: Float = 0.84f,
    /** Colours the glass (a built-in bubble given the glass look); null keeps the folder's pale blue. */
    bodyTint: Color? = null,
) {
    Box(
        modifier
            .size(size)
            .drawWithContent {
                val w = this.size.width
                val h = this.size.height
                val r = w / 2f
                val c = Offset(r, h / 2f)
                // Behind the icons: a faint body that darkens the middle a touch and glows blue (or the tint) at the edge.
                val k = if (bodyTint != null) 1.9f else 1f
                val deep = if (bodyTint != null) lerp(bodyTint, Color.Black, 0.55f) else Color(0xFF071A55)
                val body = bodyTint ?: Color(0xFF3E78E0)
                val edge = if (bodyTint != null) lerp(bodyTint, Color.White, 0.45f) else Color(0xFF8FC0FF)
                val rimTint = if (bodyTint != null) lerp(bodyTint, Color.White, 0.8f) else Color(0xFFD6EAFF)
                drawCircle(
                    Brush.radialGradient(
                        0.0f to deep.copy(alpha = (0.16f * k).coerceAtMost(1f)),
                        0.65f to body.copy(alpha = (0.14f * k).coerceAtMost(1f)),
                        0.90f to edge.copy(alpha = (0.30f * k).coerceAtMost(1f)),
                        1.0f to rimTint.copy(alpha = (0.55f * (if (bodyTint != null) 1.2f else 1f)).coerceAtMost(1f)),
                        center = Offset(r, h * 0.54f), radius = r,
                    ),
                    radius = r, center = c,
                )
                drawContent()
                // In front of them: the glass.
                val rim = 2.2.dp.toPx()
                drawCircle(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.95f), 0.5f to Color.White.copy(alpha = 0.25f), 1f to Color.White.copy(alpha = 0.65f),
                        start = Offset(w * 0.15f, h * 0.05f), end = Offset(w * 0.85f, h * 0.95f),
                    ),
                    radius = r - rim / 2f, center = c, style = Stroke(rim),
                )
                drawCircle(Color(0xFFA9D2FF).copy(alpha = 0.28f), radius = r - rim * 2.2f, center = c, style = Stroke(1.dp.toPx()))
                // The glare across the top.
                drawOval(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (selected) 0.70f else 0.55f), Color.White.copy(alpha = 0.02f)), startY = h * 0.04f, endY = h * 0.42f),
                    topLeft = Offset(w * 0.17f, h * 0.045f), size = Size(w * 0.66f, h * 0.38f),
                )
                // The light arc along the bottom, where the glass bends light the other way.
                drawArc(
                    Color(0xFFCFE6FF).copy(alpha = 0.45f), startAngle = 40f, sweepAngle = 100f, useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.12f), size = Size(w * 0.76f, h * 0.76f), style = Stroke(3.dp.toPx()),
                )
                drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.028f, center = Offset(w * 0.27f, h * 0.24f))
            },
        contentAlignment = Alignment.Center,
    ) {
        // A built-in bubble's own picture is round inside the glass; a folder's cluster already is.
        Image(icon, null, if (bodyTint != null) Modifier.fillMaxSize(iconFraction).clip(CircleShape) else Modifier.fillMaxSize(iconFraction))
    }
}
