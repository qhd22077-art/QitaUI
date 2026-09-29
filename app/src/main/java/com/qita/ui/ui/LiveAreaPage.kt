package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.LaunchableApp
import com.qita.ui.Settings
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The app's LiveArea as a floating rounded card over the dimmed home screen. Tap outside the
 * card (or press B) to close it, drag down from its top strip to slide it away, or drag its
 * folded top-right corner toward the bottom-left to peel it away and close the app.
 */
@Composable
fun LiveAreaPage(
    app: LaunchableApp,
    settings: Settings,
    wallpaper: ImageBitmap?,
    launches: Int,
    onLaunch: () -> Unit,
    onCloseApp: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissY = remember { Animatable(0f) }
    // Extra px the corner has been peeled beyond its resting size.
    val peel = remember { Animatable(0f) }
    var cardWidth by remember { mutableStateOf(1) }
    val baseFold = with(density) { 56.dp.toPx() }
    val cornerRadius = with(density) { 24.dp.toPx() }

    // Scrim: tapping outside the card closes it and also stops taps reaching the home screen.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.74f)
                .fillMaxHeight(0.86f)
                .onSizeChanged { cardWidth = it.width }
                // Swallow taps on the card so they don't fall through to the scrim.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dismissY.value
                        shape = PeelShape(baseFold + peel.value, cornerRadius)
                        clip = true
                        alpha = if (size.height > 0f) 1f - (dismissY.value / size.height).coerceIn(0f, 1f) * 0.6f else 1f
                    },
            ) {
                BubbleBackground(
                    top = settings.theme.top, mid = settings.theme.mid, bottom = settings.theme.bottom, particles = settings.particles,
                    wallpaper = wallpaper, particleCount = settings.particleCount, dim = settings.dim,
                )
                Row(Modifier.fillMaxSize().padding(start = 32.dp, top = 56.dp, end = 24.dp, bottom = 24.dp)) {
                    // Left: icon, title and the Start button.
                    Column(Modifier.width(190.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Image(
                            app.icon, app.label,
                            Modifier.size(88.dp).shadow(10.dp, CircleShape).background(Color.White, CircleShape).padding(13.dp),
                        )
                        Text(app.label, Modifier.padding(top = 12.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Start",
                            Modifier
                                .padding(top = 24.dp)
                                .padClickable("card:start", corner = null, onClick = onLaunch)
                                .background(Color.White, RoundedCornerShape(50))
                                .padding(horizontal = 44.dp, vertical = 12.dp),
                            color = Color(0xFF0B3D91), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        )
                        // Controller-friendly alternative to the corner peel.
                        Text(
                            "Close app",
                            Modifier
                                .padding(top = 10.dp)
                                .padClickable("card:close", corner = null) { onCloseApp(); onClose() }
                                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                                .padding(horizontal = 28.dp, vertical = 8.dp),
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    // Right: horizontally scrolling info cards.
                    Row(
                        Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val installed = if (app.installTime > 0) DateFormat.getDateInstance().format(Date(app.installTime)) else "Unknown"
                        listOf(
                            "Details" to "Version: ${app.version.ifBlank { "Unknown" }}\nInstalled: $installed\nOpened from here: $launches time${if (launches == 1) "" else "s"}",
                            "Package" to app.packageName,
                            "Tips" to "Drag the folded corner toward the bottom-left to close the app, or tap outside the card (B on a controller) to go back.",
                        ).forEach { (title, body) ->
                            Column(
                                Modifier.width(190.dp).fillMaxHeight(0.7f).shadow(6.dp, RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(12.dp)).padding(16.dp),
                            ) {
                                Text(title, color = Color(0xFF0B3D91), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(body, Modifier.padding(top = 8.dp), color = Color.DarkGray, fontSize = 12.sp)
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
                                    if (dismissY.value > size.height * 3f) onClose()
                                    else dismissY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
                                }
                            },
                            onDragCancel = { scope.launch { dismissY.animateTo(0f) } },
                        )
                    },
            ) {
                Box(Modifier.align(Alignment.Center).padding(top = 8.dp).size(48.dp, 4.dp).alpha(0.6f).background(Color.White, CircleShape))
            }

            // Corner grab zone: drag diagonally toward the bottom-left to peel the card away.
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
                                    if (peel.value > cardWidth * 0.3f) {
                                        // Peeled far enough: finish the peel, then close the app and the card.
                                        peel.animateTo(cardWidth * 2f)
                                        onCloseApp()
                                        onClose()
                                    } else {
                                        peel.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium))
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { peel.animateTo(0f) } },
                        )
                    },
            )
        }
    }
}

/** Rounded card shape with the top-right corner triangle (legs of [fold] px) removed, so what is behind shows through. */
private class PeelShape(private val fold: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val page = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius, radius)))
        }
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
