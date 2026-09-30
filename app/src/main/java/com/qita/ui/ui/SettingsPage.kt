package com.qita.ui.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Controller
import com.qita.ui.LAYOUTS
import com.qita.ui.Settings
import com.qita.ui.THEMES
import kotlin.math.roundToInt

private val DeepGreen = Color(0xFF0B6A14)

/** What the Games & Emulators setup page shows and does; built by the home screen, which owns the scanning. */
class GamesSetup(
    val folders: List<com.qita.ui.GameFolder>,
    val installed: List<Pair<com.qita.ui.Emulator, String>>,
    /** The package chosen to play each system (by system id), if any. */
    val choices: Map<String, String>,
    val gameCount: Int,
    /** A status line while something is running, else null. */
    val busy: String?,
    val onAddFolder: (Uri) -> Unit,
    val onFolderSystem: (Int, String) -> Unit,
    val onRemoveFolder: (Int) -> Unit,
    val onEmulator: (String, String) -> Unit,
    val onScan: () -> Unit,
    val onCovers: () -> Unit,
)

/** A readable name for a folder's tree URI: its last path segment. */
private fun folderLabel(uri: String): String =
    Uri.decode(uri).substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder" }

/**
 * Settings in the Vita's own style: a green sky, a centred title over a thin rule, big white rows
 * with round icons, a cyan band on the selected row, glowing checkboxes and a round back button.
 * The top level is a menu; each entry opens a page of settings.
 */
