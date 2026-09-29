package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.AppRepository
import com.qita.ui.LaunchableApp
import kotlinx.coroutines.launch

/**
 * Full-screen LiveArea page for an app. Drag down from the top strip (or the folded
 * corner) to peel the page away and return to the home screen.
 */
@Composable
fun LiveAreaPage(app: LaunchableApp, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissY = remember { Animatable(0f) }
    Box(Modifier.fillMaxSize()) {
      Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dismissY.value
                alpha = 1f - (dismissY.value / size.height).coerceIn(0f, 1f) * 0.6f
            },
      ) {
        BubbleBackground(top = Color(0xFF0B3D91), bottom = Color(0xFF2A8FD8))

        Row(Modifier.fillMaxSize().padding(start = 48.dp, top = 56.dp, end = 32.dp, bottom = 32.dp)) {
            // Left: icon, title and the Start button.
            Column(Modifier.width(220.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Image(app.icon, app.label, Modifier.size(96.dp).background(Color.White, CircleShape).padding(14.dp))
                Text(app.label, Modifier.padding(top = 12.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Start",
                    Modifier
                        .padding(top = 24.dp)
                        .background(Color.White, RoundedCornerShape(50))
                        .clickable { AppRepository.launch(context, app) }
                        .padding(horizontal = 48.dp, vertical = 12.dp),
                    color = Color(0xFF0B3D91), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
            }
            // Right: horizontally scrolling "live cards" (placeholders for now).
            Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Recent", "About", "Info").forEach { title ->
                    Column(
                        Modifier.width(200.dp).fillMaxHeight(0.7f).background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp)).padding(16.dp),
                    ) {
                        Text(title, color = Color(0xFF0B3D91), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(app.packageName, Modifier.padding(top = 8.dp), color = Color.DarkGray, fontSize = 12.sp)
                    }
                }
            }
        }

        FoldedCorner(Modifier.align(Alignment.TopEnd))
      }

        // Top-edge grab zone (outside the translated layer so drag deltas stay stable): drag down to dismiss.
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            scope.launch { dismissY.snapTo((dismissY.value + delta).coerceAtLeast(0f)) }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (dismissY.value > size.height * 3f) onClose() else dismissY.animateTo(0f)
                            }
                        },
                        onDragCancel = { scope.launch { dismissY.animateTo(0f) } },
                    )
                },
        ) {
            Box(Modifier.align(Alignment.Center).padding(top = 8.dp).size(48.dp, 4.dp).alpha(0.6f).background(Color.White, CircleShape))
        }
    }
}

/** Folded-back top-right corner, the Vita's visual cue that the page can be peeled away. */
@Composable
private fun FoldedCorner(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(56.dp)) {
        val fold = Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); close()
        }
        drawPath(fold, Color.White.copy(alpha = 0.85f))
        drawLine(Color.Black.copy(alpha = 0.15f), Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 2f)
    }
}
