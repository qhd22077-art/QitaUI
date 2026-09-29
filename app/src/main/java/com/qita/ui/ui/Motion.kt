package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** The motion vocabulary of the launcher: one set of curves and springs so everything moves alike. */
object VitaMotion {
    /** Fast start, long soft landing, like the Vita's transitions. */
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val Short = 180
    const val Medium = 280
    const val Long = 420

    /** Lively, with a little overshoot. Never use it for sizes or padding, which must not go negative. */
    fun <T> pop() = spring<T>(dampingRatio = 0.7f, stiffness = 400f)

    /** Settles quickly without bouncing. */
    fun <T> settle() = spring<T>(dampingRatio = 0.9f, stiffness = 300f)
}

/**
 * Fades an element in when it first appears, sliding it in from [fromX] px to the right and growing it
 * from [scaleFrom], after a delay that grows with [index]. Used to unfold a LiveArea piece by piece.
 */
fun Modifier.staggerIn(index: Int, fromX: Float = 0f, scaleFrom: Float = 1f): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(140L + index * 80L)
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 320f))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationX = (1f - p) * fromX
        val s = scaleFrom + (1f - scaleFrom) * p
        scaleX = s
        scaleY = s
    }
}
