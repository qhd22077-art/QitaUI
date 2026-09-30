package com.qita.ui.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import com.qita.ui.GameLibrary
import com.qita.ui.StoreBanners
import com.qita.ui.BannerCfg
import com.qita.ui.UserStore
import com.qita.ui.UserStores
import com.qita.ui.ScanItem
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.runtime.LaunchedEffect
import com.qita.ui.VitaHb
import com.qita.ui.VitaDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// The colours of the real PlayStation Store, measured from photos of it.
private val StoreTop = Color(0xFF22428F)
internal val StoreMid = Color(0xFF1A3379)
private val StoreBottom = Color(0xFF142460)
private val StoreIndigo = Color(0xFF2A2A94)
private val BarTop = Color(0xFF6F79B0)
private val BarBottom = Color(0xFF36408F)
internal val TabDark = Color(0xFF172248)
internal val RowLine = Color(0xFF4F66AC)
internal val SoftText = Color(0xFFA6B4E8)
internal val DimText = Color(0xFF8798D2)
internal val OrangeTop = Color(0xFFF58A3A)
internal val OrangeBottom = Color(0xFFE6621C)

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
    StoreEntry("scummvm", "ScummVM", "ScummVM Team", "Play classic adventure games on your phone. Its downloads page also lists games their authors released as freeware.", 0, "SVM", 0xFF8A5A2A, null, "https://www.scummvm.org/downloads/"),
    StoreEntry("redream", "Redream", "Redream", "Sega Dreamcast games.", 0, "DC", 0xFFC0602A, null, "https://redream.io/download"),
    StoreEntry("mupen", "Mupen64Plus FZ", "Francisco Zurita", "Nintendo 64 games.", 0, "N64", 0xFF2E8A4F, "fzurita/mupen64plus-ae", "https://github.com/fzurita/mupen64plus-ae"),
    StoreEntry("cores", "RetroArch cores", "Libretro", "The official download page for RetroArch's cores and updates.", 0, "CORE", 0xFF3A3A4A, null, "https://buildbot.libretro.com/"),
    StoreEntry("gamenative", "GameNative", "GameNative", "Windows games from your own Steam, Epic and GOG libraries.", 0, "PC", 0xFF2A3A5A, "utkarshdalal/GameNative", "https://github.com/utkarshdalal/GameNative"),
    StoreEntry("winlator", "Winlator", "Bruno Sousa", "Run Windows programs on Android.", 0, "WIN", 0xFF2A4AA0, "brunodev85/winlator", "https://github.com/brunodev85/winlator"),
    StoreEntry("scummfree", "ScummVM freeware games", "ScummVM", "Beneath a Steel Sky, Flight of the Amazon Queen, Lure of the Temptress, Dreamweb and Drascula were released as freeware by their authors. The downloads page links to each.", 1, "SCUM", 0xFF8A5A2A, null, "https://www.scummvm.org/downloads/"),
    StoreEntry("iapd", "Public-domain software", "Internet Archive", "Search the Internet Archive for public-domain software. Only download what is free to share.", 1, "IA", 0xFF5A4A3A, null, "https://archive.org/search?query=public+domain+games&and%5B%5D=mediatype%3A%22software%22"),
    StoreEntry("iafree", "Freeware games", "Internet Archive", "Search the Internet Archive for games released as freeware by their makers. Only download what is free to share.", 1, "FW", 0xFF4A5A6A, null, "https://archive.org/search?query=freeware+games&and%5B%5D=mediatype%3A%22software%22"),
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

internal val TitleShadow = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 4f))

private fun storeBackground() = Brush.verticalGradient(listOf(StoreTop, StoreMid, StoreBottom))

