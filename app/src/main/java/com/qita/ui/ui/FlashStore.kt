package com.qita.ui.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Covers
import com.qita.ui.DlState
import com.qita.ui.DownloadEngine
import com.qita.ui.FlashEntry
import com.qita.ui.FlashLibrary
import com.qita.ui.FlashSite
import com.qita.ui.FlashSiteStore
import com.qita.ui.FlashSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Flash games tab of the Store: the game list (kept in the QitaUI repository, a copy bundled and the last download cached, so it
 * opens offline) and a search of the Internet Archive. A game is downloaded like any other file (see the Downloads tab) and, when it is
 * done, goes into the app's own Flash library, where it plays offline.
 */
class FlashStoreState(private val context: Context, private val scope: CoroutineScope) {
    var query by mutableStateOf("")
    /** 0 the game list, 1 the Internet Archive, 2 the user's own sites. */
    var source by mutableIntStateOf(0)
    /** The sites the user added; the one chosen (its address); and the address last looked at, which can be saved as a site. */
    var sites by mutableStateOf(FlashSiteStore.list(context))
    var site by mutableStateOf<String?>(null)
    var scanned by mutableStateOf<String?>(null)
    var entries by mutableStateOf<List<FlashEntry>>(emptyList())
    var status by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var fetching by mutableStateOf<Set<String>>(emptySet())
    var started by mutableStateOf<Set<String>>(emptySet())
    /** What happened to each game's Get, shown on its own row (a note at the top of the list would be out of sight). */
    var notes by mutableStateOf<Map<String, String>>(emptyMap())
    private var job: Job? = null

    private fun note(id: String, text: String) { notes = notes + (id to text) }

    /** Keeps the address last looked at as a site (named after its host) and chooses it. */
    fun saveSite() {
        val a = scanned ?: return
        val full = if (a.contains("://")) a else "https://$a"
        val name = runCatching { java.net.URL(full).host.removePrefix("www.") }.getOrDefault(a)
        sites = FlashSiteStore.add(context, name, a)
        site = a
    }

    /** Forgets the chosen site. */
    fun removeSite() {
        site?.let { sites = FlashSiteStore.remove(context, it) }
        site = null
        entries = emptyList()
        status = null
    }

    fun search() {
        job?.cancel()
        job = scope.launch {
            busy = true
            status = null
            val q = query.trim()
            if (source == 2) {
                // A link typed in the box is looked at once; otherwise the chosen site is, and the text filters what it has.
                val isLink = q.contains("://") || (q.contains('.') && !q.contains(' '))
                val address = if (isLink) q else site
                if (address == null) {
                    entries = emptyList()
                    status = "Choose one of your sites above, or paste a link here: a web page, a game-list file, an Internet Archive page or a game (.swf or .zip)."
                } else {
                    val r = withContext(Dispatchers.IO) { FlashSources.scanAddress(address) }
                    scanned = address
                    entries = if (isLink || q.isEmpty()) r.entries else r.entries.filter { it.title.contains(q, true) }
                    status = if (r.entries.isNotEmpty() && entries.isEmpty()) "Nothing there matches “$q”." else r.note
                }
            } else if (source == 0) {
                val all = withContext(Dispatchers.IO) { FlashSources.catalogue(context) }
                entries = if (q.isEmpty()) all else all.filter { it.title.contains(q, true) || it.blurb.contains(q, true) }
                status = when {
                    all.isEmpty() -> "The game list is empty. Games are added to flash/catalogue.json in the QitaUI repository; the Internet Archive tab searches a big collection."
                    entries.isEmpty() -> "Nothing in the game list matches “$q”."
                    else -> null
                }
            } else {
                val result = withContext(Dispatchers.IO) { FlashSources.searchArchive(q) }
                val found = result.entries
                if (found == null) {
                    entries = emptyList()
                    status = "The Internet Archive could not be searched: ${result.error ?: "no answer"}. Check the connection (games you already added play offline)."
                } else {
                    entries = found
                    status = if (found.isEmpty()) (result.note ?: "Nothing found for “$q”.") + " Try other words, or add a site under My sites." else result.note
                }
            }
            busy = false
        }
    }

    /**
     * Downloads [e]. An Archive item is looked up first: loose .swf files are fetched one by one, or, if it only has small zips, those
     * are fetched and the .swf files in them are added when they finish. Whatever happens is said on the game's own row, and the
     * button always comes back (it cannot stay at "…").
     */
    fun get(e: FlashEntry) {
        if (e.id in fetching || e.id in started) return
        fetching = fetching + e.id
        notes = notes - e.id
        scope.launch {
            try {
                withTimeout(60_000) { fetch(e) }
            } catch (ex: TimeoutCancellationException) {
                note(e.id, "The Archive did not answer in time. Try again.")
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                note(e.id, "Something went wrong: ${ex.message ?: ex.javaClass.simpleName}")
            } finally {
                fetching = fetching - e.id
            }
        }
    }

