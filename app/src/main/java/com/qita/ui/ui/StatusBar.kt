package com.qita.ui.ui

import android.content.Context
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.combinedClickable
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.asImageBitmap
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

internal data class Status(
    val battery: Int, val charging: Boolean, val wifi: Boolean, val bluetooth: Boolean,
    /** Mobile data is the connection in use. */
    val cellular: Boolean = false,
    val airplane: Boolean = false,
    /** The ringer is silent or on vibrate. */
    val silent: Boolean = false,
)

/**
 * The Vita information bar: a thin black strip with Wi-Fi and Bluetooth on the left, the home
 * icon in the middle, and the time and a green battery on the right.
 */
/** The screen-rotation action, set by the home screen, so every screen's status bar can offer a rotate button. */
object ScreenRotator {
    var onRotate by androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null)
}

/** Two arrows chasing each other round a circle. */
@Composable
private fun RotateIcon() {
    Canvas(Modifier.size(15.dp)) {
        val w = size.width
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
        val inset = w * 0.16f
        val box = androidx.compose.ui.geometry.Size(w - 2 * inset, w - 2 * inset)
        drawArc(Color.White, 200f, 130f, false, Offset(inset, inset), box, style = stroke)
        drawArc(Color.White, 20f, 130f, false, Offset(inset, inset), box, style = stroke)
        val a = Path().apply { moveTo(w * 0.72f, w * 0.05f); lineTo(w * 0.92f, w * 0.32f); lineTo(w * 0.60f, w * 0.30f); close() }
        val b = Path().apply { moveTo(w * 0.28f, w * 0.95f); lineTo(w * 0.08f, w * 0.68f); lineTo(w * 0.40f, w * 0.70f); close() }
        drawPath(a, Color.White)
        drawPath(b, Color.White)
    }
}

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
            if (status.airplane) AirplaneIcon()
            if (status.wifi) WifiIcon() else if (status.cellular) SignalIcon()
            if (status.bluetooth) BluetoothIcon()
            if (status.silent) SilentIcon()
            ScreenRotator.onRotate?.let { rotate ->
                Box(Modifier.clip(CircleShape).clickable(onClick = rotate).padding(horizontal = 6.dp, vertical = 4.dp)) { RotateIcon() }
            }
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
            if (showBattery) BatteryIcon(status.battery, status.charging, look.batteryLow, look.batteryCritical)
        }
    }
}

