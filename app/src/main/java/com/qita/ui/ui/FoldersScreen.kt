package com.qita.ui.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.qita.ui.DownloadPlacer
import com.qita.ui.FileItem
import com.qita.ui.FileManager
import com.qita.ui.GameLibrary
import com.qita.ui.SYSTEMS
import com.qita.ui.Settings
import com.qita.ui.SortBy
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Dialog { NONE, RENAME, NEW_FOLDER, SEARCH, DELETE, PROPS, SEND, MORE }

private fun sizeText(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(b / (1L shl 10).toDouble())
    else -> "$b B"
}

private fun glyph(i: FileItem): String {
    if (i.isDir) return "📁"
    return when (i.file.extension.lowercase()) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp" -> "🖼"
        "mp3", "ogg", "wav", "flac", "m4a" -> "♫"
        "mp4", "mkv", "avi", "webm", "mov" -> "🎞"
        "zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz" -> "🗜"
        "apk" -> "📦"
        "txt", "log", "md", "json", "xml", "ini", "cfg" -> "📄"
        else -> if (SYSTEMS.any { s -> i.file.extension.lowercase() in s.exts }) "🎮" else "▫"
    }
}

/** The Folders app: a file manager over all of the device's storage. */
@Composable
fun FoldersScreen(settings: Settings, onToast: (String) -> Unit, onScanGames: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(FileManager.hasAccess()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) access = FileManager.hasAccess() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val roots = remember { FileManager.roots(context) }
    var cwd by remember { mutableStateOf(roots.first().second) }
    var rootDir by remember { mutableStateOf(roots.first().second) }
    var tab by remember { mutableIntStateOf(0) }
    var grid by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SortBy.NAME) }
    var items by remember { mutableStateOf(emptyList<FileItem>()) }
    var query by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var clip by remember { mutableStateOf(emptyList<File>()) }
    var clipMove by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    var input by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<FileItem?>(null) }
    var propsText by remember { mutableStateOf("") }
    var pins by remember { mutableStateOf(FileManager.pins(context)) }
    var recents by remember { mutableStateOf(FileManager.recents(context)) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(cwd, showHidden, sort, reload, query, access) {
        if (!access) return@LaunchedEffect
        items = withContext(Dispatchers.IO) {
            if (query.isNotBlank()) FileManager.search(cwd, query, showHidden) else FileManager.list(cwd, showHidden, sort)
        }
    }
    fun open(dir: File) {
        cwd = dir
        query = ""
        selecting = false
        selected = emptySet()
        FileManager.addRecent(context, dir.path)
        recents = FileManager.recents(context)
        tab = 0
    }
    fun chosen(): List<FileItem> = items.filter { it.path in selected }
    fun work(label: String, job: suspend () -> String) {
        if (busy) return
        busy = true
        onToast("$label…")
        scope.launch {
            val msg = runCatching { withContext(Dispatchers.IO) { job() } }.getOrElse { "Failed: ${it.message}" }
            busy = false
            selecting = false
            selected = emptySet()
            reload++
            onToast(msg)
        }
    }
    fun leave() { dialog = Dialog.NONE }

    BackHandler(enabled = true) {
        when {
            dialog != Dialog.NONE -> leave()
            selecting -> { selecting = false; selected = emptySet() }
            query.isNotBlank() -> query = ""
            tab != 0 -> tab = 0
            cwd.path != rootDir.path && cwd.parentFile != null -> open(cwd.parentFile!!)
            else -> onClose()
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF22428F), Color(0xFF1A3379), Color(0xFF142460)))).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, showHome = false)
            if (!access) {
                AccessCard(onOpen = {
                    val pkg = Uri.parse("package:${context.packageName}")
                    val intent = if (Build.VERSION.SDK_INT >= 30) Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg) else Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { runCatching { context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                })
            } else {
                TabStrip(
                    labels = listOf("Browse", "Shortcuts", "Recent"),
                    selected = tab, keyPrefix = "folders:tab",
                    selectedFill = listOf(OrangeTop, OrangeBottom), selectedText = Color.White, idleFill = TabDark,
                ) { tab = it }
                when (tab) {
                    0 -> {
                        // Path bar: tappable crumbs, a star to pin this folder.
                        val crumbs = remember(cwd, rootDir) {
                            val list = ArrayList<File>()
                            var f: File? = cwd
                            while (f != null && f.path.length >= rootDir.path.length) { list.add(0, f); f = f.parentFile }
                            list
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                crumbs.forEachIndexed { i, f ->
                                    Text(
                                        (if (i == 0) "⌂" else "›  " + f.name) + "  ",
                                        Modifier.padClickable("folders:crumb:$i", corner = 8.dp, pad = 2.dp) { open(f) }.padding(horizontal = 4.dp, vertical = 6.dp),
                                        color = Color.White, fontSize = 15.sp, maxLines = 1,
                                    )
                                }
                            }
                            BarButton("folders:pin", if (cwd.path in pins) "★" else "☆") { pins = FileManager.togglePin(context, cwd.path) }
                        }
                        // Toolbar.
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BarButton("folders:up", "▲ Up") { cwd.parentFile?.takeIf { cwd.path != rootDir.path }?.let { open(it) } }
                            BarButton("folders:view", if (grid) "List" else "Grid") { grid = !grid }
                            BarButton("folders:sort", "Sort: ${sort.label}") { sort = SortBy.values()[(sort.ordinal + 1) % SortBy.values().size] }
                            BarButton("folders:hidden", if (showHidden) "Hidden: on" else "Hidden: off") { showHidden = !showHidden }
                            BarButton("folders:search", "Search") { input = query; dialog = Dialog.SEARCH }
                            BarButton("folders:select", if (selecting) "Done" else "Select") { selecting = !selecting; selected = emptySet() }
                            BarButton("folders:new", "New folder") { input = ""; dialog = Dialog.NEW_FOLDER }
                        }
                        if (clip.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${clip.size} to ${if (clipMove) "move" else "copy"}", color = SoftText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                BarButton("folders:paste", "Paste here") {
                                    val files = clip; val mv = clipMove; val dst = cwd
                                    clip = emptyList()
                                    work(if (mv) "Moving" else "Copying") {
                                        val ok = files.count { if (mv) FileManager.move(it, dst) else FileManager.copy(it, dst) }
                                        "${if (mv) "Moved" else "Copied"} $ok of ${files.size}"
                                    }
                                }
                                BarButton("folders:clipclear", "Cancel") { clip = emptyList() }
                            }
                        }
                        if (query.isNotBlank()) Text("Results for “$query” in ${cwd.name.ifBlank { "storage" }}", color = SoftText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp))
                        // The list.
                        val listState = rememberLazyListState()
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (items.isEmpty()) {
                                Text(if (query.isBlank()) "This folder is empty" else "Nothing found", color = SoftText, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
                            }
                            LazyColumn(
                                Modifier.fillMaxSize().padScroller { listState.animateScrollBy(it) },
                                state = listState,
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 70.dp),
                            ) {
                                items(items, key = { it.path }) { it0 ->
                                    FileRow(
                                        item = it0, selecting = selecting, picked = it0.path in selected,
                                        onClick = {
                                            if (selecting) selected = if (it0.path in selected) selected - it0.path else selected + it0.path
                                            else if (it0.isDir) open(it0.file)
                                            else { target = it0; dialog = Dialog.MORE }
                                        },
                                        onMore = { target = it0; dialog = Dialog.MORE },
                                    )
                                }
                            }
                        }
                        if (selecting) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color.Black.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 6.dp).padding(end = 120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${selected.size} selected", color = Color.White, fontSize = 14.sp)
                                BarButton("folders:all", "All") { selected = items.map { it.path }.toSet() }
                                BarButton("folders:copy", "Copy") { if (selected.isNotEmpty()) { clip = chosen().map { it.file }; clipMove = false; selecting = false; selected = emptySet() } }
                                BarButton("folders:move", "Move") { if (selected.isNotEmpty()) { clip = chosen().map { it.file }; clipMove = true; selecting = false; selected = emptySet() } }
                                BarButton("folders:delete", "Delete") { if (selected.isNotEmpty()) dialog = Dialog.DELETE }
                                BarButton("folders:share", "Share") { FileManager.share(context, chosen().map { it.file })?.let(onToast) }
                                BarButton("folders:send", "Send to game folder") { if (selected.isNotEmpty()) dialog = Dialog.SEND }
                            }
                        }
                    }
                    1 -> PathList(
                        title = "Pinned folders and storage",
                        paths = roots.map { it.second.path } + pins,
                        names = roots.associate { it.second.path to it.first },
                        keyPrefix = "folders:pin",
                        onPick = { p -> val f = File(p); if (f.isDirectory) { rootDir = roots.firstOrNull { p.startsWith(it.second.path) }?.second ?: roots.first().second; open(f) } else onToast("That folder is gone") },
                    )
                    else -> PathList(
                        title = "Recently opened",
                        paths = recents, names = emptyMap(), keyPrefix = "folders:recent",
                        onPick = { p -> val f = File(p); if (f.isDirectory) { rootDir = roots.firstOrNull { p.startsWith(it.second.path) }?.second ?: roots.first().second; open(f) } else onToast("That folder is gone") },
                    )
                }
            }
        }
        BackButton(onClick = onClose, modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp), key = "folders:back")

        // In-tree dialogs, so the gamepad keeps working.
        val t = target
        when (dialog) {
            Dialog.NONE -> {}
            Dialog.MORE -> if (t != null) {
                val name = t.name.lowercase()
                val menu = ArrayList<MenuItem>()
                if (!t.isDir) menu.add(MenuItem("Open") { leave(); FileManager.open(context, t.file)?.let(onToast) })
                if (t.isDir) menu.add(MenuItem("Open folder") { leave(); open(t.file) })
                if (!t.isDir && DownloadPlacer.isArchive(name)) menu.add(MenuItem("Unpack here") {
                    leave()
                    work("Unpacking") {
                        val base = t.name.replace(Regex("(?i)\\.(tar\\.gz|tar\\.bz2|tar\\.xz|tgz|tbz2|txz|zip|7z|rar|tar|gz)$"), "")
                        val out = FileManager.uniqueTarget(t.file.parentFile ?: cwd, base.ifBlank { "unpacked" })
                        val n = FileManager.extractTree(t.file, out)
                        "Unpacked $n files into ${out.name}"
                    }
                })
                if (!t.isDir) menu.add(MenuItem("Share") { leave(); FileManager.share(context, listOf(t.file))?.let(onToast) })
                if (!t.isDir) menu.add(MenuItem("Send to game folder") { selected = setOf(t.path); dialog = Dialog.SEND })
                menu.add(MenuItem("Copy") { clip = listOf(t.file); clipMove = false; leave() })
                menu.add(MenuItem("Move") { clip = listOf(t.file); clipMove = true; leave() })
                menu.add(MenuItem("Rename") { input = t.name; dialog = Dialog.RENAME })
                menu.add(MenuItem("Delete") { selected = setOf(t.path); dialog = Dialog.DELETE })
                menu.add(MenuItem("Properties") {
                    dialog = Dialog.PROPS
                    propsText = "Measuring…"
                    scope.launch {
                        val (size, count) = withContext(Dispatchers.IO) { FileManager.measure(t.file) }
                        val date = DateFormat.getDateTimeInstance().format(Date(t.modified))
                        propsText = "${t.path}\n\n" + (if (t.isDir) "Size: ${sizeText(size)} in $count files\n" else "Size: ${sizeText(size)}\nType: ${FileManager.mime(t.file)}\n") + "Changed: $date"
                    }
                })
                ContextMenu(title = t.name, subtitle = if (t.isDir) "Folder" else sizeText(t.size), items = menu, onDismiss = { leave() })
            }
            Dialog.SEND -> {
                val folders = GameLibrary.folders(context)
                val files = (if (selected.isEmpty() && t != null) listOf(t) else chosen()).map { it.file }.filter { it.isFile }
                val menu = if (folders.isEmpty()) listOf(MenuItem("No game folders yet. Add one in Settings") { leave() })
                else folders.map { fo ->
                    MenuItem(fo.label.ifBlank { fo.systemId }) {
                        leave()
                        work("Sending") {
                            var ok = 0
                            var last = ""
                            for (f in files) {
                                val sys = SYSTEMS.firstOrNull { f.extension.lowercase() in it.exts }
                                val r = DownloadPlacer.place(context, f, f.name, sys, chosen = fo, keepSource = true)
                                if (r.ok) ok++ else last = r.message
                            }
                            withContext(Dispatchers.Main) { onScanGames() }
                            if (ok == files.size) "Sent $ok to ${fo.label.ifBlank { "the game folder" }}" else "Sent $ok of ${files.size}. $last"
                        }
                    }
                }
                ContextMenu(title = "Send to game folder", subtitle = "${files.size} file(s). The originals stay where they are.", items = menu, onDismiss = { leave() })
            }
            Dialog.DELETE -> {
                val files = chosen().ifEmpty { listOfNotNull(t) }.map { it.file }
                ContextMenu(title = "Delete ${files.size} item(s)?", subtitle = "This cannot be undone.", items = listOf(
                    MenuItem("Delete") { leave(); work("Deleting") { val ok = files.count { FileManager.delete(it) }; "Deleted $ok of ${files.size}" } },
                    MenuItem("Keep") { leave() },
                ), onDismiss = { leave() })
            }
            Dialog.PROPS -> ContextMenu(title = "Properties", subtitle = propsText, items = listOf(MenuItem("Close") { leave() }), onDismiss = { leave() })
            Dialog.RENAME, Dialog.NEW_FOLDER, Dialog.SEARCH -> TextPrompt(
                title = when (dialog) { Dialog.RENAME -> "Rename"; Dialog.NEW_FOLDER -> "New folder"; else -> "Search this folder and below" },
                value = input, onValue = { input = it },
                onOk = {
                    val v = input
                    val d = dialog
                    leave()
                    when (d) {
                        Dialog.RENAME -> t?.let { x -> work("Renaming") { if (FileManager.rename(x.file, v) != null) "Renamed" else "Could not rename (name empty or taken)" } }
                        Dialog.NEW_FOLDER -> work("Creating") { if (FileManager.mkdir(cwd, v) != null) "Folder created" else "Could not create it (name empty or taken)" }
                        else -> query = v.trim()
                    }
                },
                onCancel = { leave() },
            )
        }
    }
}

