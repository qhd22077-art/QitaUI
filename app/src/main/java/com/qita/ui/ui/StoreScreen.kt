package com.qita.ui.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.qita.ui.DlKind
import com.qita.ui.DlState
import com.qita.ui.DownloadEngine
import com.qita.ui.DownloadItem
import com.qita.ui.ReleaseResolver
import com.qita.ui.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// The colours of the real PlayStation Store, measured from photos of it.
private val StoreTop = Color(0xFF3A66CC)
private val StoreMid = Color(0xFF2F53B9)
private val StoreBottom = Color(0xFF2A3DAA)
private val StoreIndigo = Color(0xFF4343C6)
private val BarTop = Color(0xFF9AA3D2)
private val BarBottom = Color(0xFF545FC1)
private val TabDark = Color(0xFF2D3B72)
private val RowLine = Color(0xFF748BCF)
private val SoftText = Color(0xFFB8C4F0)
private val DimText = Color(0xFF9AA8E0)
private val OrangeTop = Color(0xFFF58A3A)
private val OrangeBottom = Color(0xFFE6621C)

/** One item of the catalogue. [repo] is a GitHub project whose newest APK the Install button fetches; without one it opens [page]. */
class StoreEntry(
    val id: String,
    val name: String,
    val developer: String,
    val blurb: String,
    /** 0 emulator, 1 free games and homebrew. */
    val category: Int,
    val short: String,
    val color: Long,
    val repo: String?,
    val page: String,
)

private val CATALOGUE: List<StoreEntry> = listOf(
    StoreEntry("retroarch", "RetroArch", "Libretro", "One app for many consoles: NES, SNES, Game Boy, GBA, Mega Drive, N64, PS1 and more, through cores.", 0, "RA", 0xFF2A2A30, null, "https://www.retroarch.com"),
    StoreEntry("ppsspp", "PPSSPP", "Henrik Rydgård", "PlayStation Portable games, in high resolution.", 0, "PSP", 0xFF1E3A70, null, "https://www.ppsspp.org/download"),
    StoreEntry("dolphin", "Dolphin", "Dolphin Team", "GameCube and Wii games.", 0, "DOL", 0xFF5A3A9A, null, "https://dolphin-emu.org/download/"),
    StoreEntry("vita3k", "Vita3K", "Vita3K Team", "PlayStation Vita games on Android.", 0, "VITA", 0xFF1A5FB4, "Vita3K/Vita3K", "https://vita3k.org"),
    StoreEntry("azahar", "Azahar", "Azahar Team", "Nintendo 3DS games.", 0, "3DS", 0xFFC03A3A, "azahar-emu/azahar", "https://azahar-emu.org"),
    StoreEntry("eden", "Eden", "Eden Team", "Nintendo Switch games.", 0, "NSW", 0xFFD03A3A, "eden-emulator/Releases", "https://eden-emu.dev"),
    StoreEntry("flycast", "Flycast", "Flyinghead", "Sega Dreamcast, Naomi and Atomiswave.", 0, "DC", 0xFFD0702A, "flyinghead/flycast", "https://github.com/flyinghead/flycast"),
    StoreEntry("melonds", "melonDS", "Rafael Caetano", "Nintendo DS games.", 0, "NDS", 0xFF7A7F88, "rafaelvcaetano/melonDS-android", "https://github.com/rafaelvcaetano/melonDS-android"),
    StoreEntry("duckstation", "DuckStation", "Stenzek", "PlayStation 1 games.", 0, "PS1", 0xFF6A6F78, null, "https://www.duckstation.org"),
    StoreEntry("gamenative", "GameNative", "GameNative", "Windows games from your own Steam, Epic and GOG libraries.", 0, "PC", 0xFF2A3A5A, "utkarshdalal/GameNative", "https://github.com/utkarshdalal/GameNative"),
    StoreEntry("winlator", "Winlator", "Bruno Sousa", "Run Windows programs on Android.", 0, "WIN", 0xFF2A4AA0, "brunodev85/winlator", "https://github.com/brunodev85/winlator"),
    StoreEntry("itchfree", "Free games on itch.io", "itch.io", "Thousands of free indie games and demos.", 1, "FREE", 0xFFB03A5A, null, "https://itch.io/games/free"),
    StoreEntry("itchhome", "Homebrew games", "itch.io", "Games made by fans for old consoles, free to download.", 1, "HB", 0xFF3A8A5A, null, "https://itch.io/games/tag-homebrew"),
    StoreEntry("libretro", "Libretro content", "Libretro", "Free games, demos and homebrew for RetroArch.", 1, "LIB", 0xFF5A5A8A, null, "https://docs.libretro.com/guides/download-content/"),
)

