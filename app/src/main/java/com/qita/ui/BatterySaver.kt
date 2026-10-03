package com.qita.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the battery saver is working right now: switched on by hand, or automatically while the battery is at or below the "low"
 * level and not charging. While it is, the launcher looks the way it does in Light mode and also holds still (no sway, flip, tilt,
 * moving backgrounds or sounds). It never changes the user's saved settings, so it switches back cleanly.
 */
object BatterySaver {
    var active by mutableStateOf(false)
}
