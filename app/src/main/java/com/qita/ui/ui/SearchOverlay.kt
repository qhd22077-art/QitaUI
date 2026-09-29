package com.qita.ui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Controller
import com.qita.ui.LaunchableApp
import com.qita.ui.Settings

/** Type-ahead app search. Tap a result to open its page. */
@Composable
fun SearchOverlay(
    apps: List<LaunchableApp>,
    settings: Settings,
    wallpaper: ImageBitmap?,
    onPick: (LaunchableApp) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()
    // Touch users get the keyboard straight away; gamepad users browse the results instead.
    LaunchedEffect(Unit) {
        if (!Controller.padActive) { focus.requestFocus(); keyboard?.show() }
    }
    val results = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        BubbleBackground(
            top = settings.theme.top, mid = settings.theme.mid, bottom = settings.theme.bottom, particles = settings.particles,
            wallpaper = wallpaper, particleCount = settings.particleCount, dim = settings.dim,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 48.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let(onPick) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                Text(
                    "Close",
                    Modifier
                        .padClickable("search:close", corner = null, onClick = onClose)
                        .background(Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    color = Color(0xFF0B3D91), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
            }
            if (results.isEmpty()) {
                Text("No matches", Modifier.padding(top = 24.dp), color = Color.White, fontSize = 16.sp)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                state = gridState,
                modifier = Modifier.padding(top = 12.dp).padScroller { gridState.animateScrollBy(it) },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results, key = { it.packageName }) { app ->
                    Bubble(
                        app, 64.dp,
                        onClick = { onPick(app) },
                        padKey = "search:${app.packageName}",
                        shape = if (settings.roundedBubbles) RoundedCornerShape(28) else CircleShape,
                    )
                }
            }
        }
    }
}
