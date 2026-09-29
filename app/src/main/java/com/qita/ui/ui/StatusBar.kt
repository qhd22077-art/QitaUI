package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class Status(val battery: Int, val charging: Boolean, val wifi: Boolean)

/** Top strip: clock on the left; Wi-Fi, battery and the desktop / search / settings buttons on the right. */
@Composable
fun StatusBar(
    use24h: Boolean,
    showBattery: Boolean,
    onDesktop: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val clock = if (use24h) SimpleDateFormat("HH:mm", Locale.getDefault()) else SimpleDateFormat("h:mm a", Locale.getDefault())
        Text(
            clock.format(now),
            Modifier.background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 5.dp),
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (status.wifi) WifiIcon()
                if (showBattery) {
                    BatteryIcon(status.battery, status.charging)
                    Text("${status.battery}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            BarButton("bar:desktop", "🖥", 18, onDesktop)
            BarButton("bar:search", "🔍", 17, onSearch)
            BarButton("bar:settings", "⚙", 22, onSettings)
        }
    }
}

@Composable
private fun BarButton(key: String, symbol: String, size: Int, onClick: () -> Unit) {
    Text(
        symbol,
        Modifier
            .padClickable(key, corner = null, pad = 4.dp, onClick = onClick)
            .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 4.dp),
        color = Color.White, fontSize = size.sp,
    )
}

/** Three arcs and a dot. */
@Composable
private fun WifiIcon() {
    Canvas(Modifier.size(18.dp)) {
        val c = Offset(size.width / 2f, size.height * 0.88f)
        for (r in listOf(0.30f, 0.58f, 0.86f)) {
            val radius = size.width * r
            drawArc(
                Color.White, startAngle = 225f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(c.x - radius, c.y - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        drawCircle(Color.White, 1.7.dp.toPx(), c)
    }
}

/** Outlined battery whose fill follows the charge: red when low, green while charging. */
@Composable
private fun BatteryIcon(percent: Int, charging: Boolean) {
    Canvas(Modifier.size(width = 26.dp, height = 12.dp)) {
        val body = Size(size.width - 3.dp.toPx(), size.height)
        val stroke = 1.5.dp.toPx()
        val fill = when {
            charging -> Color(0xFF7CE38B)
            percent <= 15 -> Color(0xFFFF6B6B)
            else -> Color.White
        }
        drawRoundRect(Color.White, size = body, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(width = stroke))
        val inner = (body.width - 2 * stroke - 2.dp.toPx()) * (percent.coerceIn(0, 100) / 100f)
        drawRoundRect(
            fill,
            topLeft = Offset(stroke + 1.dp.toPx(), stroke + 1.dp.toPx()),
            size = Size(inner.coerceAtLeast(0f), body.height - 2 * stroke - 2.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = Fill,
        )
        drawRoundRect(
            Color.White,
            topLeft = Offset(body.width + 0.5.dp.toPx(), size.height * 0.3f),
            size = Size(2.dp.toPx(), size.height * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

internal fun readStatus(context: Context): Status {
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