@Composable
fun SettingsPage(
    settings: Settings,
    hasWallpaper: Boolean,
    wallpaper: ImageBitmap?,
    onChange: (Settings) -> Unit,
    onWallpaper: (Uri) -> Unit,
    onClearWallpaper: () -> Unit,
    onClearHome: () -> Unit,
    onShowTutorial: () -> Unit,
    hasCustomFont: Boolean,
    onFont: (Uri) -> Unit,
    onClearFont: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    hasLockPicture: Boolean,
    onLockPicture: (Uri) -> Unit,
    onClearLockPicture: () -> Unit,
    onPreviewLock: () -> Unit,
    games: GamesSetup,
    startPage: String? = null,
    onClose: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onWallpaper(uri)
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onFont(uri)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExport(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImport(uri)
    }
    val lockPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onLockPicture(uri)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) games.onAddFolder(uri)
    }
    var page by remember { mutableStateOf<String?>(startPage) }
    // Back steps out of a page first, then closes Settings (this handler is registered after Home's, so it wins).
    BackHandler(enabled = page != null) { page = null }
    // Back closes an open choice list first.
    BackHandler(enabled = optionPicker.value != null) { optionPicker.value = null }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { optionPicker.value = null } }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    val title = when (page) {
        "theme" -> "Theme & Background"
        "home" -> "Home Screen"
        "bubbles" -> "Bubbles & Icons"
        "games" -> "Games & Emulators"
        "lock" -> "Lock Screen"
        "fonts" -> "Fonts & Text"
        "topbar" -> "Top Bar"
        "motion" -> "Motion"
        "status" -> "Date & Time"
        "controller" -> "Controller"
        "system" -> "System"
        else -> "Settings"
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        VitaGreen()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, showHome = false)
            Text(
                title,
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            )
            Box(Modifier.fillMaxWidth().padding(horizontal = 60.dp).height(1.dp).background(Color.White.copy(alpha = 0.5f)))
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(160)))
                    }
                },
                label = "settingsPage",
            ) { current ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padScroller { scroll.animateScrollBy(it) }
                        .verticalScroll(scroll)
                        .padding(start = 96.dp, end = 72.dp, bottom = 96.dp),
                ) {
                    when (current) {
                        "theme" -> {
                            CheckRow("set:vita", "◉", "PS Vita mode (silk wallpaper, glass bubbles)", settings.vitaMode) { on ->
                                onChange(
                                    if (on) settings.copy(vitaMode = true, prevTheme = settings.themeIndex, themeIndex = com.qita.ui.VITA_SILK)
                                    else settings.copy(vitaMode = false, themeIndex = settings.prevTheme),
                                )
                            }
                            THEMES.forEachIndexed { i, t ->
                                MenuRow("set:theme:$i", "◐", t.name, trailing = {
                                    Box(Modifier.size(22.dp).background(Brush.verticalGradient(listOf(t.top, t.bottom)), CircleShape).border(1.5.dp, Color.White, CircleShape))
                                    if (i == settings.themeIndex) Text("✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }) { onChange(settings.copy(themeIndex = i)) }
                            }
                            MenuRow("set:wallpaper", "▣", "Choose wallpaper image") { picker.launch("image/*") }
                            if (hasWallpaper) MenuRow("set:wallpaper:remove", "✕", "Remove wallpaper image") { onClearWallpaper() }
                            CheckRow("set:particles", "✦", "Floating particles", settings.particles) { onChange(settings.copy(particles = it)) }
                            if (settings.particles) {
                                SliderRow("set:particleCount", "✦", "Particle amount", settings.particleCount.toFloat(), 5f..60f, 5f) {
                                    onChange(settings.copy(particleCount = it.roundToInt()))
                                }
                            }
                            CheckRow("set:bgCustom", "◐", "Custom background colours", settings.bgCustom) { onChange(settings.copy(bgCustom = it)) }
                            if (settings.bgCustom) {
                                SwatchRow("set:bgTop", "▲", "Background: top colour", settings.bgTop, BG_SWATCHES) { onChange(settings.copy(bgTop = it)) }
                                SwatchRow("set:bgMid", "●", "Background: middle colour", settings.bgMid, BG_SWATCHES) { onChange(settings.copy(bgMid = it)) }
                                SwatchRow("set:bgBottom", "▼", "Background: bottom colour", settings.bgBottom, BG_SWATCHES) { onChange(settings.copy(bgBottom = it)) }
                            }
                            SliderRow("set:dim", "◑", "Dim background", settings.dim, 0f..0.6f, 0.05f) { onChange(settings.copy(dim = it)) }
                            SliderRow("set:symbolCount", "△", "Vita Symbols: number of symbols", settings.symbolCount.toFloat(), 0f..100f, 5f) {
                                onChange(settings.copy(symbolCount = it.roundToInt()))
                            }
                            SliderRow("set:sceneSpeed", "≋", "Background animation speed", settings.sceneSpeed, 0.3f..2.5f, 0.1f) { onChange(settings.copy(sceneSpeed = it)) }
                            SwatchRow("set:accent", "◎", "Accent colour (selection and highlights)", settings.accent) { onChange(settings.copy(accent = it)) }
                        }
                        "home" -> {
                            LAYOUTS.forEachIndexed { i, l ->
                                MenuRow("set:layout:$i", "⌂", "Layout: ${l.name}", trailing = {
                                    if (i == settings.layoutIndex) Text("✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }) { onChange(settings.copy(layoutIndex = i)) }
                            }
                            CheckRow("set:labels", "A", "Show app names", settings.showLabels) { onChange(settings.copy(showLabels = it)) }
                            CheckRow("set:dots", "•", "Show page dots", settings.showDots) { onChange(settings.copy(showDots = it)) }
                            SliderRow("set:size", "●", "Bubble size", settings.bubbleScale, 0.7f..1.1f, 0.05f) { onChange(settings.copy(bubbleScale = it)) }
                            CheckRow("set:newest", "↓", "Sort newest apps first", settings.sortNewest) { onChange(settings.copy(sortNewest = it)) }
                            CheckRow("set:autoadd", "+", "Add newly installed apps to home", settings.autoAdd) { onChange(settings.copy(autoAdd = it)) }
                            MenuRow("set:clearhome", "✕", "Remove all apps from home") { onClearHome() }
                        }
                        "bubbles" -> {
                            ChoiceRow("set:glassBubbles", "◌", "Bubble style (Android 13+)", listOf("Opaque", "Glass"), if (settings.glassBubbles) 1 else 0) {
                                onChange(settings.copy(glassBubbles = it == 1))
                            }
                            if (settings.glassBubbles) {
                                SliderRow("set:glass", "◌", "Glass clearness", settings.glass, 0f..1f, 0.05f) { onChange(settings.copy(glass = it)) }
                            }
                            CheckRow("set:bubble3d", "◍", "Live 3D bubbles (Android 13+)", settings.bubble3d) { onChange(settings.copy(bubble3d = it)) }
                            CheckRow("set:fullart", "◉", "Full-art bubbles (Vita style)", settings.fullArt) { onChange(settings.copy(fullArt = it)) }
                            CheckRow("set:rounded", "▢", "Rounded square bubbles", settings.roundedBubbles) { onChange(settings.copy(roundedBubbles = it)) }
                            ChoiceRow(
                                "set:bodyMode", "◐", "Glass colour behind icons",
                                listOf("Icon colour", "One colour", "Black glass", "Milky white"), settings.bodyMode,
                            ) { onChange(settings.copy(bodyMode = it)) }
                            if (settings.bodyMode == 1) {
                                SwatchRow("set:bodyColor", "●", "Glass colour", settings.bodyColor) { onChange(settings.copy(bodyColor = it)) }
                            }
                            SliderRow("set:iconScale", "▣", "Icon size inside the bubble", settings.iconScale, 0.7f..1.05f, 0.05f) { onChange(settings.copy(iconScale = it)) }
                            SliderRow("set:iconSat", "◑", "Icon colour strength", settings.iconSat, 0f..1.6f, 0.1f) { onChange(settings.copy(iconSat = it)) }
                            SliderRow("set:iconBright", "☀", "Icon brightness", settings.iconBright, 0.6f..1.4f, 0.05f) { onChange(settings.copy(iconBright = it)) }
                            SliderRow("set:thickness", "◫", "Disc thickness", settings.thickness, 0f..1.8f, 0.1f) { onChange(settings.copy(thickness = it)) }
                            SliderRow("set:dome", "◠", "Dome (flat to puffy)", settings.dome, 0.5f..2f, 0.1f) { onChange(settings.copy(dome = it)) }
                            SliderRow("set:rimWidth", "○", "Milky rim width", settings.rimWidth, 0.3f..2.5f, 0.1f) { onChange(settings.copy(rimWidth = it)) }
                            SliderRow("set:highlight", "✦", "Shine", settings.highlight, 0f..2f, 0.1f) { onChange(settings.copy(highlight = it)) }
                            SliderRow("set:sway", "≈", "Idle sway", settings.sway, 0f..3f, 0.25f) { onChange(settings.copy(sway = it)) }
                            ChoiceRow("set:tapAnim", "↻", "When tapped", listOf("Flip", "Pulse", "Nothing"), settings.tapAnim) { onChange(settings.copy(tapAnim = it)) }
                        }
                        "lock" -> {
                            MenuRow("set:lockPreview", "▶", "Preview the lock screen") { onPreviewLock() }
                            ChoiceRow("set:lockPos", "▭", "Clock position", listOf("Bottom right", "Bottom left", "Top left"), settings.lockClockPos) {
                                onChange(settings.copy(lockClockPos = it))
                            }
                            SliderRow("set:lockSize", "A", "Clock size", settings.lockClockSize, 0.6f..1.6f, 0.1f) { onChange(settings.copy(lockClockSize = it)) }
                            ChoiceRow("set:lockFont", "Aa", "Clock and date font", LOCK_FONTS, settings.lockFont) { onChange(settings.copy(lockFont = it)) }
                            SwatchRow("set:lockColor", "●", "Clock and date colour", settings.lockClockColor) { onChange(settings.copy(lockClockColor = it)) }
                            CheckRow("set:lockDate", "◷", "Show the date", settings.lockShowDate) { onChange(settings.copy(lockShowDate = it)) }
                            CheckRow("set:lockFrame", "▢", "Show the glass frame", settings.lockFrame) { onChange(settings.copy(lockFrame = it)) }
                            if (settings.lockFrame) {
                                SliderRow("set:lockBorder", "○", "Frame brightness", settings.lockBorder, 0f..1f, 0.1f) { onChange(settings.copy(lockBorder = it)) }
                            }
                            SliderRow("set:lockTint", "◑", "Panel tint", settings.lockPanelTint, 0f..0.4f, 0.02f) { onChange(settings.copy(lockPanelTint = it)) }
                            ChoiceRow("set:lockBg", "▣", "Background", listOf("Same as home", "A theme", "My picture"), settings.lockBgMode) { onChange(settings.copy(lockBgMode = it)) }
                            if (settings.lockBgMode == 1) {
                                ChoiceRow("set:lockTheme", "◐", "Lock screen theme", THEMES.map { it.name }, settings.lockTheme.coerceIn(THEMES.indices)) { onChange(settings.copy(lockTheme = it)) }
                            }
                            if (settings.lockBgMode == 2) {
                                MenuRow("set:lockPic", "▣", "Choose a picture") { lockPicker.launch("image/*") }
                                if (hasLockPicture) MenuRow("set:lockPic:rm", "✕", "Remove the picture") { onClearLockPicture() }
                            }
                            CheckRow("set:lockNotifs", "✉", "Show notifications on the lock screen", settings.lockNotifs) { onChange(settings.copy(lockNotifs = it)) }
                            if (settings.lockNotifs) {
                                SliderRow("set:lockNotifCount", "✉", "How many notifications", settings.lockNotifCount.toFloat(), 1f..5f, 1f) { onChange(settings.copy(lockNotifCount = it.roundToInt())) }
                            }
                            CheckRow("set:lockTap", "☝", "Tap the corner to unlock (otherwise peel it)", settings.lockTapPeel) { onChange(settings.copy(lockTapPeel = it)) }
                        }
                        "games" -> {
                            InfoBox(
                                "Set up your game library in four steps.\n" +
                                    "1. Add the folders that hold your games.   2. Choose the emulator for each console.\n" +
                                    "3. Scan for games.   4. Get cover art.   Games then appear in the Games bubble, and you can put any on the home screen.",
                            )
                            // Step 1: folders.
                            games.folders.forEachIndexed { i, f ->
                                val names = listOf("Auto-detect") + com.qita.ui.SYSTEMS.map { it.name }
                                val at = if (f.systemId == "auto") 0 else 1 + com.qita.ui.SYSTEMS.indexOfFirst { it.id == f.systemId }.coerceAtLeast(0)
                                ChoiceRow("set:gfolder:$i", "▣", f.label.ifBlank { folderLabel(f.uri) }, names, at) { n ->
                                    games.onFolderSystem(i, if (n == 0) "auto" else com.qita.ui.SYSTEMS[n - 1].id)
                                }
                                MenuRow("set:gfolder:rm:$i", "✕", "Remove folder ${folderLabel(f.uri)}") { games.onRemoveFolder(i) }
                            }
                            MenuRow("set:gfolder:add", "+", "Add a game folder") { folderPicker.launch(null) }
                            // Step 2: emulators.
                            InfoBox(
                                if (games.installed.isEmpty()) "No emulators found on this device yet."
                                else "Found: " + games.installed.joinToString(", ") { it.first.name },
                            )
                            com.qita.ui.SYSTEMS.forEach { sys ->
                                val options = sys.emulators.mapNotNull { id -> games.installed.firstOrNull { it.first.id == id } }
                                if (options.isNotEmpty()) {
                                    val chosen = games.choices[sys.id]
                                    val at = options.indexOfFirst { it.second == chosen }.coerceAtLeast(0)
                                    ChoiceRow("set:gemu:${sys.id}", "▶", "${sys.name} plays with", options.map { it.first.name }, at) { n ->
                                        games.onEmulator(sys.id, options[n].second)
                                    }
                                }
                            }
                            // Steps 3 and 4.
                            CheckRow("set:gamesonhome", "⌂", "Put new games on the home screen after a scan", settings.gamesOnHome) { onChange(settings.copy(gamesOnHome = it)) }
                            CheckRow("set:gcoverauto", "▦", "Get cover art automatically after a scan", settings.gameCovers) { onChange(settings.copy(gameCovers = it)) }
                            MenuRow("set:gscan", "↻", if (games.busy != null) games.busy else "Scan for games (${games.gameCount} found)") { if (games.busy == null) games.onScan() }
                            MenuRow("set:gcovers", "▦", "Get cover art online for games without one") { if (games.busy == null) games.onCovers() }
                        }
                        "fonts" -> {
                            InfoBox("The quick brown fox jumps over the lazy dog 0123456789")
                            ChoiceRow("set:uiFont", "Aa", "Menu and screen font", FONT_NAMES, settings.uiFontChoice) { onChange(settings.copy(uiFontChoice = it)) }
                            ChoiceRow("set:nameFont", "Aa", "App name font", FONT_NAMES, settings.nameFont) { onChange(settings.copy(nameFont = it)) }
                            MenuRow("set:font", "↥", "Load a font file (.ttf / .otf)") { fontPicker.launch("*/*") }
                            if (hasCustomFont) MenuRow("set:font:remove", "✕", "Remove the loaded font file") { onClearFont() }
                            SliderRow("set:nameSize", "A", "App name size", settings.nameSize, 0.7f..1.5f, 0.05f) { onChange(settings.copy(nameSize = it)) }
                            ChoiceRow("set:nameWeight", "A", "App name weight", listOf("Light", "Normal", "Bold"), settings.nameWeight) { onChange(settings.copy(nameWeight = it)) }
                            SwatchRow("set:nameColor", "●", "App name colour", settings.nameColor) { onChange(settings.copy(nameColor = it)) }
                            CheckRow("set:namePill", "▭", "Always show names on a pill", settings.namePill) { onChange(settings.copy(namePill = it)) }
                        }
                        "topbar" -> {
                            CheckRow("set:showClock", "◷", "Show the clock", settings.showClock) { onChange(settings.copy(showClock = it)) }
                            SliderRow("set:clockSize", "A", "Clock size", settings.clockSize, 0.7f..1.5f, 0.05f) { onChange(settings.copy(clockSize = it)) }
                            SwatchRow("set:notifColor", "●", "Notification button and panel colour", settings.notifColor) { onChange(settings.copy(notifColor = it)) }
                            SliderRow("set:barOpacity", "◑", "Bar opacity", settings.barOpacity, 0.2f..1f, 0.1f) { onChange(settings.copy(barOpacity = it)) }
                            CheckRow("set:24h2", "◷", "24-hour clock", settings.use24h) { onChange(settings.copy(use24h = it)) }
                            CheckRow("set:battery2", "⚡", "Show battery level", settings.showBattery) { onChange(settings.copy(showBattery = it)) }
                        }
                        "motion" -> {
                            CheckRow("set:reduce", "■", "Reduce motion (still background, no sway or flip)", settings.reduceMotion) { onChange(settings.copy(reduceMotion = it)) }
                            SliderRow("set:sceneSpeed2", "≋", "Background animation speed", settings.sceneSpeed, 0.3f..2.5f, 0.1f) { onChange(settings.copy(sceneSpeed = it)) }
                            SliderRow("set:sway2", "≈", "Bubble idle sway", settings.sway, 0f..3f, 0.25f) { onChange(settings.copy(sway = it)) }
                            ChoiceRow("set:tapAnim2", "↻", "Bubble tap animation", listOf("Flip", "Pulse", "Nothing"), settings.tapAnim) { onChange(settings.copy(tapAnim = it)) }
                        }
                        "status" -> {
                            CheckRow("set:24h", "◷", "24-hour clock", settings.use24h) { onChange(settings.copy(use24h = it)) }
                            CheckRow("set:battery", "⚡", "Show battery level", settings.showBattery) { onChange(settings.copy(showBattery = it)) }
                        }
                        "controller" -> {
                            CheckRow("set:cursor", "↖", "Cursor mode", settings.cursorMode) { onChange(settings.copy(cursorMode = it)) }
                            SliderRow("set:cursorSpeed", "↖", "Cursor speed", settings.cursorSpeed, 0.5f..2.5f, 0.1f) { onChange(settings.copy(cursorSpeed = it)) }
                            CheckRow("set:ps", "✕", "PlayStation button symbols", settings.psLabels) { onChange(settings.copy(psLabels = it)) }
                            CheckRow("set:swap", "⇄", "Swap A/B and X/Y (Nintendo layout)", settings.swapAB) { onChange(settings.copy(swapAB = it)) }
                            CheckRow("set:debug", "?", "Show live input readout on screen", settings.debugInput) { onChange(settings.copy(debugInput = it)) }
                            InfoBox("Last input: ${Controller.lastInput}", mono = true)
                            InfoBox(
                                "A select   B back   X options   Y move (home) or add/remove (desktop)\n" +
                                    "Start settings   Select cursor mode   L1/R1 page or folder\n" +
                                    "L2 desktop   R2 search   L3 recenter cursor   R3 precision cursor\n" +
                                    "Right stick left/right changes page.\n" +
                                    "Move mode: D-pad carries the bubble, A drops it, B cancels.\n" +
                                    "Cursor mode: left stick moves the pointer, A clicks (hold to drag), right stick scrolls, D-pad nudges.",
                            )
                        }
                        "system" -> {
                            ChoiceRow("set:orientation", "↻", "Screen orientation", listOf("Landscape", "Portrait", "Auto"), settings.orientation) { onChange(settings.copy(orientation = it)) }
                            CheckRow("set:haptics", "∷", "Vibrate on long-press and highlight", settings.haptics) { onChange(settings.copy(haptics = it)) }
                            CheckRow("set:lock", "▭", "Lock screen when the launcher starts or the screen wakes", settings.lockScreen) { onChange(settings.copy(lockScreen = it)) }
                            MenuRow("set:export", "↥", "Save my settings to a file") { exporter.launch("qitaui-settings.json") }
                            MenuRow("set:import", "↧", "Load settings from a file") { importer.launch("*/*") }
                            MenuRow("set:tutorial", "i", "Show tutorial") { onShowTutorial() }
                            MenuRow("set:reset", "↺", "Reset all settings") { onChange(Settings()) }
                        }
                        else -> {
                            NavRow("set:nav:theme", "◐", "Theme & Background") { page = "theme" }
                            NavRow("set:nav:home", "⌂", "Home Screen") { page = "home" }
                            NavRow("set:nav:bubbles", "◍", "Bubbles & Icons") { page = "bubbles" }
                            NavRow("set:nav:lock", "▭", "Lock Screen") { page = "lock" }
                            NavRow("set:nav:games", "G", "Games & Emulators") { page = "games" }
                            NavRow("set:nav:fonts", "Aa", "Fonts & Text") { page = "fonts" }
                            NavRow("set:nav:topbar", "▭", "Top Bar") { page = "topbar" }
                            NavRow("set:nav:motion", "≈", "Motion") { page = "motion" }
                            NavRow("set:nav:status", "◷", "Date & Time") { page = "status" }
                            NavRow("set:nav:controller", "✦", "Controller") { page = "controller" }
                            NavRow("set:nav:system", "⚙", "System") { page = "system" }
                        }
                    }
                }
            }
        }
        optionPicker.value?.let { p -> OptionPicker(p) { optionPicker.value = null } }
        BackButton(
            onClick = { if (page != null) page = null else onClose() },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
        )
    }
}

/** The green Vita settings sky: dark at the top, glowing lime at the bottom, with a faint toolbox watermark. */
@Composable
private fun VitaGreen() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF0A5A12), 0.55f to Color(0xFF1F9B2A), 0.85f to Color(0xFF3FD84A), 1f to Color(0xFF8DFF6B),
            ),
        )
        // Watermark: a big translucent toolbox on the right.
        val w = size.width * 0.36f
        val h = size.height * 0.40f
        val left = size.width * 0.62f
        val top = size.height * 0.58f
        val mark = Color.White.copy(alpha = 0.10f)
        drawRoundRect(mark, Offset(left, top), Size(w, h), CornerRadius(w * 0.06f))
        drawRoundRect(mark, Offset(left + w * 0.27f, top - h * 0.24f), Size(w * 0.46f, h * 0.30f), CornerRadius(w * 0.08f), style = Stroke(width = w * 0.07f))
        drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(left + w * 0.16f, top + h * 0.32f), Size(w * 0.14f, h * 0.30f), CornerRadius(w * 0.03f))
        drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(left + w * 0.70f, top + h * 0.32f), Size(w * 0.14f, h * 0.30f), CornerRadius(w * 0.03f))
        // Extra glow along the bottom edge.
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f)), startY = size.height * 0.8f, endY = size.height),
        )
    }
}

