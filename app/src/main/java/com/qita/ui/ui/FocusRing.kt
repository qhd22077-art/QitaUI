package com.qita.ui.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Controller
import com.qita.ui.LaunchableApp

val FocusYellow = Color(0xFFFFD54F)
val MoveCyan = Color(0xFF4FC3F7)

/** A pulsing 0.55..1 value while [active], otherwise 1. Only one item is focused at a time, so this stays cheap. */
@Composable
fun rememberPulse(active: Boolean): Float =
    if (active) {
        val transition = rememberInfiniteTransition(label = "focusPulse")
        transition.animateFloat(
            0.55f, 1f,
            infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse",
        ).value
    } else 1f

/** Soft double glow just outside [shape], drawn behind a bubble that has the highlight. */
fun DrawScope.focusGlow(shape: Shape, color: Color, pulse: Float) {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, color.copy(alpha = 0.22f * pulse), style = Stroke(width = 22.dp.toPx()))
    drawOutline(outline, color.copy(alpha = 0.45f * pulse), style = Stroke(width = 12.dp.toPx()))
}

/**
 * Unmissable highlight for controller/keyboard navigation: a thick yellow ring with a pulsing
 * glow. Place it BEFORE the clickable/focusable modifier in the chain. Passing [app] also
 * records it as the highlighted app so the X/Y buttons know what to act on.
 */
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(10.dp), app: LaunchableApp? = null): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val pulse = rememberPulse(focused)
    Modifier
        .onFocusChanged { state ->
            focused = state.hasFocus
            if (app != null) {
                if (state.hasFocus) Controller.focusedApp = app
                else if (Controller.focusedApp?.packageName == app.packageName) Controller.focusedApp = null
            }
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, FocusYellow.copy(alpha = 0.22f * pulse), style = Stroke(width = 16.dp.toPx()))
                drawOutline(outline, FocusYellow.copy(alpha = 0.5f * pulse), style = Stroke(width = 9.dp.toPx()))
                drawOutline(outline, FocusYellow, style = Stroke(width = 4.dp.toPx()))
            }
        }
}

/** Moves the highlight to this app's item when [Controller.focusPackage] names it. Place before the focusable modifier. */
fun Modifier.focusOnRequest(packageName: String): Modifier = composed {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Controller.focusPackage) {
        if (Controller.focusPackage == packageName) {
            runCatching { requester.requestFocus() }
            Controller.focusPackage = null
        }
    }
    Modifier.focusRequester(requester)
}

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
        modifier.background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 6.dp),
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

class MenuItem(val label: String, val action: () -> Unit)

/**
 * In-tree context menu (not a dialog, so gamepad keys keep reaching MainActivity). The first item
 * takes the highlight when the gamepad is in use.
 */
@Composable
fun ContextMenu(title: String, subtitle: String, items: List<MenuItem>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (Controller.padActive) runCatching { first.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(300.dp)
                .background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFAEA79F), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            items.forEachIndexed { i, item ->
                Text(
                    item.label,
                    Modifier
                        .fillMaxWidth()
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .focusRing(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .clickable { item.action() }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    color = Color.White, fontSize = 15.sp,
                )
            }
        }
    }
}
