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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.pointer.pointerInput
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
import com.qita.ui.DownloadPlacer
import com.qita.ui.DownloadPlan
import com.qita.ui.DownloadPrefs
import com.qita.ui.DownloadRequest
import com.qita.ui.GameFolder
import com.qita.ui.SYSTEMS
import com.qita.ui.systemById
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
    folders: List<GameFolder>,
    onFolders: (List<GameFolder>) -> Unit,
    onAddDownloadFolder: () -> Unit,
    onDetectFolder: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()
    var settingsTab by remember { mutableStateOf(0) }
    var modelMenu by remember { mutableStateOf(false) }
    var customModel by remember { mutableStateOf(false) }
    var loadedModels by remember { mutableStateOf(emptyList<String>()) }
    var aiBusy by remember { mutableStateOf("") }
    var aiStatus by remember { mutableStateOf("") }
    var links by remember { mutableStateOf(UserStores.aiMaxLinks(context)) }
    var dmode by remember { mutableStateOf(UserStores.aiDefaultMode(context)) }
    var extra by remember { mutableStateOf(UserStores.aiExtra(context)) }
    var tidy by remember { mutableStateOf(UserStores.aiTidy(context)) }
    var group by remember { mutableStateOf(UserStores.aiGroup(context)) }
    var askBefore by remember { mutableStateOf(DownloadPrefs.ask(context)) }
    var wifiOnly by remember { mutableStateOf(DownloadPrefs.wifiOnly(context)) }
    var parallel by remember { mutableStateOf(DownloadPrefs.maxParallel(context)) }
    fun saveOptions() = UserStores.setAiOptions(context, links, dmode, extra, tidy, group)
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(UserStores.aiKey(context)) }
    var model by remember { mutableStateOf(UserStores.aiModel(context)) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
    TabStrip(
        labels = listOf("Stores", "AI scan", "Folders", "Downloads", "Banners"),
        selected = settingsTab,
        keyPrefix = "store:settab",
        selectedFill = listOf(OrangeTop, OrangeBottom),
        selectedText = Color.White,
        idleFill = TabDark,
    ) { settingsTab = it }
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth().padScroller { state.animateScrollBy(it) },
        state = state,
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        if (settingsTab == 0) {
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
                        onStores(stores + UserStore("s${System.currentTimeMillis()}", name.trim().ifBlank { host }, url, UserStores.aiDefaultMode(context), emptyList(), 0L))
                        name = ""; link = ""
                        onToast("Added. Open it from My stores to scan it.")
                    }
                }
            }
        }
        }
        if (settingsTab == 1) {
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
                OrangeButton("store:set:ai", "Save key") { UserStores.setAi(context, key, model); saveOptions(); onToast(if (key.isBlank()) "AI scan is off (no key)" else "Saved") }
            }
        }
        item {
            SettingRow("Model", UserStores.modelName(model)) {
                SmallAction("store:ai:model", "Choose") { modelMenu = true }
            }
        }
        if (customModel) item { Field(model, { model = it; UserStores.setAi(context, key, it) }, "Model id", Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        item {
            SettingRow("Models my key can use", if (aiStatus.isNotEmpty()) aiStatus else "Fetch the list from Anthropic, then choose from it") {
                SmallAction("store:ai:load", if (aiBusy == "load") "Loading…" else "Load") {
                    if (aiBusy.isEmpty()) {
                        UserStores.setAi(context, key, model)
                        aiBusy = "load"
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { com.qita.ui.PageScanner.listModels(context) } }
                            aiBusy = ""
                            r.onSuccess { loadedModels = it; aiStatus = "${it.size} models found. Tap Choose."; modelMenu = true }.onFailure { aiStatus = it.message ?: "failed" }
                        }
                    }
                }
            }
        }
        item {
            SettingRow("Links sent to the AI", "More finds more entries but costs more") {
                SmallAction("store:ai:links", "$links") { links = when (links) { 50 -> 100; 100 -> 250; 250 -> 400; else -> 50 }; saveOptions() }
            }
        }
        item {
            SettingRow("Scan mode for new stores") {
                SmallAction("store:ai:mode", MODES[dmode]) { dmode = (dmode + 1) % 3; saveOptions() }
            }
        }
        item { SettingRow("Tidy the names") { SmallAction("store:ai:tidy", if (tidy) "On" else "Off") { tidy = !tidy; saveOptions() } } }
        item { SettingRow("Group into sections") { SmallAction("store:ai:group", if (group) "On" else "Off") { group = !group; saveOptions() } } }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(extra, { extra = it }, "Extra instruction for the AI, for example: only PS2 games")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallAction("store:ai:saveextra", "Save instruction") { saveOptions(); onToast("Saved") }
                    SmallAction("store:ai:test", if (aiBusy == "test") "Testing…" else "Test connection") {
                        if (aiBusy.isEmpty()) {
                            UserStores.setAi(context, key, model)
                            aiBusy = "test"
                            scope.launch {
                                aiStatus = withContext(Dispatchers.IO) { com.qita.ui.PageScanner.testAi(context) }
                                aiBusy = ""
                            }
                        }
                    }
                }
                if (aiStatus.isNotEmpty()) Text(aiStatus, color = SoftText, fontSize = 13.sp)
            }
        }
        }
        if (settingsTab == 2) {
        item { SectionHeader("Download folders") }
        item {
            Text(
                "Give each folder a label (PS2, PSP, Vita...). When something downloads, these are the folders offered, and the best match is chosen for you. The console is found by the folder's name or what is in it; change it under Settings, Games & Emulators.",
                color = DimText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        itemsIndexed(folders, key = { _, f -> f.uri }) { i, f ->
            var text by remember(f.uri) { mutableStateOf(f.label) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(text, { text = it; onFolders(folders.map { g -> if (g.uri == f.uri) g.copy(label = it) else g }) }, "Label, for example PS2", Modifier.weight(1f))
                    SmallAction("store:fd:detect:$i", "Detect") { onDetectFolder(i) }
                    SmallAction("store:fd:rm:$i", "Remove") { onFolders(folders.filter { g -> g.uri != f.uri }) }
                }
                Text(
                    (if (f.systemId == "auto") "Any console" else systemById(f.systemId)?.name ?: "Any console") + "  ·  " + folderName(f),
                    color = DimText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                OrangeButton("store:fd:add", "Add a folder", onClick = onAddDownloadFolder)
            }
        }
        }
        if (settingsTab == 3) {
        item { SectionHeader("Downloads") }
        item {
            SettingRow("Ask before each download", "Unzip it? Which folder? Keep the zip afterwards? (APKs are not asked.) Off uses your last answers.") {
                SmallAction("store:dl:ask", if (askBefore) "On" else "Off") { askBefore = !askBefore; DownloadPrefs.setAsk(context, askBefore) }
            }
        }
        item {
            var unrestricted by remember { mutableStateOf(runCatching { (context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)) }
            SettingRow("Keep downloads running", "Handhelds and phones often stop apps in the background. Allow it to run without battery limits so downloads carry on when the launcher is closed. (Force-stopping the app from Settings still ends them until you open it again.)") {
                SmallAction("store:dl:battery", if (unrestricted) "Allowed" else "Allow") {
                    if (!unrestricted) {
                        val pkg = android.net.Uri.parse("package:" + context.packageName)
                        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    } else unrestricted = runCatching { (context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
                }
            }
        }
        item {
            SettingRow("Wi-Fi only", "On mobile data a download waits until Wi-Fi is back.") {
                SmallAction("store:dl:wifi", if (wifiOnly) "On" else "Off") { wifiOnly = !wifiOnly; DownloadPrefs.setWifiOnly(context, wifiOnly) }
            }
        }
        item {
            SettingRow("Downloads at once", "More start together; the rest wait their turn.") {
                SmallAction("store:dl:par:less", "-") { parallel = (parallel - 1).coerceAtLeast(1); DownloadPrefs.setMaxParallel(context, parallel) }
                Text("$parallel", color = Color.White, fontSize = 16.sp)
                SmallAction("store:dl:par:more", "+") { parallel = (parallel + 1).coerceAtMost(6); DownloadPrefs.setMaxParallel(context, parallel) }
            }
        }
        }
        if (settingsTab == 4) {
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
    }
    if (modelMenu) {
        val ids = (UserStores.MODELS.map { it.first } + loadedModels).distinct()
        com.qita.ui.ui.ContextMenu(
            title = "AI model",
            subtitle = "Now: ${UserStores.modelName(model)}",
            items = ids.take(12).map { id -> MenuItem(UserStores.modelName(id)) { model = id; UserStores.setAi(context, key, id); customModel = false; modelMenu = false } } +
                MenuItem("Type an id myself") { customModel = true; modelMenu = false } +
                MenuItem("Cancel") { modelMenu = false },
            onDismiss = { modelMenu = false },
            layer = 5,
        )
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

/** A readable name for a game folder: its label, else the last part of its address. */
internal fun folderName(f: GameFolder): String =
    android.net.Uri.decode(f.uri).substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder" }

internal fun folderTitle(f: GameFolder): String = f.label.ifBlank { folderName(f) }

@Composable
private fun ChoiceChip(key: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padClickable(key, corner = 8.dp, pad = 2.dp, ring = false, onClick = onClick)
            .litEdge(lit, 8.dp)
            .background(if (selected) Brush.verticalGradient(listOf(OrangeTop, OrangeBottom)) else Brush.verticalGradient(listOf(TabDark.copy(alpha = if (lit) 0.95f else 0.6f), TabDark.copy(alpha = if (lit) 0.95f else 0.6f))), RoundedCornerShape(8.dp))
            .border(if (lit) 2.dp else 1.dp, Color.White.copy(alpha = if (lit || selected) 0.95f else 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = Color.White, fontSize = 15.sp, maxLines = 1,
    )
}

/**
 * The questions asked before a download starts: unzip it (for a zip), which folder it goes to (the best match is preselected),
 * and whether to keep or delete the zip afterwards. APKs never come here.
 */
@Composable
fun DownloadAskDialog(req: DownloadRequest, folders: List<GameFolder>, onConfirm: (DownloadPlan) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val isZip = DownloadPlacer.isArchive(req.name)
    var unzip by remember(req) { mutableStateOf(DownloadPrefs.lastUnzip(context)) }
    var folder by remember(req) { mutableStateOf(DownloadPlacer.suggest(context, folders, req.name)) }
    var deleteZip by remember(req) { mutableStateOf(DownloadPrefs.lastDeleteZip(context)) }
    CompositionLocalProvider(LocalPadLayer provides 5) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .width(460.dp)
                    .heightIn(max = 330.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF2A3F86), Color(0xFF16245C))), shape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), shape)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Download", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = TitleShadow)
                Text(req.name, color = SoftText, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (isZip) {
                    Text("Unpack it?", color = Color.White, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("store:ask:unzip:yes", "Yes, unpack it", unzip) { unzip = true }
                        ChoiceChip("store:ask:unzip:no", "No, keep it packed", !unzip) { unzip = false }
                    }
                }
                Text("Put it in", color = Color.White, fontSize = 16.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.forEachIndexed { i, f -> ChoiceChip("store:ask:folder:$i", folderTitle(f), folder == i) { folder = i } }
                    ChoiceChip("store:ask:folder:none", "Just Downloads", folder < 0) { folder = -1 }
                }
                if (folders.isEmpty()) Text("No game folders yet. Add some in Store Settings, Download folders.", color = DimText, fontSize = 13.sp)
                if (isZip && unzip && folder >= 0) {
                    Text("Afterwards", color = Color.White, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("store:ask:zip:delete", "Delete the archive", deleteZip) { deleteZip = true }
                        ChoiceChip("store:ask:zip:keep", "Keep the archive", !deleteZip) { deleteZip = false }
                    }
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OrangeButton("store:ask:go", "Download") {
                        val f = folders.getOrNull(folder)
                        val plan = DownloadPlan(unzip = isZip && unzip && f != null, folderUri = f?.uri, deleteZip = deleteZip)
                        DownloadPrefs.remember(context, plan, isZip)
                        onConfirm(plan)
                    }
                    SmallAction("store:ask:cancel", "Cancel", onCancel)
                }
            }
        }
    }
}