/** Cyan band behind the selected row, fading out at both ends, plus the thin separator line under every row. */
private fun Modifier.rowBand(lit: Boolean): Modifier = composed {
    val band by animateFloatAsState(if (lit) 1f else 0f, tween(140), label = "band")
    val Cyan = LocalLook.current.accent
    drawBehind {
        if (band > 0.01f) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(Cyan.copy(alpha = 0f), Cyan.copy(alpha = 0.55f * band), Cyan.copy(alpha = 0.55f * band), Cyan.copy(alpha = 0f)),
                ),
            )
        }
        drawLine(Color.White.copy(alpha = 0.28f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }
}

@Composable
private fun GlyphBadge(glyph: String) {
    Box(
        Modifier
            .size(34.dp)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFCFE8CF))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = DeepGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MenuRow(
    key: String,
    glyph: String,
    label: String,
    trailing: @Composable () -> Unit = {},
    onAdjust: ((Int) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val lit = padHighlighted(key) || padHovered(key)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padClickable(key, corner = 0.dp, ring = false, onAdjust = onAdjust, onClick = onClick)
            .rowBand(lit)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlyphBadge(glyph)
        Text(label, Modifier.weight(1f), color = Color.White, fontSize = 20.sp)
        trailing()
    }
}

@Composable
private fun NavRow(key: String, glyph: String, label: String, onClick: () -> Unit) {
    MenuRow(key, glyph, label, trailing = { Text("▶", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp) }, onClick = onClick)
}

