package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.LaunchableApp
import kotlinx.coroutines.delay

/**
 * The quick panel: screen brightness and volume, shortcuts to Wi-Fi, Bluetooth and airplane mode, the battery saver, and the games last
 * played and starred, so the usual things are one step from the home screen. Android no longer lets an app flip Wi-Fi or Bluetooth
 * itself, so those buttons open Android's own small panel or page.
 */
@Composable
fun QuickPanel(
    saverMode: Int,
    onSaver: (Int) -> Unit,
    games: List<LaunchableApp>,
    favourites: Set<String>,
    played: Map<String, Long>,
    onLaunch: (LaunchableApp) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    BackHandler(enabled = true) { onClose() }
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 600.dp).fillMaxWidth(0.94f).heightIn(max = 460.dp)
                    .background(Color(0xF01E2630), RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick panel", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    BarButton("quick:close", "Close", onClose)
                }
                Column(
                    Modifier.weight(1f, fill = false).padScroller { scroll.animateScrollBy(it) }.verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BrightnessRow(context)
                    VolumeRow(context)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("quick:wifi", "Wi-Fi", false) { open(context, if (Build.VERSION.SDK_INT >= 29) android.provider.Settings.Panel.ACTION_WIFI else android.provider.Settings.ACTION_WIFI_SETTINGS) }
                        Tile("quick:bt", "Bluetooth", false) { open(context, android.provider.Settings.ACTION_BLUETOOTH_SETTINGS) }
                        Tile("quick:air", "Airplane mode", false) { open(context, android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
                        Tile("quick:saver", "Battery saver: " + listOf("Off", "Auto", "On")[saverMode.coerceIn(0, 2)], saverMode == 2) { onSaver((saverMode + 1) % 3) }
                    }
                    val recent = games.filter { it.game != null && played[it.game!!.id] != null }.sortedByDescending { played[it.game!!.id] }.take(6)
                    val starred = games.filter { it.game != null && it.game!!.id in favourites }.take(12)
                    if (recent.isNotEmpty()) Shelf("Continue playing", "recent", recent, onLaunch)
                    if (starred.isNotEmpty()) Shelf("Favourites", "fav", starred, onLaunch)
                    if (recent.isEmpty() && starred.isEmpty()) {
                        Text("Games you play and star in the Games bubble show up here.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun open(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun BrightnessRow(context: Context) {
    val resolver = context.contentResolver
    var canWrite by remember { mutableStateOf(android.provider.Settings.System.canWrite(context)) }
    // Allowing "Modify system settings" happens in Android's own page, so look again now and then while this is open.
    LaunchedEffect(Unit) { while (true) { canWrite = android.provider.Settings.System.canWrite(context); delay(1000) } }
    var level by remember { mutableFloatStateOf(runCatching { android.provider.Settings.System.getInt(resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128).toFloat()) }
    if (canWrite) {
        QuickSlider("quick:bright", "Brightness", level, 10f..255f, 15f) {
            level = it
            runCatching {
                android.provider.Settings.System.putInt(resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                android.provider.Settings.System.putInt(resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, it.toInt())
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Brightness needs permission to change system settings.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.weight(1f))
            BarButton("quick:bright:allow", "Allow") {
                runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(context: Context) {
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    QuickSlider("quick:vol", "Volume", level, 0f..max.toFloat(), 1f) {
        level = it
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }
    }
}

/** A labelled slider; with it highlighted, left and right on the gamepad step the value. */
@Composable
private fun QuickSlider(key: String, label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, onChange: (Float) -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Column(
        Modifier
            .fillMaxWidth()
            .padTarget(key, corner = 12.dp, onAdjust = { dir -> onChange((value + dir * step).coerceIn(range.start, range.endInclusive)) })
            .background(Color.White.copy(alpha = if (lit) 0.18f else 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color.White, fontSize = 15.sp)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
        )
    }
}

@Composable
private fun Tile(key: String, label: String, on: Boolean, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padClickable(key, corner = 14.dp, pad = 2.dp, ring = false, onClick = onClick)
            .litEdge(lit, 14.dp)
            .background(Color.White.copy(alpha = if (on) 0.40f else if (lit) 0.28f else 0.14f), RoundedCornerShape(14.dp))
            .border(if (lit) 2.dp else 1.dp, Color.White.copy(alpha = if (lit || on) 0.95f else 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
    )
}

@Composable
private fun Shelf(title: String, id: String, apps: List<LaunchableApp>, onLaunch: (LaunchableApp) -> Unit) {
    Column {
        Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.forEach { app ->
                Bubble(app, 60.dp, onClick = { onLaunch(app) }, modifier = Modifier.width(88.dp), padKey = "quick:$id:${app.packageName}")
            }
        }
    }
}
