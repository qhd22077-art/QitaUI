package com.qita.ui.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.sin

/** Animated blue gradient with soft layered waves, like the Vita home screen. */
@Composable
fun WaveBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waves")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF0A5BC4), Color(0xFF1E9BEA), Color(0xFF6CCBF5))),
        )
        val layers = listOf(
            Triple(0.55f, 0.06f, Color.White.copy(alpha = 0.10f)),
            Triple(0.68f, 0.05f, Color.White.copy(alpha = 0.12f)),
            Triple(0.82f, 0.04f, Color.White.copy(alpha = 0.16f)),
        )
        layers.forEachIndexed { i, (base, amp, color) ->
            val path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width + 8f) {
                    val y = size.height * base +
                        sin(x / size.width * 2f * PI.toFloat() * (1.2f + i * 0.4f) + phase * (i + 1) * (if (i % 2 == 0) 1 else -1)) *
                        size.height * amp
                    lineTo(x, y)
                    x += 8f
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, color)
        }
        // Soft highlight in the upper corner.
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), Offset(size.width * 0.15f, 0f), size.width * 0.5f),
            radius = size.width * 0.5f,
            center = Offset(size.width * 0.15f, 0f),
        )
    }
}