private class Preset(val title: String, val url: String, val blurb: String)

/** A few set pages to start from: official emulator sites and free-game sites. */
private val PRESETS = listOf(
    Preset("RetroArch", "https://www.retroarch.com", "Many consoles in one app"),
    Preset("PPSSPP", "https://www.ppsspp.org", "PlayStation Portable"),
    Preset("Dolphin", "https://dolphin-emu.org", "GameCube and Wii"),
    Preset("Vita3K", "https://vita3k.org", "PlayStation Vita"),
    Preset("Azahar", "https://azahar-emu.org", "Nintendo 3DS"),
    Preset("Eden", "https://eden-emu.dev", "Nintendo Switch"),
    Preset("itch.io", "https://itch.io/games/free", "Free indie games"),
    Preset("Libretro docs", "https://docs.libretro.com", "Guides and free content"),
)

private fun storeBackground() = Brush.verticalGradient(listOf(StoreTop, StoreMid, StoreBottom))

/**
 * The Store, in the look of the PlayStation Store: a blue sky, a bevelled tab bar (Catalogue, Browser, Downloads) with a Search
 * button, a banner strip and a segmented filter above a list of glossy rows, a detail page with orange Download buttons, and round
 * Back and "..." buttons in the lower corners. Downloads go into the game folders and show in the notification panel.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StoreScreen(
    settings: Settings,
    onToast: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var segment by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<StoreEntry?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    // The download started from each catalogue entry, so its button can show progress.
    val started = remember { mutableStateMapOf<String, DownloadItem>() }
    val looking = remember { mutableStateMapOf<String, Boolean>() }

    // The browser is created once and kept while the user moves between tabs.
    var browsing by remember { mutableStateOf(false) }
    var pageUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptCookie(true)
        }
    }
    DisposableEffect(web) {
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { if (url != null) pageUrl = url }
            override fun onPageFinished(view: WebView?, url: String?) { pageTitle = view?.title.orEmpty(); if (url != null) pageUrl = url }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
        }
        web.setDownloadListener { url, userAgent, disposition, mime, _ ->
            val name = URLUtil.guessFileName(url, disposition, mime)
            val kind = if (name.endsWith(".apk", true)) DlKind.APK else DlKind.FILE
            DownloadEngine.enqueue(url, name, kind, CookieManager.getInstance().getCookie(url), userAgent)
            onToast("Downloading $name. Progress is in the notification panel.")
        }
        onDispose { web.stopLoading(); web.destroy() }
    }
    fun openPage(url: String) { tab = 1; browsing = true; web.loadUrl(url) }
    fun go(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            " " !in t && "." in t -> "https://$t"
            else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
        }
        openPage(url)
    }

    fun download(entry: StoreEntry) {
        val repo = entry.repo
        if (repo == null) { openPage(entry.page); return }
        val existing = started[entry.id]
        if (existing != null && existing.state == DlState.DONE) {
            existing.finalPath?.let { path ->
                com.qita.ui.ApkInstaller.install(context, java.io.File(path))?.let(onToast)
            }
            return
        }
        if (looking[entry.id] == true) return
        looking[entry.id] = true
        scope.launch {
            val found = withContext(Dispatchers.IO) { ReleaseResolver.latestApk(repo) }
            looking[entry.id] = false
            if (found == null) {
                onToast("No APK found for ${entry.name}. Opening its page.")
                openPage(entry.page)
            } else {
                started[entry.id] = DownloadEngine.enqueue(found.first, found.second, DlKind.APK)
                onToast("Downloading ${entry.name}. Progress is in the notification panel.")
            }
        }
    }

    BackHandler(enabled = menu) { menu = false }
    BackHandler(enabled = !menu && tab == 0 && detail != null) { detail = null }
    BackHandler(enabled = !menu && tab == 1 && browsing && web.canGoBack()) { web.goBack() }
    BackHandler(enabled = !menu && tab == 1 && browsing && !web.canGoBack()) { browsing = false }

    Box(Modifier.fillMaxSize().background(storeBackground()).pointerInput(Unit) { detectTapGestures { } }) {
        // A soft indigo wash on the right and a light sheen across the top, as in the real store.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, StoreIndigo.copy(alpha = 0.35f))))
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), 0f, size.height * 0.35f))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, showHome = false)
            TabBar(tab, onTab = { tab = it; if (it == 0) detail = null }, onSearch = { searching = !searching; tab = if (tab == 2) 0 else tab })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> Catalogue(
                        segment, { segment = it }, detail, { detail = it }, searching, query, { query = it },
                        started, looking, ::download, ::openPage,
                    )
                    1 -> BrowserTab(web, browsing, pageUrl, progress, ::go, ::openPage, context, onToast, { browsing = false; web.loadUrl("about:blank") })
                    else -> DownloadsTab(onToast)
                }
            }
        }
        RoundButton(
            onClick = { if (tab == 0 && detail != null) detail = null else if (tab == 1 && browsing && web.canGoBack()) web.goBack() else onClose() },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 6.dp),
            key = "store:back", dots = false,
        )
        RoundButton(
            onClick = { menu = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp),
            key = "store:more", dots = true,
        )
        if (menu) {
            com.qita.ui.ui.ContextMenu(
                title = "Store",
                subtitle = "${DownloadEngine.items.count { it.state == DlState.RUNNING }} downloading",
                items = listOf(
                    MenuItem("Downloads") { menu = false; tab = 2 },
                    MenuItem("Notifications") { menu = false; onOpenNotifications() },
                    MenuItem("Catalogue") { menu = false; tab = 0; detail = null },
                    MenuItem("Close the Store") { menu = false; onClose() },
                    MenuItem("Cancel") { menu = false },
                ),
                onDismiss = { menu = false },
                layer = 5,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// Tab bar and buttons
// ------------------------------------------------------------------------------------------------------------------------

@Composable
private fun TabBar(tab: Int, onTab: (Int) -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Brush.verticalGradient(listOf(BarTop, BarBottom)))
            .drawLine(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "QitaUI Store", Modifier.padding(start = 14.dp, end = 10.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 3f)),
        )
        listOf("Catalogue", "Browser", "Downloads").forEachIndexed { i, label ->
            val on = i == tab
            val lit = padHighlighted("store:tab:$i") || padHovered("store:tab:$i")
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padClickable("store:tab:$i", corner = 0.dp, ring = false) { onTab(i) }
                    .background(if (on) TabDark else if (lit) Color.White.copy(alpha = 0.18f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, fontSize = 19.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
        val lit = padHighlighted("store:search") || padHovered("store:search")
        Text(
            "Search",
            Modifier
                .padding(horizontal = 10.dp)
                .padClickable("store:search", corner = 8.dp, ring = false, onClick = onSearch)
                .background(if (lit) TabDark.copy(alpha = 0.9f) else TabDark.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 7.dp),
            color = Color.White, fontSize = 15.sp,
        )
    }
}

/** A thin dark line along the bottom of the bar, like the real one's edge. */
private fun Modifier.drawLine(): Modifier = this.then(
    Modifier.drawBehindLine(),
)

