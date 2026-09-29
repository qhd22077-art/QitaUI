package com.qita.ui.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.qita.ui.SettingsStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.AppRepository
import com.qita.ui.LaunchableApp

/** Vita home pages hold 10 bubbles, laid out in staggered rows of 4, 3 and 3. */
private val ROW_SIZES = listOf(4, 3, 3)
private val PAGE_SIZE = ROW_SIZES.sum()

/** Vita-style home: swipeable pages of bubbles; tap one to open its full-screen LiveArea page. */
@Composable
fun HomeScreen(homePresses: Int = 0) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var wallpaper by remember { mutableStateOf(store.loadWallpaper()) }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<LaunchableApp?>(null) }
    var lastSelected by remember { mutableStateOf<LaunchableApp?>(null) }
    var menuFor by remember { mutableStateOf<LaunchableApp?>(null) }
    var showSettings by remember { mutableStateOf(false) }

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

    val shown = remember(apps, settings.sortNewest) {
        if (settings.sortNewest) apps.sortedByDescending { it.installTime } else apps
    }
    val pageCount = maxOf(1, (shown.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val pagerState = rememberPagerState { pageCount }

    // Pressing Home while the launcher is open closes everything and returns to the first page.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) {
            selected = null; showSettings = false; menuFor = null
            pagerState.animateScrollToPage(0)
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && selected != null) { selected = null }

    Box(Modifier.fillMaxSize()) {
        BubbleBackground(top = settings.theme.top, bottom = settings.theme.bottom, particles = settings.particles, wallpaper = wallpaper)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, onSettings = { showSettings = true })
            BubblePager(
                shown, pagerState, settings.bubbleScale, Modifier.weight(1f),
                onSelect = { selected = it },
                onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menuFor = it },
            )
        }
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            // Fall back to the last app so the exit animation still has content to fade out.
            (selected ?: lastSelected)?.let {
                LiveAreaPage(it, settings.theme, settings.particles, wallpaper, onClose = { selected = null })
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
                onClose = { showSettings = false },
            )
        }
    }

    menuFor?.let { app ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(app.label) },
            text = { Text(app.packageName) },
            confirmButton = {
                TextButton(onClick = { menuFor = null; AppRepository.showInfo(context, app) }) { Text("App info") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { menuFor = null; AppRepository.uninstall(context, app) }) { Text("Uninstall") }
                    TextButton(onClick = { menuFor = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun BubblePager(
    apps: List<LaunchableApp>,
    pagerState: PagerState,
    scale: Float,
    modifier: Modifier,
    onSelect: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
) {
    val pages = apps.chunked(PAGE_SIZE)
    if (pages.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No apps found", color = Color.White, fontSize = 18.sp)
        }
        return
    }
    Row(modifier.fillMaxSize()) {
        // Page dots run down the left edge, like the Vita.
        PageDots(pages.size, pagerState.currentPage, Modifier.padding(start = 16.dp).align(Alignment.CenterVertically))
        VerticalPagerPlaceholder()
        HorizontalPager(pagerState, Modifier.weight(1f)) { index ->
            val pageApps = pages.getOrElse(index) { emptyList() }
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                // Largest bubble whose row of four cells (bubble + 44dp) and three rows (bubble + 36dp) still fit.
                val fit = minOf(maxWidth / ROW_SIZES[0] - 44.dp, maxHeight / ROW_SIZES.size - 36.dp)
                val bubble = (fit * 0.9f * scale).coerceAtLeast(40.dp)
                val cell = bubble + 20.dp
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    var offset = 0
                    ROW_SIZES.forEach { count ->
                        val row = pageApps.drop(offset).take(count)
                        offset += count
                        // Rows with fewer bubbles are centred, which produces the staggered look.
                        Row(Modifier.fillMaxWidth().height(cell + 16.dp), horizontalArrangement = Arrangement.Center) {
                            row.forEach { app ->
                                Bubble(app, bubble, onClick = { onSelect(app) }, onLongClick = { onLongPress(app) }, modifier = Modifier.padding(horizontal = 12.dp).width(cell))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalPagerPlaceholder() = Spacer(Modifier.width(4.dp))

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
