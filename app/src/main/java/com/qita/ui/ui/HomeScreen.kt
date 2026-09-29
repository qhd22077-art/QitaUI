package com.qita.ui.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qita.ui.AppRepository
import com.qita.ui.LAYOUTS
import com.qita.ui.LaunchableApp
import com.qita.ui.Settings
import com.qita.ui.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Puts apps in the saved [order]; apps not in it (new installs) keep their loaded order at the end. */
private fun applyOrder(apps: List<LaunchableApp>, order: List<String>): List<LaunchableApp> {
    if (order.isEmpty()) return apps
    val byPackage = apps.associateBy { it.packageName }
    val ordered = order.mapNotNull { byPackage[it] }
    val known = ordered.map { it.packageName }.toSet()
    return ordered + apps.filter { it.packageName !in known }
}

/** Vita-style home: swipeable pages of bubbles; tap one to open its full-screen LiveArea page. */
@Composable
fun HomeScreen(homePresses: Int = 0) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var wallpaper by remember { mutableStateOf(store.loadWallpaper()) }
    var order by remember { mutableStateOf(store.loadOrder()) }
    var hidden by remember { mutableStateOf(store.loadHidden()) }
    var showSearch by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<LaunchableApp?>(null) }
    var lastSelected by remember { mutableStateOf<LaunchableApp?>(null) }
    var menuFor by remember { mutableStateOf<LaunchableApp?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Drag-to-rearrange state. Positions are in root coordinates.
    var dragApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragTravel by remember { mutableStateOf(0f) }
    var rootWidth by remember { mutableStateOf(0) }
    val rects = remember { mutableMapOf<String, Rect>() }

    // Loading icons is slow with many apps, so do it off the main thread.
    LaunchedEffect(reload) { apps = withContext(Dispatchers.Default) { AppRepository.load(context) } }
    // Reload whenever an app is installed, removed or updated.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { reload++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(selected) { if (selected != null) lastSelected = selected }

    val rowSizes = LAYOUTS[settings.layoutIndex.coerceIn(LAYOUTS.indices)].second
    val pageSize = rowSizes.sum()
    val visible = remember(apps, hidden) { apps.filter { it.packageName !in hidden } }
    val shown = remember(visible, order, settings.sortNewest) {
        if (settings.sortNewest) visible.sortedByDescending { it.installTime } else applyOrder(visible, order)
    }
    val pageCount = maxOf(1, (shown.size + pageSize - 1) / pageSize)
    val pagerState = rememberPagerState { pageCount }

    // Hovering a dragged bubble near the left or right edge flips pages.
    val edgePx = with(density) { 56.dp.toPx() }
    val edge = if (dragApp == null) 0 else when {
        dragPos.x < edgePx -> -1
        dragPos.x > rootWidth - edgePx -> 1
        else -> 0
    }
    LaunchedEffect(edge) {
        if (edge != 0) {
            while (true) {
                delay(700)
                pagerState.animateScrollToPage((pagerState.currentPage + edge).coerceIn(0, pageCount - 1))
            }
        }
    }

    // Pressing Home while the launcher is open closes everything and returns to the first page.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) {
            selected = null; showSettings = false; showSearch = false; menuFor = null; dragApp = null
            pagerState.animateScrollToPage(0)
        }
    }

    fun drop(app: LaunchableApp) {
        val list = shown
        val from = list.indexOfFirst { it.packageName == app.packageName }
        if (from < 0) return
        // Dropped on a bubble: take its place. Dropped on empty space: go to the end of the visible page.
        val target = list.firstOrNull { it.packageName != app.packageName && rects[it.packageName]?.contains(dragPos) == true }
        val to = if (target != null) list.indexOf(target)
        else minOf(pagerState.currentPage * pageSize + pageSize, list.size) - 1
        if (to == from) return
        val moved = list.toMutableList()
        val item = moved.removeAt(from)
        moved.add(to.coerceIn(0, moved.size), item)
        order = moved.map { it.packageName }
        store.saveOrder(order)
        // A manual order replaces the "newest first" sort.
        if (settings.sortNewest) {
            settings = settings.copy(sortNewest = false)
            store.save(settings)
        }
    }

    BackHandler(enabled = showSearch) { showSearch = false }
    BackHandler(enabled = !showSearch && showSettings) { showSettings = false }
    BackHandler(enabled = !showSearch && !showSettings && selected != null) { selected = null }

    Box(Modifier.fillMaxSize().onSizeChanged { rootWidth = it.width }) {
        BubbleBackground(
            top = settings.theme.top, bottom = settings.theme.bottom, particles = settings.particles,
            wallpaper = wallpaper, particleCount = settings.particleCount, dim = settings.dim,
        )
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, onSearch = { showSearch = true }, onSettings = { showSettings = true })
            BubblePager(
                apps = shown,
                pagerState = pagerState,
                scale = settings.bubbleScale,
                rowSizes = rowSizes,
                settings = settings,
                hiddenPackage = dragApp?.packageName,
                modifier = Modifier.weight(1f),
                onSelect = { selected = it },
                onDragStart = { app, local ->
                    dragApp = app
                    dragTravel = 0f
                    dragPos = (rects[app.packageName]?.topLeft ?: Offset.Zero) + local
                    if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { delta -> dragPos += delta; dragTravel += delta.getDistance() },
                onDragEnd = {
                    dragApp?.let { app ->
                        // No real movement means a plain long-press: show the app menu instead of rearranging.
                        if (dragTravel < 24f) menuFor = app else drop(app)
                    }
                    dragApp = null
                },
                onDragCancel = { dragApp = null },
                onPositioned = { pkg, rect -> rects[pkg] = rect },
                onDisposed = { pkg -> rects.remove(pkg) },
            )
        }
        // The bubble being dragged, following the finger.
        dragApp?.let { app ->
            val half = with(density) { 44.dp.toPx() }
            Bubble(
                app, 88.dp, onClick = {},
                shape = if (settings.roundedBubbles) RoundedCornerShape(28) else CircleShape,
                showLabel = settings.showLabels,
                modifier = Modifier
                    .width(108.dp)
                    .offset { IntOffset((dragPos.x - with(density) { 54.dp.toPx() }).roundToInt(), (dragPos.y - half).roundToInt()) }
                    .alpha(0.9f),
            )
        }
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            // Fall back to the last app so the exit animation still has content to fade out.
            (selected ?: lastSelected)?.let {
                LiveAreaPage(it, settings, wallpaper, onClose = { selected = null })
            }
        }
        AnimatedVisibility(visible = showSettings, enter = fadeIn(), exit = fadeOut()) {
            SettingsPage(
                settings = settings,
                hasWallpaper = wallpaper != null,
                wallpaper = wallpaper,
                onChange = { settings = it; store.save(it) },
                onWallpaper = { uri -> store.saveWallpaper(uri)?.let { wallpaper = it } },
                onClearWallpaper = { store.clearWallpaper(); wallpaper = null },
                onResetOrder = { order = emptyList(); store.saveOrder(order) },
                hiddenCount = hidden.count { h -> apps.any { it.packageName == h } },
                onUnhideAll = { hidden = emptySet(); store.saveHidden(hidden) },
                onClose = { showSettings = false },
            )
        }
        AnimatedVisibility(visible = showSearch, enter = fadeIn(), exit = fadeOut()) {
            SearchOverlay(
                apps = visible, settings = settings, wallpaper = wallpaper,
                onPick = { showSearch = false; selected = it },
                onClose = { showSearch = false },
            )
        }
    }

    menuFor?.let { app ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(app.label) },
            text = {
                Column {
                    Text(app.packageName, fontSize = 12.sp)
                    TextButton(onClick = { menuFor = null; AppRepository.showInfo(context, app) }) { Text("App info") }
                    TextButton(onClick = {
                        menuFor = null
                        hidden = hidden + app.packageName
                        store.saveHidden(hidden)
                    }) { Text("Hide from home") }
                    TextButton(onClick = { menuFor = null; AppRepository.uninstall(context, app) }) { Text("Uninstall") }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BubblePager(
    apps: List<LaunchableApp>,
    pagerState: PagerState,
    scale: Float,
    rowSizes: List<Int>,
    settings: Settings,
    hiddenPackage: String?,
    modifier: Modifier,
    onSelect: (LaunchableApp) -> Unit,
    onDragStart: (LaunchableApp, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onPositioned: (String, Rect) -> Unit,
    onDisposed: (String) -> Unit,
) {
    val pages = apps.chunked(rowSizes.sum())
    if (pages.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No apps found", color = Color.White, fontSize = 18.sp)
        }
        return
    }
    Row(modifier.fillMaxSize()) {
        // Page dots run down the left edge, like the Vita.
        if (settings.showDots) PageDots(pages.size, pagerState.currentPage, Modifier.padding(start = 16.dp).align(Alignment.CenterVertically))
        Spacer(Modifier.width(4.dp))
        // Keep every page composed so a bubble being dragged is not disposed when the page flips away.
        HorizontalPager(pagerState, Modifier.weight(1f), beyondViewportPageCount = pages.size) { index ->
            val pageApps = pages.getOrElse(index) { emptyList() }
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                // Largest bubble whose row of four cells (bubble + 44dp) and three rows (bubble + 36dp) still fit.
                val fit = minOf(maxWidth / rowSizes.max() - 44.dp, maxHeight / rowSizes.size - 36.dp)
                val bubble = (fit * 0.9f * scale).coerceAtLeast(40.dp)
                val cell = bubble + 20.dp
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    var offset = 0
                    rowSizes.forEach { count ->
                        val row = pageApps.drop(offset).take(count)
                        offset += count
                        // Rows with fewer bubbles are centred, which produces the staggered look.
                        Row(Modifier.fillMaxWidth().height(cell + 16.dp), horizontalArrangement = Arrangement.Center) {
                            row.forEach { app ->
                                DisposableEffect(app.packageName) { onDispose { onDisposed(app.packageName) } }
                                Bubble(
                                    app, bubble,
                                    onClick = { onSelect(app) },
                                    modifier = Modifier.padding(horizontal = 12.dp).width(cell),
                                    hidden = app.packageName == hiddenPackage,
                                    shape = if (settings.roundedBubbles) RoundedCornerShape(28) else CircleShape,
                                    showLabel = settings.showLabels,
                                    onDragStart = { onDragStart(app, it) },
                                    onDrag = onDrag,
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                    onPositioned = { onPositioned(app.packageName, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.size(if (i == current) 10.dp else 7.dp).background(
                    Color.White.copy(alpha = if (i == current) 1f else 0.5f), CircleShape,
                ),
            )
        }
    }
}
