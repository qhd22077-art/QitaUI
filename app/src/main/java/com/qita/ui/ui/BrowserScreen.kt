package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import com.qita.ui.BrowserData
import com.qita.ui.HistoryEntry
import com.qita.ui.SavedTab
import com.qita.ui.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One tab: its title and address, and (while it is not the shown tab) the saved state of its page. */
private class BTab(val id: String, title0: String, url0: String, var state: Bundle? = null) {
    var title by mutableStateOf(title0)
    var url by mutableStateOf(url0)
}

private fun newId() = java.lang.Long.toHexString(System.nanoTime())

private const val MAX_TABS = 8

/**
 * The Browser app: several tabs, bookmarks (shared with the Store's browser) and history. To stay light only the tab on screen
 * has a live web view; the others are kept as small saved states and woken when chosen.
 */
@Composable
fun BrowserScreen(
    settings: Settings,
    onToast: (String) -> Unit,
    onRotate: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allowInsecure = remember { HashSet<String>() }
    val tabs = remember {
        val saved = BrowserData.loadTabs(context).first
        mutableStateListOf<BTab>().apply {
            if (saved.isEmpty()) add(BTab(newId(), "", "")) else saved.forEach { add(BTab(it.id, it.title, it.url)) }
        }
    }
    var active by remember { mutableStateOf(BrowserData.loadTabs(context).second.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))) }
    var desktop by remember { mutableStateOf(context.getSharedPreferences("qita_store", Context.MODE_PRIVATE).getBoolean("web_desktop", false)) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    // 0 the page (or the start page), 1 bookmarks, 2 history.
    var panel by remember { mutableStateOf(0) }
    var bookmarks by remember { mutableStateOf(loadBookmarks(context)) }
    var history by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var address by remember { mutableStateOf("") }

    // Tabs open links in new tabs through this holder, because making a web view and opening a tab need each other.
    val openTabRef = remember { arrayOf<(String) -> Unit>({}) }

    fun makeWeb(t: BTab): WebView {
        val w = createBrowserWebView(context, desktop)
        installBrowserClients(
            w, context, allowInsecure,
            BrowserHooks(
                onPageStarted = { u -> t.url = u; if (tabs.getOrNull(active) === t) address = u },
                onPageFinished = { u, title ->
                    t.url = u
                    t.title = title
                    if (tabs.getOrNull(active) === t) address = u
                    scope.launch(Dispatchers.IO) { BrowserData.addHistory(context, title, u) }
                },
                onProgress = { p -> if (tabs.getOrNull(active) === t) progress = p },
                onNewTab = { u -> openTabRef[0](u) },
            ),
        )
        val st = t.state
        if (st != null) w.restoreState(st) else if (t.url.isNotBlank()) w.loadUrl(t.url)
        t.state = null
        return w
    }

    /** Puts the live page away: its state is kept in the tab and the web view is destroyed. */
    fun parkWeb() {
        val w = web ?: return
        val t = tabs.getOrNull(active)
        if (t != null) {
            val b = Bundle()
            w.saveState(b)
            t.state = b
            w.url?.takeIf { it.startsWith("http") }?.let { t.url = it }
        }
        w.stopLoading()
        (w.parent as? ViewGroup)?.removeView(w)
        w.destroy()
        web = null
    }

    fun showTab(i: Int) {
        active = i
        val t = tabs[i]
        address = t.url
        progress = 0
        panel = 0
        web = if (t.url.isNotBlank() || t.state != null) makeWeb(t) else null
    }

    fun switchTo(i: Int) {
        if (i !in tabs.indices || i == active) return
        parkWeb()
        showTab(i)
    }

    fun openTab(url: String) {
        if (tabs.size >= MAX_TABS) { onToast("$MAX_TABS tabs are open. Close one first."); return }
        parkWeb()
        tabs.add(BTab(newId(), "", url))
        showTab(tabs.lastIndex)
    }
    openTabRef[0] = { u -> openTab(u) }

    fun closeTab(i: Int) {
        if (tabs.size == 1) {
            // The last tab goes back to the start page.
            parkWeb()
            tabs[0].url = ""; tabs[0].title = ""; tabs[0].state = null
            address = ""
            return
        }
        val wasActive = i == active
        if (wasActive) parkWeb()
        tabs.removeAt(i)
        if (i < active) active -= 1 else if (wasActive) active = active.coerceAtMost(tabs.lastIndex)
        if (wasActive) showTab(active)
    }

    fun navigate(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            " " !in t && "." in t -> "https://$t"
            else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
        }
        val current = tabs.getOrNull(active) ?: return
        panel = 0
        val w = web
        if (w == null) { current.url = url; current.state = null; web = makeWeb(current) } else w.loadUrl(url)
        address = url
    }

    fun back() {
        when {
            panel != 0 -> panel = 0
            web?.canGoBack() == true -> web?.goBack()
            tabs.size > 1 || tabs.getOrNull(active)?.url?.isNotBlank() == true -> closeTab(active)
            else -> onClose()
        }
    }

    LaunchedEffect(Unit) {
        val t = tabs.getOrNull(active) ?: return@LaunchedEffect
        address = t.url
        if (t.url.isNotBlank() && web == null) web = makeWeb(t)
    }
    // The tab list is remembered, so the app reopens where it was.
    LaunchedEffect(tabs.map { it.url to it.title }, active) {
        BrowserData.saveTabs(context, tabs.map { SavedTab(it.id, it.title, it.url) }, active)
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { BrowserData.saveTabs(context, tabs.map { SavedTab(it.id, it.title, it.url) }, active) }
            runCatching { parkWeb() }
        }
    }
    // Lists for the start page and the panels.
    LaunchedEffect(panel, web == null) {
        if (panel != 0 || web == null) {
            bookmarks = loadBookmarks(context)
            history = withContext(Dispatchers.IO) { BrowserData.history(context) }
        }
    }
    // The right stick scrolls the page.
    LaunchedEffect(web) {
        val w = web ?: return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                val v = com.qita.ui.Controller.scrollY
                val a = kotlin.math.abs(v)
                if (!com.qita.ui.Controller.cursorMode && a > 0.15f) {
                    val n = (a - 0.15f) / 0.85f
                    w.scrollBy(0, (kotlin.math.sign(v) * n * n * 1800f * dt * context.resources.displayMetrics.density).toInt())
                }
            }
        }
    }
    BackHandler(enabled = true) { back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF22428F), Color(0xFF1A3379), Color(0xFF142460))))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, showHome = false)
            TabStrip(
                labels = tabs.map { t -> t.title.ifBlank { if (t.url.isBlank()) "New tab" else Uri.parse(t.url).host.orEmpty() }.take(16) },
                selected = active,
                keyPrefix = "browser:tab",
                selectedFill = listOf(OrangeTop, OrangeBottom),
                selectedText = Color.White,
                idleFill = TabDark,
            ) { switchTo(it) }
            // Address bar.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BarButton("browser:reload", if (progress in 1..99) "✕" else "↻") {
                    val w = web
                    if (w != null) { if (progress in 1..99) w.stopLoading() else w.reload() }
                }
                BasicTextField(
                    value = address, onValueChange = { address = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { navigate(address) }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (address.isEmpty()) Text("Search or type an address", color = SoftText, fontSize = 15.sp)
                            inner()
                        }
                    },
                )
                BarButton("browser:go", "Go") { navigate(address) }
                BarButton("browser:mark", "★") {
                    val url = web?.url
                    if (url != null && url.startsWith("http")) {
                        bookmarks = (bookmarks.filter { it.second != url } + ((web?.title).orEmpty().ifBlank { url } to url))
                        saveBookmarks(context, bookmarks)
                        onToast("Bookmarked")
                    }
                }
            }
            // The rest of the buttons.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BarButton("browser:back", "◀") { if (web?.canGoBack() == true) web?.goBack() }
                BarButton("browser:fwd", "▶") { if (web?.canGoForward() == true) web?.goForward() }
                BarButton("browser:new", "+ New tab") { openTab("") }
                BarButton("browser:close", "Close tab") { closeTab(active) }
                BarButton("browser:bookmarks", "Bookmarks") { panel = if (panel == 1) 0 else 1 }
                BarButton("browser:history", "History") { panel = if (panel == 2) 0 else 2 }
                BarButton("browser:up", "▲") { web?.pageUp(false) }
                BarButton("browser:down", "▼") { web?.pageDown(false) }
                BarButton("browser:desk", if (desktop) "Mobile site" else "Desktop site") {
                    desktop = !desktop
                    context.getSharedPreferences("qita_store", Context.MODE_PRIVATE).edit().putBoolean("web_desktop", desktop).apply()
                    web?.let { applyWebMode(context, it, desktop); it.reload() }
                    onToast(if (desktop) "Desktop site" else "Mobile site")
                }
                BarButton("browser:chrome", "Open in Chrome") {
                    val url = web?.url
                    if (url != null && url.startsWith("http")) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { onToast("No other browser found") }
                    }
                }
                BarButton("browser:rotate", "Rotate") { onRotate() }
            }
            if (progress in 1..99) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(progress / 100f).height(3.dp).background(OrangeTop))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val w = web
                when {
                    panel == 1 -> LinkList(
                        title = "Bookmarks", empty = "Nothing yet. Open a page and tap ★ to keep it here.",
                        rows = bookmarks.map { Triple(it.first, it.second, "bm") },
                        onOpen = { navigate(it) },
                        onRemove = { url -> bookmarks = bookmarks.filter { it.second != url }; saveBookmarks(context, bookmarks) },
                        action = null,
                    )
                    panel == 2 -> LinkList(
                        title = "History", empty = "No pages visited yet.",
                        rows = history.map { Triple(it.title, it.url, "h") },
                        onOpen = { navigate(it) },
                        onRemove = { url -> BrowserData.removeHistory(context, url); history = history.filter { it.url != url } },
                        action = "Clear history" to { BrowserData.clearHistory(context); history = emptyList() },
                    )
                    w != null -> key(w) {
                        AndroidView(
                            factory = { (w.parent as? ViewGroup)?.removeView(w); w },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> StartPage(bookmarks, history, onOpen = { navigate(it) })
                }
            }
        }
        BackButton(
            onClick = { back() },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
            key = "browser:backbutton",
        )
    }
}

