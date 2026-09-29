package com.qita.ui.ui

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qita.ui.AppRepository
import com.qita.ui.Command
import com.qita.ui.CrashReporter
import com.qita.ui.Controller
import com.qita.ui.CursorLayer
import com.qita.ui.LAYOUTS
import com.qita.ui.LaunchableApp
import com.qita.ui.Settings
import com.qita.ui.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/** Vita-style home: swipeable pages of bubbles; tap one to open its floating LiveArea card. */
@Composable
fun HomeScreen(homePresses: Int = 0) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var wallpaper by remember { mutableStateOf(store.loadWallpaper()) }
    var home by remember { mutableStateOf(store.loadHome()) }
    var counts by remember { mutableStateOf(store.loadLaunchCounts()) }
    var showDesktop by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(!store.tutorialSeen()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var lastToast by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<LaunchableApp?>(null) }
    var lastSelected by remember { mutableStateOf<LaunchableApp?>(null) }
    var menuFor by remember { mutableStateOf<LaunchableApp?>(null) }
    var moveOriginal by remember { mutableStateOf<List<String>?>(null) }
    var crashTrace by remember { mutableStateOf(CrashReporter.read(context)) }

    // Push the saved controller settings into the shared state before the first frame.
    remember(store) {
        Controller.cursorMode = settings.cursorMode
        Controller.speed = settings.cursorSpeed
        Controller.swapAB = settings.swapAB
        0
    }

    // Drag-to-rearrange state. Positions are in root coordinates.
    var dragApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragTravel by remember { mutableStateOf(0f) }
    var rootWidth by remember { mutableStateOf(0) }
    val rects = remember { mutableMapOf<String, Rect>() }

    val rowSizes = LAYOUTS[settings.layoutIndex.coerceIn(LAYOUTS.indices)].second
    val pageSize = rowSizes.sum()
    // Only apps the user has added appear on the home screen; the desktop lists everything.
    val shown = remember(apps, home, settings.sortNewest) {
        val byPackage = apps.associateBy { it.packageName }
        val list = home.mapNotNull { byPackage[it] }
        if (settings.sortNewest) list.sortedByDescending { it.installTime } else list
    }
    val homeSet = remember(home) { home.toSet() }
    val pageCount = maxOf(1, (shown.size + pageSize - 1) / pageSize)
    val pagerState = rememberPagerState { pageCount }

    val menuOpen = menuFor != null
    val anyOverlay = showDesktop || showSettings || showSearch || showTutorial || selected != null || menuOpen || crashTrace != null

    fun addToHome(app: LaunchableApp) {
        if (app.packageName !in home) { home = home + app.packageName; store.saveHome(home) }
        toast = "Added ${app.label} to home"
    }
    fun removeFromHome(app: LaunchableApp) {
        home = home - app.packageName; store.saveHome(home)
        toast = "Removed ${app.label} from home"
    }
    fun launchApp(app: LaunchableApp) {
        AppRepository.launch(context, app)
        store.recordLaunch(app.packageName)
        counts = store.loadLaunchCounts()
    }
    fun closeApp(app: LaunchableApp) {
        AppRepository.close(context, app)
        toast = "Closed ${app.label}"
    }

    // --- Controller move mode: pick a bubble up and carry it with the D-pad. ---
    fun startMove(app: LaunchableApp) {
        if (app.packageName !in homeSet) return
        moveOriginal = home
        if (settings.sortNewest) {
            // A manual order replaces the "newest first" sort.
            home = shown.map { it.packageName }; store.saveHome(home)
            settings = settings.copy(sortNewest = false); store.save(settings)
        }
        Controller.movingPackage = app.packageName
    }
    fun moveStep(dx: Int, dy: Int) {
        val pkg = Controller.movingPackage ?: return
        val list = home.toMutableList()
        val from = list.indexOf(pkg)
        if (from < 0) return
        val to = (from + dx + dy * rowSizes.max()).coerceIn(0, list.size - 1)
        if (to == from) return
        list.add(to, list.removeAt(from))
        home = list; store.saveHome(home)
    }
    fun endMove(confirm: Boolean) {
        val pkg = Controller.movingPackage ?: return
        if (!confirm) moveOriginal?.let { home = it; store.saveHome(it) }
        moveOriginal = null
        Controller.movingPackage = null
        PadNav.select("home:$pkg")
    }
    // Keep the carried bubble's page on screen.
    val movingPage = Controller.movingPackage?.let { home.indexOf(it) / pageSize }
    LaunchedEffect(movingPage) { movingPage?.let { pagerState.animateScrollToPage(it.coerceIn(0, pageCount - 1)) } }

    // Gamepad navigation plumbing.
    SideEffect {
        PadNav.scope = scope
        PadNav.onMoved = {
            if (settings.haptics && Controller.padActive) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            if (Controller.padActive) PadNav.ensure()
        }
    }
    // Follow the highlight to the page its bubble is on.
    val currentKey = PadNav.current
    LaunchedEffect(currentKey) {
        val k = currentKey
        if (k is String && k.startsWith("home:")) {
            val idx = shown.indexOfFirst { "home:${it.packageName}" == k }
            if (idx >= 0 && pagerState.currentPage != idx / pageSize) pagerState.animateScrollToPage(idx / pageSize)
        }
    }

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
    // Optionally add apps that were installed since the launcher last looked.
    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) {
            val known = store.loadKnown()
            if (known.isNotEmpty() && settings.autoAdd) {
                val fresh = apps.filter { it.packageName !in known && it.packageName !in home }.map { it.packageName }
                if (fresh.isNotEmpty()) {
                    home = home + fresh
                    store.saveHome(home)
                    toast = "Added ${fresh.size} new app${if (fresh.size == 1) "" else "s"} to home"
                }
            }
            store.saveKnown(known + apps.map { it.packageName })
        }
    }
    LaunchedEffect(selected) { if (selected != null) lastSelected = selected }
    LaunchedEffect(toast) {
        val t = toast
        if (t != null) { lastToast = t; delay(1900); toast = null }
    }

    // Select on the controller toggles cursor mode: persist it and confirm with a short message.
    LaunchedEffect(Controller.cursorMode) {
        val on = Controller.cursorMode
        if (settings.cursorMode != on) {
            settings = settings.copy(cursorMode = on)
            store.save(settings)
            toast = if (on) "Cursor mode on – Select to turn off" else "Cursor mode off"
        }
    }

    // Gamepad buttons (see MainActivity). The handler is re-created every recomposition and read through
    // rememberUpdatedState, so the long-lived collector below always sees current state and functions.
    val commandHandler = rememberUpdatedState<(Command) -> Unit> { cmd ->
        when (cmd) {
            is Command.Page ->
                if (!anyOverlay) scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + cmd.delta).coerceIn(0, pageCount - 1)) }
            Command.Desktop -> if (!menuOpen && !showTutorial) { showSettings = false; showSearch = false; selected = null; showDesktop = !showDesktop }
            Command.Search -> if (!menuOpen && !showTutorial) { showDesktop = false; showSettings = false; selected = null; showSearch = !showSearch }
            Command.Settings -> if (!menuOpen && !showTutorial) showSettings = !showSettings
            Command.Options ->
                if (!showSettings && !showSearch && !menuOpen && !showTutorial) (selected ?: PadNav.currentApp())?.let { menuFor = it }
            Command.Toggle -> when {
                showSettings || showSearch || menuOpen || showTutorial || selected != null -> {}
                showDesktop -> PadNav.currentApp()?.let { if (it.packageName in homeSet) removeFromHome(it) else addToHome(it) }
                else -> PadNav.currentApp()?.let { startMove(it) }
            }
            is Command.MoveStep -> moveStep(cmd.dx, cmd.dy)
            is Command.MoveEnd -> endMove(cmd.confirm)
        }
    }
    LaunchedEffect(Unit) { Controller.commands.collect { commandHandler.value(it) } }

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
            selected = null; showSettings = false; showSearch = false; showDesktop = false; menuFor = null; dragApp = null
            endMove(true)
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
        home = moved.map { it.packageName }
        store.saveHome(home)
        // A manual order replaces the "newest first" sort.
        if (settings.sortNewest) {
            settings = settings.copy(sortNewest = false)
            store.save(settings)
        }
    }

    BackHandler(enabled = crashTrace != null) { CrashReporter.clear(context); crashTrace = null }
    BackHandler(enabled = menuOpen && crashTrace == null) { menuFor = null }
    BackHandler(enabled = showTutorial && !menuOpen) { showTutorial = false; store.setTutorialSeen() }
    BackHandler(enabled = showSettings && !menuOpen && !showTutorial) { showSettings = false }
    BackHandler(enabled = showSearch && !showSettings && !menuOpen && !showTutorial) { showSearch = false }
    BackHandler(enabled = showDesktop && !showSearch && !showSettings && !menuOpen && !showTutorial) { showDesktop = false }
    BackHandler(enabled = selected != null && !showDesktop && !showSearch && !showSettings && !menuOpen && !showTutorial) { selected = null }

    // Depth: the home screen recedes a little while something is open on top of it.
    val depth by animateFloatAsState(if (anyOverlay) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 300f), label = "depth")
    // Leave room for the button hints while the gamepad is in use.
    // A tween, never a spring: springs overshoot and padding must never go negative.
    val hintPad by animateDpAsState(if (Controller.padActive) 46.dp else 0.dp, tween(220), label = "hintPad")

    Box(Modifier.fillMaxSize().onSizeChanged { rootWidth = it.width; PadNav.viewport = Rect(0f, 0f, it.width.toFloat(), it.height.toFloat()) }) {
        BubbleBackground(
            top = settings.theme.top, bottom = settings.theme.bottom, particles = settings.particles,
            wallpaper = wallpaper, particleCount = settings.particleCount, dim = settings.dim,
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer {
                    val s = 1f - 0.04f * depth
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.35f * depth
                },
        ) {
            StatusBar(
                settings.use24h, settings.showBattery,
                onDesktop = { showDesktop = true }, onSearch = { showSearch = true }, onSettings = { showSettings = true },
            )
            BubblePager(
                apps = shown,
                pagerState = pagerState,
                scale = settings.bubbleScale,
                rowSizes = rowSizes,
                settings = settings,
                hiddenPackage = dragApp?.packageName,
                movingPackage = Controller.movingPackage,
                modifier = Modifier.weight(1f).padding(bottom = hintPad.coerceAtLeast(0.dp)),
                onSelect = { selected = it },
                onOpenDesktop = { showDesktop = true },
                onSwipeUp = { showDesktop = true },
                onSwipeDown = { showSearch = true },
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
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.82f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f)),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200)),
        ) {
            // Fall back to the last app so the exit animation still has content to fade out.
            (selected ?: lastSelected)?.let { app ->
                CompositionLocalProvider(LocalPadLayer provides 1) {
                    LiveAreaPage(
                        app, settings, wallpaper,
                        launches = counts[app.packageName] ?: 0,
                        onLaunch = { launchApp(app) },
                        onCloseApp = { closeApp(app) },
                        onClose = { selected = null },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showDesktop,
            enter = fadeIn(tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(200)),
        ) {
            CompositionLocalProvider(LocalPadLayer provides 1) {
                DesktopScreen(
                    apps = apps,
                    homeApps = shown,
                    onHome = homeSet,
                    counts = counts,
                    settings = settings,
                    wallpaper = wallpaper,
                    onLaunch = { launchApp(it) },
                    onToggleHome = { if (it.packageName in homeSet) removeFromHome(it) else addToHome(it) },
                    onLongPress = { menuFor = it },
                    onLauncherSettings = { showSettings = true },
                    onClose = { showDesktop = false },
                )
            }
        }
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(220)) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { -it / 5 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 5 },
        ) {
            CompositionLocalProvider(LocalPadLayer provides 2) {
                SearchOverlay(
                    apps = apps, settings = settings, wallpaper = wallpaper,
                    onPick = { showSearch = false; selected = it },
                    onClose = { showSearch = false },
                )
            }
        }
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(tween(220)) + slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 4 },
            exit = fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 4 },
        ) {
            CompositionLocalProvider(LocalPadLayer provides 3) {
                SettingsPage(
                    settings = settings,
                    hasWallpaper = wallpaper != null,
                    wallpaper = wallpaper,
                    onChange = {
                        settings = it; store.save(it)
                        Controller.cursorMode = it.cursorMode
                        Controller.speed = it.cursorSpeed
                        Controller.swapAB = it.swapAB
                    },
                    onWallpaper = { uri -> store.saveWallpaper(uri)?.let { wallpaper = it } },
                    onClearWallpaper = { store.clearWallpaper(); wallpaper = null },
                    onClearHome = { home = emptyList(); store.saveHome(home) },
                    onShowTutorial = { showSettings = false; showTutorial = true },
                    onClose = { showSettings = false },
                )
            }
        }

        menuFor?.let { app ->
            val onHome = app.packageName in homeSet
            ContextMenu(
                title = app.label,
                subtitle = app.packageName,
                items = listOf(
                    MenuItem("Open") { menuFor = null; launchApp(app) },
                    MenuItem(if (onHome) "Remove from home" else "Add to home") {
                        menuFor = null
                        if (onHome) removeFromHome(app) else addToHome(app)
                    },
                    MenuItem("Close app") { menuFor = null; closeApp(app) },
                    MenuItem("App info") { menuFor = null; AppRepository.showInfo(context, app) },
                    MenuItem("Uninstall") { menuFor = null; AppRepository.uninstall(context, app) },
                    MenuItem("Cancel") { menuFor = null },
                ),
                onDismiss = { menuFor = null },
            )
        }

        AnimatedVisibility(
            visible = showTutorial,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.95f, animationSpec = tween(200)),
        ) {
            CompositionLocalProvider(LocalPadLayer provides 5) {
                Onboarding(settings.psLabels, onDone = { showTutorial = false; store.setTutorialSeen() })
            }
        }

        AnimatedVisibility(visible = crashTrace != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
            CompositionLocalProvider(LocalPadLayer provides 6) {
                CrashReport(
                    trace = crashTrace.orEmpty(),
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("QitaUI crash", crashTrace.orEmpty()))
                        toast = "Copied"
                    },
                    onDismiss = { CrashReporter.clear(context); crashTrace = null },
                )
            }
        }

        // The gamepad highlight (or the cursor's hover ring) glides between items above everything.
        PadRing()

        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 62.dp),
            enter = fadeIn(tween(160)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
        ) {
            Text(
                lastToast,
                Modifier.background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp),
                color = Color.White, fontSize = 13.sp,
            )
        }
        // Button hints for whatever is on screen, while the gamepad is in use.
        val hints = when {
            showTutorial -> listOf("A" to "Got it")
            menuOpen -> listOf("D-pad" to "Move", "A" to "Choose", "B" to "Cancel")
            Controller.movingPackage != null -> listOf("D-pad" to "Move", "A" to "Drop", "B" to "Cancel")
            showSettings -> listOf("D-pad" to "Move / adjust", "A" to "Toggle", "B" to "Done")
            showSearch -> listOf("D-pad" to "Move", "A" to "Open", "B" to "Close")
            showDesktop -> listOf("A" to "Launch", "X" to "Options", "Y" to "Add / remove", "L1" to "Folder", "L2" to "Close", "B" to "Back")
            selected != null -> listOf("A" to "Start", "X" to "Options", "B" to "Back")
            else -> listOf("A" to "Open", "X" to "Options", "Y" to "Move", "L1" to "Prev", "R1" to "Next", "L2" to "Desktop", "R2" to "Search", "START" to "Settings")
        }
        AnimatedVisibility(
            visible = Controller.padActive,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 450f)) { it },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it },
        ) {
            AnimatedContent(
                targetState = hints,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "hints",
            ) { h -> HintBar(h, settings.psLabels) }
        }
        if (settings.debugInput) {
            Text(
                Controller.lastInput,
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 44.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(0xFF8AE234), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            )
        }
        CursorLayer()
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
    movingPackage: String?,
    modifier: Modifier,
    onSelect: (LaunchableApp) -> Unit,
    onOpenDesktop: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your home screen is empty", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Add the apps you want here from the desktop.", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                Text(
                    "Open desktop",
                    Modifier
                        .padClickable("home:desktop", corner = null, onClick = onOpenDesktop)
                        .background(Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 28.dp, vertical = 10.dp),
                    color = Color(0xFF0B3D91), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
            }
        }
        return
    }
    Row(
        modifier
            .fillMaxSize()
            // Swipe up for the desktop, down for search (horizontal swipes still change page).
            .pointerInput(Unit) {
                var total = 0f
                val threshold = 90.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = { if (total > threshold) onSwipeDown() else if (total < -threshold) onSwipeUp() },
                    onDragCancel = { total = 0f },
                )
            },
    ) {
        // Page dots run down the left edge, like the Vita.
        if (settings.showDots) PageDots(pages.size, pagerState.currentPage, Modifier.padding(start = 16.dp).align(Alignment.CenterVertically))
        Spacer(Modifier.width(4.dp))
        // Keep every page composed so a bubble being dragged is not disposed when the page flips away.
        HorizontalPager(pagerState, Modifier.weight(1f), beyondViewportPageCount = pages.size) { index ->
            val pageApps = pages.getOrElse(index) { emptyList() }
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    // Pages shrink and fade slightly as they slide away.
                    .graphicsLayer {
                        val distance = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                        val s = 1f - 0.1f * distance
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.5f * distance
                    },
            ) {
                // Largest bubble whose row of four cells (bubble + 44dp) and three rows (bubble + 36dp) still fit.
                val fit = minOf(maxWidth / rowSizes.max() - 44.dp, maxHeight / rowSizes.size - 36.dp)
                val bubble = (fit * 0.9f * scale).coerceAtLeast(40.dp)
                val cell = bubble + 20.dp
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    var offset = 0
                    rowSizes.forEach { count ->
                        val start = offset
                        val row = pageApps.drop(offset).take(count)
                        offset += count
                        // Rows with fewer bubbles are centred, which produces the staggered look.
                        Row(Modifier.fillMaxWidth().height(cell + 16.dp), horizontalArrangement = Arrangement.Center) {
                            row.forEachIndexed { i, app ->
                                androidx.compose.runtime.key(app.packageName) {
                                    DisposableEffect(app.packageName) { onDispose { onDisposed(app.packageName) } }
                                    Bubble(
                                        app, bubble,
                                        onClick = { onSelect(app) },
                                        modifier = Modifier.padding(horizontal = 12.dp).width(cell),
                                        padKey = "home:${app.packageName}",
                                        hidden = app.packageName == hiddenPackage,
                                        shape = if (settings.roundedBubbles) RoundedCornerShape(28) else CircleShape,
                                        showLabel = settings.showLabels,
                                        moving = app.packageName == movingPackage,
                                        enterDelay = (start + i) * 35,
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
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val size by animateFloatAsState(if (i == current) 10f else 7f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "dot")
            val alpha by animateFloatAsState(if (i == current) 1f else 0.5f, tween(200), label = "dotAlpha")
            Box(Modifier.size(size.dp).background(Color.White.copy(alpha = alpha), CircleShape))
        }
    }
}
