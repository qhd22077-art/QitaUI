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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qita.ui.LaunchableApp

/**
 * Clear glass, like the Vita's folders: the sphere is almost invisible, and what shows it is there, and its shape, is the light. There is
 * no outline. Instead a bright sliver of light runs along the upper-left edge and thins out to nothing at both ends, a fainter sliver of
 * bounced light sits on the opposite lower-right edge, a short curved reflection floats just inside the upper left, and a bright speck
 * sits in it. Together they trace the roundness of the bubble the way real glass catches light, so it reads as a 3D ball in space
 * while the wallpaper shows straight through. Its contents (the folder's apps, or a system bubble's pictogram) sit inside.
 */
@Composable
fun FolderGlass(
    app: LaunchableApp,
    size: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** What sits inside the glass: the folder's cluster, or for a clear-glass system bubble its pictogram. */
    icon: ImageBitmap = app.icon,
    iconFraction: Float = 0.84f,
    /** The glass's hue (a system bubble given the clear glass look); null is the folder's pale blue. */
    bodyTint: Color? = null,
) {
    Box(
        modifier
            .size(size)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val r = minOf(w, h) / 2f
                val c = Offset(w / 2f, h / 2f)
                val hue = if (bodyTint != null) lerp(bodyTint, Color.White, 0.25f) else Color(0xFF6FA8FF)
                val deep = lerp(hue, Color.Black, 0.55f)
                val lightHue = lerp(hue, Color.White, 0.65f)
                val boost = if (selected) 1f else 0.9f

                // A sliver of light along the edge between two angles (degrees, clockwise from the right), thickest in the middle and
                // thinning to nothing at both ends, so it can never read as a ring.
                fun sliver(from: Float, to: Float, outer: Float, thick: Float, inset: Float = 0f): Path {
                    val path = Path()
                    val steps = 32
                    fun pt(deg: Float, rad: Float): Offset {
                        val a = Math.toRadians(deg.toDouble())
                        return Offset(c.x + rad * cos(a).toFloat(), c.y + rad * sin(a).toFloat())
                    }
                    for (i in 0..steps) {
                        val o = pt(from + (to - from) * i / steps, outer - inset)
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    for (i in steps downTo 0) {
                        val f = i / steps.toFloat()
                        val taper = sin(Math.PI * f).toFloat().coerceAtLeast(0f).pow(1.4f)
                        val o = pt(from + (to - from) * f, outer - inset - thick * taper)
                        path.lineTo(o.x, o.y)
                    }
                    path.close()
                    return path
                }
                val edgeLight = sliver(168f, 290f, r * 0.99f, r * 0.10f)
                val edgeBounce = sliver(8f, 88f, r * 0.99f, r * 0.060f)
                val reflection = sliver(196f, 266f, r * 0.99f, r * 0.055f, inset = r * 0.15f)

                // The faint body: a touch of hue, a little denser toward the edge but gone at the very edge, and a soft shade toward the
                // lower right that gives the ball some thickness.
                val body = Brush.radialGradient(
                    0.00f to hue.copy(alpha = 0.02f),
                    0.70f to hue.copy(alpha = 0.04f),
                    0.93f to hue.copy(alpha = 0.09f),
                    1.00f to hue.copy(alpha = 0f),
                    center = c, radius = r,
                )
                val shade = Brush.radialGradient(
                    0.00f to Color.Transparent,
                    0.60f to Color.Transparent,
                    0.88f to deep.copy(alpha = 0.10f),
                    1.00f to deep.copy(alpha = 0f),
                    center = Offset(c.x - r * 0.30f, c.y - r * 0.30f), radius = r * 1.32f,
                )
                val reflectBrush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.60f * boost), Color.White.copy(alpha = 0.10f)),
                    start = Offset(c.x - r * 0.65f, c.y - r * 0.65f), end = Offset(c.x - r * 0.05f, c.y - r * 0.2f),
                )
                onDrawWithContent {
                    drawCircle(body, radius = r, center = c)
                    drawCircle(shade, radius = r, center = c)
                    drawContent()
                    drawPath(reflection, reflectBrush)
                    drawPath(edgeBounce, lightHue.copy(alpha = 0.50f * boost))
                    drawPath(edgeLight, Color.White.copy(alpha = if (selected) 1f else 0.92f))
                    // The bright speck, with a soft glow round it, where the light is strongest.
                    val speck = Offset(c.x - r * 0.42f, c.y - r * 0.56f)
                    drawCircle(Color.White.copy(alpha = 0.20f * boost), radius = r * 0.11f, center = speck)
                    drawCircle(Color.White.copy(alpha = 0.95f), radius = r * 0.045f, center = speck)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // A system bubble's own picture is round inside the glass; a folder's cluster already is.
        Image(icon, null, if (bodyTint != null) Modifier.fillMaxSize(iconFraction).clip(CircleShape) else Modifier.fillMaxSize(iconFraction))
    }
}