/** A page shown in place of one that would not load, saying why, with buttons to try again (or, for https, over http or despite the certificate). */
private fun showPageError(view: WebView, url: String, reason: String, certificate: Boolean) {
    fun esc(t: String) = android.text.TextUtils.htmlEncode(t)
    val http = if (url.startsWith("https://")) "http://" + url.removePrefix("https://") else null
    val extra = buildString {
        if (certificate) append("<a class=b href=\"qita-insecure://${esc(Uri.encode(url))}\">Open anyway</a>")
        if (http != null && !certificate) append("<a class=b href=\"${esc(http)}\">Try without https</a>")
    }
    val html = "<html><head><meta name=viewport content='width=device-width,initial-scale=1'><style>" +
        "body{margin:0;padding:28px;background:#14245f;color:#fff;font-family:sans-serif}h2{margin:0 0 8px}" +
        "p{color:#a6b4e8;word-break:break-all}.b{display:inline-block;margin:10px 10px 0 0;padding:10px 18px;border-radius:8px;" +
        "background:#f58a3a;color:#fff;text-decoration:none}</style></head><body><h2>This page did not load</h2>" +
        "<p>${esc(reason.ifBlank { "Something went wrong" })}</p><p>${esc(url)}</p>" +
        "<a class=b href=\"${esc(url)}\">Try again</a>$extra</body></html>"
    view.loadDataWithBaseURL(url, html, "text/html", "UTF-8", url)
}

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
    // Banner pictures: from the user's own folder (changing weekly), and screenshots of their games.
    var pics by remember { mutableStateOf(emptyList<String>()) }
    var picRev by remember { mutableStateOf(0) }
    var snapRev by remember { mutableStateOf(0) }
    var picFolder by remember { mutableStateOf(StoreBanners.folder(context) != null) }
    var cfg by remember { mutableStateOf(BannerCfg.load(context)) }
    fun updateCfg(n: BannerCfg) { cfg = n; BannerCfg.save(context, n) }
    LaunchedEffect(picRev, cfg.picCount) { pics = withContext(Dispatchers.IO) { StoreBanners.picks(context, cfg.picCount) } }
    LaunchedEffect(Unit) { if (StoreBanners.ensureSnaps(context) > 0) snapRev++ }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            StoreBanners.setFolder(context, uri)
            picFolder = true
            picRev++
            onToast("Banner folder set. A few of its pictures will show in the Store, a different few each week.")
        }
    }
    // The Vita homebrew list, loaded the first time its tab is opened.
    var vita by remember { mutableStateOf<List<VitaHb>?>(null) }
    var vitaLoading by remember { mutableStateOf(false) }
    var vitaError by remember { mutableStateOf<String?>(null) }
    var vitaType by remember { mutableStateOf(0) }
    var vitaDetail by remember { mutableStateOf<VitaHb?>(null) }
    fun loadVita(force: Boolean) {
        if (vitaLoading) return
        vitaLoading = true
        vitaError = null
        scope.launch {
            val result = runCatching { VitaDb.load(context, force) }
            result.onSuccess { vita = it }.onFailure { vitaError = it.message ?: "unknown error" }
            vitaLoading = false
        }
    }
    LaunchedEffect(segment, tab) { if (tab == 0 && vita == null && !vitaLoading) loadVita(false) }
    // The user's own stores, made from links.
    var stores by remember { mutableStateOf(UserStores.load(context)) }
    fun updateStores(n: List<UserStore>) { stores = n; UserStores.save(context, n) }
    var openStoreId by remember { mutableStateOf<String?>(null) }
    var storeStack by remember { mutableStateOf(emptyList<StoreLevel>()) }
    fun storeBack() { if (storeStack.size > 1) storeStack = storeStack.dropLast(1) else { storeStack = emptyList(); openStoreId = null } }
    fun openStore(st: UserStore) {
        tab = 0; segment = 5; detail = null; vitaDetail = null
        openStoreId = st.id
        storeStack = listOf(StoreLevel(st.name, st.url, st.items))
    }
    fun getFile(item: ScanItem) {
        val name = URLUtil.guessFileName(item.url, null, null)
        DownloadEngine.enqueue(item.url, name, if (name.endsWith(".apk", true)) DlKind.APK else DlKind.FILE)
        onToast("Downloading $name. Progress is in the notification panel.")
    }

    // The browser is created once and kept while the user moves between tabs.
    var browsing by remember { mutableStateOf(false) }
    var pageUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    // Sites the user chose to open although their certificate is not trusted.
    val allowInsecure = remember { HashSet<String>() }
    val web = remember {
        WebView(context).apply {
            // "this.settings" is the web view's; plain "settings" would be the launcher's own.
            val ws = this.settings
            ws.javaScriptEnabled = true
            ws.domStorageEnabled = true
            ws.databaseEnabled = true
            ws.builtInZoomControls = true
            ws.displayZoomControls = false
            ws.useWideViewPort = true
            ws.loadWithOverviewMode = true
            ws.javaScriptCanOpenWindowsAutomatically = true
            ws.setSupportMultipleWindows(true)
            ws.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Many sites turn away the stock web view's identity ("; wv"); introduce it as a plain mobile Chrome.
            ws.userAgentString = WebSettings.getDefaultUserAgent(context).replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }
    DisposableEffect(web) {
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { if (url != null) pageUrl = url }
            override fun onPageFinished(view: WebView?, url: String?) { pageTitle = view?.title.orEmpty(); if (url != null) pageUrl = url }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                when (u.scheme?.lowercase()) {
                    "http", "https", "about", "data", "blob" -> return false
                    "qita-insecure" -> {
                        // "Open anyway" on the certificate warning page.
                        val target = Uri.decode(u.schemeSpecificPart.removePrefix("//"))
                        runCatching { allowInsecure.add(Uri.parse(target).host.orEmpty()) }
                        view.loadUrl(target)
                        return true
                    }
                    "file", "content", "javascript" -> return true
                    "intent" -> {
                        // Links that try to open another app: use the page's fallback address if there is one.
                        runCatching {
                            val intent = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                            val fallback = intent.getStringExtra("browser_fallback_url")
                            if (fallback != null) view.loadUrl(fallback)
                        }
                        return true
                    }
                    else -> {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        return true
                    }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showPageError(view, request.url.toString(), error.description?.toString().orEmpty(), false)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                val host = runCatching { Uri.parse(error.url).host.orEmpty() }.getOrDefault("")
                if (host in allowInsecure) handler.proceed()
                else { handler.cancel(); showPageError(view, error.url, "The site's security certificate is not trusted", true) }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }

            // Links that open a new window or tab (target="_blank", window.open) load in this same page.
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val popup = WebView(view.context)
                popup.settings.javaScriptEnabled = true
                var done = false
                fun take(url: String?) {
                    if (done || url.isNullOrBlank() || url == "about:blank") return
                    done = true
                    web.loadUrl(url)
                    web.post { runCatching { popup.stopLoading(); popup.destroy() } }
                }
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean { take(r.url.toString()); return true }
                    override fun onPageStarted(v: WebView?, url: String?, favicon: android.graphics.Bitmap?) { take(url) }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }
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

    fun downloadVita(hb: VitaHb) {
        val existing = vitaDownload(started, hb)
        when {
            existing != null && existing.state == DlState.DONE -> onToast("Already downloaded. It is in your Download folder.")
            existing != null && existing.state != DlState.FAILED -> onToast("Already downloading. Progress is in the notification panel.")
            else -> {
                started["vita:${hb.id}"] = DownloadEngine.enqueue(hb.download, "${hb.name} ${hb.version}".trim() + ".vpk", DlKind.FILE)
                onToast("Downloading ${hb.name}. Progress is in the notification panel.")
            }
        }
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
    BackHandler(enabled = !menu && tab == 3) { tab = 0 }
    BackHandler(enabled = !menu && tab == 0 && openStoreId != null && vitaDetail == null) { storeBack() }
    BackHandler(enabled = !menu && tab == 0 && vitaDetail != null) { vitaDetail = null }
    BackHandler(enabled = !menu && tab == 0 && detail != null) { detail = null }
    BackHandler(enabled = !menu && tab == 1 && browsing && web.canGoBack()) { web.goBack() }
    BackHandler(enabled = !menu && tab == 1 && browsing && !web.canGoBack()) { browsing = false }

    Box(Modifier.fillMaxSize().background(storeBackground()).pointerInput(Unit) { detectTapGestures { } }) {
        // A soft indigo wash on the right and a light sheen across the top, as in the real store.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, StoreIndigo.copy(alpha = 0.35f))))
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), 0f, size.height * 0.35f))
            // Darker corners pull the eye to the middle, like light falling off on a real screen.
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), center, size.maxDimension * 0.75f))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, showHome = false)
            TabBar(tab, onTab = { tab = it; if (it == 0) { detail = null; vitaDetail = null } }, onSearch = { searching = !searching; tab = if (tab == 2 || tab == 3) 0 else tab }, onSettings = { tab = 3 })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> if (vitaDetail != null) {
                        val hb = vitaDetail!!
                        VitaDetailPage(
                            hb, vitaDownload(started, hb),
                            others = vita.orEmpty().filter { it.id != hb.id && it.type == hb.type }.take(3),
                            onDownload = ::downloadVita, onOpen = { vitaDetail = it },
                        )
                    } else if (openStoreId != null && storeStack.isNotEmpty()) {
                        val st = stores.firstOrNull { it.id == openStoreId }
                        if (st == null) { openStoreId = null } else StoreItemsPage(
                            st, storeStack, { storeStack = it },
                            onRootScanned = { items -> updateStores(stores.map { if (it.id == st.id) it.copy(items = items, scannedAt = System.currentTimeMillis()) else it }) },
                            onGet = ::getFile, onOpenPage = ::openPage, onToast = onToast,
                        )
                    } else Catalogue(
                        segment, { segment = it }, detail, { detail = it }, searching, query, { query = it },
                        started, looking, ::download, ::openPage, pics, snapRev, cfg, stores, ::openStore, { tab = 3 },
                        VitaUi(vita, vitaLoading, vitaError, vitaType, { vitaType = it }, { vitaDetail = it }, ::downloadVita, { loadVita(true) }, { openPage(VitaDb.SITE) }, started),
                    )
                    1 -> BrowserTab(web, browsing, pageUrl, progress, ::go, ::openPage, context, onToast, { browsing = false; web.loadUrl("about:blank") })
                    2 -> DownloadsTab(onToast)
                    else -> StoreSettings(
                        stores, ::updateStores, cfg, ::updateCfg, picFolder, onToast,
                        onOpenStore = ::openStore,
                        onChooseFolder = { folderPicker.launch(null) },
                        onShuffle = { StoreBanners.shuffle(context); picRev++; onToast("Picked another few pictures") },
                        onClearFolder = { StoreBanners.clearFolder(context); picFolder = false; picRev++; onToast("Banner folder removed") },
                    )
                }
            }
        }
        RoundButton(
            onClick = { if (tab == 3) tab = 0 else if (tab == 0 && openStoreId != null && vitaDetail == null) storeBack() else if (tab == 0 && vitaDetail != null) vitaDetail = null else if (tab == 0 && detail != null) detail = null else if (tab == 1 && browsing && web.canGoBack()) web.goBack() else onClose() },
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
                items = listOfNotNull(
                    MenuItem("Downloads") { menu = false; tab = 2 },
                    MenuItem("Notifications") { menu = false; onOpenNotifications() },
                    MenuItem("Catalogue") { menu = false; tab = 0; detail = null },
                    MenuItem(if (picFolder) "Change banner folder" else "Choose a banner folder") { menu = false; folderPicker.launch(null) },
                    if (picFolder) MenuItem("Shuffle banner pictures") { menu = false; StoreBanners.shuffle(context); picRev++; onToast("Picked another few pictures") } else null,
                    if (picFolder) MenuItem("Stop using my folder") { menu = false; StoreBanners.clearFolder(context); picFolder = false; picRev++; onToast("Banner folder removed") } else null,
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
private fun TabBar(tab: Int, onTab: (Int) -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Brush.verticalGradient(listOf(BarTop, BarBottom)))
            .gloss(0.dp, 0.20f)
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
        val litSet = padHighlighted("store:settings") || padHovered("store:settings")
        Text(
            "Settings",
            Modifier
                .padClickable("store:settings", corner = 8.dp, ring = false, onClick = onSettings)
                .background(if (litSet || tab == 3) TabDark.copy(alpha = 0.9f) else TabDark.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White, fontSize = 15.sp, maxLines = 1,
        )
        val lit = padHighlighted("store:search") || padHovered("store:search")
        Text(
            "Search",
            Modifier
                .padding(start = 8.dp, end = 10.dp)
                .padClickable("store:search", corner = 8.dp, ring = false, onClick = onSearch)
                .background(if (lit) TabDark.copy(alpha = 0.9f) else TabDark.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = Color.White, fontSize = 15.sp, maxLines = 1,
        )
    }
}

/** A thin dark line along the bottom of the bar, like the real one's edge. */
private fun Modifier.drawLine(): Modifier = this.drawBehind {
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
            .shadow(if (lit) 12.dp else 8.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF6A77BE), Color(0xFF34428F), Color(0xFF1E2A66)), Offset(60f, 40f), 120f))
            .drawBehind {
                // The glare: a pale lens across the upper half.
                drawOval(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.03f)), 0f, size.height * 0.5f),
                    topLeft = Offset(size.width * 0.14f, size.height * 0.05f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.45f),
                )
            }
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
internal fun OrangeButton(key: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padClickable(key, corner = 10.dp, ring = false, onClick = onClick)
            .shadow(if (lit) 8.dp else 4.dp, RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(if (enabled) listOf(OrangeTop, OrangeBottom) else listOf(Color(0xFF8A94C8), Color(0xFF5A66B0))),
                RoundedCornerShape(10.dp),
            )
            .gloss(10.dp, 0.35f)
            .border(if (lit) 2.dp else 1.dp, if (lit) Color.White else Color(0xFFFFC08A), RoundedCornerShape(10.dp))
            .padding(horizontal = 28.dp, vertical = 9.dp),
        color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center, maxLines = 1,
    )
}

