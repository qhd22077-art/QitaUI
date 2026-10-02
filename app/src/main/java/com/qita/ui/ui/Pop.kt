package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** A panel that opens over the screen: it fades in while growing from a little smaller, with a soft spring. Put it before the panel's background. */
fun Modifier.popIn(): Modifier = composed {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 420f)) }
    graphicsLayer {
        alpha = t.value.coerceIn(0f, 1f)
        val s = 0.92f + 0.08f * t.value
        scaleX = s
        scaleY = s
    }
}
