package com.qita.ui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.AppRepository
import com.qita.ui.LaunchableApp

private const val COLUMNS = 4
private const val ROWS = 2

/** Vita-style home: swipeable pages of bubbles over animated waves; tap one for its LiveArea card. */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var selected by remember { mutableStateOf<LaunchableApp?>(null) }
    LaunchedEffect(Unit) { apps = AppRepository.load(context) }

    BackHandler(enabled = selected != null) { selected = null }

    Box(Modifier.fillMaxSize()) {
        WaveBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar()
            BubblePager(apps, Modifier.weight(1f)) { selected = it }
        }
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            selected?.let { LiveAreaCard(it, onClose = { selected = null }) }
        }
    }
}

@Composable
private fun BubblePager(apps: List<LaunchableApp>, modifier: Modifier, onSelect: (LaunchableApp) -> Unit) {
    val pages = apps.chunked(COLUMNS * ROWS)
    if (pages.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No apps found", color = Color.White, fontSize = 18.sp)
        }
        return
    }
    val pagerState = rememberPagerState { pages.size }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pagerState, Modifier.weight(1f)) { index ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
                val bubble = minOf(maxWidth / (COLUMNS + 1), maxHeight / (ROWS + 1.2f))
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    pages[index].chunked(COLUMNS).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { app ->
                                Bubble(app, bubble, onClick = { onSelect(app) }, modifier = Modifier.size(bubble + 8.dp, bubble + 32.dp))
                            }
                            // Keep short rows aligned with full ones.
                            repeat(COLUMNS - row.size) { Spacer(Modifier.size(bubble + 8.dp, 1.dp)) }
                        }
                    }
                }
            }
        }
        PageDots(pages.size, pagerState.currentPage)
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.size(if (i == current) 10.dp else 8.dp).background(
                    Color.White.copy(alpha = if (i == current) 1f else 0.5f), CircleShape,
                ),
            )
        }
    }
}

/** Simplified LiveArea: a large card with the app name and a "Start" button. */
@Composable
private fun LiveAreaCard(app: LaunchableApp, onClose: () -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.55f)
                .background(Color.White, RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.Image(app.icon, app.label, Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text(app.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B3D91))
            Spacer(Modifier.height(16.dp))
            Text(
                "Start",
                Modifier
                    .background(Color(0xFF1B6FD0), RoundedCornerShape(50))
                    .clickable { AppRepository.launch(context, app); onClose() }
                    .padding(horizontal = 40.dp, vertical = 10.dp),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
