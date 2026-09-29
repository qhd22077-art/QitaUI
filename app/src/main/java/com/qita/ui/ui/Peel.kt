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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The region removed from a page's top-right corner: from the point [fold] px left of the corner, along a
 * fold that bows slightly into the page, to the point [fold] px below it. Curved rather than straight, so
 * the peel reads as a curled sheet.
 */
private fun cutPath(w: Float, fold: Float): Path {
    val bow = fold * 0.10f
    return Path().apply {
        moveTo(w - fold, 0f)
        quadraticBezierTo(w - fold / 2f - bow, fold / 2f + bow, w, fold)
        lineTo(w, 0f)
        close()
    }
}

/** A page (corners rounded by [radius] px) with its top-right corner peeled off, so what is behind shows through. */
internal class PeelShape(private val fold: Float, private val radius: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val page = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius, radius))) }
        return Outline.Generic(Path.combine(PathOperation.Difference, page, cutPath(size.width, fold)))
    }
}

/**
 * The peeled corner: a dark reveal where the page used to be, and the curled-back flap (the removed corner
 * folded over, with a rounded free tip) casting a soft shadow on the page. [radius] rounds the reveal to
 * match a page with rounded corners.
 */
@Composable
internal fun PeelBack(fold: Float, tint: Color, radius: Float = 0f) {
    Canvas(Modifier.fillMaxSize().clipToBounds()) {
        val w = size.width
        val f = fold
        val bow = f * 0.10f
        val mid = Offset(w - f / 2f, f / 2f)

        // Revealed corner: deep blue, darkest along the fold where the flap shades it.
        val reveal = cutPath(w, f)
        val paintReveal = {
            drawPath(
                reveal,
                Brush.linearGradient(
                    listOf(lerp(tint, Color.Black, 0.80f), lerp(tint, Color.Black, 0.55f)),
                    Offset(mid.x - f * 0.25f, mid.y + f * 0.25f), Offset(w, 0f),
                ),
            )
        }
        if (radius > 0f) {
            val round = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius, radius))) }
            clipPath(round) { paintReveal() }
        } else paintReveal()

        // The flap: the removed corner reflected across the fold, its free tip rounded off.
        val tip = f * 0.34f
        val flap = Path().apply {
            moveTo(w - f, 0f)
            quadraticBezierTo(w - f / 2f - bow, f / 2f + bow, w, f)
            lineTo(w - f + tip, f)
            quadraticBezierTo(w - f, f, w - f, f - tip)
            close()
        }
        // Soft shadow the flap casts on the page: a few offset copies, fading outward.
        for (i in 3 downTo 1) {
            translate(-f * 0.035f * i, f * 0.05f * i) { drawPath(flap, Color.Black.copy(alpha = 0.10f)) }
        }
        drawPath(
            flap,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.97f), lerp(tint, Color.White, 0.72f)),
                Offset(mid.x, mid.y), Offset(w - f, f),
            ),
        )
        // Gloss along the fold, and a faint shine across the flap.
        val foldLine = Path().apply {
            moveTo(w - f, 0f)
            quadraticBezierTo(w - f / 2f - bow, f / 2f + bow, w, f)
        }
        drawPath(foldLine, Color.White, style = Stroke(width = 2.5f))
        drawPath(
            Path().apply {
                moveTo(w - f * 0.80f, f * 0.16f)
                quadraticBezierTo(w - f * 0.5f - bow, f * 0.5f + bow, w - f * 0.16f, f * 0.80f)
            },
            Color.White.copy(alpha = 0.55f), style = Stroke(width = f * 0.05f, cap = StrokeCap.Round),
        )
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
                // Speed of the drag along the peel direction (px/s), so a quick flick finishes the peel.
                var velocity = 0f
                var lastTime = 0L
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        velocity = 0f
                        lastTime = 0L
                        scope.launch { peel.stop() }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        // Project the drag onto the diagonal from the top-right corner toward the bottom-left.
                        val along = (-drag.x + drag.y) / 1.4142f
                        val dt = change.uptimeMillis - (if (lastTime == 0L) change.previousUptimeMillis else lastTime)
                        if (dt > 0) velocity = 0.6f * velocity + 0.4f * (along / dt * 1000f)
                        lastTime = change.uptimeMillis
                        scope.launch { peel.snapTo((peel.value + along).coerceAtLeast(0f)) }
                    },
                    onDragEnd = {
                        dragging = false
                        if (peel.value > width * 0.25f || (velocity > 1400f && peel.value > 40f)) finish()
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
