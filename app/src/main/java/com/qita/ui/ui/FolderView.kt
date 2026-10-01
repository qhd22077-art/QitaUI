package com.qita.ui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
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
) {
    BackHandler(enabled = backEnabled) { onClose() }
    val members = folder.folderMembers.orEmpty()
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
            Text("${members.size} ${if (members.size == 1) "item" else "items"}", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                state = state,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padScroller { state.animateScrollBy(it) },
                contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(members, key = { it.packageName }) { app ->
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
                Modifier.padding(24.dp).fillMaxWidth(0.8f).background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp)).pointerInput(Unit) { detectTapGestures { } }.padding(16.dp),
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
