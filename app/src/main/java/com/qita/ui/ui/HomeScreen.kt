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
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.pager.VerticalPager
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
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
import com.qita.ui.PAGE_PHOTO
import com.qita.ui.PageLayout
import com.qita.ui.THEMES
import com.qita.ui.Theme
import com.qita.ui.SYSTEM_APPS
import com.qita.ui.SYSTEM_IDS
import com.qita.ui.SystemAction
import com.qita.ui.LaunchableApp
import com.qita.ui.Settings
import com.qita.ui.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.floor
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
    var showQuickMenu by remember { mutableStateOf(false) }
    var showLock by remember { mutableStateOf(settings.lockScreen) }
    var editMode by remember { mutableStateOf(false) }
    var liveOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    var toast by remember { mutableStateOf<String?>(null) }
    var lastToast by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<LaunchableApp?>(null) }
    // Open LiveArea pages, most recent first (the Vita keeps up to six).
    var openPages by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var menuFor by remember { mutableStateOf<LaunchableApp?>(null) }
    var moveOriginal by remember { mutableStateOf<List<String>?>(null) }
    var crashTrace by remember { mutableStateOf(CrashReporter.read(context)) }
    // Each home page can have its own background: a theme, or its own photo. Pages not listed use the global one.
    var pageBg by remember { mutableStateOf(store.loadPageBg()) }
    var pageWallpapers by remember { mutableStateOf(store.loadPageWallpapers(pageBg)) }
    var showBackgrounds by remember { mutableStateOf(false) }
    var showIndex by remember { mutableStateOf(false) }

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
    var rootHeight by remember { mutableStateOf(0) }
    val rects = remember { mutableMapOf<String, Rect>() }

    val layout = LAYOUTS[settings.layoutIndex.coerceIn(LAYOUTS.indices)]
    val pageSize = layout.size
    // Only apps the user has added appear on the home screen; the desktop lists everything.
    val shown = remember(apps, home, settings.sortNewest) {
        val byPackage = (apps + SYSTEM_APPS).associateBy { it.packageName }
        val list = home.mapNotNull { byPackage[it] }
        // Newest first, with the built-in bubbles staying in front.
        if (settings.sortNewest) list.sortedWith(compareByDescending<LaunchableApp> { it.action != null }.thenByDescending { it.installTime }) else list
    }
    val homeSet = remember(home) { home.toSet() }
    val pageCount = maxOf(1, (shown.size + pageSize - 1) / pageSize)
    val pagerState = rememberPagerState { pageCount }
    // 0 -> 1 as the page edit view (zoomed out, framed, information bar gone) opens.
    val editAmt by animateFloatAsState(if (editMode) 1f else 0f, tween(VitaMotion.Long, easing = VitaMotion.Ease), label = "editAmt")
    fun themeOf(page: Int): Theme {
        val v = pageBg[page]
        return if (v != null && v >= 0) THEMES[v.coerceIn(THEMES.indices)] else settings.theme
    }
    // The page whose photo (if any) is shown: the nearest one while swiping.
    val bgPage by remember { derivedStateOf { (pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt().coerceAtLeast(0) } }

    val menuOpen = menuFor != null || showQuickMenu
    val anyOverlay = showLock || showDesktop || showSettings || showSearch || showTutorial || selected != null || menuOpen || crashTrace != null

    fun addToHome(app: LaunchableApp) {
        if (app.packageName !in home) { home = home + app.packageName; store.saveHome(home) }
        toast = "Added ${app.label} to home"
    }
    fun removeFromHome(app: LaunchableApp) {
        if (app.action != null) return
        home = home - app.packageName; store.saveHome(home)
        toast = "Removed ${app.label} from home"
    }
    fun launchApp(app: LaunchableApp) {
        when (app.action) {
            SystemAction.SETTINGS -> { selected = null; showSettings = true }
            SystemAction.DESKTOP -> { selected = null; showDesktop = true }
            SystemAction.STORE -> toast = "The Store is coming soon"
            null -> {
                AppRepository.launch(context, app)
                store.recordLaunch(app.packageName)
                counts = store.loadLaunchCounts()
            }
        }
    }
    fun closeApp(app: LaunchableApp) {
        if (app.action != null) return
        AppRepository.close(context, app)
        toast = "Closed ${app.label}"
    }
    /** Opens (or brings to the front) an app's LiveArea page. */
    fun openLiveArea(app: LaunchableApp, from: Offset? = null) {
        // The page grows out of the bubble that was tapped (and shrinks back into it).
        liveOrigin = if (from != null && rootWidth > 0 && rootHeight > 0) TransformOrigin(from.x / rootWidth, from.y / rootHeight) else TransformOrigin.Center
        openPages = (listOf(app) + openPages.filter { it.packageName != app.packageName }).take(6)
        selected = app
    }
    /** Peeled away: the app is closed and its page removed; the neighbouring page (or home) takes over. */
    fun closePage(app: LaunchableApp) {
        closeApp(app)
        val index = openPages.indexOfFirst { it.packageName == app.packageName }
        openPages = openPages.filter { it.packageName != app.packageName }
        selected = if (openPages.isEmpty()) null else openPages[index.coerceIn(0, openPages.size - 1)]
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
        val page = from / pageSize
        val local = layout.step(from % pageSize, dx, dy)
        // Off the page: horizontal steps continue in reading order, vertical steps jump a whole page.
        val to = when {
            local != null -> page * pageSize + local
            dx != 0 -> from + dx
            else -> from + dy * pageSize
        }.coerceIn(0, list.size - 1)
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
    // The lock screen comes back when the screen has been off.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { if (settings.lockScreen) showLock = true }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
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
        if (!showLock) when (cmd) {
            is Command.Page -> when {
                selected != null && !showIndex && !showDesktop && !showSearch && !showSettings && !menuOpen && !showTutorial -> {
                    val index = openPages.indexOfFirst { it.packageName == selected?.packageName } + cmd.delta
                    if (index < 0) selected = null else openPages.getOrNull(index)?.let { selected = it }
                }
                !anyOverlay -> scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + cmd.delta).coerceIn(0, pageCount - 1)) }
                else -> {}
            }
            Command.Desktop -> if (!menuOpen && !showTutorial) { showSettings = false; showSearch = false; selected = null; showDesktop = !showDesktop }
            Command.Search -> if (!menuOpen && !showTutorial) { showDesktop = false; showSettings = false; selected = null; showSearch = !showSearch }
            Command.Settings ->
                if (editMode) { if (!menuOpen && !showTutorial) showBackgrounds = !showBackgrounds }
                else if (!menuOpen && !showTutorial) showSettings = !showSettings
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
        dragPos.y < edgePx + with(density) { 28.dp.toPx() } -> -1
        dragPos.y > rootHeight - edgePx -> 1
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
        if (homePresses > 0 && selected != null && !showIndex && !showLock && !showDesktop && !showSearch && !showSettings) {
            // Home while a LiveArea is open shows the index of open pages; pressing it again goes home.
            showIndex = true
        } else if (homePresses > 0) {
            showIndex = false
            selected = null; showSettings = false; showSearch = false; showDesktop = false; menuFor = null; showQuickMenu = false; editMode = false; showBackgrounds = false; dragApp = null
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

    // Lowest priority: Back leaves edit mode only when nothing else is open.
    BackHandler(enabled = editMode) { editMode = false }
    BackHandler(enabled = showBackgrounds && !menuOpen) { showBackgrounds = false }
    BackHandler(enabled = crashTrace != null && !showLock) { CrashReporter.clear(context); crashTrace = null }
    BackHandler(enabled = menuOpen && crashTrace == null) { menuFor = null; showQuickMenu = false }
    BackHandler(enabled = showTutorial && !menuOpen) { showTutorial = false; store.setTutorialSeen() }
    BackHandler(enabled = showSettings && !menuOpen && !showTutorial) { showSettings = false }
    BackHandler(enabled = showSearch && !showSettings && !menuOpen && !showTutorial) { showSearch = false }
    BackHandler(enabled = showDesktop && !showSearch && !showSettings && !menuOpen && !showTutorial) { showDesktop = false }
    BackHandler(enabled = selected != null && !showDesktop && !showSearch && !showSettings && !menuOpen && !showTutorial) { selected = null }
    BackHandler(enabled = showIndex && !showLock) { showIndex = false }
    // Registered last so it wins: while locked, Back does nothing.
    BackHandler(enabled = showLock) { }

    // Depth: the home screen recedes a little while something is open on top of it.
    // While a LiveArea is open only the wallpaper shows behind it, as on the Vita.
    val liveOpen by animateFloatAsState(if (selected != null) 1f else 0f, tween(VitaMotion.Medium, easing = VitaMotion.Ease), label = "liveOpen")
    val depth by animateFloatAsState(if (anyOverlay) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 300f), label = "depth")
    // Leave room for the button hints while the gamepad is in use.
    // A tween, never a spring: springs overshoot and padding must never go negative.
    val hintPad by animateDpAsState(if (Controller.padActive) 46.dp else 0.dp, tween(220), label = "hintPad")

    // One looping clock drives the idle sway of every 3D bubble.
    val ballClock = rememberInfiniteTransition(label = "ballClock").animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "ballClockValue",
    )
    CompositionLocalProvider(LocalFullArt provides settings.fullArt, LocalBall3D provides settings.bubble3d, LocalBallClock provides ballClock) {
    Box(Modifier.fillMaxSize().onSizeChanged { rootWidth = it.width; rootHeight = it.height; PadNav.viewport = Rect(0f, 0f, it.width.toFloat(), it.height.toFloat()) }) {
        BubbleBackground(
            top = settings.theme.top, mid = settings.theme.mid, bottom = settings.theme.bottom, particles = settings.particles,
            wallpaper = when (pageBg[bgPage]) {
                null -> wallpaper
                PAGE_PHOTO -> pageWallpapers[bgPage]
                else -> null
            },
            particleCount = settings.particleCount, dim = settings.dim,
            scroll = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            // The sky blends from one page's theme into the next as you swipe.
            palette = {
                val pos = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceAtLeast(0f)
                val lo = floor(pos).toInt()
                val f = pos - lo
                val a = themeOf(lo)
                val b = themeOf(lo + 1)
                Triple(lerp(a.top, b.top, f), lerp(a.mid, b.mid, f), lerp(a.bottom, b.bottom, f))
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer {
                    val s = 1f - 0.04f * depth
                    scaleX = s
                    scaleY = s
                    alpha = (1f - 0.35f * depth) * (1f - liveOpen)
                },
        ) {
            // Room for the information bar, which is drawn once above everything so it never moves.
            Spacer(Modifier.height(28.dp))
            BubblePager(
                apps = shown,
                pagerState = pagerState,
                scale = settings.bubbleScale,
                layout = layout,
                settings = settings,
                hiddenPackage = dragApp?.packageName,
                movingPackage = Controller.movingPackage,
                modifier = Modifier.weight(1f).padding(bottom = hintPad.coerceAtLeast(0.dp)),
                editAmt = { editAmt },
                onLongPressAt = { p ->
                    if (!editMode && dragApp == null && rects.values.none { it.contains(p) }) {
                        editMode = true
                        if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onSelect = { app -> if (editMode) menuFor = app else openLiveArea(app, rects[app.packageName]?.center) },
                onOpenDesktop = { showDesktop = true },
                onOpenRecent = { openPages.firstOrNull()?.let { selected = it } },
                editing = editMode,
                onRemove = { removeFromHome(it) },
                onDragStart = { app, local ->
                    dragApp = app
                    dragTravel = 0f
                    dragPos = (rects[app.packageName]?.topLeft ?: Offset.Zero) + local
                    if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { delta -> dragPos += delta; dragTravel += delta.getDistance() },
                onDragEnd = {
                    dragApp?.let { app ->
                        // No real movement means a plain long-press: enter edit mode (wiggling bubbles).
                        if (dragTravel < 24f) { if (!editMode) editMode = true } else drop(app)
                    }
                    dragApp = null
                },
                onDragCancel = { dragApp = null },
                onPositioned = { pkg, rect -> rects[pkg] = rect },
                onDisposed = { pkg -> rects.remove(pkg) },
            )
        }
        // Page edit view: a back button bottom-left and the wallpaper button by the frame's bottom-right corner.
        AnimatedVisibility(
            visible = editMode && !showBackgrounds,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp + hintPad.coerceAtLeast(0.dp)),
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 450f)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(140)),
        ) {
            BackButton(onClick = { editMode = false }, key = "edit:back")
        }
        AnimatedVisibility(
            visible = editMode && !showBackgrounds,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 66.dp, bottom = 36.dp + hintPad.coerceAtLeast(0.dp)),
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 450f)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(140)),
        ) {
            WallpaperButton(onClick = { showBackgrounds = true })
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
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.12f, transformOrigin = liveOrigin, animationSpec = spring(dampingRatio = 0.82f, stiffness = 330f)),
            exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.12f, transformOrigin = liveOrigin, animationSpec = tween(280)),
        ) {
            CompositionLocalProvider(LocalPadLayer provides 1) {
                LiveAreaHost(
                    pages = openPages,
                    current = openPages.indexOfFirst { it.packageName == selected?.packageName },
                    settings = settings,
                    counts = counts,
                    // -1 means the pager came to rest on the home screen.
                    onSettle = { index -> if (index < 0) selected = null else openPages.getOrNull(index)?.let { selected = it } },
                    onLaunch = { launchApp(it) },
                    onClosePage = { closePage(it) },
                    onInfo = { if (it.action == null) AppRepository.showInfo(context, it) },
                )
            }
        }
        AnimatedVisibility(
            visible = showIndex,
            enter = fadeIn(tween(VitaMotion.Medium)) + scaleIn(initialScale = 0.94f, animationSpec = VitaMotion.settle()),
            exit = fadeOut(tween(VitaMotion.Short)),
        ) {
            CompositionLocalProvider(LocalPadLayer provides 2) {
                IndexScreen(
                    pages = openPages,
                    current = openPages.indexOfFirst { it.packageName == selected?.packageName },
                    onPick = { i -> showIndex = false; selected = if (i < 0) null else openPages.getOrNull(i) },
                    onClose = { app -> closePage(app); if (openPages.isEmpty()) showIndex = false },
                    onDismiss = { showIndex = false },
                )
            }
        }
        // The information bar and corner sphere sit above the home screen and LiveArea pages and never move
        // with them. In the page edit view they slide away.
        if (editAmt < 0.99f) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .graphicsLayer {
                        alpha = 1f - editAmt
                        translationY = -size.height * editAmt
                    },
            ) {
                StatusBar(
                    settings.use24h, settings.showBattery,
                    openApps = openPages,
                    current = openPages.indexOfFirst { it.packageName == selected?.packageName },
                    onHome = { selected = null },
                    onPick = { i -> openPages.getOrNull(i)?.let { selected = it } },
                )
            }
            CornerSphere(
                onClick = { showQuickMenu = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 24.dp, y = (-22).dp)
                    .graphicsLayer { alpha = 1f - editAmt },
            )
        }
        AnimatedVisibility(
            visible = showDesktop,
            enter = fadeIn(tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(200)),
        ) {
            CompositionLocalProvider(LocalPadLayer provides 1) {
                DesktopScreen(
                    apps = apps,
                    homeApps = shown.filter { it.action == null },
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
                    onPick = { showSearch = false; openLiveArea(it, null) },
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
                    onClearHome = { home = SYSTEM_IDS; store.saveHome(home) },
                    onShowTutorial = { showSettings = false; showTutorial = true },
                    onClose = { showSettings = false },
                )
            }
        }

        AnimatedVisibility(
            visible = showBackgrounds,
            enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 3 },
        ) {
            CompositionLocalProvider(LocalPadLayer provides 3) {
                BackgroundPicker(
                    page = pagerState.currentPage,
                    override = pageBg[pagerState.currentPage],
                    defaultThemeIndex = settings.themeIndex,
                    onTheme = { t ->
                        pageBg = pageBg + (pagerState.currentPage to t); store.savePageBg(pageBg)
                    },
                    onDefault = {
                        pageBg = pageBg - pagerState.currentPage; store.savePageBg(pageBg)
                    },
                    onPhoto = { uri ->
                        val page = pagerState.currentPage
                        store.saveWallpaper(uri, page)?.let {
                            pageWallpapers = pageWallpapers + (page to it)
                            pageBg = pageBg + (page to PAGE_PHOTO); store.savePageBg(pageBg)
                        }
                    },
                    onDone = { showBackgrounds = false },
                )
            }
        }

        if (showQuickMenu) {
            ContextMenu(
                title = "QitaUI",
                subtitle = "Launcher menu",
                items = listOf(
                    MenuItem("Edit home screen") { showQuickMenu = false; editMode = true },
                    MenuItem("Desktop") { showQuickMenu = false; showDesktop = true },
                    MenuItem("Search") { showQuickMenu = false; showSearch = true },
                    MenuItem("Settings") { showQuickMenu = false; showSettings = true },
                    MenuItem("Cancel") { showQuickMenu = false },
                ),
                onDismiss = { showQuickMenu = false },
            )
        }

        menuFor?.let { app ->
            val onHome = app.packageName in homeSet
            ContextMenu(
                title = app.label,
                subtitle = if (app.action != null) "Built in" else app.packageName,
                items = if (app.action != null) listOf(
                    MenuItem("Open") { menuFor = null; launchApp(app) },
                    MenuItem("Cancel") { menuFor = null },
                ) else listOf(
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

        AnimatedVisibility(visible = showLock, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            CompositionLocalProvider(LocalPadLayer provides 7) {
                LockScreen(settings, onUnlock = { showLock = false })
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
            showLock -> listOf("A" to "Unlock")
            showTutorial -> listOf("A" to "Got it")
            menuOpen -> listOf("D-pad" to "Move", "A" to "Choose", "B" to "Cancel")
            showIndex -> listOf("D-pad" to "Move", "A" to "Open", "HOME" to "Home screen", "B" to "Back")
            showBackgrounds -> listOf("D-pad" to "Choose", "A" to "Apply", "L1" to "Prev page", "R1" to "Next page", "B" to "Back")
            Controller.movingPackage != null -> listOf("D-pad" to "Move", "A" to "Drop", "B" to "Cancel")
            showSettings -> listOf("D-pad" to "Move / adjust", "A" to "Toggle", "B" to "Done")
            showSearch -> listOf("D-pad" to "Move", "A" to "Open", "B" to "Close")
            showDesktop -> listOf("A" to "Launch", "X" to "Options", "Y" to "Add / remove", "L1" to "Folder", "L2" to "Close", "B" to "Back")
            editMode && selected == null -> listOf("A" to "Options", "Y" to "Move", "START" to "Background", "B" to "Done")
            selected != null -> listOf("A" to "Start", "X" to "Options", "L1" to "Prev", "R1" to "Next", "B" to "Home")
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
}

@Composable
private fun BubblePager(
    apps: List<LaunchableApp>,
    pagerState: PagerState,
    scale: Float,
    layout: PageLayout,
    settings: Settings,
    hiddenPackage: String?,
    movingPackage: String?,
    modifier: Modifier,
    onSelect: (LaunchableApp) -> Unit,
    onOpenDesktop: () -> Unit,
    onOpenRecent: () -> Unit,
    editAmt: () -> Float,
    onLongPressAt: (Offset) -> Unit,
    editing: Boolean,
    onRemove: (LaunchableApp) -> Unit,
    onDragStart: (LaunchableApp, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onPositioned: (String, Rect) -> Unit,
    onDisposed: (String) -> Unit,
) {
    val pages = apps.chunked(layout.size)
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
    val longPress by rememberUpdatedState(onLongPressAt)
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Holding the background (not a bubble) opens the page edit view.
            .pointerInput(Unit) { detectTapGestures(onLongPress = { longPress(origin + it) }) }
            // Swiping sideways on the home screen reaches the open LiveArea pages, as on the Vita.
            .pointerInput(Unit) {
                var total = 0f
                val threshold = 80.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onHorizontalDrag = { _, dx -> total += dx },
                    onDragEnd = { if (total < -threshold || total > threshold) onOpenRecent() },
                    onDragCancel = { total = 0f },
                )
            },
    ) {
        // Pages scroll vertically, like the Vita. Every page stays composed so a bubble being dragged
        // is not disposed when its page scrolls away.
        VerticalPager(
            pagerState,
            Modifier.fillMaxSize().padScroller { pagerState.animateScrollBy(it) },
            // The page edit view zooms out: the pages shrink inside a margin, so the frame and neighbours show.
            contentPadding = PaddingValues(horizontal = (44.dp * editAmt()).coerceAtLeast(0.dp), vertical = (22.dp * editAmt()).coerceAtLeast(0.dp)),
            beyondViewportPageCount = pages.size,
        ) { index ->
            val pageApps = pages.getOrElse(index) { emptyList() }
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    // Pages shrink and fade slightly as they slide away.
                    .graphicsLayer {
                        val distance = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                        val s = 1f - 0.08f * distance
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.55f * distance
                    },
            ) {
                // The sphere is a quarter of the page height, like the real home screen.
                val bubble = (minOf(maxHeight * 0.25f, maxWidth * 0.14f) * scale).coerceAtLeast(40.dp)
                val column = bubble + 56.dp
                val pageHeightPx = constraints.maxHeight.toFloat()
                if (editAmt() > 0.01f) {
                    // The translucent frame around the page being edited.
                    val frame = RoundedCornerShape(6.dp)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .graphicsLayer { alpha = editAmt() }
                            .vitaPanel(6.dp, 0.9f),
                    )
                }
                pageApps.forEachIndexed { i, app ->
                    val (fx, fy) = layout.slots[i]
                    androidx.compose.runtime.key(app.packageName) {
                        DisposableEffect(app.packageName) { onDispose { onDisposed(app.packageName) } }
                        Bubble(
                            app, bubble,
                            onClick = { onSelect(app) },
                            modifier = Modifier
                                .offset(x = maxWidth * fx - column / 2, y = maxHeight * fy - bubble / 2)
                                .width(column)
                                // While the page scrolls, lower rows trail behind and upper rows lead, so the bubbles ripple.
                                .graphicsLayer {
                                    val away = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                                    translationY = away * (fy - 0.5f) * pageHeightPx * 0.25f
                                },
                            padKey = "home:${app.packageName}",
                            hidden = app.packageName == hiddenPackage,
                            shape = if (settings.roundedBubbles) RoundedCornerShape(28) else CircleShape,
                            showLabel = settings.showLabels,
                            moving = app.packageName == movingPackage,
                            editing = editing,
                            removable = app.action == null,
                            onRemove = { onRemove(app) },
                            enterDelay = i * 45,
                            onDragStart = { onDragStart(app, it) },
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                            onPositioned = { onPositioned(app.packageName, it) },
                            // Bubbles roll as the page scrolls, the lower rows a little more than the upper ones.
                            scrollRoll = {
                                ((pagerState.currentPage + pagerState.currentPageOffsetFraction) - index).coerceIn(-2f, 2f) * (1.6f + (fy - 0.5f) * 1.2f)
                            },
                        )
                    }
                }
            }
        }
        // Page dots run down the left edge, the current one larger and brighter.
        if (settings.showDots) PageDots(pages.size, pagerState.currentPage, Modifier.align(Alignment.CenterStart).padding(start = 14.dp))
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