@Composable
private fun CheckRow(key: String, glyph: String, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    MenuRow(key, glyph, label, trailing = { GlowCheck(checked) }) { onChecked(!checked) }
}

private val FONT_NAMES = listOf("Built-in", "System", "Serif", "Monospace", "Loaded file")
private val LOCK_FONTS = listOf("Thin sans", "Built-in", "System", "Serif", "Monospace", "Loaded file")

/** A row that steps through a short list of choices: tap or A goes to the next, left/right on the gamepad steps either way. */
@Composable
private fun ChoiceRow(key: String, glyph: String, label: String, options: List<String>, index: Int, onIndex: (Int) -> Unit) {
    val n = options.size
    val i = index.coerceIn(0, n - 1)
    MenuRow(
        key, glyph, label,
        trailing = { Text(options[i], color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium) },
        onAdjust = { dir -> onIndex((i + dir + n) % n) },
    ) {
        // A long list (the consoles, the themes) opens as a picker; a short one just steps to the next choice.
        if (n > 6) optionPicker.value = OptionPick(label, options, i, onIndex) else onIndex((i + 1) % n)
    }
}

/** A list of choices shown in the picker. */
private class OptionPick(val title: String, val options: List<String>, val selected: Int, val onPick: (Int) -> Unit)

/** The choice list currently open, if any. There is only ever one Settings page, so one shared holder does. */
private val optionPicker = mutableStateOf<OptionPick?>(null)

