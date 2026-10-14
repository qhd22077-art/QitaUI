package com.qita.ui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Find a setting by a word of its name: the results are the rows of [SETTINGS_INDEX] (and the pages themselves); choosing one opens
 * its page. [tabs] are the page ids with their display names.
 */
@Composable
internal fun SettingsSearch(tabs: List<Pair<String, String>>, onPick: (String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val names = remember(tabs) { tabs.toMap() }
    val results = remember(query, tabs) {
        val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) tabs.map { SettingsEntry(it.first, "Open the ${it.second} page") }
        else (tabs.map { SettingsEntry(it.first, it.second) } + SETTINGS_INDEX)
            .filter { e -> words.all { w -> e.label.lowercase().contains(w) || (names[e.page] ?: "").lowercase().contains(w) } }
            .take(40)
    }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(enabled = true) { onClose() }
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(Modifier.fillMaxSize().background(Color(0xF2053A0C)).pointerInput(Unit) { detectTapGestures { } }) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Search settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    BarButton("setsearch:close", "Close", onClose)
                }
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp), cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (query.isEmpty()) Text("Type part of a setting's name", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
                            inner()
                        }
                    },
                )
                if (results.isEmpty()) Text("No setting matches that.", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padScroller { list.animateScrollBy(it) },
                    state = list,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(results.size, key = { "${results[it].page}/${results[it].label}" }) { i ->
                        val e = results[i]
                        val key = "setsearch:$i"
                        val lit = padHighlighted(key) || padHovered(key)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padClickable(key, corner = 12.dp, pad = 2.dp, ring = false, onClick = { onPick(e.page) })
                                .background(Color.White.copy(alpha = if (lit) 0.30f else 0.12f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(e.label, color = Color.White, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(names[e.page] ?: e.page, color = Color(0xFFCFF3CF), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
