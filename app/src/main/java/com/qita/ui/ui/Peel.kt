package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A page with its top-right corner triangle (legs of [fold] px) removed, so what is behind shows through. */
internal class PeelShape(private val fold: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val page = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(0f, 0f))) }
        val corner = Path().apply {
            moveTo(size.width - fold, 0f); lineTo(size.width, 0f); lineTo(size.width, fold); close()
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, page, corner))
    }
}

/** The curled-back flap: the removed corner reflected across the fold line, shaded like paper and casting a soft shadow. */
@Composable
internal fun PeelBack(fold: Float, tint: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        // Shadow the flap casts on the page, fading away from the fold.
        val s = fold * 0.28f
        val shadow = Path().apply {
            moveTo(w - fold, 0f); lineTo(w, fold)
            lineTo(w - s, fold + s); lineTo(w - fold - s, s)
            close()
        }
        drawPath(
            shadow,
            Brush.linearGradient(
                listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent),
                Offset(w - fold * 0.5f, fold * 0.5f), Offset(w - fold * 0.5f - s * 0.7f, fold * 0.5f + s * 0.7f),
            ),
        )
        val flap = Path().apply {
            moveTo(w - fold, 0f); lineTo(w, fold); lineTo(w - fold, fold); close()
        }
        drawPath(
            flap,
            Brush.linearGradient(
                listOf(lerp(tint, Color.White, 0.72f), lerp(tint, Color.Black, 0.38f)),
                Offset(w - fold, 0f), Offset(w, fold),
            ),
        )
        drawLine(Color.White.copy(alpha = 0.65f), Offset(w - fold, 0f), Offset(w, fold), strokeWidth = 2f)
        val curl = Path().apply {
            moveTo(w - fold * 0.86f, fold * 0.14f)
            quadraticBezierTo(w - fold * 0.5f, fold * 0.5f, w - fold * 0.14f, fold * 0.86f)
        }
        drawPath(curl, Color.White.copy(alpha = 0.38f), style = Stroke(width = 2f))
    }
}

/**
 * The touch zone for a page's curled corner. Drag it toward the bottom-left to peel the page; past
 * a quarter of the page width it finishes and calls [onPeeled]. It also nudges itself now and then
 * ([hint]) so it is obvious it can be peeled. [tapToPeel] decides whether a plain tap finishes the
 * peel (lock screen) or only wiggles the corner (LiveArea, where finishing closes the app). Pressing the
 * confirm button on the gamepad always finishes.
 */
@Composable
fun PeelCorner(
    peel: Animatable<Float, AnimationVector1D>,
    pageWidth: Int,
    padKey: String,
    hint: Boolean,
    repeatHint: Boolean,
    tapToPeel: Boolean,
    onPeeled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // The page width is measured after the first frame, so the gestures must read the latest value.
    val width by rememberUpdatedState(pageWidth)
    val peeled by rememberUpdatedState(onPeeled)
    var dragging by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            peel.stop()
            peel.animateTo(width * 2f, tween(380, easing = FastOutSlowInEasing))
            peeled()
        }
    }
    suspend fun wiggle() {
        peel.animateTo(with(density) { 22.dp.toPx() }, tween(420))
        peel.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 260f))
    }

    if (hint) {
        LaunchedEffect(dragging, finishing) {
            if (!dragging && !finishing) {
                delay(1400)
                do {
                    wiggle()
                    if (repeatHint) delay(3200)
                } while (repeatHint)
            }
        }
    }

    Box(
        modifier
            .size(96.dp)
            .padTarget(padKey, corner = null, pad = 4.dp, bring = false, onClick = { finish() })
            .pointerInput(Unit) {
                detectTapGestures(onTap = { if (tapToPeel) finish() else scope.launch { wiggle() } })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        scope.launch { peel.stop() }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        // Project the drag onto the diagonal from the top-right corner toward the bottom-left.
                        val along = (-drag.x + drag.y) / 1.4142f
                        scope.launch { peel.snapTo((peel.value + along).coerceAtLeast(0f)) }
                    },
                    onDragEnd = {
                        dragging = false
                        if (peel.value > width * 0.25f) finish()
                        else scope.launch { peel.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)) }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { peel.animateTo(0f) }
                    },
                )
            },
    )
}