// ------------------------------------------------------------------------------------------------------------------------
// Catalogue
// ------------------------------------------------------------------------------------------------------------------------

/** A soft light across the top half of a glossy part (bars, buttons), like the glass of the real store's controls. */
internal fun Modifier.gloss(radius: Dp = 8.dp, strength: Float = 0.28f): Modifier = this.drawBehind {
    val r = radius.toPx()
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = strength), Color.White.copy(alpha = strength * 0.12f)), 0f, size.height * 0.5f),
        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width - 2.dp.toPx(), size.height * 0.5f),
        cornerRadius = CornerRadius(r, r),
    )
}

/** Slides a piece in from the right and fades it up when it first appears, so lists and pages move instead of popping. */
internal fun Modifier.slideIn(fromX: Float = 60f): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(260, easing = VitaMotion.Ease)) }
    graphicsLayer {
        alpha = p.value
        translationX = (1f - p.value) * fromX
    }
}

private val RAIL_GROUPS = listOf("0-9", "A-D", "E-H", "I-L", "M-P", "Q-T", "U-Z")

private fun groupOf(name: String): String {
    val c = name.trim().firstOrNull()?.uppercaseChar() ?: return "0-9"
    return when {
        c !in 'A'..'Z' -> "0-9"
        c <= 'D' -> "A-D"
        c <= 'H' -> "E-H"
        c <= 'L' -> "I-L"
        c <= 'P' -> "M-P"
        c <= 'T' -> "Q-T"
        else -> "U-Z"
    }
}

