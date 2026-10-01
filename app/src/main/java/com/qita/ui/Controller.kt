package com.qita.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.qita.ui.ui.PadNav
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/** Actions triggered by gamepad buttons; collected by the home screen (and the desktop for pages). */
sealed interface Command {
    data class Page(val delta: Int) : Command
    data object Desktop : Command
    data object Search : Command
    data object Settings : Command
    /** Context menu for the highlighted app. */
    data object Options : Command
    /** Move the highlighted bubble (home) or add/remove it from home (desktop). */
    data object Toggle : Command
    data class MoveStep(val dx: Int, val dy: Int) : Command
    data class MoveEnd(val confirm: Boolean) : Command
}

/** Takes every gamepad button and joystick event first while a Flash game is open (see FlashScreen); returns true if it used the event. */
interface FlashInput {
    fun onKey(event: android.view.KeyEvent): Boolean
    fun onMotion(event: android.view.MotionEvent): Boolean
}

/**
 * Shared gamepad state. MainActivity writes it from key and joystick events; the UI reads it.
 *
 * Cursor mode: the left stick moves an on-screen pointer, A acts as a touch (held = drag or
 * long-press), the right stick scrolls, the D-pad nudges. Otherwise the D-pad and left stick
 * move the highlight (see PadNav) and A activates it.
 */
object Controller {
    var cursorMode by mutableStateOf(false)
    /** Set while a Flash game is playing: it gets the gamepad instead of the launcher. */
    var flashInput by mutableStateOf<FlashInput?>(null)
    var cursor by mutableStateOf(Offset.Zero)
    var viewSize = Size.Zero
    var speed = 1f
    var swapAB = false

    /** True while the gamepad was the last input used; drives the button hints and the highlight. */
    var padActive by mutableStateOf(false)
    /** R3 toggles slow, precise cursor movement. */
    var precision by mutableStateOf(false)
    /** Package being carried in controller move mode, if any. */
    var movingPackage by mutableStateOf<String?>(null)
    /** Last raw input seen, for the controller test readout in Settings. */
    var lastInput by mutableStateOf("(nothing yet)")

    @Volatile var stickX = 0f
    @Volatile var stickY = 0f
    @Volatile var scrollY = 0f
    @Volatile var aHeld = false

    val commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)

    /** Moves the cursor by a fixed step (D-pad in cursor mode). */
    fun nudge(dx: Float, dy: Float) {
        val p = cursor
        cursor = Offset(
            (p.x + dx).coerceIn(0f, max(0f, viewSize.width - 1f)),
            (p.y + dy).coerceIn(0f, max(0f, viewSize.height - 1f)),
        )
        PadNav.updateHover(cursor)
    }
}

private const val DEADZONE = 0.15f

/** Dead zone plus a squared response so small stick movements give fine control. */
private fun curve(v: Float): Float {
    val a = abs(v)
    if (a < DEADZONE) return 0f
    val n = (a - DEADZONE) / (1f - DEADZONE)
    return sign(v) * n * n
}

/** A soft-shadowed, gradient pointer whose tip is at [tip]. [k] scales it around the tip. */
private fun DrawScope.drawPointer(tip: Offset, s: Float, k: Float, hovering: Boolean) {
    fun shape(dx: Float, dy: Float) = Path().apply {
        moveTo(tip.x + dx, tip.y + dy)
        lineTo(tip.x + dx, tip.y + dy + 30f * s)
        lineTo(tip.x + dx + 7.5f * s, tip.y + dy + 23.5f * s)
        lineTo(tip.x + dx + 13f * s, tip.y + dy + 35f * s)
        lineTo(tip.x + dx + 18.5f * s, tip.y + dy + 32.5f * s)
        lineTo(tip.x + dx + 13f * s, tip.y + dy + 21.5f * s)
        lineTo(tip.x + dx + 22f * s, tip.y + dy + 21.5f * s)
        close()
    }
    scale(k, tip) {
        if (hovering) {
            drawCircle(Color(0xFFFFD54F).copy(alpha = 0.28f), 20f * s, tip)
            drawCircle(Color(0xFFFFD54F).copy(alpha = 0.55f), 20f * s, tip, style = Stroke(width = 2f * s))
        }
        // Layered offsets fake a blurred drop shadow.
        for ((i, a) in listOf(0.08f, 0.10f, 0.14f).withIndex()) {
            drawPath(shape(4f * s - i * s, 5f * s - i * s), Color.Black.copy(alpha = a))
        }
        val body = shape(0f, 0f)
        drawPath(
            body,
            Brush.linearGradient(listOf(Color.White, Color(0xFFCFE0F5)), tip, Offset(tip.x + 20f * s, tip.y + 36f * s)),
        )
        drawPath(body, Color(0xFF14202E), style = Stroke(width = 2.2f * s, join = StrokeJoin.Round))
    }
}

