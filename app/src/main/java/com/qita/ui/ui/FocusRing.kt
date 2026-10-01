package com.qita.ui.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val FocusYellow = Color(0xFFFFD54F)
val MoveCyan = Color(0xFF4FC3F7)

/** A pulsing 0.55..1 value while [active], otherwise 1. Only one item is highlighted at a time, so this stays cheap. */
@Composable
fun rememberPulse(active: Boolean): Float =
    if (active) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            0.55f, 1f,
            infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulseValue",
        ).value
    } else 1f

/** Round face-button badge (or a small pill for other keys), coloured like the real button. */
@Composable
fun KeyBadge(key: String, psLabels: Boolean) {
    val face = key == "A" || key == "B" || key == "X" || key == "Y"
    if (face) {
        val (symbol, color) = when (key) {
            "A" -> (if (psLabels) "✕" else "A") to Color(if (psLabels) 0xFF5B9BD5 else 0xFF4CAF50)
            "B" -> (if (psLabels) "○" else "B") to Color(if (psLabels) 0xFFE06666 else 0xFFF44336)
            "X" -> (if (psLabels) "□" else "X") to Color(if (psLabels) 0xFFD07BB0 else 0xFF2196F3)
            else -> (if (psLabels) "△" else "Y") to Color(if (psLabels) 0xFF6AA84F else 0xFFFFC107)
        }
        Box(Modifier.size(22.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Text(
            key,
            Modifier.background(Color(0xFF616161), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** Bar of button hints such as "A Open  X Options", shown while the gamepad is in use. */
@Composable
fun HintBar(hints: List<Pair<String, String>>, psLabels: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { (key, label) ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                KeyBadge(key, psLabels)
                Text(label, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/** A row of a [ContextMenu]. With [section] set it is only a small heading (not a button). */
class MenuItem(val label: String, val section: Boolean = false, val action: () -> Unit = {})

/**
 * In-tree context menu (not a dialog, so gamepad keys keep reaching MainActivity). It sits on
 * its own navigation layer, so the highlight starts on the first item and returns to where it
 * was when the menu closes. A long menu scrolls (by touch or the gamepad) inside the screen's height; with [footer] a Close
 * button stays visible under it.
 */
@Composable
fun ContextMenu(title: String, subtitle: String, items: List<MenuItem>, onDismiss: () -> Unit, layer: Int = 4, footer: Boolean = false) {
    CompositionLocalProvider(LocalPadLayer provides layer) {
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        val maxHeight = (config.screenHeightDp * 0.88f).dp
        val width = (config.screenWidthDp * 0.92f).coerceIn(240f, 360f).dp
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center,
        ) {
            val enter = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(Unit) { enter.animateTo(1f, androidx.compose.animation.core.tween(200, easing = VitaMotion.Ease)) }
            val scroll = androidx.compose.foundation.rememberScrollState()
            Column(
                Modifier
                    .graphicsLayer {
                        alpha = enter.value
                        val sc = 0.94f + 0.06f * enter.value
                        scaleX = sc
                        scaleY = sc
                    }
                    .width(width)
                    .heightIn(max = maxHeight)
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                if (subtitle.isNotBlank()) Text(subtitle, color = Color(0xFFAEA79F), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 3)
                if (scroll.canScrollBackward) Text("▲ more above", color = Color(0xFFAEA79F), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .padScroller { scroll.animateScrollBy(it) }
                        .verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    var index = 0
                    items.forEach { item ->
                        if (item.section) {
                            Text(item.label.uppercase(), color = Color(0xFF8FB4FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                        } else {
                            val key = "menu:${index++}"
                            val lit = padHighlighted(key) || padHovered(key)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padClickable(key) { item.action() }
                                    .background(Color.White.copy(alpha = if (lit) 0.26f else 0.10f), RoundedCornerShape(10.dp))
                                    .border(if (lit) 2.dp else 0.dp, if (lit) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) { Text(item.label, color = Color.White, fontSize = 15.sp) }
                        }
                    }
                }
                if (scroll.canScrollForward) Text("▼ more below", color = Color(0xFFAEA79F), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                if (footer) {
                    val key = "menu:close"
                    val lit = padHighlighted(key) || padHovered(key)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padClickable(key) { onDismiss() }
                            .background(Color.White.copy(alpha = if (lit) 0.30f else 0.16f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = if (lit) 1f else 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("Close", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}
