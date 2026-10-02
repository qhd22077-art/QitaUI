package com.qita.ui.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.qita.ui.Parallax

/**
 * The far layer: slightly enlarged and shifted against the tilt, so its edges never show. [strength] 0 leaves it untouched.
 * The tilt is read only while the layer is drawn, so nothing is recomposed.
 */
fun Modifier.tiltBackdrop(strength: Float): Modifier =
    if (strength <= 0f) this else graphicsLayer {
        val s = 1f + 0.10f * strength
        scaleX = s
        scaleY = s
        translationX = -Parallax.x * strength * size.width * 0.045f
        translationY = -Parallax.y * strength * size.height * 0.045f
    }

/** A near layer: shifted with the tilt by up to [dp] density-independent pixels (times [strength] and [depth]). */
fun Modifier.tiltNear(strength: Float, dp: Float, depth: Float = 1f): Modifier =
    if (strength <= 0f) this else graphicsLayer {
        val d = dp * density * strength * depth
        translationX += Parallax.x * d
        translationY += Parallax.y * d
    }