/**
 * Draws the cursor and, while cursor mode is on, turns stick and A-button input into real touch
 * and scroll events dispatched to the view, so every screen works with it unchanged.
 * Place it last in the root layout so it draws above everything.
 */
@Composable
fun CursorLayer(modifier: Modifier = Modifier) {
    val enabled = Controller.cursorMode
    val view = LocalView.current
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Look: press squash, hover grow, click ripple, fade when idle.
    val pressed = Controller.aHeld
    val hovering = PadNav.hover != null
    val k by animateFloatAsState(
        if (pressed) 0.82f else if (hovering) 1.18f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pointerScale",
    )
    val ripple = remember { Animatable(1f) }
    var rippleAt by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pressed) {
        if (pressed) {
            rippleAt = Controller.cursor
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(450))
        }
    }
    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(Controller.cursor, pressed) {
        idle = false
        delay(3500)
        idle = true
    }
    val pointerAlpha by animateFloatAsState(if (idle) 0.45f else 1f, tween(450), label = "pointerAlpha")

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                size = it
                Controller.viewSize = Size(it.width.toFloat(), it.height.toFloat())
            },
    ) {
        if (enabled) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { this.alpha = pointerAlpha }) {
                val s = density.density
                val r = ripple.value
                if (r < 1f) {
                    drawCircle(Color.White.copy(alpha = (1f - r) * 0.25f), (12f + 40f * r) * s, rippleAt)
                    drawCircle(Color.White.copy(alpha = (1f - r) * 0.7f), (12f + 40f * r) * s, rippleAt, style = Stroke(width = 3f * s))
                }
                drawPointer(Controller.cursor, s, k, hovering)
            }
        }
    }

    LaunchedEffect(enabled, size) {
        if (!enabled || size == IntSize.Zero) return@LaunchedEffect
        val maxSpeed = with(density) { 520.dp.toPx() }
        var pressedNow = false
        var downTime = 0L
        var last = 0L

        fun touch(action: Int) {
            val c = Controller.cursor
            val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, c.x, c.y, 0)
            ev.source = InputDevice.SOURCE_TOUCHSCREEN
            view.dispatchTouchEvent(ev)
            ev.recycle()
        }

        fun wheel(amount: Float) {
            val c = Controller.cursor
            val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
            val coords = arrayOf(MotionEvent.PointerCoords().apply {
                x = c.x; y = c.y
                setAxisValue(MotionEvent.AXIS_VSCROLL, amount)
            })
            val now = SystemClock.uptimeMillis()
            val ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            view.dispatchGenericMotionEvent(ev)
            ev.recycle()
        }

        try {
            PadNav.updateHover(Controller.cursor)
            while (true) {
                withFrameNanos { now ->
                    // Offset.Zero means "recenter" (initial state, or L3).
                    if (Controller.cursor == Offset.Zero) Controller.cursor = Offset(size.width / 2f, size.height / 2f)
                    val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    var moved = false
                    val sx = curve(Controller.stickX)
                    val sy = curve(Controller.stickY)
                    if (sx != 0f || sy != 0f) {
                        val p = Controller.cursor
                        val factor = maxSpeed * Controller.speed * (if (Controller.precision) 0.35f else 1f) * dt
                        val next = Offset(
                            (p.x + sx * factor).coerceIn(0f, size.width - 1f),
                            (p.y + sy * factor).coerceIn(0f, size.height - 1f),
                        )
                        if (next != p) { Controller.cursor = next; moved = true }
                    }
                    if (moved) PadNav.updateHover(Controller.cursor)
                    val want = Controller.aHeld
                    if (want && !pressedNow) {
                        pressedNow = true
                        downTime = SystemClock.uptimeMillis()
                        touch(MotionEvent.ACTION_DOWN)
                    } else if (!want && pressedNow) {
                        pressedNow = false
                        touch(MotionEvent.ACTION_UP)
                    } else if (pressedNow && moved) {
                        touch(MotionEvent.ACTION_MOVE)
                    }
                    val scroll = curve(Controller.scrollY)
                    // Stick up = scroll toward the top, which is a positive wheel value.
                    if (scroll != 0f) wheel(-scroll * 0.4f)
                }
            }
        } finally {
            // Mode switched off (or screen left) mid-press: cancel so nothing stays held down.
            if (pressedNow) touch(MotionEvent.ACTION_CANCEL)
        }
    }
}
