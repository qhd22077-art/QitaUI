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
import androidx.compose.foundation.layout.width
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

/** Vita home pages hold 10 bubbles, laid out in staggered rows of 4, 3 and 3. */
private val ROW_SIZES = listOf(4, 3, 3)
private val PAGE_SIZE = ROW_SIZES.sum()

/** Vita-style home: swipeable pages of bubbles; tap one to open its full-screen LiveArea page. */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var selected by remember { mutableStateOf<LaunchableApp?>(null) }
    LaunchedEffect(Unit) { apps = AppRepository.load(context) }

    BackHandler(enabled = selected != null) { selected = null }

    Box(Modifier.fillMaxSize()) {
        BubbleBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar()
            BubblePager(apps, Modifier.weight(1f)) { selected = it }
        }
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            selected?.let { LiveAreaPage(it, onClose = { selected = null }) }
        }
    }
}

@Composable
private fun BubblePager(apps: List<LaunchableApp>, modifier: Modifier, onSelect: (LaunchableApp) -> Unit) {
    val pages = apps.chunked(PAGE_SIZE)
    if (pages.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No apps found", color = Color.White, fontSize = 18.sp)
        }
        return
    }
    val pagerState = rememberPagerState { pages.size }
    Row(modifier.fillMaxSize()) {
        // Page dots run down the left edge, like the Vita.
        PageDots(pages.size, pagerState.currentPage, Modifier.padding(start = 16.dp).align(Alignment.CenterVertically))
        VerticalPagerPlaceholder()
        HorizontalPager(pagerState, Modifier.weight(1f)) { index ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                val bubble = minOf(maxWidth / (ROW_SIZES[0] + 0.5f), maxHeight / (ROW_SIZES.size + 0.9f))
                val cell = bubble + 20.dp
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    var offset = 0
                    ROW_SIZES.forEachIndexed { r, count ->
                        val row = pages[index].drop(offset).take(count)
                        offset += count
                        // Rows with fewer bubbles are centred, which produces the staggered look.
                        Row(Modifier.fillMaxWidth().height(cell + 16.dp), horizontalArrangement = Arrangement.Center) {
                            row.forEach { app ->
                                Bubble(app, bubble, onClick = { onSelect(app) }, modifier = Modifier.padding(horizontal = 12.dp).width(cell))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalPagerPlaceholder() = Spacer(Modifier.width(4.dp))

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.size(if (i == current) 10.dp else 7.dp).background(
                    Color.White.copy(alpha = if (i == current) 1f else 0.5f), CircleShape,
                ),
            )
        }
    }
}
