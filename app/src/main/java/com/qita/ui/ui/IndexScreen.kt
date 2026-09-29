package com.qita.ui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.LaunchableApp

/**
 * The index of open pages, opened by pressing Home while a LiveArea is showing (like the Vita's PS button):
 * a row of cards, the home screen first. Tap a card to go there; flick a card up, or use its cross, to close
 * that app. Tap outside the cards to go back.
 */
@Composable
fun IndexScreen(
    pages: List<LaunchableApp>,
    current: Int,
    onPick: (Int) -> Unit,
    onClose: (LaunchableApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val list = rememberLazyListState()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Open applications",
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            )
            LazyRow(
                Modifier.fillMaxWidth().padScroller { list.animateScrollBy(it) },
                state = list,
                contentPadding = PaddingValues(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "home") {
                    Card(
                        key = "index:home", selected = current < 0,
                        brush = Brush.verticalGradient(listOf(Color(0xFF1B5BD8), Color(0xFFB4D8FF))),
                        onClick = { onPick(-1) },
                    ) {
                        Text("Home", Modifier.align(Alignment.Center), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
                itemsIndexed(pages, key = { _, app -> app.packageName }) { i, app ->
                    var lift by remember { mutableFloatStateOf(0f) }
                    Card(
                        key = "index:${app.packageName}", selected = i == current,
                        brush = Brush.verticalGradient(listOf(lerp(app.tint, Color.White, 0.35f), lerp(app.tint, Color.Black, 0.45f))),
                        onClick = { onPick(i) },
                        modifier = Modifier
                            .graphicsLayer { translationY = lift; alpha = 1f - (-lift / 400f).coerceIn(0f, 0.7f) }
                            // Flicking a card upwards closes that app.
                            .pointerInput(app.packageName) {
                                var total = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { total = 0f },
                                    onVerticalDrag = { _, dy -> total += dy; lift = total.coerceAtMost(0f) },
                                    onDragEnd = { if (total < -110.dp.toPx()) onClose(app) else lift = 0f },
                                    onDragCancel = { lift = 0f },
                                )
                            },
                    ) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Sphere(app, 62.dp, elevation = 8.dp)
                            Text(
                                app.label, Modifier.padding(top = 6.dp),
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(26.dp)
                                .padClickable("index:x:${app.packageName}", corner = null, pad = 3.dp, onClick = { onClose(app) })
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) { Text("✕", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(
    key: String,
    selected: Boolean,
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(width = 196.dp, height = 122.dp)
            .padClickable(key, corner = 16.dp, onClick = onClick)
            .clip(shape)
            .background(brush)
            .vitaPanel(16.dp, 0.5f)
            .border(if (selected) 3.dp else 1.5.dp, if (selected) VitaColors.Aqua else Color.White.copy(alpha = 0.7f), shape),
        content = content,
    )
}
