package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.qita.ui.Command
import com.qita.ui.Controller
import com.qita.ui.LaunchableApp
import com.qita.ui.Settings
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Ubuntu / GNOME (Yaru) palette.
private val Aubergine = Color(0xFF2C001E)
private val Plum = Color(0xFF5E2750)
private val Orange = Color(0xFFE95420)
private val TopBarBg = Color(0xFF000000)
private val DockBg = Color(0xCC1A1A1A)
private val WinBody = Color(0xFF2B2B2B)
private val WinHeader = Color(0xFF3C3C3C)
private val Sidebar = Color(0xFF323232)
private val Text1 = Color(0xFFEEEEEE)
private val Muted = Color(0xFFAEA79F)
private val Mono = FontFamily.Monospace

/** Folders in the sidebar, named like paths in a Linux file manager. */
private enum class Place(val label: String, val path: String) {
    ALL("All Apps", "apps"),
    HOME("On Home", "home"),
    FREQUENT("Frequently Used", "frequent"),
    NEW("Recently Installed", "recent"),
    GAMES("Games", "apps/games"),
    MEDIA("Media", "apps/media"),
    SOCIAL("Social", "apps/social"),
    WORK("Productivity", "apps/work"),
    SYSTEM("System", "system"),
    OTHER("Other", "apps/other"),
}

