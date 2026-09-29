package com.qita.ui.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared colours, so panels, bubbles and bars stay in one family. */
object VitaColors {
    val Glass = Color.White
    val Aqua = Color(0xFF40E0E0)
    val Navy = Color(0xFF0B3D91)
}

/**
 * A frosted-glass panel: a translucent white gradient, a thin border that is bright on top and faint at
 * the bottom, and a fine highlight just under the top edge. Drawn from cached brushes.
 */
fun Modifier.vitaPanel(radius: Dp = 14.dp, strength: Float = 1f): Modifier = drawWithCache {
    val r = radius.toPx()
    val corner = CornerRadius(r, r)
    val fill = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f * strength), Color.White.copy(alpha = 0.07f * strength)))
    val border = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.18f)))
    val stroke = Stroke(width = 1.dp.toPx())
    val inset = maxOf(r * 0.6f, 4.dp.toPx())
    val lineHeight = 1.5.dp.toPx()
    onDrawBehind {
        drawRoundRect(fill, cornerRadius = corner)
        // Fine highlight under the top edge.
        drawRoundRect(
            Color.White.copy(alpha = 0.40f * strength),
            topLeft = Offset(inset, 2.dp.toPx()),
            size = Size((size.width - 2 * inset).coerceAtLeast(0f), lineHeight),
            cornerRadius = CornerRadius(lineHeight, lineHeight),
        )
        drawRoundRect(border, cornerRadius = corner, style = stroke)
    }
}
