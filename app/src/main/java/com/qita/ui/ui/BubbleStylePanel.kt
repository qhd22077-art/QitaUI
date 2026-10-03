package com.qita.ui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.BubbleStyle
import kotlin.math.roundToInt

/** The colours offered as a tint (the first, no tint, is handled apart). */
private val TINTS = listOf(
    0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFFECEFF1, 0xFF607D8B,
).map { it.toInt() }

/**
 * What the user can change on a built-in bubble: how see-through it is, a clear-glass look like the folder bubbles, a tint,
 * and a picture of its own. Drawn in the screen (not a dialog) so the gamepad keeps working.
 */
@Composable
fun BubbleStylePanel(
    label: String,
    style: BubbleStyle,
    onStyle: (BubbleStyle) -> Unit,
    /** Called when a slider is let go, so the look is saved once instead of at every step. */
    onSettled: () -> Unit,
    /** The bubble as it looks now, drawn over a sample of the sky (null shows nothing). */
    preview: @Composable () -> Unit,
    onPicture: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    val scroll = rememberScrollState()
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { onClose() } }, contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth(0.82f)
                    .heightIn(max = 520.dp)
                    .popIn()
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padScroller { scroll.animateScrollBy(it) }
                    .verticalScroll(scroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Customise $label", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                // How it looks right now, over a patch of sky (the screen behind this panel is dimmed).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF0A2C9A), Color(0xFF1B5BD8), Color(0xFFB4D8FF)))),
                    contentAlignment = Alignment.Center,
                ) { preview() }

                // Translucency: 0% is solid.
                val see = ((1f - style.alpha) * 100).roundToInt()
                val sliderLit = padHighlighted("bstyle:alpha") || padHovered("bstyle:alpha")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padTarget("bstyle:alpha", corner = 10.dp, pad = 2.dp, ring = false, onAdjust = { dir ->
                            onStyle(style.copy(alpha = (style.alpha - dir * 0.05f).coerceIn(0.25f, 1f)))
                            onSettled()
                        })
                        .litEdge(sliderLit, 10.dp)
                        .background(Color.White.copy(alpha = if (sliderLit) 0.16f else 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Translucency: $see%", color = Color.White, fontSize = 15.sp)
                    Slider(
                        value = 1f - style.alpha,
                        onValueChange = { onStyle(style.copy(alpha = 1f - it)) },
                        onValueChangeFinished = onSettled,
                        valueRange = 0f..0.75f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                    )
                }

                Text("Look", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleChip("bstyle:glass:black", "Black glass", style.glass != true) { onStyle(style.copy(glass = null)) }
                    StyleChip("bstyle:glass:clear", "Clear glass", style.glass == true) { onStyle(style.copy(glass = true)) }
                }

                Text("Tint", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StyleChip("bstyle:tint:none", "None", style.tint == null) { onStyle(style.copy(tint = null)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TINTS.take(5).forEach { c -> Swatch("bstyle:tint:$c", c, style.tint == c) { onStyle(style.copy(tint = c)) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TINTS.drop(5).forEach { c -> Swatch("bstyle:tint:$c", c, style.tint == c) { onStyle(style.copy(tint = c)) } }
                }
                Text(
                    "The tint colours the bubble's background (or its glass). A picture or theme icon keeps its own colours.",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StyleChip("bstyle:picture", if (style.picture) "Change picture" else "Choose picture", false, onPicture)
                    StyleChip("bstyle:reset", "Reset", false, onReset)
                    StyleChip("bstyle:done", "Done", false, onClose)
                }
            }
        }
    }
}

/** A small round colour button; lit when touched or highlighted by the gamepad, ringed when chosen. */
@Composable
private fun Swatch(key: String, color: Int, selected: Boolean, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Box(
        Modifier
            .size(36.dp)
            .padClickable(key, corner = null, pad = 2.dp, ring = false, onClick = onClick)
            .litEdge(lit, 18.dp)
            .background(Color(color), CircleShape)
            .border(if (selected || lit) 3.dp else 1.dp, Color.White.copy(alpha = if (selected || lit) 1f else 0.5f), CircleShape),
    )
}

@Composable
private fun StyleChip(key: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padClickable(key, corner = 14.dp, pad = 2.dp, ring = false, onClick = onClick)
            .litEdge(lit, 14.dp)
            .background(if (selected) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = if (lit) 0.30f else 0.14f), RoundedCornerShape(14.dp))
            .border(if (lit) 2.dp else 1.dp, Color.White.copy(alpha = if (lit || selected) 0.95f else 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
    )
}