@Composable
private fun TextPrompt(title: String, value: String, onValue: (String) -> Unit, onOk: () -> Unit, onCancel: () -> Unit) {
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { onCancel() } }, contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(0.8f).background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp)).pointerInput(Unit) { detectTapGestures { } }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                BasicTextField(
                    value = value, onValueChange = onValue, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onOk() }),
                    modifier = Modifier.fillMaxWidth().padClickable("folders:input", corner = 10.dp, pad = 2.dp) { },
                    decorationBox = { inner -> Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(12.dp)) { inner() } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BarButton("folders:ok", "OK") { onOk() }
                    BarButton("folders:cancel", "Cancel") { onCancel() }
                }
            }
        }
    }
}

@Composable
private fun AccessCard(onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Allow access to all files", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "To show and manage your files, Android needs you to switch on “All files access” for QitaUI. Tap the button, turn the switch on, then come back.\n\nAndroid 13 and later keep other apps' Android/data folders hidden from every file manager.",
            color = SoftText, fontSize = 15.sp,
        )
        OrangeButton("folders:grant", "Open the setting", onClick = onOpen)
    }
}

@Composable
private fun PathList(title: String, paths: List<String>, names: Map<String, String>, keyPrefix: String, onPick: (String) -> Unit) {
    val state = rememberLazyListState()
    Text(title, color = SoftText, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
    if (paths.isEmpty()) Text("Nothing here yet", color = DimText, fontSize = 15.sp, modifier = Modifier.padding(14.dp))
    LazyColumn(Modifier.fillMaxSize().padScroller { state.animateScrollBy(it) }, state = state, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 70.dp)) {
        items(paths.distinct(), key = { it }) { p ->
            val key = "$keyPrefix:$p"
            val lit = padHighlighted(key) || padHovered(key)
            Column(
                Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    .padClickable(key, corner = 10.dp, pad = 3.dp, ring = false) { onPick(p) }
                    .litEdge(lit, 10.dp)
                    .background(Color.White.copy(alpha = if (lit) 0.22f else 0.10f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(names[p] ?: File(p).name.ifBlank { p }, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p, color = DimText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FileRow(item: FileItem, selecting: Boolean, picked: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    val key = "folders:f:${item.path}"
    val lit = padHighlighted(key) || padHovered(key)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 5.dp)
            .padClickable(key, corner = 10.dp, pad = 3.dp, ring = false, onClick = onClick)
            .litEdge(lit, 10.dp)
            .background(if (picked) Color(0xFFE8820C).copy(alpha = 0.45f) else Color.White.copy(alpha = if (lit) 0.22f else 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selecting) (if (picked) "☑" else "☐") else glyph(item), color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (item.isDir) "Folder" else sizeText(item.size)) + "  ·  " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.modified)),
                color = DimText, fontSize = 12.sp, maxLines = 1,
            )
        }
        if (!selecting) Text("⋯", Modifier.padClickable("$key:more", corner = null, pad = 2.dp, onClick = onMore).padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, fontSize = 20.sp)
    }
}
