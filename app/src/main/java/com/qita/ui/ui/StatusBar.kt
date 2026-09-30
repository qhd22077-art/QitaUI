package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.LaunchableApp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class Status(val battery: Int, val charging: Boolean, val wifi: Boolean, val bluetooth: Boolean)

/**
 * The Vita information bar: a thin black strip with Wi-Fi and Bluetooth on the left, the home
 * icon in the middle, and the time and a green battery on the right.
 */
@Composable
fun StatusBar(
    use24h: Boolean,
    showBattery: Boolean,
    modifier: Modifier = Modifier,
    showHome: Boolean = true,
    /** Apps with an open LiveArea, shown as small icons beside the home icon; [current] is the one on screen (-1 = home). */
    openApps: List<LaunchableApp> = emptyList(),
    current: Int = -1,
    onHome: () -> Unit = {},
    onPick: (Int) -> Unit = {},
    /** Live position of the page on screen (-1 = home, 0.. = open apps), fractional while swiping. Read while drawing. */
    position: (() -> Float)? = null,
) {
    // How strongly icon [i] is highlighted: follows the live position if there is one, so the ring moves with the page.
    fun strength(i: Int): Float = position?.let { (1f - kotlin.math.abs(it() - i)).coerceIn(0f, 1f) } ?: if (i == current) 1f else 0f
    val look = LocalLook.current
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
    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF000000).copy(alpha = look.barOpacity), Color(0xFF0C0C0C).copy(alpha = look.barOpacity)))),
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (status.wifi) WifiIcon()
            if (status.bluetooth) BluetoothIcon()
        }
        if (showHome) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .drawBehind { if (openApps.isNotEmpty()) drawRect(Color.White.copy(alpha = 0.20f * strength(-1))) }
                        .clickable(onClick = onHome)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) { HomeIcon() }
                openApps.forEachIndexed { i, app ->
                    Image(
                        app.icon, app.label,
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .graphicsLayer { alpha = 0.65f + 0.35f * strength(i) }
                            .drawWithContent {
                                drawContent()
                                val s = strength(i)
                                if (s > 0.01f) {
                                    drawCircle(Color.White.copy(alpha = s), radius = this.size.minDimension / 2f - 1.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
                                }
                            }
                            .clickable { onPick(i) },
                    )
                }
            }
        }
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 78.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val clock = if (use24h) SimpleDateFormat("HH:mm", Locale.getDefault()) else SimpleDateFormat("h:mm", Locale.getDefault())
            if (look.showClock) Row(verticalAlignment = Alignment.Bottom) {
                Text(clock.format(now), color = Color.White, fontSize = (17f * look.clockSize).sp, fontWeight = FontWeight.Medium)
                if (!use24h) {
                    Text(
                        SimpleDateFormat(" a", Locale.getDefault()).format(now).uppercase(Locale.getDefault()),
                        Modifier.padding(bottom = 2.dp), color = Color.White, fontSize = 10.sp,
                    )
                }
            }
            if (showBattery) BatteryIcon(status.battery, status.charging)
        }
    }
}

/** The big translucent sphere in the top-right corner. Here it opens the launcher menu. */
@Composable
fun CornerSphere(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(68.dp)
            .padTarget("bar:menu", corner = null, pad = 4.dp, bring = false, onClick = onClick)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFFE3EEFF).copy(alpha = 0.90f), Color(0xFF6E9BE8).copy(alpha = 0.80f), Color(0xFF2D55B0).copy(alpha = 0.80f)),
                        center = Offset(size.width * 0.38f, size.height * 0.32f),
                        radius = size.width * 0.85f,
                    ),
                )
            }
            .border(2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.25f))), CircleShape),
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 5.dp)
                .size(46.dp, 24.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent))),
        )
    }
}

/** A small house. */
@Composable
private fun HomeIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 22.dp, height = 17.dp)) {
        val w = size.width
        val h = size.height
        val roof = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.5f)
            lineTo(w * 0.86f, h * 0.5f)
            lineTo(w * 0.86f, h)
            lineTo(w * 0.14f, h)
            lineTo(w * 0.14f, h * 0.5f)
            lineTo(0f, h * 0.5f)
            close()
        }
        drawPath(roof, Color.White.copy(alpha = 0.92f), style = Fill)
        drawRect(Color.Black.copy(alpha = 0.55f), Offset(w * 0.43f, h * 0.6f), Size(w * 0.14f, h * 0.4f))
    }
}

/** Three arcs and a dot. */
@Composable
private fun WifiIcon() {
    Canvas(Modifier.size(17.dp)) {
        val c = Offset(size.width / 2f, size.height * 0.9f)
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

/** The Bluetooth rune, drawn as a polyline. */
@Composable
private fun BluetoothIcon() {
    Canvas(Modifier.size(width = 11.dp, height = 17.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.05f, h * 0.28f)
            lineTo(w * 0.95f, h * 0.72f)
            lineTo(w * 0.5f, h)
            lineTo(w * 0.5f, 0f)
            lineTo(w * 0.95f, h * 0.28f)
            lineTo(w * 0.05f, h * 0.72f)
        }
        drawPath(p, Color.White, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Glossy Vita-style battery: pale outline, green gradient fill that follows the charge. */
@Composable
private fun BatteryIcon(percent: Int, charging: Boolean) {
    Canvas(Modifier.size(width = 31.dp, height = 14.dp)) {
        val body = Size(size.width - 3.dp.toPx(), size.height)
        val stroke = 1.5.dp.toPx()
        val (c1, c2) = when {
            charging -> Color(0xFFB8F7C0) to Color(0xFF39C24F)
            percent <= 15 -> Color(0xFFFFB0B0) to Color(0xFFE23B3B)
            else -> Color(0xFFC8F7A8) to Color(0xFF4CC22D)
        }
        drawRoundRect(Color(0xFFE6E6E6), size = body, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(width = stroke))
        val inner = (body.width - 2 * stroke - 2.dp.toPx()) * (percent.coerceIn(0, 100) / 100f)
        drawRoundRect(
            Brush.verticalGradient(listOf(c1, c2)),
            topLeft = Offset(stroke + 1.dp.toPx(), stroke + 1.dp.toPx()),
            size = Size(inner.coerceAtLeast(0f), body.height - 2 * stroke - 2.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = Fill,
        )
        drawRoundRect(
            Color(0xFFE6E6E6),
            topLeft = Offset(body.width + 0.5.dp.toPx(), size.height * 0.28f),
            size = Size(2.dp.toPx(), size.height * 0.44f),
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
    val bluetooth = runCatching { AndroidSettings.Global.getInt(context.contentResolver, "bluetooth_on", 0) == 1 }.getOrDefault(false)
    return Status(if (level < 0) 0 else level * 100 / scale, plugged, wifi, bluetooth)
}