private fun Modifier.drawBehindLine(): Modifier = androidx.compose.ui.draw.drawBehind {
    drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, size.height - 1.5.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx()))
}

/** The round glossy buttons in the lower corners: back (a return arrow) and "..." (options). */
@Composable
private fun RoundButton(onClick: () -> Unit, modifier: Modifier, key: String, dots: Boolean) {
    val lit = padHighlighted(key) || padHovered(key)
    Box(
        modifier
            .size(64.dp)
            .padClickable(key, corner = null, pad = 3.dp, onClick = onClick)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF8C98D8), Color(0xFF4C5AAE), Color(0xFF2D3B8A)), Offset(60f, 40f), 120f))
            .border(if (lit) 3.dp else 1.5.dp, if (lit) Color.White else Color.White.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val w = size.width
            val h = size.height
            if (dots) {
                for (i in 0..2) drawCircle(Color.White, w * 0.08f, Offset(w * (0.2f + 0.3f * i), h * 0.5f))
            } else {
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawLine(Color.White, Offset(w * 0.30f, h * 0.25f), Offset(w * 0.60f, h * 0.25f), stroke.width, StrokeCap.Round)
                drawArc(Color.White, -90f, 180f, false, Offset(w * 0.34f, h * 0.25f), androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.50f), style = stroke)
                drawLine(Color.White, Offset(w * 0.60f, h * 0.75f), Offset(w * 0.34f, h * 0.75f), stroke.width, StrokeCap.Round)
                val head = Path().apply { moveTo(w * 0.10f, h * 0.25f); lineTo(w * 0.32f, h * 0.08f); lineTo(w * 0.32f, h * 0.42f); close() }
                drawPath(head, Color.White)
            }
        }
    }
}

