package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import java.text.DateFormat
import java.util.Date

/** Top strip with the clock (left) and battery level (right). */
@Composable
fun StatusBar(modifier: Modifier = Modifier) {
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
        Text(
            DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        )
        Text("$battery%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun batteryPercent(context: Context): Int {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    return if (level < 0) 0 else level * 100 / scale
}