/**
 * The catalogue is one long page that scrolls as a whole: a banner strip that moves by itself, a segmented bar that sticks to the
 * top, then the rows. The Vita list adds the store's letter rail down the left side (A-D, E-H, ...), which jumps to a letter.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    pics: List<String>,
    snapRev: Int,
    cfg: BannerCfg,
    stores: List<UserStore>,
    onOpenStore: (UserStore) -> Unit,
    onAddStore: () -> Unit,
    vita: VitaUi,
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
    val vitaAll = vita.list
    val vitaRows = remember(vitaAll, query, vita.type) {
        val rows = vitaAll.orEmpty().filter {
            (vita.type < 2 || it.type == vita.type - 1) &&
                (query.isBlank() || it.name.contains(query.trim(), true) || it.author.contains(query.trim(), true))
        }
        // "New" keeps the list's own order (newest first); the others run A to Z.
        if (vita.type == 0) rows else rows.sortedBy { it.name.trim().uppercase() }
    }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val appContext = LocalContext.current
    // Covers of the user's own games, already fetched for the Games screen.
    val myGames = remember(snapRev) {
        runCatching {
            GameLibrary.games(appContext).filter { GameLibrary.coverFile(appContext, it.id).exists() || StoreBanners.snapFile(appContext, it.id).exists() }
        }.getOrDefault(emptyList())
    }
    val banners = remember(vitaAll, myGames, pics, cfg) {
        val art = cfg.style == 0
        buildList {
            // The user's own pictures come first (artwork style only: the classic cards have no pictures).
            if (art && cfg.pics) pics.forEachIndexed { i, path -> add(BannerArt("p$i", "", "", path, false, 0xFF203060, "", null)) }
            if (cfg.vita) vitaAll?.filter { it.type == 1 && it.iconUrl != null }?.take(5)?.forEach { hb ->
                add(BannerArt("v${hb.id}", hb.name, "Vita homebrew  ·  ${hb.author}", if (art) hb.iconUrl else null, true, 0xFF1A5FB4, "VITA") { vita.onOpen(hb) })
            }
            if (cfg.games) myGames.take(5).forEach { g ->
                val snap = StoreBanners.snapFile(appContext, g.id)
                // A screenshot fills the banner; a box cover is shown whole over a blurred copy of itself.
                if (snap.exists()) add(BannerArt("g${g.id}", g.title, "From your library", if (art) snap.path else null, false, 0xFF203060, "GAME", null))
                else add(BannerArt("g${g.id}", g.title, "From your library", if (art) GameLibrary.coverFile(appContext, g.id).path else null, true, 0xFF203060, "GAME", null))
            }
            if (cfg.emus) CATALOGUE.take(7).forEach { e -> add(BannerArt(e.id, e.name, e.developer, if (art) bannerUrl(e) else null, false, e.color, e.short) { onDetail(e) }) }
        }
    }
    val onVita = segment == 3
    val rail = onVita && vitaAll != null && vita.type >= 1 && vitaRows.isNotEmpty()
    val railPad = if (rail) 128.dp else 0.dp
    // Items above the first row: the banners, the sticky bar and (for the Vita list) its filter bar.
    val hasBanners = cfg.style != 2 && banners.isNotEmpty()
    val headerCount = (if (hasBanners) 1 else 0) + 1 + (if (onVita && vitaAll != null) 1 else 0)
    val groups = remember(vitaRows) { vitaRows.map { groupOf(it.name) } }
    val current by remember(groups, headerCount) {
        derivedStateOf {
            if (groups.isEmpty()) "" else groups[(state.firstVisibleItemIndex - headerCount).coerceIn(0, groups.size - 1)]
        }
    }
    Box(Modifier.fillMaxSize()) {
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
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padScroller { state.animateScrollBy(it) },
                state = state,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp),
            ) {
                if (hasBanners) item(key = "banners") {
                    // The strip drifts up slower than the list, so it sits a little behind the rows.
                    Box(Modifier.graphicsLayer { translationY = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset * 0.3f else 0f }) {
                        BannerStrip(banners, railPad, cfg)
                    }
                }
                stickyHeader(key = "segments") {
                    Box(Modifier.fillMaxWidth().background(StoreMid)) {
                        Segmented(listOf("Featured", "Emulators", "Free games", "Vita homebrew", "All", "My stores"), segment, onSegment)
                    }
                }
                if (segment == 5) {
                    if (stores.isEmpty()) item(key = "no-stores") {
                        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Add a store by its link, and the app reads the page and makes a menu of what it offers to download.", color = SoftText, fontSize = 16.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            OrangeButton("store:addfirst", "Add a store", onClick = onAddStore)
                        }
                    } else items(stores, key = { it.id }) { st ->
                        Column(Modifier.slideIn()) {
                            Row(
                                Modifier.fillMaxWidth().height(74.dp)
                                    .padClickable("store:ms:${st.id}", corner = 0.dp, ring = false) { onOpenStore(st) }
                                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)))),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(74.dp).background(Brush.verticalGradient(listOf(Color(0xFF2A5BA8), Color.Black.copy(alpha = 0.8f)))).gloss(0.dp, 0.22f), contentAlignment = Alignment.Center) {
                                    Text(st.name.take(1).uppercase(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                                }
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(if (st.items.isEmpty()) "Not scanned yet" else "${st.items.size} entries", color = SoftText, fontSize = 13.sp, maxLines = 1)
                                    Text(st.name, color = Color.White, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TitleShadow)
                                    Text(st.url.removePrefix("https://").removePrefix("http://"), color = DimText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                GetButton("store:msopen:${st.id}", "Open") { onOpenStore(st) }
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.25f)))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.55f)))
                        }
                    }
                } else if (onVita) {
                    if (vitaAll == null) {
                        item(key = "vita-status") { VitaStatus(vita) }
                    } else {
                        item(key = "vita-types") { Box(Modifier.padding(start = railPad)) { Segmented(VITA_TYPES, vita.type, vita.onType) } }
                        items(vitaRows, key = { it.id }) { hb ->
                            VitaRow(hb, vitaDownload(vita.started, hb), railPad, { vita.onGet(hb) }) { vita.onOpen(hb) }
                        }
                    }
                } else {
                    items(if (segment == 0) list.take(8) else list, key = { it.id }) { e ->
                        CatalogueRow(e, started[e.id], looking[e.id] == true, { onDownload(e) }) { onDetail(e) }
                    }
                }
            }
        }
        if (rail) {
            val present = RAIL_GROUPS.filter { it in groups }
            val at = present.indexOf(current).coerceAtLeast(0)
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 10.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                present.drop((at - 1).coerceAtLeast(0)).take(4).forEach { g ->
                    RailTile(g, g == current) {
                        val index = groups.indexOfFirst { it == g }.coerceAtLeast(0)
                        scope.launch { state.animateScrollToItem(headerCount + index, -with(density) { 58.dp.roundToPx() }) }
                    }
                }
            }
        }
    }
}

/** One tile of the letter rail: the current group is big, the others small and dim. */
@Composable
private fun RailTile(label: String, big: Boolean, onClick: () -> Unit) {
    val key = "store:rail:$label"
    val lit = padHighlighted(key) || padHovered(key)
    val width = if (big) 96.dp else 60.dp
    val height = if (big) 92.dp else 46.dp
    Box(
        Modifier
            .size(width, height)
            .padClickable(key, corner = 0.dp, ring = false, onClick = onClick)
            .background(
                Brush.verticalGradient(
                    if (big) listOf(Color(0xFF244B86), Color(0xFF6A90C2)) else listOf(Color(0xFF2B5797), Color(0xFF2B5797)).map { if (lit) it.copy(alpha = 1f) else it.copy(alpha = 0.8f) },
                ),
            )
            .then(if (lit) Modifier.border(2.dp, Color.White) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (big) Color.White else Color(0xFFB6C6E6), fontSize = if (big) 34.sp else 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private val BANNER_REPOS = mapOf(
    "retroarch" to "libretro/RetroArch", "ppsspp" to "hrydgard/ppsspp", "dolphin" to "dolphin-emu/dolphin",
    "duckstation" to "stenzek/duckstation", "scummvm" to "scummvm/scummvm", "redream" to "",
)

/** A picture for an entry: its GitHub project's social image (the project's own artwork), when it has a project. */
private fun bannerUrl(e: StoreEntry): String? =
    (e.repo ?: BANNER_REPOS[e.id])?.takeIf { it.contains('/') }?.let { "https://opengraph.githubassets.com/1/$it" }

/** One banner: real artwork when there is some, the entry's colour and letters until it arrives or if it cannot be loaded. */
private class BannerArt(
    val id: String,
    val title: String,
    val sub: String,
    val image: String?,
    /** A tall picture (an icon or a box cover) shown whole over a blurred copy of itself, rather than cropped across. */
    val portrait: Boolean,
    val color: Long,
    val short: String,
    val onClick: (() -> Unit)?,
)

@Composable
private fun ArtBanner(b: BannerArt, glare: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val click = b.onClick
    Box(
        Modifier
            .width(270.dp)
            .height(112.dp)
            .shadow(if (glare) 10.dp else 0.dp, shape)
            .then(if (click != null) Modifier.padClickable("store:banner:${b.id}", corner = 8.dp, pad = 3.dp, onClick = click) else Modifier)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(b.color), Color.Black.copy(alpha = 0.85f))))
            .border(1.dp, Color.White.copy(alpha = 0.4f), shape),
    ) {
        val fallback: @Composable () -> Unit = {
            Text(b.short, color = Color.White.copy(alpha = 0.22f), fontSize = 60.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
        }
        if (b.image == null) fallback()
        else if (b.portrait) {
            RemoteImage(b.image, Modifier.fillMaxSize().blur(16.dp).graphicsLayer { alpha = 0.65f }) { }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            RemoteImage(
                b.image,
                Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(width = 76.dp, height = 92.dp).shadow(8.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp)),
            ) { Text(b.short, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center)) }
        } else {
            RemoteImage(b.image, Modifier.fillMaxSize()) { fallback() }
        }
        // A dark band at the foot for the words, and a pale diagonal glare across the corner.
        if (b.title.isNotBlank()) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)), 120f, 300f)))
        if (glare) Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color.Transparent), Offset.Zero, Offset(size.width * 0.65f, size.height * 0.9f)))
        }
        if (b.title.isNotBlank()) Column(Modifier.align(Alignment.BottomStart).padding(start = if (b.portrait && b.image != null) 96.dp else 14.dp, end = 10.dp, bottom = 9.dp)) {
            Text(b.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TitleShadow)
            Text(b.sub, color = SoftText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The banner strip moves by itself every few seconds (and stops while a finger or the gamepad is scrolling it). */
@Composable
private fun BannerStrip(banners: List<BannerArt>, startPad: Dp, cfg: BannerCfg) {
    val strip = rememberLazyListState()
    LaunchedEffect(banners.size, cfg.auto, cfg.seconds) {
        while (cfg.auto && banners.size > 1) {
            delay(cfg.seconds * 1000L)
            if (!strip.isScrollInProgress) strip.animateScrollToItem((strip.firstVisibleItemIndex + 1) % banners.size)
        }
    }
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        state = strip,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp + startPad, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(banners, key = { it.id }) { b -> ArtBanner(b, cfg.glare) }
    }
}

/** A small orange button at the end of a row: one tap starts the download without opening the page. */
@Composable
internal fun GetButton(key: String, label: String, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padding(end = 14.dp)
            .padClickable(key, corner = 8.dp, pad = 2.dp, ring = false, onClick = onClick)
            .shadow(if (lit) 7.dp else 3.dp, RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(OrangeTop, OrangeBottom)), RoundedCornerShape(8.dp))
            .gloss(8.dp, 0.35f)
            .border(if (lit) 2.dp else 1.dp, if (lit) Color.White else Color(0xFFFFC08A), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = Color.White, fontSize = 16.sp, maxLines = 1,
    )
}