@Composable
private fun OrangeButton(key: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padClickable(key, corner = 10.dp, ring = false, onClick = onClick)
            .background(
                Brush.verticalGradient(if (enabled) listOf(OrangeTop, OrangeBottom) else listOf(Color(0xFF8A94C8), Color(0xFF5A66B0))),
                RoundedCornerShape(10.dp),
            )
            .border(if (lit) 2.dp else 1.dp, if (lit) Color.White else Color(0xFFFFC08A), RoundedCornerShape(10.dp))
            .padding(horizontal = 28.dp, vertical = 9.dp),
        color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center, maxLines = 1,
    )
}

// ------------------------------------------------------------------------------------------------------------------------
// Catalogue
// ------------------------------------------------------------------------------------------------------------------------

@Composable
private fun Catalogue(
    segment: Int,
    onSegment: (Int) -> Unit,
    detail: StoreEntry?,
    onDetail: (StoreEntry?) -> Unit,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    started: Map<String, DownloadItem>,
    looking: Map<String, Boolean>,
    onDownload: (StoreEntry) -> Unit,
    onOpenPage: (String) -> Unit,
) {
    if (detail != null) {
        DetailPage(detail, started[detail.id], looking[detail.id] == true, onDownload, onOpenPage, onDetail)
        return
    }
    val list = remember(segment, query) {
        val base = when (segment) {
            1 -> CATALOGUE.filter { it.category == 0 }
            2 -> CATALOGUE.filter { it.category == 1 }
            else -> CATALOGUE
        }
        if (query.isBlank()) base else base.filter { it.name.contains(query.trim(), true) || it.developer.contains(query.trim(), true) }
    }
    val state = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        if (searching) {
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 9.dp)) {
                        if (query.isEmpty()) Text("Search the catalogue", color = SoftText, fontSize = 17.sp)
                        inner()
                    }
                },
            )
        }
        // The banner strip: the first few emulators as wide cards.
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(CATALOGUE.take(7), key = { it.id }) { e -> Banner(e) { onDetail(e) } }
        }
        Segmented(listOf("Featured", "Emulators", "Free games", "All").let { listOf(it[0], it[1], it[2], it[3]) }, segment, onSegment)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padScroller { state.animateScrollBy(it) },
            state = state,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp),
        ) {
            items(if (segment == 0) list.take(8) else list, key = { it.id }) { e ->
                CatalogueRow(e, started[e.id], looking[e.id] == true) { onDetail(e) }
            }
        }
    }
}

