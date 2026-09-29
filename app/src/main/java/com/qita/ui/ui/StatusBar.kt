package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Top strip with the clock (left) and battery level (right). */
@Composable
fun StatusBar(use24h: Boolean, showBattery: Boolean, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableStateOf(batteryPercent(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            battery = batteryPercent(context)
            delay(15_000)
        }
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val clock = if (use24h) SimpleDateFormat("HH:mm", Locale.getDefault()) else SimpleDateFormat("h:mm a", Locale.getDefault())
        Text(clock.format(now), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showBattery) Text("$battery%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("⚙", Modifier.clickable(onClick = onSettings).padding(horizontal = 6.dp), color = Color.White, fontSize = 22.sp)
        }
    }
}

private fun batteryPercent(context: Context): Int {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    return if (level < 0) 0 else level * 100 / scale
}
