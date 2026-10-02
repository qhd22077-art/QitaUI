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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qita.ui.LaunchableApp

/**
 * Clear glass, like the Vita's folders. The sphere is fully see-through: the wallpaper shows straight through it, with only a faint
 * glassy hue (a little denser toward the edge, fading out at the very edge, so there is no outline). What shows it is glass is the
 * light on it: a soft glare across the top, a faint bounce of light at the bottom and a bright speck. Its contents (the folder's apps,
 * or a system bubble's pictogram) sit inside.
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
            .drawWithContent {
                val w = this.size.width
                val h = this.size.height
                val r = w / 2f
                val c = Offset(r, h / 2f)
                val hue = if (bodyTint != null) lerp(bodyTint, Color.White, 0.25f) else Color(0xFF6FA8FF)
                // Behind the contents: only a faint hue, denser toward the edge and gone at the very edge (no ring).
                drawCircle(
                    Brush.radialGradient(
                        0.00f to hue.copy(alpha = 0.03f),
                        0.60f to hue.copy(alpha = 0.06f),
                        0.88f to hue.copy(alpha = 0.13f),
                        1.00f to hue.copy(alpha = 0f),
                        center = Offset(r, h * 0.55f), radius = r,
                    ),
                    radius = r, center = c,
                )
                drawContent()
                // The glare across the top: the main sign of glass.
                drawOval(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (selected) 0.85f else 0.70f), Color.White.copy(alpha = 0f)),
                        startY = h * 0.05f, endY = h * 0.41f,
                    ),
                    topLeft = Offset(w * 0.16f, h * 0.05f), size = Size(w * 0.68f, h * 0.36f),
                )
                // A faint bounce of light at the bottom.
                drawOval(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.20f)),
                        startY = h * 0.72f, endY = h * 0.88f,
                    ),
                    topLeft = Offset(w * 0.28f, h * 0.72f), size = Size(w * 0.44f, h * 0.16f),
                )
                // The bright speck, with a soft glow round it.
                drawCircle(Color.White.copy(alpha = 0.22f), radius = w * 0.06f, center = Offset(w * 0.26f, h * 0.22f))
                drawCircle(Color.White.copy(alpha = 0.92f), radius = w * 0.026f, center = Offset(w * 0.26f, h * 0.22f))
            },
        contentAlignment = Alignment.Center,
    ) {
        // A system bubble's own picture is round inside the glass; a folder's cluster already is.
        Image(icon, null, if (bodyTint != null) Modifier.fillMaxSize(iconFraction).clip(CircleShape) else Modifier.fillMaxSize(iconFraction))
    }
}