@Composable
private fun Banner(e: StoreEntry, onClick: () -> Unit) {
    Box(
        Modifier
            .width(260.dp)
            .height(100.dp)
            .padClickable("store:banner:${e.id}", corner = 8.dp, pad = 3.dp, onClick = onClick)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(Color(e.color).copy(alpha = 1f), Color.Black.copy(alpha = 0.85f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(e.short, color = Color.White.copy(alpha = 0.22f), fontSize = 64.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) {
            Text(e.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(e.developer, color = SoftText, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .height(40.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF8793D6), Color(0xFF4F5DB8))))
            .border(1.dp, Color(0xFF2D3B72), shape),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val lit = padHighlighted("store:seg:$i") || padHovered("store:seg:$i")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padClickable("store:seg:$i", corner = 0.dp, ring = false) { onSelect(i) }
                    .background(if (on) TabDark else if (lit) Color.White.copy(alpha = 0.18f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
            if (i < options.size - 1) Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF2D3B72).copy(alpha = 0.7f)))
        }
    }
}

/** A catalogue row like the store's: a bevelled square icon, a dim line, a big white title, the developer and the price side. */
@Composable
private fun CatalogueRow(e: StoreEntry, download: DownloadItem?, looking: Boolean, onClick: () -> Unit) {
    val lit = padHighlighted("store:row:${e.id}") || padHovered("store:row:${e.id}")
    Row(
        Modifier
            .fillMaxWidth()
            .height(74.dp)
            .padClickable("store:row:${e.id}", corner = 0.dp, ring = false, onClick = onClick)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (lit) 0.30f else 0.14f), Color.White.copy(alpha = if (lit) 0.16f else 0.04f)))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryIcon(e, 74.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(if (e.category == 0) "Emulator" else "Free games", color = SoftText, fontSize = 13.sp, maxLines = 1)
            Text(e.name, color = Color.White, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.developer, color = DimText, fontSize = 15.sp, maxLines = 1)
        }
        Text(
            when {
                looking -> "Looking…"
                download != null && download.state == DlState.RUNNING -> "${(download.fraction.coerceAtLeast(0f) * 100).toInt()}%"
                download != null && download.state == DlState.DONE -> "Downloaded"
                else -> "Free"
            },
            Modifier.padding(end = 18.dp), color = Color.White, fontSize = 20.sp,
        )
    }
    // The line under each row: dark, then a light one, so the rows look cut into the page.
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.25f)))
    Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.55f)))
}

