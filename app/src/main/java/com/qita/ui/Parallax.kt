package com.qita.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * How the device is tilted, as two numbers from -1 to 1, so the wallpaper, the bubbles and the lock screen can shift a little against
 * each other (parallax). The "centre" is wherever the device has been resting for the last few seconds, so it works held at any angle.
 * [x] and [y] are Compose state read only while drawing a layer, so a change redraws that layer without recomposing anything.
 * The sensor is only listened to between [start] and [stop].
 */
object Parallax {
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set

    private var manager: SensorManager? = null
    private var listener: SensorEventListener? = null
    private var restRoll = Float.NaN
    private var restPitch = Float.NaN
    private val matrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val angles = FloatArray(3)

    /** True if the device has a sensor that tells how it is tilted. */
    fun available(c: Context): Boolean {
        val sm = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null || sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }

    @Suppress("DEPRECATION")
    fun start(c: Context) {
        if (listener != null) return
        val sm = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        restRoll = Float.NaN
        restPitch = Float.NaN
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, e.values)
                // The axes depend on how the screen is turned (a handheld is held sideways).
                val rotation = runCatching { wm?.defaultDisplay?.rotation }.getOrNull() ?: Surface.ROTATION_0
                val (ax, ay) = when (rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(matrix, ax, ay, remapped)
                SensorManager.getOrientation(remapped, angles)
                val pitch = angles[1]
                val roll = angles[2]
                if (restRoll.isNaN()) { restRoll = roll; restPitch = pitch }
                // The centre follows the device slowly (about ten seconds), so what shows is a recent tilt, not the resting angle.
                restRoll += (roll - restRoll) * 0.002f
                restPitch += (pitch - restPitch) * 0.002f
                // About 20 degrees of tilt is the full shift.
                val tx = ((roll - restRoll) / 0.35f).coerceIn(-1f, 1f)
                val ty = ((pitch - restPitch) / 0.35f).coerceIn(-1f, 1f)
                val nx = x + (tx - x) * 0.15f
                val ny = y + (ty - y) * 0.15f
                if (abs(nx - x) > 0.003f) x = nx
                if (abs(ny - y) > 0.003f) y = ny
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        // About 60 times a second is plenty for a soft shift.
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
        manager = sm
        listener = l
    }

    fun stop() {
        listener?.let { manager?.unregisterListener(it) }
        listener = null
        manager = null
        x = 0f
        y = 0f
    }
}
