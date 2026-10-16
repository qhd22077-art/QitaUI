package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.basicMarquee

/**
 * A looping clock from 0 to 2π as State, to be read while drawing so that only the drawing is redone each tick. Normally it runs at
 * the screen's rate; in [light] mode it ticks 30 times a second, which halves the work of everything that follows it.
 */
@Composable
fun rememberLoopClock(periodMs: Int, light: Boolean): androidx.compose.runtime.State<Float> =
    if (light) rememberSlowClock(periodMs)
    else rememberInfiniteTransition(label = "loop").animateFloat(
        0f, 6.2831855f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(periodMs, easing = androidx.compose.animation.core.LinearEasing)),
        label = "loopValue",
    )

@Composable
private fun rememberSlowClock(periodMs: Int): androidx.compose.runtime.State<Float> {
    val phase = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(periodMs) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = now - last
                if (dt >= 33_000_000L) {
                    phase.floatValue = (phase.floatValue + dt / 1_000_000f / periodMs * 6.2831855f) % 6.2831855f
                    last = now
                }
            }
        }
    }
    return phase
}

/** True while the launcher is the screen in front (between resume and pause), so loops can stop when it is not. */
@Composable
fun rememberResumed(): androidx.compose.runtime.State<Boolean> {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val state = remember { androidx.compose.runtime.mutableStateOf(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) state.value = true
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) state.value = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

/**
 * A looping clock from 0 to 2π that ticks [hz] times a second, and not at all while [active] is false (nothing is woken up then).
 * For slow motion (the idle sway of the bubbles, the drifting background) a few ticks a second look the same as every frame but
 * cost a fraction of the drawing; read the value only while drawing.
 */
@Composable
fun rememberTickClock(periodMs: Int, hz: Int, active: Boolean): androidx.compose.runtime.State<Float> {
    val phase = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(periodMs, hz, active) {
        if (!active) return@LaunchedEffect
        val step = (1000 / hz.coerceIn(1, 60)).toLong()
        var last = android.os.SystemClock.uptimeMillis()
        while (true) {
            delay(step)
            val now = android.os.SystemClock.uptimeMillis()
            phase.floatValue = (phase.floatValue + (now - last).toFloat() / periodMs * 6.2831855f) % 6.2831855f
            last = now
        }
    }
    return phase
}

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

/**
 * Fades an element in and grows it slightly from [originX], [originY] (0..1 of its size) when it first appears, so panels and
 * overlays ease in instead of popping into place.
 */
fun Modifier.easeIn(originX: Float = 0.5f, originY: Float = 0.5f, from: Float = 0.94f, millis: Int = 220): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, androidx.compose.animation.core.tween(millis, easing = VitaMotion.Ease)) }
    graphicsLayer {
        alpha = progress.value
        val s = from + (1f - from) * progress.value
        scaleX = s
        scaleY = s
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(originX, originY)
    }
}

/** Whether a long name should be sliding right now: the setting says always, or only for the one that is [active] (selected or touched). */
@androidx.compose.runtime.Composable
fun marqueeOn(active: Boolean = false): Boolean {
    val mode = LocalLook.current.scrollNames
    return mode == 2 || (mode == 1 && active)
}

/**
 * Makes a one-line name that does not fit slide sideways so its end comes into view, wait, and start again; a name that fits
 * stays still. The text must be single-line, without wrapping or an ellipsis, while this is on.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun androidx.compose.ui.Modifier.nameMarquee(on: Boolean): androidx.compose.ui.Modifier =
    if (on) this.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200, repeatDelayMillis = 1200, velocity = 36.dp) else this