    private suspend fun fetch(e: FlashEntry) {
        // What to download: (link, file name). Zips carry a mark in their name so the launcher knows to unpack them.
        val todo = ArrayList<Pair<String, String>>()
        var zips = false
        if (e.url.isNotEmpty()) {
            // A link to a zip is unpacked when it arrives; anything else is taken to be the .swf itself.
            if (e.url.substringBefore('?').endsWith(".zip", true)) {
                zips = true
                todo.add(e.url to "${FlashLibrary.safeBase(e.title)} [flashzip].zip")
            } else todo.add(e.url to FlashLibrary.fileNameFor(e.title))
        } else {
            val r = withContext(Dispatchers.IO) { FlashSources.resolveArchive(e.id.removePrefix("ia:")) }
            val base = FlashLibrary.safeBase(e.title)
            when {
                r.swf.size == 1 -> todo.add(r.swf[0].url to "$base.swf")
                r.swf.size > 1 -> r.swf.forEach { f -> todo.add(f.url to FlashLibrary.fileNameFor(f.name.substringAfterLast('/').removeSuffix(".swf").removeSuffix(".SWF"))) }
                r.zips.isNotEmpty() -> {
                    zips = true
                    r.zips.forEachIndexed { i, f -> todo.add(f.url to "$base${if (r.zips.size > 1) " ${i + 1}" else ""} [flashzip].zip") }
                }
                else -> { note(e.id, r.note ?: "Nothing to download in this item."); return }
            }
        }
        // A single game's picture becomes its cover (best effort).
        if (todo.size == 1 && !zips) {
            e.thumb?.let { t ->
                withContext(Dispatchers.IO) { FlashSources.picture(t)?.let { Covers.save(context, FlashLibrary.idFor(todo[0].second), it) } }
            }
        }
        var begun = 0
        for ((url, name) in todo) {
            val running = DownloadEngine.items.firstOrNull { it.url == url && (it.state == DlState.RUNNING || it.state == DlState.PAUSED) }
            if (running == null) DownloadEngine.enqueue(url, name)
            begun++
        }
        started = started + e.id
        note(
            e.id,
            if (zips) "Downloading a zip; the Flash games in it are added when it is done (see the Downloads tab)."
            else if (begun > 1) "Downloading $begun games. Watch the Downloads tab; they appear in Games, under Flash."
            else "Downloading. Watch the Downloads tab; it appears in Games, under Flash, when it is done.",
        )
    }
}

/** The rows of the Flash games tab, for the catalogue's list. */
fun LazyListScope.flashItems(st: FlashStoreState) {
    item(key = "flash-head") { FlashHead(st) }
    if (st.busy) item(key = "flash-busy") { Text("Loading…", Modifier.padding(16.dp), color = SoftText, fontSize = 15.sp) }
    st.status?.let { s -> item(key = "flash-status") { Text(s, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = SoftText, fontSize = 14.sp) } }
    items(st.entries, key = { it.id }) { e ->
        FlashRow(e, e.id in st.fetching, e.id in st.started, st.notes[e.id]) { st.get(e) }
    }
}

@Composable
private fun FlashHead(st: FlashStoreState) {
    LaunchedEffect(Unit) { if (st.entries.isEmpty() && !st.busy) st.search() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Game list", "Internet Archive", "My sites").forEachIndexed { i, label ->
                val key = "flash:src:$i"
                val lit = padHighlighted(key) || padHovered(key)
                val on = st.source == i
                Text(
                    label,
                    Modifier
                        .padClickable(key, corner = 14.dp, pad = 2.dp, ring = false) { if (!on) { st.source = i; st.entries = emptyList(); st.search() } }
                        .litEdge(lit, 14.dp)
                        .background(if (on) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = if (lit) 0.28f else 0.12f), RoundedCornerShape(14.dp))
                        .border(if (lit) 2.dp else 1.dp, Color.White.copy(alpha = if (lit || on) 0.95f else 0.45f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = st.query, onValueChange = { st.query = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { st.search() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                        if (st.query.isEmpty()) Text(
                            when (st.source) { 0 -> "Search the game list"; 1 -> "Search the Internet Archive"; else -> "Paste a link to a page, a list or a game" },
                            color = SoftText, fontSize = 16.sp,
                        )
                        inner()
                    }
                },
            )
            BarButton("flash:search", "Search") { st.search() }
        }
        if (st.source == 2) {
            // The user's own sites: tap one to see its games; a link looked at once can be kept as a site.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                st.sites.forEachIndexed { i, s ->
                    SmallAction("flash:site:$i", (if (st.site == s.address) "✓ " else "") + s.name) { st.site = s.address; st.query = ""; st.search() }
                }
                if (st.scanned != null && st.sites.none { it.address == st.scanned }) SmallAction("flash:site:save", "Keep this as a site") { st.saveSite() }
                if (st.site != null) SmallAction("flash:site:remove", "Forget this site") { st.removeSite() }
            }
        }
        Text(
            "Games you get here are kept inside the launcher, play offline, and show up in Games under Flash. A .swf file you download in the Browser is added too. " +
                "Only take games that are free to share or that you have the right to keep.",
            color = DimText, fontSize = 12.sp,
        )
    }
}

@Composable
private fun FlashRow(e: FlashEntry, fetching: Boolean, started: Boolean, note: String?, onGet: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 74.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The item's own picture when it has one (loaded as the row appears); else the first letter.
            val picture by produceState<ImageBitmap?>(null, e.thumb) {
                value = e.thumb?.let { t -> withContext(Dispatchers.IO) { FlashSources.picture(t)?.asImageBitmap() } }
            }
            Box(Modifier.size(74.dp).background(Brush.verticalGradient(listOf(Color(0xFFD0501E), Color.Black.copy(alpha = 0.8f)))), contentAlignment = Alignment.Center) {
                val p = picture
                if (p != null) Image(p, null, Modifier.size(74.dp), contentScale = ContentScale.Crop)
                else Text(e.title.take(1).uppercase(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text(e.source, color = SoftText, fontSize = 12.sp, maxLines = 1)
                Text(e.title, color = Color.White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.blurb.isNotBlank()) Text(e.blurb, color = DimText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // What happened when Get was tapped, right where it was tapped.
                if (note != null) Text(note, color = Color(0xFFFFB070), fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            GetButton("flash:get:${e.id}", if (started) "Added" else if (fetching) "…" else "Get") { onGet() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.55f)))
    }
}
