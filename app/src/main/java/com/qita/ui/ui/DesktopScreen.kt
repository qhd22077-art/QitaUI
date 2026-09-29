package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.LaunchableApp
import com.qita.ui.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mono = FontFamily.Monospace
private val Panel = Color(0xFF16161E)
private val Window = Color(0xF01E1E2E)
private val Accent = Color(0xFF8AE234)
private val Muted = Color(0xFF9A9AB0)

/** Folders in the sidebar, named like paths in a Linux file manager. */
private enum class Place(val label: String, val path: String) {
    ALL("All Apps", "/apps"),
    HOME("On Home", "/home"),
    NEW("Recently Installed", "/recent"),
    GAMES("Games", "/apps/games"),
    MEDIA("Media", "/apps/media"),
    SOCIAL("Social", "/apps/social"),
    WORK("Productivity", "/apps/work"),
    SYSTEM("System", "/system"),
    OTHER("Other", "/apps/other"),
}

private fun LaunchableApp.isIn(place: Place, onHome: Set<String>): Boolean = when (place) {
    Place.ALL, Place.NEW -> true
    Place.HOME -> packageName in onHome
    Place.GAMES -> category == ApplicationInfo.CATEGORY_GAME
    Place.MEDIA -> category == ApplicationInfo.CATEGORY_AUDIO || category == ApplicationInfo.CATEGORY_VIDEO || category == ApplicationInfo.CATEGORY_IMAGE
    Place.SOCIAL -> category == ApplicationInfo.CATEGORY_SOCIAL
    Place.WORK -> category == ApplicationInfo.CATEGORY_PRODUCTIVITY
    Place.SYSTEM -> isSystem
    Place.OTHER -> !isSystem && category !in setOf(
        ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_PRODUCTIVITY,
    )
}

/**
 * A desktop-style view of every app on the device: top panel, a file-manager window with a
 * sidebar of "folders", a filterable list and a shell-style prompt. Tap an app to launch it
 * directly, or use the "+ home" button to put it on the home screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopScreen(
    apps: List<LaunchableApp>,
    onHome: Set<String>,
    settings: Settings,
    wallpaper: ImageBitmap?,
    onLaunch: (LaunchableApp) -> Unit,
    onToggleHome: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    onLauncherSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var place by remember { mutableStateOf(Place.ALL) }
    var query by remember { mutableStateOf("") }
    val listed = remember(apps, onHome, place, query) {
        val base = apps.filter { it.isIn(place, onHome) }
        val ordered = if (place == Place.NEW) base.sortedByDescending { it.installTime }.take(20) else base
        if (query.isBlank()) ordered
        else ordered.filter { it.label.contains(query.trim(), true) || it.packageName.contains(query.trim(), true) }
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        BubbleBackground(
            top = settings.theme.top, bottom = settings.theme.bottom, particles = settings.particles,
            wallpaper = wallpaper, particleCount = settings.particleCount, dim = settings.dim,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Column(Modifier.fillMaxSize()) {
            TopPanel(settings.use24h, onClose)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .background(Window, RoundedCornerShape(10.dp)),
            ) {
                // Window title bar with the three traffic-light buttons.
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF2A2A3C), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(12.dp).background(Color(0xFFFF5F56), CircleShape).clickable(onClick = onClose))
                    Box(Modifier.size(12.dp).background(Color(0xFFFFBD2E), CircleShape))
                    Box(Modifier.size(12.dp).background(Color(0xFF27C93F), CircleShape))
                    Text("Applications — ${place.path}", Modifier.padding(start = 8.dp), color = Color.White, fontFamily = Mono, fontSize = 13.sp)
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // Sidebar
                    Column(Modifier.width(190.dp).fillMaxHeight().background(Color(0x33000000)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("PLACES", color = Muted, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                        Place.entries.forEach { p ->
                            Text(
                                p.label,
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (p == place) Color(0x55FFFFFF) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { place = p }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                color = Color.White, fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("SYSTEM", color = Muted, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                        SideAction("Android Settings") { open(context, AndroidSettings.ACTION_SETTINGS) }
                        SideAction("Wi-Fi") { open(context, AndroidSettings.ACTION_WIFI_SETTINGS) }
                        SideAction("Launcher Settings", onLauncherSettings)
                    }
                    // Main pane
                    Column(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("~${place.path}", color = Accent, fontFamily = Mono, fontSize = 13.sp)
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontFamily = Mono, fontSize = 14.sp),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    Box(Modifier.background(Color(0xFF2A2A3C), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        if (query.isEmpty()) Text("grep ...", color = Muted, fontFamily = Mono, fontSize = 14.sp)
                                        inner()
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            items(listed, key = { it.packageName }) { app ->
                                AppRow(app, app.packageName in onHome, onLaunch, onToggleHome, onLongPress)
                            }
                        }
                        // Shell-style prompt with a summary of the current folder.
                        Text(
                            "user@qita:~${place.path}$ ls | wc -l  # ${listed.size} apps, ${apps.count { it.packageName in onHome }} on home",
                            Modifier.padding(top = 8.dp), color = Accent, fontFamily = Mono, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: LaunchableApp,
    onHome: Boolean,
    onLaunch: (LaunchableApp) -> Unit,
    onToggleHome: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(app.icon, app.label, Modifier.size(34.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${app.packageName}  ${app.version}",
                color = Muted, fontFamily = Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (onHome) "✓ on home" else "+ home",
            Modifier
                .background(if (onHome) Color(0x5527C93F) else Color(0x33FFFFFF), RoundedCornerShape(50))
                .clickable { onToggleHome(app) }
                .padding(horizontal = 12.dp, vertical = 5.dp),
            color = Color.White, fontFamily = Mono, fontSize = 12.sp,
        )
    }
}

@Composable
private fun SideAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        color = Color(0xFF7FDBFF), fontSize = 13.sp,
    )
}

/** Thin dark bar at the top, like a desktop panel: menu name, clock, close. */
@Composable
private fun TopPanel(use24h: Boolean, onClose: () -> Unit) {
    val fmt = if (use24h) "EEE MMM d  HH:mm" else "EEE MMM d  h:mm a"
    Row(
        Modifier.fillMaxWidth().background(Panel).statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Applications", color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(SimpleDateFormat(fmt, Locale.getDefault()).format(Date()), color = Color.White, fontFamily = Mono, fontSize = 13.sp)
        Text(
            "✕ close",
            Modifier.weight(1f).clickable(onClick = onClose),
            color = Color.White, fontFamily = Mono, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

private fun open(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