/** The download of [hb]: the one started from this screen, or any other in the list for the same file (so it survives leaving the Store). */
private fun vitaDownload(started: Map<String, DownloadItem>, hb: VitaHb): DownloadItem? =
    started["vita:${hb.id}"] ?: DownloadEngine.items.firstOrNull { it.url == hb.download }

@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .height(40.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF6670B6), Color(0xFF323E8C))))
            .gloss(10.dp, 0.22f)
            .border(1.dp, Color(0xFF172248), shape),
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
            if (i < options.size - 1) Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF172248).copy(alpha = 0.8f)))
        }
    }
}

/** A catalogue row like the store's: a bevelled square icon, a dim line, a big white title, the developer and a Download button. */
@Composable
private fun CatalogueRow(e: StoreEntry, download: DownloadItem?, looking: Boolean, onGet: () -> Unit, onClick: () -> Unit) {
    val lit = padHighlighted("store:row:${e.id}") || padHovered("store:row:${e.id}")
    Column(Modifier.slideIn()) {
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
                Text(e.name, color = Color.White, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TitleShadow)
                Text(e.developer, color = DimText, fontSize = 15.sp, maxLines = 1)
            }
            GetButton(
                "store:get:${e.id}",
                when {
                    looking -> "Looking…"
                    download != null && download.state == DlState.RUNNING -> "${(download.fraction.coerceAtLeast(0f) * 100).toInt()}%"
                    download != null && download.state == DlState.DONE -> "Install"
                    e.repo != null -> "Download"
                    else -> "Open"
                },
                onGet,
            )
        }
        // The line under each row: dark, then a light one, so the rows look cut into the page.
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.55f)))
    }
}

