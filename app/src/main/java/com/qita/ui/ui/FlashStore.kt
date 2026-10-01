package com.qita.ui.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.qita.ui.FlashSources
import kotlinx.coroutines.CoroutineScope
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
    /** 0 the game list, 1 the Internet Archive. */
    var source by mutableIntStateOf(0)
    var entries by mutableStateOf<List<FlashEntry>>(emptyList())
    var status by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var fetching by mutableStateOf<Set<String>>(emptySet())
    var started by mutableStateOf<Set<String>>(emptySet())
    private var job: Job? = null

    fun search() {
        job?.cancel()
        job = scope.launch {
            busy = true
            status = null
            val q = query.trim()
            if (source == 0) {
                val all = withContext(Dispatchers.IO) { FlashSources.catalogue(context) }
                entries = if (q.isEmpty()) all else all.filter { it.title.contains(q, true) || it.blurb.contains(q, true) }
                status = when {
                    all.isEmpty() -> "The game list is empty. Games are added to flash/catalogue.json in the QitaUI repository; the Internet Archive tab searches a big collection."
                    entries.isEmpty() -> "Nothing in the game list matches “$q”."
                    else -> null
                }
            } else {
                val found = withContext(Dispatchers.IO) { FlashSources.searchArchive(q) }
                if (found == null) {
                    entries = emptyList()
                    status = "The Internet Archive could not be reached. Check the connection (games you already added play offline)."
                } else {
                    entries = found
                    status = if (found.isEmpty()) "Nothing found for “$q”." else null
                }
            }
            busy = false
        }
    }

    /** Downloads [e]: looks up its .swf file if it is an Internet Archive item, saves its picture as the cover, starts the download. */
    fun get(e: FlashEntry) {
        if (e.id in fetching || e.id in started) return
        fetching = fetching + e.id
        scope.launch {
            val url = if (e.url.isNotEmpty()) e.url else withContext(Dispatchers.IO) { FlashSources.resolveArchive(e.id) }
            if (url == null) {
                status = "“${e.title}” has no .swf file to download (it may be packed in a zip, or the Archive could not be reached)."
                fetching = fetching - e.id
                return@launch
            }
            val fileName = FlashLibrary.fileNameFor(e.title)
            // Its picture becomes the game's cover (best effort).
            e.thumb?.let { t ->
                withContext(Dispatchers.IO) {
                    FlashSources.picture(t)?.let { Covers.save(context, FlashLibrary.idFor(fileName), it) }
                }
            }
            val running = DownloadEngine.items.firstOrNull { it.url == url && (it.state == DlState.RUNNING || it.state == DlState.PAUSED) }
            if (running == null) DownloadEngine.enqueue(url, fileName)
            fetching = fetching - e.id
            started = started + e.id
            status = "Downloading “${e.title}”. Watch it in the Downloads tab; it appears in Games, under Flash, when it is done."
        }
    }
}

/** The rows of the Flash games tab, for the catalogue's list. */
fun LazyListScope.flashItems(st: FlashStoreState) {
    item(key = "flash-head") { FlashHead(st) }
    if (st.busy) item(key = "flash-busy") { Text("Loading…", Modifier.padding(16.dp), color = SoftText, fontSize = 15.sp) }
    st.status?.let { s -> item(key = "flash-status") { Text(s, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = SoftText, fontSize = 14.sp) } }
    items(st.entries, key = { it.id }) { e ->
        FlashRow(e, e.id in st.fetching, e.id in st.started) { st.get(e) }
    }
}

@Composable
private fun FlashHead(st: FlashStoreState) {
    LaunchedEffect(Unit) { if (st.entries.isEmpty() && !st.busy) st.search() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Game list", "Internet Archive").forEachIndexed { i, label ->
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
                        if (st.query.isEmpty()) Text(if (st.source == 0) "Search the game list" else "Search the Internet Archive", color = SoftText, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            BarButton("flash:search", "Search") { st.search() }
        }
        Text(
            "Games you get here are kept inside the launcher, play offline, and show up in Games under Flash. A .swf file you download in the Browser is added too.",
            color = DimText, fontSize = 12.sp,
        )
    }
}

@Composable
private fun FlashRow(e: FlashEntry, fetching: Boolean, started: Boolean, onGet: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(74.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(74.dp).background(Brush.verticalGradient(listOf(Color(0xFFD0501E), Color.Black.copy(alpha = 0.8f)))), contentAlignment = Alignment.Center) {
                Text(e.title.take(1).uppercase(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(e.source, color = SoftText, fontSize = 12.sp, maxLines = 1)
                Text(e.title, color = Color.White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.blurb.isNotBlank()) Text(e.blurb, color = DimText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GetButton("flash:get:${e.id}", if (started) "Added" else if (fetching) "…" else "Get") { onGet() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine.copy(alpha = 0.55f)))
    }
}
