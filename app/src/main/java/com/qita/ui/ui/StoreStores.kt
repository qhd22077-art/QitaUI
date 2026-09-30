package com.qita.ui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.BannerCfg
import com.qita.ui.PageScanner
import com.qita.ui.ScanItem
import com.qita.ui.UserStore
import com.qita.ui.UserStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One step of the way into a user's store: a page, and what was found on it. */
data class StoreLevel(val title: String, val url: String, val items: List<ScanItem>)

private val MODES = listOf("Auto", "No AI", "AI")

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, password: Boolean = false) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        cursorBrush = SolidColor(Color.White),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier,
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                if (value.isEmpty()) Text(hint, color = SoftText, fontSize = 15.sp)
                inner()
            }
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = TitleShadow, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp))
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(RowLine.copy(alpha = 0.6f)))
}

/** A line of the settings: a label (and a smaller explanation) on the left, controls on the right. */
@Composable
private fun SettingRow(label: String, note: String? = null, controls: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 17.sp)
            if (note != null) Text(note, color = DimText, fontSize = 13.sp)
        }
        controls()
    }
}

/**
 * The Store's settings: the user's own stores (added by link, scanned with or without AI), the settings of the AI scan,
 * and how the banner strip looks and what it shows.
 */
@Composable
fun StoreSettings(
    stores: List<UserStore>,
    onStores: (List<UserStore>) -> Unit,
    cfg: BannerCfg,
    onCfg: (BannerCfg) -> Unit,
    picFolder: Boolean,
    onToast: (String) -> Unit,
    onOpenStore: (UserStore) -> Unit,
    onChooseFolder: () -> Unit,
    onShuffle: () -> Unit,
    onClearFolder: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberLazyListState()
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(UserStores.aiKey(context)) }
    var model by remember { mutableStateOf(UserStores.aiModel(context)) }
    LazyColumn(
        Modifier.fillMaxSize().padScroller { state.animateScrollBy(it) },
        state = state,
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        item { SectionHeader("My stores") }
        item {
            Text(
                "Add the link of a page that lists downloads (a site's download page, a folder listing). The app reads it and makes a menu from it. You choose what you add; the app adds nothing of its own.",
                color = DimText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        items(stores, key = { it.id }) { st ->
            SettingRow(st.name, st.url.removePrefix("https://").removePrefix("http://")) {
                SmallAction("store:set:mode:${st.id}", "Scan: ${MODES[st.mode]}") {
                    onStores(stores.map { if (it.id == st.id) it.copy(mode = (it.mode + 1) % 3) else it })
                }
                SmallAction("store:set:open:${st.id}", "Open") { onOpenStore(st) }
                SmallAction("store:set:del:${st.id}", "Remove") { onStores(stores.filter { it.id != st.id }); onToast("Removed ${st.name}") }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Name (optional)")
                Field(link, { link = it }, "Link, for example https://example.com/files/")
                OrangeButton("store:set:add", "Add store") {
                    var url = link.trim()
                    if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
                    if (url.length < 10 || '.' !in url) onToast("Type the full link of the page")
                    else {
                        val host = runCatching { java.net.URL(url).host.removePrefix("www.") }.getOrDefault(url)
                        onStores(stores + UserStore("s${System.currentTimeMillis()}", name.trim().ifBlank { host }, url, 0, emptyList(), 0L))
                        name = ""; link = ""
                        onToast("Added. Open it from My stores to scan it.")
                    }
                }
            }
        }
        item { SectionHeader("AI scan") }
        item {
            Text(
                "Without AI the app lists the files and folders it finds on the page by their addresses. With AI it also tidies the names and groups them, and can make sense of pages that are not plain lists. " +
                    "It needs your own Anthropic API key, kept only on this device; when it is used, the page's link names and addresses are sent to Anthropic. Each store can be set to Auto (AI only when the plain scan finds nothing), No AI, or AI.",
                color = DimText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(key, { key = it }, "API key", password = true)
                Field(model, { model = it }, "Model (default ${UserStores.DEFAULT_MODEL})")
                OrangeButton("store:set:ai", "Save") { UserStores.setAi(context, key, model); onToast(if (key.isBlank()) "AI scan is off (no key)" else "Saved") }
            }
        }
        item { SectionHeader("Banners") }
        item {
            SettingRow("Style", "Artwork shows pictures with glare and shadows; Classic is plain colour cards.") {
                SmallAction("store:bn:style", listOf("Artwork", "Classic", "Off")[cfg.style]) { onCfg(cfg.copy(style = (cfg.style + 1) % 3)) }
            }
        }
        item { SettingRow("Show Vita homebrew games") { SmallAction("store:bn:vita", if (cfg.vita) "On" else "Off") { onCfg(cfg.copy(vita = !cfg.vita)) } } }
        item { SettingRow("Show my games") { SmallAction("store:bn:games", if (cfg.games) "On" else "Off") { onCfg(cfg.copy(games = !cfg.games)) } } }
        item { SettingRow("Show emulators") { SmallAction("store:bn:emus", if (cfg.emus) "On" else "Off") { onCfg(cfg.copy(emus = !cfg.emus)) } } }
        item { SettingRow("Show my folder's pictures") { SmallAction("store:bn:pics", if (cfg.pics) "On" else "Off") { onCfg(cfg.copy(pics = !cfg.pics)) } } }
        item { SettingRow("Glare and shadows") { SmallAction("store:bn:glare", if (cfg.glare) "On" else "Off") { onCfg(cfg.copy(glare = !cfg.glare)) } } }
        item { SettingRow("Move by themselves") { SmallAction("store:bn:auto", if (cfg.auto) "On" else "Off") { onCfg(cfg.copy(auto = !cfg.auto)) } } }
        item {
            SettingRow("Time on each banner") {
                SmallAction("store:bn:sec:less", "-") { onCfg(cfg.copy(seconds = (cfg.seconds - 1).coerceAtLeast(2))) }
                Text("${cfg.seconds} s", color = Color.White, fontSize = 16.sp)
                SmallAction("store:bn:sec:more", "+") { onCfg(cfg.copy(seconds = (cfg.seconds + 1).coerceAtMost(12))) }
            }
        }
        item {
            SettingRow("Pictures from my folder", "A different few each week, or shuffle now. How many at a time:") {
                SmallAction("store:bn:cnt:less", "-") { onCfg(cfg.copy(picCount = (cfg.picCount - 1).coerceAtLeast(1))) }
                Text("${cfg.picCount}", color = Color.White, fontSize = 16.sp)
                SmallAction("store:bn:cnt:more", "+") { onCfg(cfg.copy(picCount = (cfg.picCount + 1).coerceAtMost(12))) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction("store:bn:folder", if (picFolder) "Change folder" else "Choose folder", onChooseFolder)
                if (picFolder) SmallAction("store:bn:shuffle", "Shuffle now", onShuffle)
                if (picFolder) SmallAction("store:bn:clear", "Stop using folder", onClearFolder)
            }
        }
    }
}

/**
 * The menu of one of the user's stores: what the scan found, in sections. A folder opens another list (the page it points to is
 * scanned in turn); a file downloads in one tap; an ordinary page opens in the Store's browser.
 */
@Composable
fun StoreItemsPage(
    store: UserStore,
    stack: List<StoreLevel>,
    setStack: (List<StoreLevel>) -> Unit,
    onRootScanned: (List<ScanItem>) -> Unit,
    onGet: (ScanItem) -> Unit,
    onOpenPage: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stackNow by rememberUpdatedState(stack)
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    val state = rememberLazyListState()
    val level = stack.last()

    fun scanLevel(url: String, index: Int) {
        if (busy) return
        busy = true
        note = "Reading the page…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { PageScanner.scan(context, url, store.mode) } }
            busy = false
            r.onSuccess { res ->
                val cur = stackNow
                if (index < cur.size) setStack(cur.toMutableList().also { it[index] = it[index].copy(items = res.items) })
                if (index == 0) onRootScanned(res.items)
                note = res.note
            }.onFailure { note = "Could not read that page (${it.message})" }
        }
    }
    LaunchedEffect(store.id) { if (stack.size == 1 && level.items.isEmpty()) scanLevel(store.url, 0) }

    val shown = remember(level.items, filter) {
        if (filter.isBlank()) level.items else level.items.filter { it.name.contains(filter.trim(), true) }
    }
    val groups = remember(shown) { shown.groupBy { it.section }.toList() }

    Column(Modifier.fillMaxSize().slideIn(80f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stack.joinToString("  ›  ") { it.title }, color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TitleShadow)
                Text(if (busy) "Reading the page…" else note.ifEmpty { "${level.items.size} entries" }, color = DimText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            SmallAction("store:ms:rescan", if (busy) "Scanning…" else "Scan again") { scanLevel(level.url, stack.size - 1) }
            SmallAction("store:ms:browser", "Browser") { onOpenPage(level.url) }
        }
        if (level.items.size > 8) Field(filter, { filter = it }, "Filter this list", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.6f)))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padScroller { state.animateScrollBy(it) },
            state = state,
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            if (shown.isEmpty() && !busy) item {
                Text(
                    "Nothing to list yet. If the page is not a plain list of files, try setting this store's scan to AI in Settings, or open the page in the browser.",
                    color = SoftText, fontSize = 15.sp, modifier = Modifier.padding(24.dp),
                )
            }
            groups.forEach { (section, list) ->
                item(key = "h:$section") {
                    Text(section, color = SoftText, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                }
                items(list, key = { it.url }) { item ->
                    val act = {
                        when {
                            item.folder -> { setStack(stack + StoreLevel(item.name, item.url, emptyList())); scanLevel(item.url, stack.size) }
                            item.page -> onOpenPage(item.url)
                            else -> onGet(item)
                        }
                    }
                    val key = "store:mi:${item.url.hashCode()}"
                    val lit = padHighlighted(key) || padHovered(key)
                    Column(Modifier.slideIn()) {
                        Row(
                            Modifier.fillMaxWidth().height(62.dp)
                                .padClickable(key, corner = 0.dp, ring = false) { act() }
                                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (lit) 0.30f else 0.14f), Color.White.copy(alpha = if (lit) 0.16f else 0.04f)))),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(62.dp).background(Brush.verticalGradient(listOf(Color(if (item.folder) 0xFF8A6A2A else if (item.page) 0xFF3A5A8A else 0xFF1A5FB4), Color.Black.copy(alpha = 0.8f)))).gloss(0.dp, 0.22f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (item.folder) "DIR" else if (item.page) "WEB" else item.url.substringBefore('?').substringAfterLast('.', "").take(4).uppercase().ifEmpty { "FILE" },
                                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1,
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(item.name, color = Color.White, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TitleShadow)
                                Text(runCatching { java.net.URL(item.url).host }.getOrDefault(""), color = DimText, fontSize = 13.sp, maxLines = 1)
                            }
                            GetButton("store:mig:${item.url.hashCode()}", if (item.folder) "Open" else if (item.page) "Open page" else "Download") { act() }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.25f)))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.55f)))
                    }
                }
            }
        }
    }
}