/** The square picture of an entry: its colour with its short name, and a dark bottom band like the store's "PSP GAME" label. */
@Composable
private fun EntryIcon(e: StoreEntry, size: Dp) {
    Box(
        Modifier
            .size(size)
            .background(Brush.verticalGradient(listOf(Color(e.color), Color.Black.copy(alpha = 0.8f))))
            .gloss(0.dp, 0.22f),
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
    Row(Modifier.fillMaxSize().slideIn(80f).padding(start = 18.dp, end = 12.dp, top = 12.dp)) {
        Box(
            Modifier.size(150.dp).shadow(14.dp, RoundedCornerShape(6.dp)).border(2.dp, Color.White, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)),
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
                    Box(Modifier.size(84.dp).shadow(8.dp, RoundedCornerShape(6.dp)).border(2.dp, Color.White, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) { EntryIcon(o, 84.dp) }
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
            val clipboard = LocalClipboardManager.current
            OrangeButton("store:dl:paste", "Paste & get") {
                val t = clipboard.getText()?.text?.trim().orEmpty()
                if (t.startsWith("http://") || t.startsWith("https://")) {
                    val name = URLUtil.guessFileName(t, null, null)
                    DownloadEngine.enqueue(t, name, if (name.endsWith(".apk", true)) DlKind.APK else DlKind.FILE)
                    onToast("Downloading $name")
                } else onToast("Copy a full link that starts with http first")
            }
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
internal fun SmallAction(key: String, label: String, onClick: () -> Unit) {
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

// ------------------------------------------------------------------------------------------------------------------------
// Vita homebrew (from VitaDB)
// ------------------------------------------------------------------------------------------------------------------------

/** What the Vita homebrew tab shows and does; built by [StoreScreen], which owns the loading. */
private class VitaUi(
    val list: List<VitaHb>?,
    val loading: Boolean,
    val error: String?,
    val type: Int,
    val onType: (Int) -> Unit,
    val onOpen: (VitaHb) -> Unit,
    val onGet: (VitaHb) -> Unit,
    val onRetry: () -> Unit,
    val onSite: () -> Unit,
    val started: Map<String, DownloadItem>,
)

private val VITA_TYPES = listOf("New", "A-Z", "Games", "Ports", "Emulators", "Tools")

/** What shows in place of the Vita list while it loads, or when it could not be loaded. */
@Composable
private fun VitaStatus(v: VitaUi) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (v.error == null) {
            Text(if (v.loading) "Loading the Vita homebrew list…" else "Opening the Vita homebrew list…", color = SoftText, fontSize = 17.sp, textAlign = TextAlign.Center)
        } else {
            Text("Couldn't load the Vita homebrew list (${v.error}).", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OrangeButton("store:vita:retry", "Try again", onClick = v.onRetry)
                OrangeButton("store:vita:site", "Open VitaDB", onClick = v.onSite)
            }
        }
    }
}

/** The picture of a homebrew: its icon from VitaDB, or a blue square with its first letter until (or unless) it arrives. */
@Composable
private fun VitaIcon(hb: VitaHb, size: Dp) {
    RemoteImage(hb.iconUrl, Modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A5FB4), Color.Black.copy(alpha = 0.8f)))),
            contentAlignment = Alignment.Center,
        ) { Text(hb.name.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun VitaRow(hb: VitaHb, download: DownloadItem?, startPad: Dp, onGet: () -> Unit, onClick: () -> Unit) {
    val key = "store:vita:${hb.id}"
    val lit = padHighlighted(key) || padHovered(key)
    Column(Modifier.padding(start = startPad).slideIn()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(74.dp)
                .padClickable(key, corner = 0.dp, ring = false, onClick = onClick)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (lit) 0.30f else 0.14f), Color.White.copy(alpha = if (lit) 0.16f else 0.04f)))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VitaIcon(hb, 74.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(hb.date.ifEmpty { "Vita homebrew" }, color = SoftText, fontSize = 13.sp, maxLines = 1)
                Text(hb.name, color = Color.White, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TitleShadow)
                Text(hb.author, color = DimText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GetButton(
                "store:vget:${hb.id}",
                when {
                    download != null && download.state == DlState.RUNNING -> "${(download.fraction.coerceAtLeast(0f) * 100).toInt()}%"
                    download != null && download.state == DlState.DONE -> "Downloaded"
                    else -> "Download"
                },
                onGet,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.55f)))
    }
}