/** The big translucent sphere in the top-right corner. Here it opens the launcher menu. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun CornerSphere(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1D3E8F),
    /** Number of notifications, shown as a small badge; 0 shows none. */
    count: Int = 0,
    onLongClick: () -> Unit = {},
) {
    Box(
        modifier
            .size(68.dp)
            .padTarget("bar:menu", corner = null, pad = 4.dp, bring = false, onClick = onClick)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(lerp(color, Color.White, 0.55f).copy(alpha = 0.92f), color.copy(alpha = 0.92f), lerp(color, Color.Black, 0.35f).copy(alpha = 0.95f)),
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
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 12.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Text(if (count > 9) "9+" else count.toString(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
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

/** The user's own pictures for the battery's states, kept as small files; the drawn icon is used for a state with no picture. */
object BatteryArt {
    /** Bumped whenever a picture is added or removed, so the status bar redraws. */
    var rev by mutableStateOf(0)
    val STATES = listOf("normal" to "Normal", "charging" to "Charging", "full" to "Full", "low" to "Low", "critical" to "Nearly dead")

    private fun file(c: Context, state: String) = java.io.File(c.filesDir, "battery_$state.png")
    fun has(c: Context, state: String) = file(c, state).exists()
    fun load(c: Context, state: String): androidx.compose.ui.graphics.ImageBitmap? =
        file(c, state).takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() }

    fun save(c: Context, state: String, uri: android.net.Uri): Boolean = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 512) sample *= 2
        val bmp = c.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        file(c, state).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        rev++
        true
    }.getOrDefault(false)

    fun clear(c: Context, state: String) { file(c, state).delete(); rev++ }
}

/** Which picture of the battery applies: full and charging at once, charging, nearly dead, low, or normal. */
internal fun batteryState(percent: Int, charging: Boolean, low: Int, critical: Int): String = when {
    charging && percent >= 100 -> "full"
    charging -> "charging"
    percent <= critical -> "critical"
    percent <= low -> "low"
    else -> "normal"
}

/** The battery in the top bar: the user's own picture for its state if there is one, else the drawn icon of that state. */
@Composable
private fun BatteryIcon(percent: Int, charging: Boolean, low: Int, critical: Int) {
    val context = LocalContext.current
    val state = batteryState(percent, charging, low, critical)
    val own = remember(state, BatteryArt.rev) { BatteryArt.load(context, state) }
    if (own != null) Image(own, null, Modifier.size(width = 31.dp, height = 14.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    else BatteryDrawn(percent, state)
}

/**
 * Glossy Vita-style battery with a look for each state: green while normal, a green fill that breathes with a bolt while charging
 * (still with the bolt when full), amber when low, and a red, blinking outline with a mark when nearly dead.
 */
@Composable
private fun BatteryDrawn(percent: Int, state: String) {
    val pulse = if (state == "charging") {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "batteryPulse").animateFloat(
            0.7f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "pulse",
        ).value
    } else 1f
    val blink = if (state == "critical") {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "batteryBlink").animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(600), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "blink",
        ).value
    } else 0f
    Canvas(Modifier.size(width = 31.dp, height = 14.dp)) {
        val body = Size(size.width - 3.dp.toPx(), size.height)
        val stroke = 1.5.dp.toPx()
        val (c1, c2) = when (state) {
            "charging", "full" -> Color(0xFFB8F7C0) to Color(0xFF39C24F)
            "low" -> Color(0xFFFFE2A0) to Color(0xFFF29A1F)
            "critical" -> Color(0xFFFFB0B0) to Color(0xFFE23B3B)
            else -> Color(0xFFC8F7A8) to Color(0xFF4CC22D)
        }
        val outline = if (state == "critical") lerp(Color(0xFFE6E6E6), Color(0xFFE23B3B), blink) else Color(0xFFE6E6E6)
        drawRoundRect(outline, size = body, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(width = stroke))
        val fraction = if (state == "critical") percent.coerceIn(6, 100) / 100f else percent.coerceIn(0, 100) / 100f
        val inner = (body.width - 2 * stroke - 2.dp.toPx()) * fraction
        drawRoundRect(
            Brush.verticalGradient(listOf(c1.copy(alpha = pulse), c2.copy(alpha = pulse))),
            topLeft = Offset(stroke + 1.dp.toPx(), stroke + 1.dp.toPx()),
            size = Size(inner.coerceAtLeast(0f), body.height - 2 * stroke - 2.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = Fill,
        )
        drawRoundRect(
            outline,
            topLeft = Offset(body.width + 0.5.dp.toPx(), size.height * 0.28f),
            size = Size(2.dp.toPx(), size.height * 0.44f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        val w = body.width
        val h = body.height
        if (state == "charging" || state == "full") {
            // A lightning bolt in the middle.
            val bolt = Path().apply {
                moveTo(w * 0.54f, h * 0.06f); lineTo(w * 0.40f, h * 0.56f); lineTo(w * 0.50f, h * 0.56f)
                lineTo(w * 0.44f, h * 0.94f); lineTo(w * 0.64f, h * 0.42f); lineTo(w * 0.53f, h * 0.42f); lineTo(w * 0.60f, h * 0.06f); close()
            }
            drawPath(bolt, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round))
            drawPath(bolt, Color.White)
        } else if (state == "critical") {
            // An exclamation mark.
            val x = w * 0.5f
            drawLine(Color.White, Offset(x, h * 0.2f), Offset(x, h * 0.58f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White, 1.1.dp.toPx(), Offset(x, h * 0.80f))
        }
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
    // Ask the adapter first (it needs no permission to say whether it is on); the system setting is the fallback.
    val bluetooth = runCatching { (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.isEnabled }.getOrNull()
        ?: runCatching { AndroidSettings.Global.getInt(context.contentResolver, "bluetooth_on", 0) == 1 }.getOrDefault(false)
    val cellular = runCatching { !wifi && cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true }.getOrDefault(false)
    val airplane = runCatching { AndroidSettings.Global.getInt(context.contentResolver, AndroidSettings.Global.AIRPLANE_MODE_ON, 0) == 1 }.getOrDefault(false)
    val silent = runCatching { (context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL }.getOrDefault(false)
    return Status(if (level < 0) 0 else level * 100 / scale, plugged, wifi, bluetooth, cellular, airplane, silent)
}

/** Four rising bars: mobile data. */
@Composable
private fun SignalIcon() {
    Canvas(Modifier.size(width = 16.dp, height = 15.dp)) {
        val bar = size.width / 7f
        for (i in 0..3) {
            val h = size.height * (0.30f + 0.23f * i)
            drawRect(Color.White, Offset(i * bar * 1.75f, size.height - h), Size(bar, h))
        }
    }
}

/** A small aeroplane: airplane mode is on. */
@Composable
private fun AirplaneIcon() {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val p = Path().apply {
            moveTo(w * 0.50f, w * 0.02f)
            lineTo(w * 0.58f, w * 0.34f)
            lineTo(w * 0.98f, w * 0.60f)
            lineTo(w * 0.98f, w * 0.72f)
            lineTo(w * 0.58f, w * 0.60f)
            lineTo(w * 0.56f, w * 0.84f)
            lineTo(w * 0.72f, w * 0.94f)
            lineTo(w * 0.72f, w * 1.00f)
            lineTo(w * 0.50f, w * 0.94f)
            lineTo(w * 0.28f, w * 1.00f)
            lineTo(w * 0.28f, w * 0.94f)
            lineTo(w * 0.44f, w * 0.84f)
            lineTo(w * 0.42f, w * 0.60f)
            lineTo(w * 0.02f, w * 0.72f)
            lineTo(w * 0.02f, w * 0.60f)
            lineTo(w * 0.42f, w * 0.34f)
            close()
        }
        drawPath(p, Color.White)
    }
}

/** A speaker crossed out: the ringer is silent or on vibrate. */
@Composable
private fun SilentIcon() {
    Canvas(Modifier.size(width = 17.dp, height = 15.dp)) {
        val w = size.width
        val h = size.height
        val speaker = Path().apply {
            moveTo(0f, h * 0.36f); lineTo(w * 0.22f, h * 0.36f); lineTo(w * 0.50f, h * 0.10f)
            lineTo(w * 0.50f, h * 0.90f); lineTo(w * 0.22f, h * 0.64f); lineTo(0f, h * 0.64f); close()
        }
        drawPath(speaker, Color.White)
        val stroke = 1.6.dp.toPx()
        drawLine(Color.White, Offset(w * 0.64f, h * 0.30f), Offset(w * 0.96f, h * 0.70f), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.96f, h * 0.30f), Offset(w * 0.64f, h * 0.70f), stroke, StrokeCap.Round)
    }
}