/** The square picture of an entry: its colour with its short name, and a dark bottom band like the store's "PSP GAME" label. */
@Composable
private fun EntryIcon(e: StoreEntry, size: Dp) {
    Box(
        Modifier
            .size(size)
            .background(Brush.verticalGradient(listOf(Color(e.color), Color.Black.copy(alpha = 0.8f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(e.short, color = Color.White, fontSize = (size.value * 0.28f).sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun DetailPage(
    e: StoreEntry,
    download: DownloadItem?,
    looking: Boolean,
    onDownload: (StoreEntry) -> Unit,
    onOpenPage: (String) -> Unit,
    onDetail: (StoreEntry?) -> Unit,
) {
    val others = remember(e.id) { CATALOGUE.filter { it.id != e.id && it.category == e.category }.take(3) }
    Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 12.dp, top = 12.dp)) {
        Box(
            Modifier.size(150.dp).border(2.dp, Color.White, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)),
        ) { EntryIcon(e, 150.dp) }
        Column(Modifier.weight(1f).padding(start = 18.dp).verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
            Text(e.name, color = Color.White, fontSize = 30.sp, maxLines = 2)
            Text(e.developer.uppercase(), color = SoftText, fontSize = 17.sp, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (e.repo != null) "Latest release" else "Official page", color = SoftText, fontSize = 18.sp)
                Text("Free", color = Color.White, fontSize = 22.sp)
                val label = when {
                    looking -> "Looking…"
                    download == null -> if (e.repo != null) "Download" else "Open page"
                    download.state == DlState.RUNNING -> "${(download.fraction.coerceAtLeast(0f) * 100).toInt()}%"
                    download.state == DlState.DONE -> "Install"
                    download.state == DlState.FAILED -> "Try again"
                    else -> "Paused"
                }
                OrangeButton("store:get:${e.id}", label, enabled = !looking) {
                    if (download != null && download.state == DlState.FAILED) DownloadEngine.resume(download) else onDownload(e)
                }
            }
            Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.5f)))
            Text(e.blurb, color = Color.White, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (e.repo != null) "The Install button downloads the newest APK from the project's official release page, then Android asks you to confirm the install."
                else "This opens the official page in the Store's browser, where you can pick the right file and download it.",
                color = DimText, fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Open the official page", Modifier.padClickable("store:page:${e.id}", corner = 8.dp, ring = false) { onOpenPage(e.page) }
                    .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
                color = Color.White, fontSize = 15.sp,
            )
        }
        // "You may like": other entries of the same kind.
        Column(Modifier.width(150.dp).padding(start = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("You May Like", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            others.forEach { o ->
                Column(
                    Modifier.padClickable("store:like:${o.id}", corner = 8.dp, pad = 2.dp) { onDetail(o) }.padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(84.dp).border(2.dp, Color.White, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) { EntryIcon(o, 84.dp) }
                    Text(o.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// Browser
// ------------------------------------------------------------------------------------------------------------------------

@Composable
private fun BrowserTab(
    web: WebView,
    browsing: Boolean,
    url: String,
    progress: Int,
    onGo: (String) -> Unit,
    onOpen: (String) -> Unit,
    context: Context,
    onToast: (String) -> Unit,
    onHome: () -> Unit,
) {
    var address by remember(url, browsing) { mutableStateOf(if (browsing) url else "") }
    var bookmarks by remember { mutableStateOf(loadBookmarks(context)) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BarButton("store:web:home", "⌂") { onHome() }
            BarButton("store:web:reload", "↻") { if (browsing) web.reload() }
            BasicTextField(
                value = address, onValueChange = { address = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { onGo(address) }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (address.isEmpty()) Text("Search or type an address", color = SoftText, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            BarButton("store:web:go", "Go") { onGo(address) }
            BarButton("store:web:mark", "★") {
                if (browsing && url.isNotBlank()) {
                    bookmarks = (bookmarks.filter { it.second != url } + (web.title.orEmpty().ifBlank { url } to url))
                    saveBookmarks(context, bookmarks)
                    onToast("Bookmarked")
                }
            }
        }
        if (browsing && progress in 1..99) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(progress / 100f).height(3.dp).background(OrangeTop))
            }
        }
        if (browsing) {
            AndroidView(
                factory = {
                    (web.parent as? ViewGroup)?.removeView(web)
                    web
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 90.dp)) {
                Text("Set pages", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(vertical = 6.dp))
                PRESETS.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { p ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padClickable("store:preset:${p.title}", corner = 10.dp, pad = 3.dp) { onOpen(p.url) }
                                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f))), RoundedCornerShape(10.dp))
                                    .border(1.dp, RowLine.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                            ) {
                                Text(p.title, color = Color.White, fontSize = 19.sp, maxLines = 1)
                                Text(p.blurb, color = SoftText, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text("Your bookmarks", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                if (bookmarks.isEmpty()) {
                    Text("Nothing yet. Open a page and tap ★ to keep it here.", color = SoftText, fontSize = 14.sp)
                }
                bookmarks.forEachIndexed { i, (title, link) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .padClickable("store:mark:$i", corner = 10.dp, pad = 3.dp) { onOpen(link) }
                            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(link, color = DimText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            "✕", Modifier.padClickable("store:markrm:$i", corner = null, pad = 2.dp) {
                                bookmarks = bookmarks.filterIndexed { idx, _ -> idx != i }
                                saveBookmarks(context, bookmarks)
                            }.padding(8.dp),
                            color = Color.White, fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarButton(key: String, label: String, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padClickable(key, corner = 8.dp, pad = 2.dp, ring = false, onClick = onClick)
            .background(if (lit) TabDark else TabDark.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = if (lit) 0.9f else 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = Color.White, fontSize = 15.sp, maxLines = 1,
    )
}

private fun loadBookmarks(context: Context): List<Pair<String, String>> = runCatching {
    val arr = JSONArray(context.getSharedPreferences("qita_store", Context.MODE_PRIVATE).getString("bookmarks", "[]"))
    (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("t") to o.getString("u") } }
}.getOrDefault(emptyList())

private fun saveBookmarks(context: Context, list: List<Pair<String, String>>) {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject().put("t", it.first).put("u", it.second)) }
    context.getSharedPreferences("qita_store", Context.MODE_PRIVATE).edit().putString("bookmarks", arr.toString()).apply()
}

// ------------------------------------------------------------------------------------------------------------------------
// Downloads
// ------------------------------------------------------------------------------------------------------------------------

@Composable
private fun DownloadsTab(onToast: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    val state = rememberLazyListState()
    val list = DownloadEngine.items
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = link, onValueChange = { link = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                        if (link.isEmpty()) Text("Paste a link to download", color = SoftText, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            OrangeButton("store:dl:add", "Download") {
                val t = link.trim()
                if (t.startsWith("http://") || t.startsWith("https://")) {
                    val name = URLUtil.guessFileName(t, null, null)
                    DownloadEngine.enqueue(t, name, if (name.endsWith(".apk", true)) DlKind.APK else DlKind.FILE)
                    link = ""
                    onToast("Downloading $name")
                } else onToast("Paste a full link that starts with http")
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.5f)))
        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                Text("No downloads yet. Start one from the Browser, or paste a link above.", color = SoftText, fontSize = 16.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padScroller { state.animateScrollBy(it) },
                state = state,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp),
            ) {
                items(list.toList(), key = { it.id }) { d -> DownloadRowLarge(d) }
            }
        }
    }
}

@Composable
private fun DownloadRowLarge(d: DownloadItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DownloadGlyph(Modifier.size(46.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d.state != DlState.DONE) DownloadBar(d, Modifier.padding(vertical = 4.dp))
            Text(d.status(), color = SoftText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (d.state) {
            DlState.RUNNING -> SmallAction("store:dl:pause:${d.id}", "Pause") { DownloadEngine.pause(d) }
            DlState.PAUSED, DlState.FAILED -> SmallAction("store:dl:resume:${d.id}", "Resume") { DownloadEngine.resume(d) }
            DlState.DONE -> SmallAction("store:dl:clear:${d.id}", "Clear") { DownloadEngine.remove(d) }
        }
        if (d.state != DlState.DONE) SmallAction("store:dl:cancel:${d.id}", "Cancel") { DownloadEngine.cancel(d) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.25f)))
    Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.55f)))
}

@Composable
private fun SmallAction(key: String, label: String, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padClickable(key, corner = 8.dp, pad = 2.dp, ring = false, onClick = onClick)
            .background(if (lit) TabDark else TabDark.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = if (lit) 0.9f else 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color.White, fontSize = 14.sp, maxLines = 1,
    )
}

/** The Vita's download picture: a blue rounded square with a white down arrow. Also used in the notification panel. */
@Composable
internal fun DownloadGlyph(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3F8BE0), Color(0xFF1C58B8))))
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize(0.55f)) {
            val w = size.width
            val h = size.height
            drawCircle(Color.White, w * 0.05f, Offset(w * 0.5f, h * 0.06f))
            drawCircle(Color.White, w * 0.05f, Offset(w * 0.5f, h * 0.26f))
            val arrow = Path().apply { moveTo(w * 0.12f, h * 0.45f); lineTo(w * 0.88f, h * 0.45f); lineTo(w * 0.5f, h * 0.95f); close() }
            drawPath(arrow, Color.White)
        }
    }
}

/** The thin progress line under a download's title: grey track, green fill with a bright top edge. */
@Composable
internal fun DownloadBar(d: DownloadItem, modifier: Modifier = Modifier) {
    val fraction = d.fraction
    Box(modifier.fillMaxWidth().height(5.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape)) {
        if (fraction >= 0f) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceAtLeast(0.01f)).height(5.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFD6FF9A), Color(0xFF4DB82A))), CircleShape),
            )
        }
    }
}
