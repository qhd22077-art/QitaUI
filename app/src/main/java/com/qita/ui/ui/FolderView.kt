package com.qita.ui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.LaunchableApp
import com.qita.ui.SYSTEMS

/**
 * An open folder: a big glass bubble over the home screen with the folder's bubbles inside, as in the Vita. Tap one to open it,
 * hold (or X on the gamepad) for its options (take it out of the folder), tap the name to rename it, tap outside or press B to close.
 */
@Composable
fun FolderView(
    folder: LaunchableApp,
    backEnabled: Boolean,
    onClose: () -> Unit,
    onOpen: (LaunchableApp) -> Unit,
    onMenu: (LaunchableApp) -> Unit,
    onRename: () -> Unit,
    favourites: Set<String> = emptySet(),
    played: Map<String, Long> = emptyMap(),
) {
    BackHandler(enabled = backEnabled) { onClose() }
    val members = folder.folderMembers.orEmpty()
    // Search and filters: by name, by kind (games or apps), favourites, and console.
    var query by remember(folder.packageName) { mutableStateOf("") }
    var filter by remember(folder.packageName) { mutableStateOf("all") }
    var sort by remember(folder.packageName) { mutableStateOf(0) }
    val systems = remember(members) { members.mapNotNull { it.game?.systemId }.distinct() }
    val hasGames = members.any { it.game != null }
    val hasApps = members.any { it.game == null }
    val hasFav = members.any { it.game?.id in favourites }
    val folderContext = androidx.compose.ui.platform.LocalContext.current
    val shownMembers = remember(members, query, filter, sort, favourites, played) {
        val matching = members.filter { a ->
            (query.isBlank() || a.label.contains(query.trim(), true) || (a.game?.let { com.qita.ui.GameStats.matches(folderContext, it.id, query.trim()) } == true)) && when (filter) {
                "all" -> true
                "fav" -> a.game?.id in favourites
                "game" -> a.game != null
                "app" -> a.game == null
                else -> a.game?.systemId == filter
            }
        }
        when (sort) {
            1 -> matching.sortedBy { it.label.lowercase() }
            2 -> matching.sortedByDescending { played[it.game?.id] ?: 0L }
            else -> matching
        }
    }
    val config = LocalConfiguration.current
    val maxHeight = (config.screenHeightDp * 0.80f).dp
    val width = (config.screenWidthDp * 0.86f).coerceAtMost(720f).dp
    val state = rememberLazyGridState()
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(42.dp)
        Column(
            Modifier
                .width(width)
                .heightIn(max = maxHeight)
                .background(Brush.verticalGradient(listOf(Color(0xFF9CC4FF).copy(alpha = 0.55f), Color(0xFF3F6FD0).copy(alpha = 0.40f), Color(0xFF1B3F94).copy(alpha = 0.55f))), shape)
                .border(2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.25f))), shape)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val titleKey = "folder:title"
            val lit = padHighlighted(titleKey) || padHovered(titleKey)
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .padClickable(titleKey, corner = 14.dp, pad = 2.dp, ring = false, onClick = onRename)
                    .litEdge(lit, 14.dp)
                    .background(Color.White.copy(alpha = if (lit) 0.28f else 0.12f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(folder.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("✎", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            }
            Text(
                if (shownMembers.size == members.size) "${members.size} ${if (members.size == 1) "item" else "items"}" else "${shownMembers.size} of ${members.size}",
                color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            // The search box and the order.
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.weight(1f).padClickable("folder:search", corner = 12.dp, pad = 2.dp, ring = false) { },
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                            if (query.isEmpty()) Text("Search this folder", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
                            inner()
                        }
                    },
                )
                FilterChip("folder:sort", "Sort: " + listOf("Folder", "A-Z", "Recent")[sort], false) { sort = (sort + 1) % 3 }
            }
            // The filters: only the ones that mean something for what is inside.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("folder:f:all", "All", filter == "all") { filter = "all" }
                if (hasFav) FilterChip("folder:f:fav", "★ Favourites", filter == "fav") { filter = "fav" }
                if (hasGames && hasApps) {
                    FilterChip("folder:f:game", "Games", filter == "game") { filter = "game" }
                    FilterChip("folder:f:app", "Apps", filter == "app") { filter = "app" }
                }
                if (systems.size > 1) systems.forEach { id ->
                    FilterChip("folder:f:$id", SYSTEMS.firstOrNull { it.id == id }?.short ?: id, filter == id) { filter = id }
                }
            }
            if (shownMembers.isEmpty()) Text("Nothing matches", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                state = state,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padScroller { state.animateScrollBy(it) },
                contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(shownMembers, key = { it.packageName }) { app ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Bubble(
                            app, 72.dp,
                            onClick = { onOpen(app) },
                            modifier = Modifier.width(106.dp),
                            padKey = "folder:${app.packageName}",
                            onDragStart = { onMenu(app) },
                        )
                    }
                }
            }
        }
        BackButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
            key = "folder:back",
        )
    }
}

/** A small box asking for a name, drawn in the screen (not a dialog) so the gamepad keeps working. */
@Composable
fun NamePrompt(title: String, value: String, onValue: (String) -> Unit, onOk: () -> Unit, onCancel: () -> Unit) {
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { onCancel() } }, contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(0.8f).popIn().background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp)).pointerInput(Unit) { detectTapGestures { } }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                BasicTextField(
                    value = value, onValueChange = onValue, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onOk() }),
                    modifier = Modifier.fillMaxWidth().padClickable("name:input", corner = 10.dp, pad = 2.dp) { },
                    decorationBox = { inner -> Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(12.dp)) { inner() } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BarButton("name:ok", "OK") { onOk() }
                    BarButton("name:cancel", "Cancel") { onCancel() }
                }
            }
        }
    }
}

/** A question with an OK and a Cancel button, drawn in the screen (not a dialog) so the gamepad keeps working. */
@Composable
fun ConfirmPrompt(title: String, text: String, okLabel: String, onOk: () -> Unit, onCancel: () -> Unit) {
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { onCancel() } }, contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(0.8f).popIn().background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp)).pointerInput(Unit) { detectTapGestures { } }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BarButton("confirm:cancel", "Cancel") { onCancel() }
                    BarButton("confirm:ok", okLabel) { onOk() }
                }
            }
        }
    }
}

/** A small filter button for an open folder; lit when touched or highlighted by the gamepad. */
@Composable
private fun FilterChip(key: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padClickable(key, corner = 14.dp, pad = 2.dp, ring = false, onClick = onClick)
            .litEdge(lit, 14.dp)
            .background(if (selected) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = if (lit) 0.30f else 0.14f), RoundedCornerShape(14.dp))
            .border(if (lit) 2.dp else 1.dp, Color.White.copy(alpha = if (lit || selected) 0.95f else 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
    )
}