@Composable
private fun VitaDetailPage(hb: VitaHb, download: DownloadItem?, others: List<VitaHb>, onDownload: (VitaHb) -> Unit, onOpen: (VitaHb) -> Unit) {
    Row(Modifier.fillMaxSize().slideIn(80f).padding(start = 18.dp, end = 12.dp, top = 12.dp)) {
        Box(Modifier.size(150.dp).shadow(14.dp, RoundedCornerShape(6.dp)).border(2.dp, Color.White, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) { VitaIcon(hb, 150.dp) }
        Column(Modifier.weight(1f).padding(start = 18.dp).verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
            Text(hb.name, color = Color.White, fontSize = 30.sp, maxLines = 2)
            Text(hb.author.uppercase(), color = SoftText, fontSize = 17.sp, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (hb.version.isNotBlank()) "Version ${hb.version}" else "Homebrew", color = SoftText, fontSize = 18.sp)
                Text("Free", color = Color.White, fontSize = 22.sp)
                val label = when {
                    download == null -> "Download"
                    download.state == DlState.RUNNING -> "${(download.fraction.coerceAtLeast(0f) * 100).toInt()}%"
                    download.state == DlState.DONE -> "Downloaded"
                    download.state == DlState.FAILED -> "Try again"
                    else -> "Paused"
                }
                OrangeButton("store:vita:get:${hb.id}", label) {
                    if (download != null && download.state == DlState.FAILED) DownloadEngine.resume(download) else onDownload(hb)
                }
            }
            if (hb.date.isNotBlank()) Text("Updated ${hb.date}", color = DimText, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.5f)))
            Text(hb.description.ifBlank { "No description." }, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "This is a .vpk file. When it has downloaded, it is in your Download folder: open Vita3K and choose File, then Install .vpk.",
                color = DimText, fontSize = 14.sp,
            )
        }
        Column(Modifier.width(150.dp).padding(start = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (others.isNotEmpty()) Text("You May Like", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            others.forEach { o ->
                Column(
                    Modifier.padClickable("store:vlike:${o.id}", corner = 8.dp, pad = 2.dp) { onOpen(o) }.padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(84.dp).shadow(8.dp, RoundedCornerShape(6.dp)).border(2.dp, Color.White, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) { VitaIcon(o, 84.dp) }
                    Text(o.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
