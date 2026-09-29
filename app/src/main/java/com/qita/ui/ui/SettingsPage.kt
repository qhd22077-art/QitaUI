package com.qita.ui.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Settings
import com.qita.ui.LAYOUTS
import com.qita.ui.THEMES
import kotlin.math.roundToInt

/** Full-screen settings: theme, wallpaper, particles, bubble size, clock and battery. */
@Composable
fun SettingsPage(
    settings: Settings,
    hasWallpaper: Boolean,
    wallpaper: ImageBitmap?,
    onChange: (Settings) -> Unit,
    onWallpaper: (Uri) -> Unit,
    onClearWallpaper: () -> Unit,
    onClearHome: () -> Unit,
    onClose: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onWallpaper(uri)
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        BubbleBackground(
            top = settings.theme.top, bottom = settings.theme.bottom, particles = settings.particles,
            wallpaper = wallpaper, particleCount = settings.particleCount, dim = settings.dim,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 48.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Done",
                    Modifier.background(Color.White, RoundedCornerShape(50)).clickable(onClick = onClose).padding(horizontal = 28.dp, vertical = 8.dp),
                    color = Color(0xFF0B3D91), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card("Theme") {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        THEMES.forEachIndexed { i, t ->
                            val selected = i == settings.themeIndex
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .background(Brush.verticalGradient(listOf(t.top, t.bottom)), CircleShape)
                                        .border(if (selected) 3.dp else 1.dp, Color.White.copy(alpha = if (selected) 1f else 0.5f), CircleShape)
                                        .clickable { onChange(settings.copy(themeIndex = i)) },
                                )
                                Text(t.name, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                Card("Wallpaper") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("Choose image") { picker.launch("image/*") }
                        if (hasWallpaper) Pill("Remove") { onClearWallpaper() }
                        Text(if (hasWallpaper) "Custom wallpaper in use" else "Using the theme colours", color = Color.White, fontSize = 13.sp)
                    }
                }
                Card("Layout") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LAYOUTS.forEachIndexed { i, (name, rows) ->
                            Chip("$name (${rows.joinToString("/")})", i == settings.layoutIndex) { onChange(settings.copy(layoutIndex = i)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Round bubbles", !settings.roundedBubbles) { onChange(settings.copy(roundedBubbles = false)) }
                        Chip("Rounded squares", settings.roundedBubbles) { onChange(settings.copy(roundedBubbles = true)) }
                    }
                    ToggleRow("Show app names", settings.showLabels) { onChange(settings.copy(showLabels = it)) }
                    ToggleRow("Show page dots", settings.showDots) { onChange(settings.copy(showDots = it)) }
                    Text("Bubble size", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    Slider(
                        value = settings.bubbleScale,
                        onValueChange = { onChange(settings.copy(bubbleScale = it)) },
                        valueRange = 0.7f..1.1f,
                        modifier = Modifier.width(320.dp),
                    )
                }
                Card("Background") {
                    ToggleRow("Floating particles", settings.particles) { onChange(settings.copy(particles = it)) }
                    if (settings.particles) {
                        Text("Particle amount", color = Color.White, fontSize = 14.sp)
                        Slider(
                            value = settings.particleCount.toFloat(),
                            onValueChange = { onChange(settings.copy(particleCount = it.roundToInt())) },
                            valueRange = 5f..60f,
                            modifier = Modifier.width(320.dp),
                        )
                    }
                    Text("Dim background", color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = settings.dim,
                        onValueChange = { onChange(settings.copy(dim = it)) },
                        valueRange = 0f..0.6f,
                        modifier = Modifier.width(320.dp),
                    )
                }
                Card("General") {
                    ToggleRow("24-hour clock", settings.use24h) { onChange(settings.copy(use24h = it)) }
                    ToggleRow("Show battery level", settings.showBattery) { onChange(settings.copy(showBattery = it)) }
                    ToggleRow("Sort newest apps first", settings.sortNewest) { onChange(settings.copy(sortNewest = it)) }
                    ToggleRow("Vibrate on long-press", settings.haptics) { onChange(settings.copy(haptics = it)) }
                }
                Card("Apps") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("Remove all from home") { onClearHome() }
                        Pill("Reset settings") { onChange(Settings()) }
                    }
                }
                Text(
                    "Tip: add apps to home from the desktop (\uD83D\uDDA5 in the top strip). Long-press a bubble and drag to rearrange; long-press without moving for App info, Remove and Uninstall.",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.width(360.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp)
        Switch(checked, onChecked)
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.background(Color.White, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        color = Color(0xFF0B3D91), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (selected) Color(0xFF0B3D91) else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
    )
}
