package com.qita.ui.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import com.qita.ui.LaunchableApp
import com.qita.ui.Settings
import com.qita.ui.SYSTEMS

/**
 * The games library: every game found in the user's folders as a bubble with its cover, filtered by console, plus a tab
 * with the emulators installed on the device. Tap to play; long-press (or X on the gamepad) for options.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesScreen(
    games: List<LaunchableApp>,
    emulators: List<LaunchableApp>,
    settings: Settings,
    wallpaper: ImageBitmap?,
    onLaunch: (LaunchableApp) -> Unit,
    onOptions: (LaunchableApp) -> Unit,
    onSetup: () -> Unit,
    onScan: () -> Unit,
    /** A status line while a scan (or cover download) is running, else null. */
    scanning: String?,
    /** Ids of starred games, and when each game was last started. */
    favourites: Set<String>,
    played: Map<String, Long>,
    onClose: () -> Unit,
) {
    var tab by remember { mutableStateOf("all") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // 0 by name, 1 by when it was last played, 2 by console.
    var sort by remember { mutableStateOf(0) }
    val gridState = rememberLazyGridState()
    // The consoles that have games, in the order of the system list.
    val present = remember(games) { SYSTEMS.filter { s -> games.any { it.game?.systemId == s.id } } }
    val shown = remember(games, emulators, tab, query, sort, favourites, played) {
        val base = when (tab) {
            "all" -> games
            "emu" -> emulators
            "fav" -> games.filter { it.game?.id in favourites }
            else -> games.filter { it.game?.systemId == tab }
        }
        val found = if (query.isBlank()) base else base.filter { it.label.contains(query.trim(), true) }
        if (tab == "emu") found else found.sortedWith(
            compareByDescending<LaunchableApp> { it.game?.id in favourites }
                .thenComparator { a, b ->
                    when (sort) {
                        1 -> (played[b.game?.id] ?: 0L).compareTo(played[a.game?.id] ?: 0L)
                        2 -> (a.game?.systemId ?: "").compareTo(b.game?.systemId ?: "")
                        else -> 0
                    }
                }
                .thenBy { it.label.lowercase() },
        )
    }
    val theme = settings.theme

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        BubbleBackground(
            top = theme.top, mid = theme.mid, bottom = theme.bottom, wallpaper = wallpaper, dim = settings.dim,
            scene = { SceneMix(theme.scene, theme.scene, 0f, Triple(theme.top, theme.mid, theme.bottom), Triple(theme.top, theme.mid, theme.bottom)) },
        )
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, showHome = false)
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Games", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Chip("games:tab:all", "All ${games.size}", tab == "all") { tab = "all" } }
                    if (favourites.isNotEmpty()) item { Chip("games:tab:fav", "★ ${games.count { it.game?.id in favourites }}", tab == "fav") { tab = "fav" } }
                    items(present) { s ->
                        Chip("games:tab:${s.id}", s.short, tab == s.id) { tab = s.id }
                    }
                    item { Chip("games:tab:emu", "Emulators ${emulators.size}", tab == "emu") { tab = "emu" } }
                }
                Chip("games:scan", if (scanning != null) "Scanning…" else "Scan", scanning != null, onClick = { if (scanning == null) onScan() })
                Chip("games:setup", "Setup", false, onClick = onSetup)
            }
            // Search and sort.
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("games:search", if (searching) "Search ✕" else "Search", searching) { searching = !searching; if (!searching) query = "" }
                Chip("games:sort", "Sort: " + listOf("A-Z", "Recent", "Console")[sort], false) { sort = (sort + 1) % 3 }
                if (searching) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                if (query.isEmpty()) Text("Type a game's name", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
                                inner()
                            }
                        },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 20.dp).background(Color.White.copy(alpha = 0.35f)))
            if (shown.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (tab == "emu") "No emulators found. Install one (RetroArch, PPSSPP, Dolphin, Vita3K and others are recognised)."
                        else "No games yet. Tap Setup to choose a folder with your games, then scan it.",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(128.dp),
                    state = gridState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padScroller { gridState.animateScrollBy(it) }
                        .padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = 96.dp),
                    // Names can run to two lines, so the rows need room.
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.packageName }) { app ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Bubble(
                                app, 84.dp,
                                onClick = { onLaunch(app) },
                                modifier = Modifier.width(118.dp),
                                padKey = "games:${app.packageName}",
                                // A long press opens the options (launch, add to home, cover art, remove).
                                onDragStart = { onOptions(app) },
                            )
                        }
                    }
                }
            }
        }
        BackButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
            key = "games:back",
        )
    }
}

@Composable
private fun Chip(key: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    val accent = LocalLook.current.accent
    // The fill eases between states instead of snapping.
    val fill by androidx.compose.animation.animateColorAsState(
        if (selected) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = if (lit) 0.45f else 0.18f),
        androidx.compose.animation.core.tween(160), label = "chip",
    )
    Text(
        label,
        Modifier
            .padClickable(key, corner = 14.dp, ring = false, onClick = onClick)
            .litEdge(lit, 14.dp)
            .background(fill, RoundedCornerShape(14.dp))
            .border(if (lit) 2.dp else 1.dp, Color.White.copy(alpha = if (lit) 1f else 0.6f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
    )
}
