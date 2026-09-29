package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.AppRepository
import com.qita.ui.LaunchableApp
import com.qita.ui.Theme
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.launch

/**
 * Full-screen LiveArea page for an app. Drag down from the top strip (or the folded
 * corner) to peel the page away and return to the home screen.
 */
@Composable
fun LiveAreaPage(app: LaunchableApp, theme: Theme, particles: Boolean, wallpaper: ImageBitmap?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val screenW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val baseFold = with(density) { 56.dp.toPx() }
    // Extra px the corner has been peeled beyond its resting size.
    val peel = remember { Animatable(0f) }
    Box(Modifier.fillMaxSize()) {
      Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dismissY.value
                shape = PeelShape(baseFold + peel.value)
                clip = true
                alpha = 1f - (dismissY.value / size.height).coerceIn(0f, 1f) * 0.6f
            },
      ) {
        BubbleBackground(top = theme.top, bottom = theme.bottom, particles = particles, wallpaper = wallpaper)

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

      }

        PeelBack(baseFold + peel.value)

        // Top-edge grab zone (outside the translated layer so drag deltas stay stable): drag down to dismiss.
        Box(
            Modifier
                .padding(end = 72.dp)
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

        // Corner grab zone: drag diagonally toward the bottom-left to peel the page away.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(72.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, drag ->
                            change.consume()
                            // Project the drag onto the diagonal from the top-right corner.
                            val along = (-drag.x + drag.y) / 1.4142f
                            scope.launch { peel.snapTo((peel.value + along).coerceAtLeast(0f)) }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (peel.value > screenW * 0.3f) {
                                    // Peeled far enough: finish the peel, then close the app and the page.
                                    peel.animateTo(screenW * 2f)
                                    AppRepository.close(context, app)
                                    onClose()
                                } else {
                                    peel.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { peel.animateTo(0f) } },
                    )
                },
        )
    }
}

/** Page shape with the top-right corner triangle (legs of [fold] px) removed, so what is behind shows through. */
private class PeelShape(private val fold: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val page = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) }
        val corner = Path().apply {
            moveTo(size.width - fold, 0f); lineTo(size.width, 0f); lineTo(size.width, fold); close()
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, page, corner))
    }
}

/** The back of the peeled corner: the removed triangle reflected across the fold line. */
@Composable
private fun PeelBack(fold: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val back = Path().apply {
            moveTo(size.width - fold, 0f); lineTo(size.width, fold); lineTo(size.width - fold, fold); close()
        }
        drawPath(back, Color.White.copy(alpha = 0.92f))
        drawLine(Color.Black.copy(alpha = 0.18f), Offset(size.width - fold, 0f), Offset(size.width, fold), strokeWidth = 2f)
    }
}