/** What a tab with no page shows: the set pages, then the bookmarks and the latest history. */
@Composable
private fun StartPage(bookmarks: List<Pair<String, String>>, history: List<HistoryEntry>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 90.dp)) {
        Text("Set pages", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(vertical = 6.dp), style = TitleShadow)
        PRESETS.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { p ->
                    Column(
                        Modifier
                            .weight(1f)
                            .padClickable("browser:preset:${p.title}", corner = 10.dp, pad = 3.dp) { onOpen(p.url) }
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
        if (bookmarks.isNotEmpty()) {
            Text("Bookmarks", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp), style = TitleShadow)
            bookmarks.take(8).forEachIndexed { i, (title, url) -> LinkRow("browser:smark:$i", title, url, onOpen = { onOpen(url) }, onRemove = null) }
        }
        if (history.isNotEmpty()) {
            Text("Recent", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp), style = TitleShadow)
            history.take(8).forEachIndexed { i, e -> LinkRow("browser:shist:$i", e.title, e.url, onOpen = { onOpen(e.url) }, onRemove = null) }
        }
    }
}

/** A list of pages (bookmarks or history) with a heading, an optional action button and a remove cross on each row. */
@Composable
private fun LinkList(
    title: String,
    empty: String,
    rows: List<Triple<String, String, String>>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    action: Pair<String, () -> Unit>?,
) {
    val state = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f), style = TitleShadow)
            if (action != null && rows.isNotEmpty()) SmallAction("browser:panelaction", action.first, action.second)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.6f)))
        if (rows.isEmpty()) {
            Text(empty, color = SoftText, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padScroller { state.animateScrollBy(it) },
                state = state,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp, ),
            ) {
                items(rows, key = { it.third + it.second }) { (t, url, kind) ->
                    LinkRow("browser:$kind:${url.hashCode()}", t, url, onOpen = { onOpen(url) }, onRemove = { onRemove(url) })
                }
            }
        }
    }
}

@Composable
private fun LinkRow(key: String, title: String, url: String, onOpen: () -> Unit, onRemove: (() -> Unit)?) {
    val lit = padHighlighted(key) || padHovered(key)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .padClickable(key, corner = 10.dp, pad = 3.dp, ring = false, onClick = onOpen)
            .litEdge(lit, 10.dp)
            .background(Color.White.copy(alpha = if (lit) 0.22f else 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { url }, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, color = DimText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            Text("✕", Modifier.padClickable("$key:rm", corner = null, pad = 2.dp, onClick = onRemove).padding(8.dp), color = Color.White, fontSize = 16.sp)
        }
    }
}
