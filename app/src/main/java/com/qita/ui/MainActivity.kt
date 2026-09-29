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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.qita.ui.ui.HomeScreen
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    /** Bumped each time Home is pressed while the launcher is already open. */
    private var homePresses by mutableIntStateOf(0)

    // State for turning the left stick into D-pad presses when cursor mode is off.
    private var navDirection = 0
    private var navLastFire = 0L

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

    /**
     * Gamepad buttons:
     *  A = select (touch in cursor mode, otherwise activates the focused item), B = back,
     *  X = desktop, Y = search, Start = settings, Select = toggle cursor mode, L1/R1 = page.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE -> {
                if (first) Controller.cursorMode = !Controller.cursorMode
                return true
            }
            KeyEvent.KEYCODE_BUTTON_START -> { if (first) Controller.commands.tryEmit(Command.Settings); return true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (first) Controller.commands.tryEmit(Command.Desktop); return true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (first) Controller.commands.tryEmit(Command.Search); return true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (first) Controller.commands.tryEmit(Command.Page(-1)); return true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (first) Controller.commands.tryEmit(Command.Page(1)); return true }
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (Controller.cursorMode) {
                    Controller.aHeld = down
                    return true
                }
                // Compose treats D-pad centre as "click" on the focused item.
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
            val ry = strongest(ev.getAxisValue(MotionEvent.AXIS_RZ), ev.getAxisValue(MotionEvent.AXIS_RY))
            Controller.stickX = x
            Controller.stickY = y
            Controller.scrollY = ry
            if (!Controller.cursorMode) stickAsDpad(x, y)
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun strongest(a: Float, b: Float) = if (abs(a) >= abs(b)) a else b

    /** Left stick as D-pad presses (with key repeat) so the stick can move focus around. */
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
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, dir))
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, dir))
        }
    }
}