private fun LaunchableApp.isIn(place: Place, onHome: Set<String>, counts: Map<String, Int>): Boolean = when (place) {
    Place.ALL, Place.NEW -> true
    Place.HOME -> packageName in onHome
    Place.FREQUENT -> (counts[packageName] ?: 0) > 0
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
 * An Ubuntu-style desktop over every app on the device: black top bar (Activities, clock, quick
 * settings), a left dock with the home-screen apps, desktop icons, and a draggable
 * "Applications" window with sidebar folders, sorting, search and a terminal prompt. Tap an app
 * to launch it; "+ Add to home" puts it on the home screen (and the dock).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopScreen(
    apps: List<LaunchableApp>,
    homeApps: List<LaunchableApp>,
    onHome: Set<String>,
    counts: Map<String, Int>,
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
    var sortNewest by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var winVisible by remember { mutableStateOf(true) }
    var maximized by remember { mutableStateOf(false) }
    var winOffset by remember { mutableStateOf(Offset.Zero) }
    val firstFocus = remember { FocusRequester() }

    val listed = remember(apps, onHome, counts, place, query, sortNewest) {
        val base = apps.filter { it.isIn(place, onHome, counts) }
        val ordered = when {
            place == Place.NEW -> base.sortedByDescending { it.installTime }.take(20)
            place == Place.FREQUENT -> base.sortedByDescending { counts[it.packageName] ?: 0 }.take(20)
            sortNewest -> base.sortedByDescending { it.installTime }
            else -> base
        }
        if (query.isBlank()) ordered
        else ordered.filter { it.label.contains(query.trim(), true) || it.packageName.contains(query.trim(), true) }
    }

    // Back closes the grid or quick menu first (registered after Home's handlers, so it wins).
    BackHandler(enabled = showGrid) { showGrid = false }
    BackHandler(enabled = showQuick) { showQuick = false }
    // L1/R1 flip through the folders.
    LaunchedEffect(Unit) {
        Controller.commands.collect { cmd ->
            if (cmd is Command.Page && !showGrid && !showQuick) {
                val n = Place.entries.size
                place = Place.entries[(place.ordinal + cmd.delta + n) % n]
            }
        }
    }
    // Gamepad users start with the highlight on the folder list.
    LaunchedEffect(Unit) {
        delay(300)
        if (Controller.padActive) runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        // Ubuntu aubergine wallpaper, unless the user picked their own image.
        if (wallpaper != null) {
            BubbleBackground(wallpaper = wallpaper, particles = false, dim = settings.dim)
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(Aubergine, Plum, Color(0xFFAE3C3C)), Offset.Zero, Offset(size.width, size.height)))
            }
        }
        // Desktop icons down the right-hand side, like Ubuntu's Home folder and friends.
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 18.dp).focusProperties { canFocus = !showGrid && !showQuick },
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DesktopIcon("⌂", "Home", Orange) { place = Place.HOME; winVisible = true; maximized = false }
            DesktopIcon("☰", "Apps", Color(0xFF77216F)) { showGrid = true }
            DesktopIcon("⚙", "Settings", Color(0xFF5E5E5E)) { onLauncherSettings() }
        }
        Column(Modifier.fillMaxSize().focusProperties { canFocus = !showGrid && !showQuick }) {
            TopBar(
                settings.use24h,
                onActivities = { showQuick = false; showGrid = !showGrid },
                onQuick = { showGrid = false; showQuick = !showQuick },
                onClose = onClose,
            )
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Dock(
                    homeApps, onLaunch, onLongPress,
                    onWindow = { winVisible = !winVisible },
                    onShowApps = { showQuick = false; showGrid = !showGrid },
                )
                Box(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                    if (winVisible) {
                        val sizeMod = if (maximized) Modifier.fillMaxSize()
                        else Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.94f).offset { IntOffset(winOffset.x.roundToInt(), winOffset.y.roundToInt()) }
                        Column(sizeMod.shadow(14.dp, RoundedCornerShape(10.dp)).background(WinBody, RoundedCornerShape(10.dp))) {
                            // Yaru-style header bar: drag it to move the window, controls on the right.
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(WinHeader, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                    .pointerInput(maximized) {
                                        if (!maximized) detectDragGestures(onDrag = { change, drag -> change.consume(); winOffset += drag })
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(Modifier.width(88.dp))
                                Text("Applications", Modifier.weight(1f), color = Text1, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                                Row(Modifier.width(88.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                                    WindowButton("–", Color(0xFF555555)) { winVisible = false }
                                    WindowButton(if (maximized) "❐" else "□", Color(0xFF555555)) { maximized = !maximized; winOffset = Offset.Zero }
                                    WindowButton("✕", Orange, onClose)
                                }
                            }
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                Column(Modifier.width(180.dp).fillMaxHeight().background(Sidebar).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Places", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                                    Place.entries.forEach { p ->
                                        Text(
                                            p.label,
                                            Modifier
                                                .fillMaxWidth()
                                                .then(if (p == Place.ALL) Modifier.focusRequester(firstFocus) else Modifier)
                                                .focusRing(RoundedCornerShape(6.dp))
                                                .background(if (p == place) Orange.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(6.dp))
                                                .clickable { place = p }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            color = Text1, fontSize = 13.sp,
                                            fontWeight = if (p == place) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text("System", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                                    SideAction("Settings") { open(context, AndroidSettings.ACTION_SETTINGS) }
                                    SideAction("Wi-Fi") { open(context, AndroidSettings.ACTION_WIFI_SETTINGS) }
                                    SideAction("Launcher settings", onLauncherSettings)
                                }
                                Column(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        BasicTextField(
                                            value = query,
                                            onValueChange = { query = it },
                                            singleLine = true,
                                            textStyle = TextStyle(color = Text1, fontSize = 14.sp),
                                            cursorBrush = SolidColor(Orange),
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                Box(Modifier.fillMaxWidth().background(Color(0xFF3C3C3C), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                                    if (query.isEmpty()) Text("Search in ~/${place.path}", color = Muted, fontSize = 14.sp)
                                                    inner()
                                                }
                                            },
                                        )
                                        SortChip("A–Z", !sortNewest) { sortNewest = false }
                                        SortChip("Newest", sortNewest) { sortNewest = true }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                                        items(listed, key = { it.packageName }) { app ->
                                            AppRow(app, app.packageName in onHome, counts[app.packageName] ?: 0, onLaunch, onToggleHome, onLongPress)
                                        }
                                    }
                                    // Terminal-style prompt in Ubuntu's colours.
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(SpanStyle(color = Color(0xFF8AE234), fontWeight = FontWeight.Bold)) { append("user@qita") }
                                            withStyle(SpanStyle(color = Text1)) { append(":") }
                                            withStyle(SpanStyle(color = Color(0xFF729FCF), fontWeight = FontWeight.Bold)) { append("~/${place.path}") }
                                            withStyle(SpanStyle(color = Text1)) { append("$ ls | wc -l   # ${listed.size} apps, ${apps.count { it.packageName in onHome }} on home") }
                                        },
                                        Modifier.padding(top = 8.dp), fontFamily = Mono, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showQuick) {
            QuickPanel(
                onDismiss = { showQuick = false },
                items = listOf(
                    "Wi-Fi" to { open(context, AndroidSettings.ACTION_WIFI_SETTINGS) },
                    "Bluetooth" to { open(context, AndroidSettings.ACTION_BLUETOOTH_SETTINGS) },
                    "Display" to { open(context, AndroidSettings.ACTION_DISPLAY_SETTINGS) },
                    "Sound" to { open(context, AndroidSettings.ACTION_SOUND_SETTINGS) },
                    "All settings" to { open(context, AndroidSettings.ACTION_SETTINGS) },
                    "Launcher settings" to onLauncherSettings,
                    "Close desktop" to onClose,
                ),
            )
        }
        if (showGrid) {
            AppGrid(apps, onLaunch = { showGrid = false; onLaunch(it) }, onLongPress, onClose = { showGrid = false })
        }
    }
}

/** GNOME-style "Show Applications" overlay: search field on top, grid of app icons. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGrid(
    apps: List<LaunchableApp>,
    onLaunch: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), true) }
    }
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        if (Controller.padActive) runCatching { firstItem.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Aubergine.copy(alpha = 0.94f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 56.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Text1, fontSize = 16.sp),
                cursorBrush = SolidColor(Orange),
                modifier = Modifier.width(360.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().background(Color(0x33FFFFFF), RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                        if (query.isEmpty()) Text("Type to search", color = Muted, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                gridItems(results, key = { it.packageName }) { app ->
                    Column(
                        Modifier
                            .then(if (app.packageName == results.first().packageName) Modifier.focusRequester(firstItem) else Modifier)
                            .focusOnRequest(app.packageName)
                            .focusRing(RoundedCornerShape(12.dp), app)
                            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(app.icon, app.label, Modifier.size(56.dp))
                        Text(app.label, Modifier.padding(top = 4.dp), color = Text1, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Drop-down from the top-right of the bar: quick access to Android's own settings screens. */
@Composable
private fun QuickPanel(onDismiss: () -> Unit, items: List<Pair<String, () -> Unit>>) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (Controller.padActive) runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 36.dp, end = 8.dp)
                .width(240.dp)
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .background(Color(0xFF2B2B2B), RoundedCornerShape(12.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEachIndexed { i, (label, action) ->
                Text(
                    label,
                    Modifier
                        .fillMaxWidth()
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .focusRing(RoundedCornerShape(8.dp))
                        .clickable { onDismiss(); action() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    color = Text1, fontSize = 14.sp,
                )
            }
        }
    }
}

/** Left dock like Ubuntu's: the Applications window, your home apps, and Show Applications (3x3 dots). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    homeApps: List<LaunchableApp>,
    onLaunch: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    onWindow: () -> Unit,
    onShowApps: () -> Unit,
) {
    Column(
        Modifier
            // Above the window so the name tooltips are not covered by it.
            .zIndex(1f)
            .width(64.dp)
            .fillMaxHeight()
            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
            .background(DockBg, RoundedCornerShape(14.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DockItem("Applications", null, onWindow) {
            Box(Modifier.size(38.dp).background(Orange, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("☰", color = Color.White, fontSize = 18.sp)
            }
        }
        LazyColumn(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            items(homeApps, key = { it.packageName }) { app ->
                DockItem(app.label, app, { onLaunch(app) }, { onLongPress(app) }) {
                    Image(app.icon, app.label, Modifier.size(40.dp))
                }
            }
        }
        DockItem("Show Applications", null, onShowApps) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) { Box(Modifier.size(6.dp).background(Color.White, CircleShape)) }
                    }
                }
            }
        }
    }
}

/** One dock slot. While highlighted it shows its name to the right, like Ubuntu's dock tooltip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    label: String,
    app: LaunchableApp?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.padding(vertical = 4.dp)) {
        Box(
            Modifier
                .size(48.dp)
                .then(if (app != null) Modifier.focusOnRequest(app.packageName) else Modifier)
                .focusRing(RoundedCornerShape(12.dp), app)
                .onFocusChanged { focused = it.hasFocus }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
        if (focused) {
            Text(
                label,
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 58.dp)
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .background(Color(0xE6000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White, fontSize = 12.sp, maxLines = 1,
            )
        }
    }
}

@Composable
private fun DesktopIcon(symbol: String, label: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .width(76.dp)
            .focusRing(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(46.dp).background(color, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(symbol, color = Color.White, fontSize = 24.sp)
        }
        Text(label, Modifier.padding(top = 4.dp), color = Color.White, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: LaunchableApp,
    onHome: Boolean,
    launches: Int,
    onLaunch: (LaunchableApp) -> Unit,
    onToggleHome: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusOnRequest(app.packageName)
            .focusRing(RoundedCornerShape(8.dp), app)
            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(app.icon, app.label, Modifier.size(34.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = Text1, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${app.packageName}  ${app.version}" + if (launches > 0) "  · opened $launches×" else "",
                color = Muted, fontFamily = Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (onHome) "✓ On home" else "+ Add to home",
            Modifier
                .focusRing(RoundedCornerShape(50), app)
                .background(if (onHome) Orange.copy(alpha = 0.55f) else Color(0x33FFFFFF), RoundedCornerShape(50))
                .clickable { onToggleHome(app) }
                .padding(horizontal = 12.dp, vertical = 5.dp),
            color = Color.White, fontSize = 12.sp,
        )
    }
}

@Composable
private fun WindowButton(symbol: String, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).focusRing(CircleShape).background(bg, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .focusRing(RoundedCornerShape(50))
            .background(if (selected) Orange else Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color.White, fontSize = 12.sp,
    )
}

@Composable
private fun SideAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.fillMaxWidth().focusRing(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        color = Color(0xFFF4A582), fontSize = 13.sp,
    )
}

/** Black GNOME top bar: Activities on the left, date and time centred, quick settings and close on the right. */
@Composable
private fun TopBar(use24h: Boolean, onActivities: () -> Unit, onQuick: () -> Unit, onClose: () -> Unit) {
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
    val fmt = if (use24h) "MMM d  HH:mm" else "MMM d  h:mm a"
    Row(
        Modifier.fillMaxWidth().background(TopBarBg).statusBarsPadding().padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Activities",
            Modifier.focusRing(RoundedCornerShape(50)).clickable(onClick = onActivities).padding(horizontal = 12.dp, vertical = 4.dp),
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        Text(SimpleDateFormat(fmt, Locale.getDefault()).format(now), Modifier.weight(1f), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
        // Status area: opens the quick settings drop-down, as on Ubuntu.
        Row(
            Modifier.focusRing(RoundedCornerShape(50)).clickable(onClick = onQuick).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status.wifi) Text("Wi-Fi", color = Color.White, fontSize = 12.sp)
            Text((if (status.charging) "⚡" else "") + "${status.battery}%", color = Color.White, fontSize = 12.sp)
            Text("▾", color = Color.White, fontSize = 12.sp)
        }
        Text(
            "✕",
            Modifier.focusRing(CircleShape).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color.White, fontSize = 14.sp,
        )
    }
}

private fun open(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
