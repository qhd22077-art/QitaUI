package com.qita.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.qita.ui.ui.HomeScreen
import kotlin.math.abs
import kotlin.math.max

class MainActivity : ComponentActivity() {
    /** Bumped each time Home is pressed while the launcher is already open. */
    private var homePresses by mutableIntStateOf(0)

    // Stick-as-D-pad, hat and trigger edge detection.
    private var navDirection = 0
    private var navLastFire = 0L
    private var hatDirection = 0
    private var l2Down = false
    private var r2Down = false
    private var rightStickDir = 0
    // Some pads report triggers/D-pad as both keys and axes; once keys are seen, ignore the axes.
    private var sawL2Key = false
    private var sawR2Key = false
    private var sawDpadKey = false
    private var injecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { HomeScreen(homePresses) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    // Touching the screen means the gamepad is no longer the active input.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) Controller.padActive = false
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Gamepad buttons (see the README table):
     *  A select/click, B back, X options for the highlighted app, Y move (home) or add/remove (desktop),
     *  Start settings, Select cursor mode, L1/R1 page, L2 desktop, R2 search, L3 recenter cursor, R3 precision.
     * While carrying a bubble (move mode) the D-pad moves it and A/B/Y drop or cancel.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        val isDpad = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
        if (!injecting && (KeyEvent.isGamepadButton(code) || isDpad)) {
            Controller.padActive = true
            if (isDpad) sawDpadKey = true
        }

        // Move mode: the D-pad carries the bubble.
        if (Controller.movingPackage != null) {
            if (isDpad) {
                if (down) Controller.commands.tryEmit(
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> Command.MoveStep(-1, 0)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> Command.MoveStep(1, 0)
                        KeyEvent.KEYCODE_DPAD_UP -> Command.MoveStep(0, -1)
                        else -> Command.MoveStep(0, 1)
                    },
                )
                return true
            }
            when (code) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (first) Controller.commands.tryEmit(Command.MoveEnd(true))
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                    if (first) Controller.commands.tryEmit(Command.MoveEnd(false))
                    return true
                }
            }
        }

        when (code) {
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE -> {
                if (first) Controller.cursorMode = !Controller.cursorMode
                return true
            }
            KeyEvent.KEYCODE_BUTTON_START -> { if (first) Controller.commands.tryEmit(Command.Settings); return true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (first) Controller.commands.tryEmit(Command.Options); return true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (first) Controller.commands.tryEmit(Command.Toggle); return true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (first) Controller.commands.tryEmit(Command.Page(-1)); return true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (first) Controller.commands.tryEmit(Command.Page(1)); return true }
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                sawL2Key = true
                if (first) Controller.commands.tryEmit(Command.Desktop)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                sawR2Key = true
                if (first) Controller.commands.tryEmit(Command.Search)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_THUMBL -> { if (first) Controller.cursor = Offset.Zero; return true }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> { if (first) Controller.precision = !Controller.precision; return true }
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (Controller.cursorMode) {
                    Controller.aHeld = down
                    return true
                }
                // Compose treats D-pad centre as "click" on the highlighted item.
                return super.dispatchKeyEvent(
                    KeyEvent(
                        event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER,
                        event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
                    ),
                )
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val isStick = ev.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (isStick && ev.action == MotionEvent.ACTION_MOVE) {
            val x = ev.getAxisValue(MotionEvent.AXIS_X)
            val y = ev.getAxisValue(MotionEvent.AXIS_Y)
            // Controllers report the right stick as Z/RZ or RX/RY; take whichever is deflected.
            val rx = strongest(ev.getAxisValue(MotionEvent.AXIS_Z), ev.getAxisValue(MotionEvent.AXIS_RX))
            val ry = strongest(ev.getAxisValue(MotionEvent.AXIS_RZ), ev.getAxisValue(MotionEvent.AXIS_RY))
            if (abs(x) > 0.3f || abs(y) > 0.3f || abs(rx) > 0.3f || abs(ry) > 0.3f) Controller.padActive = true
            Controller.stickX = x
            Controller.stickY = y
            Controller.scrollY = ry
            if (!Controller.cursorMode) {
                stickAsDpad(x, y)
                rightStickPages(rx)
            }

            // Analog triggers, for pads that do not also send L2/R2 key events.
            val lt = max(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE))
            val rt = max(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS))
            if (!sawL2Key) {
                val pressed = lt > 0.75f
                if (pressed && !l2Down) Controller.commands.tryEmit(Command.Desktop)
                l2Down = pressed
            }
            if (!sawR2Key) {
                val pressed = rt > 0.75f
                if (pressed && !r2Down) Controller.commands.tryEmit(Command.Search)
                r2Down = pressed
            }

            // D-pad reported as a hat axis, for pads that do not send D-pad key events.
            if (!sawDpadKey) {
                val hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
                val hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
                val dir = when {
                    hx < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
                    hx > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
                    hy < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
                    hy > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
                    else -> 0
                }
                if (dir != hatDirection) {
                    hatDirection = dir
                    if (dir != 0) press(dir)
                }
            }
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun strongest(a: Float, b: Float) = if (abs(a) >= abs(b)) a else b

    /** Sends a D-pad key press (down and up) without marking it as coming from a real D-pad. */
    private fun press(code: Int) {
        injecting = true
        try {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } finally {
            injecting = false
        }
    }

    /** Left stick as D-pad presses (with key repeat) so the stick can move the highlight. */
    private fun stickAsDpad(x: Float, y: Float) {
        val dir = when {
            x < -0.6f -> KeyEvent.KEYCODE_DPAD_LEFT
            x > 0.6f -> KeyEvent.KEYCODE_DPAD_RIGHT
            y < -0.6f -> KeyEvent.KEYCODE_DPAD_UP
            y > 0.6f -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> 0
        }
        val now = SystemClock.uptimeMillis()
        if (dir == 0) { navDirection = 0; return }
        if (dir != navDirection || now - navLastFire > 220) {
            navDirection = dir
            navLastFire = now
            press(dir)
        }
    }

    /** Flicking the right stick left or right changes page (outside cursor mode). */
    private fun rightStickPages(rx: Float) {
        val dir = when {
            rx < -0.7f -> -1
            rx > 0.7f -> 1
            else -> 0
        }
        if (dir != rightStickDir) {
            rightStickDir = dir
            if (dir != 0) Controller.commands.tryEmit(Command.Page(dir))
        }
    }
}
