package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

private data class Status(val battery: Int, val charging: Boolean, val wifi: Boolean)

/** Top strip: clock on the left; Wi-Fi, battery and the settings gear on the right. */
@Composable
fun StatusBar(use24h: Boolean, showBattery: Boolean, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var status by remember { mutableStateOf(readStatus(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            status = readStatus(context)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (status.wifi) Text("Wi-Fi", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (showBattery) {
                Text(
                    (if (status.charging) "\u26A1" else "") + "${status.battery}%",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Text("\u2699", Modifier.clickable(onClick = onSettings).padding(horizontal = 6.dp), color = Color.White, fontSize = 22.sp)
        }
    }
}

private fun readStatus(context: Context): Status {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifi = runCatching {
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)
    return Status(if (level < 0) 0 else level * 100 / scale, plugged, wifi)
}
