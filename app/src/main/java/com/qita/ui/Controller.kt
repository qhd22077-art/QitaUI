package com.qita.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.abs
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

/**
 * Shared gamepad state. MainActivity writes it from key and joystick events; the UI reads it.
 *
 * Cursor mode: the left stick moves an on-screen pointer, A acts as a touch (held = drag or
 * long-press), the right stick scrolls. Outside cursor mode the D-pad and left stick move
 * keyboard focus and A activates the focused item.
 */
object Controller {
    var cursorMode by mutableStateOf(false)
    var cursor by mutableStateOf(Offset.Zero)
    var speed = 1f

    /** True while the gamepad was the last input used; drives the button hints and auto-focus. */
    var padActive by mutableStateOf(false)
    /** L3 toggles slow, precise cursor movement. */
    var precision by mutableStateOf(false)
    /** The app whose bubble/row currently has the highlight. */
    var focusedApp by mutableStateOf<LaunchableApp?>(null)
    /** Package being carried in controller move mode, if any. */
    var movingPackage by mutableStateOf<String?>(null)
    /** Set to a package name to move the highlight onto that app's bubble/row. */
    var focusPackage by mutableStateOf<String?>(null)

    @Volatile var stickX = 0f
    @Volatile var stickY = 0f
    @Volatile var scrollY = 0f
    @Volatile var aHeld = false

    val commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
}

private const val DEADZONE = 0.15f

/** Dead zone plus a squared response so small stick movements give fine control. */
private fun curve(v: Float): Float {
    val a = abs(v)
    if (a < DEADZONE) return 0f
    val n = (a - DEADZONE) / (1f - DEADZONE)
    return sign(v) * n * n
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

    Box(modifier.fillMaxSize().onSizeChanged { size = it }) {
        if (enabled) {
            Canvas(Modifier.fillMaxSize()) {
                val s = density.density
                val p = Controller.cursor
                val arrow = Path().apply {
                    moveTo(p.x, p.y)
                    lineTo(p.x, p.y + 34f * s)
                    lineTo(p.x + 9f * s, p.y + 26f * s)
                    lineTo(p.x + 15f * s, p.y + 40f * s)
                    lineTo(p.x + 21f * s, p.y + 37f * s)
                    lineTo(p.x + 15f * s, p.y + 24f * s)
                    lineTo(p.x + 26f * s, p.y + 24f * s)
                    close()
                }
                drawPath(arrow, Color.White, style = Fill)
                drawPath(arrow, Color.Black, style = Stroke(width = 2f * s))
            }
        }
    }

    LaunchedEffect(enabled, size) {
        if (!enabled || size == IntSize.Zero) return@LaunchedEffect
        val maxSpeed = with(density) { 520.dp.toPx() }
        var pressed = false
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
                        val next = Offset(
                            (p.x + sx * maxSpeed * Controller.speed * (if (Controller.precision) 0.35f else 1f) * dt).coerceIn(0f, size.width - 1f),
                            (p.y + sy * maxSpeed * Controller.speed * (if (Controller.precision) 0.35f else 1f) * dt).coerceIn(0f, size.height - 1f),
                        )
                        if (next != p) { Controller.cursor = next; moved = true }
                    }
                    val want = Controller.aHeld
                    if (want && !pressed) {
                        pressed = true
                        downTime = SystemClock.uptimeMillis()
                        touch(MotionEvent.ACTION_DOWN)
                    } else if (!want && pressed) {
                        pressed = false
                        touch(MotionEvent.ACTION_UP)
                    } else if (pressed && moved) {
                        touch(MotionEvent.ACTION_MOVE)
                    }
                    val scroll = curve(Controller.scrollY)
                    // Stick up = scroll toward the top, which is a positive wheel value.
                    if (scroll != 0f) wheel(-scroll * 0.4f)
                }
            }
        } finally {
            // Mode switched off (or screen left) mid-press: cancel so nothing stays held down.
            if (pressed) touch(MotionEvent.ACTION_CANCEL)
        }
    }
}