/** A scrollable list of every choice with the current one marked; tap or press A on one to pick it. */
@Composable
private fun OptionPicker(pick: OptionPick, onDismiss: () -> Unit) {
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val accent = LocalLook.current.accent
    LaunchedEffect(pick) {
        // Start with the current choice in view and highlighted.
        scroll.scrollTo(with(density) { (pick.selected * 46).dp.toPx() }.toInt())
        kotlinx.coroutines.delay(60)
        PadNav.select("pick:${pick.selected}")
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(340.dp)
                    .heightIn(max = 320.dp)
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(14.dp),
            ) {
                Text(pick.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Column(
                    Modifier.weight(1f, fill = false).padScroller { scroll.animateScrollBy(it) }.verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pick.options.forEachIndexed { i, name ->
                        val on = i == pick.selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padClickable("pick:$i") { pick.onPick(i); onDismiss() }
                                .background(if (on) accent.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
                            if (on) Text("✓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** A row of colour swatches; the chosen one has a white ring. Left/right on the gamepad steps through them. */
@Composable
private fun SwatchRow(key: String, glyph: String, label: String, selected: Int, swatches: List<Int> = SWATCHES, onPick: (Int) -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    val at = swatches.indexOf(selected)
    Column(
        Modifier
            .fillMaxWidth()
            .padTarget(key, corner = 0.dp, ring = false, onAdjust = { dir ->
                onPick(swatches[((if (at < 0) 0 else at) + dir + swatches.size) % swatches.size])
            })
            .rowBand(lit)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlyphBadge(glyph)
            Text(label, color = Color.White, fontSize = 20.sp)
        }
        Row(Modifier.padding(start = 48.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { c ->
                val on = c == selected
                Box(
                    Modifier
                        .size(if (on) 34.dp else 30.dp)
                        .background(Color(c), CircleShape)
                        .border(if (on) 3.dp else 1.5.dp, Color.White.copy(alpha = if (on) 1f else 0.7f), CircleShape)
                        .clickable { onPick(c) },
                )
            }
        }
    }
}

/** Rounded box with a soft glow and a check when on, like the Vita's checkbox. */
@Composable
private fun GlowCheck(checked: Boolean) {
    val glow by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "glow")
    Canvas(Modifier.size(34.dp)) {
        val r = CornerRadius(7.dp.toPx())
        if (glow > 0.01f) {
            drawRoundRect(Color.White.copy(alpha = 0.30f * glow), Offset(-3.dp.toPx(), -3.dp.toPx()), Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), CornerRadius(10.dp.toPx()))
        }
        drawRoundRect(Color.White.copy(alpha = 0.16f + 0.5f * glow), size = size, cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.9f), size = size, cornerRadius = r, style = Stroke(width = 2.dp.toPx()))
        if (checked) {
            val tick = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.74f)
                lineTo(size.width * 0.80f, size.height * 0.28f)
            }
            drawPath(tick, DeepGreen, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A row with a slider under its label; with it highlighted, left/right on the gamepad steps the value. */
@Composable
private fun SliderRow(
    key: String,
    glyph: String,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
) {
    val lit = padHighlighted(key) || padHovered(key)
    Column(
        Modifier
            .fillMaxWidth()
            .padTarget(key, corner = 0.dp, ring = false, onAdjust = { dir ->
                onChange((value + dir * step).coerceIn(range.start, range.endInclusive))
            })
            .rowBand(lit)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlyphBadge(glyph)
            Text(label, color = Color.White, fontSize = 20.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
        )
    }
}

@Composable
private fun InfoBox(text: String, mono: Boolean = false) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        color = if (mono) Color(0xFFD8FFB0) else Color.White.copy(alpha = 0.9f),
        fontSize = 12.sp,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
    )
}

/** Round glossy back button in the bottom-left corner, with a return arrow. */
@Composable
internal fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier, key: String = "set:back") {
    val lit = padHighlighted(key) || padHovered(key)
    val Cyan = LocalLook.current.accent
    Box(
        modifier
            .size(66.dp)
            .padClickable(key, corner = null, pad = 4.dp, onClick = onClick)
            .clip(CircleShape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFFA6FF8F), Color(0xFF3FD84A), Color(0xFF14902A)),
                        center = Offset(size.width * 0.4f, size.height * 0.32f),
                        radius = size.width * 0.85f,
                    ),
                )
            }
            .border(if (lit) 4.dp else 2.dp, if (lit) Cyan else Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            // Top run, the curve around the right, and the bottom run: a return arrow.
            drawLine(Color.White, Offset(w * 0.30f, h * 0.25f), Offset(w * 0.60f, h * 0.25f), stroke.width, StrokeCap.Round)
            drawArc(
                Color.White, startAngle = -90f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(w * 0.34f, h * 0.25f), size = Size(w * 0.52f, h * 0.50f), style = stroke,
            )
            drawLine(Color.White, Offset(w * 0.60f, h * 0.75f), Offset(w * 0.34f, h * 0.75f), stroke.width, StrokeCap.Round)
            val head = Path().apply {
                moveTo(w * 0.10f, h * 0.25f)
                lineTo(w * 0.32f, h * 0.08f)
                lineTo(w * 0.32f, h * 0.42f)
                close()
            }
            drawPath(head, Color.White)
        }
    }
}
