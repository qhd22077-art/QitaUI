package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Vita lock screen: faceted wallpaper, a translucent framed panel, a huge thin clock with the
 * date above it, and a curled corner. Peel the corner away (or tap it, or press the confirm
 * button) to reveal the home screen. This is in-app only: Android does not let a launcher
 * replace the phone's real lock screen, so it is visual and not a security lock.
 */
@Composable
fun LockScreen(settings: Settings, onUnlock: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val peel = remember { Animatable(0f) }
    var pageWidth by remember { mutableStateOf(1) }
    val baseFold = with(density) { 56.dp.toPx() }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(5_000)
        }
    }
    fun finish() {
        scope.launch {
            peel.animateTo(pageWidth * 2f, tween(340))
            onUnlock()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        StatusBar(settings.use24h, settings.showBattery, showHome = false)
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { pageWidth = it.width }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        shape = PeelShape(baseFold + peel.value)
                        clip = true
                    },
            ) {
                Shards(settings.theme.top, settings.theme.mid)
                // The translucent panel that frames the lock screen.
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                ) {
                    val clockSize = (maxHeight.value * 0.40f).sp
                    val dateSize = (maxHeight.value * 0.075f).sp
                    Column(
                        Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            SimpleDateFormat("d MMMM (EEEE)", Locale.getDefault()).format(now),
                            color = Color.White.copy(alpha = 0.92f), fontSize = dateSize, fontWeight = FontWeight.Light,
                        )
                        Text(
                            SimpleDateFormat(if (settings.use24h) "HH : mm" else "h : mm", Locale.getDefault()).format(now),
                            color = Color.White, fontSize = clockSize, fontWeight = FontWeight.ExtraLight,
                            style = TextStyle(letterSpacing = 2.sp),
                        )
                    }
                }
            }

            PeelBack(baseFold + peel.value, settings.theme.mid)

            // Corner: drag it diagonally to peel, or tap it (or press the confirm button) to unlock.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(72.dp)
                    .padTarget("lock:peel", corner = null, pad = 4.dp, bring = false, onClick = { finish() })
                    .clickable { finish() }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                val along = (-drag.x + drag.y) / 1.4142f
                                scope.launch { peel.snapTo((peel.value + along).coerceAtLeast(0f)) }
                            },
                            onDragEnd = {
                                if (peel.value > pageWidth * 0.25f) finish()
                                else scope.launch { peel.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)) }
                            },
                            onDragCancel = { scope.launch { peel.animateTo(0f) } },
                        )
                    },
            )
            // Swallow stray taps so nothing underneath is touched while locked.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
        }
    }
}

/** Faceted background: the theme's sky with overlapping translucent polygons, like the Vita's default lock wallpaper. */
@Composable
private fun Shards(top: Color, mid: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(top, mid)))
        val light = Color(0xFF6FD6FF)
        shard(Color.White.copy(alpha = 0.10f), 0f to 0f, 0.55f to 0f, 0.30f to 0.35f, 0f to 0.45f)
        shard(light.copy(alpha = 0.20f), 0.40f to 0f, 0.72f to 0f, 0.62f to 0.22f, 0.30f to 0.30f)
        shard(Color.White.copy(alpha = 0.07f), 0f to 0.45f, 0.30f to 0.35f, 0.55f to 0.62f, 0.15f to 0.80f, 0f to 0.75f)
        shard(Color.Black.copy(alpha = 0.16f), 0.30f to 0.35f, 0.62f to 0.22f, 1f to 0.45f, 1f to 0.70f, 0.55f to 0.62f)
        shard(Color.Black.copy(alpha = 0.28f), 0.15f to 0.80f, 0.55f to 0.62f, 1f to 0.70f, 1f to 1f, 0.20f to 1f)
        shard(Color.White.copy(alpha = 0.06f), 0.70f to 0f, 1f to 0f, 1f to 0.45f, 0.62f to 0.22f)
        shard(light.copy(alpha = 0.22f), 0f to 0.75f, 0.15f to 0.80f, 0.20f to 1f, 0f to 1f)
    }
}

private fun DrawScope.shard(color: Color, vararg points: Pair<Float, Float>) {
    val path = Path()
    points.forEachIndexed { i, (x, y) ->
        val p = Offset(x * size.width, y * size.height)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}
