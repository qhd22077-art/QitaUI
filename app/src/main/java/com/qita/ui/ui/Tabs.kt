package com.qita.ui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Command
import com.qita.ui.Controller

/**
 * A row of bevelled, glossy tabs for the settings pages. The selected tab is filled, the others are dark glass, and whichever the
 * gamepad or pointer is on gets a bright edge. L1 and R1 step to the previous and next tab, and the selected tab scrolls into view.
 */
@Composable
fun TabStrip(
    labels: List<String>,
    selected: Int,
    keyPrefix: String,
    selectedFill: List<Color>,
    selectedText: Color,
    idleFill: Color,
    modifier: Modifier = Modifier,
    /** Whether L1 and R1 step through these tabs (only one strip on a screen should). */
    padStep: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    val scroll = rememberScrollState()
    val offsets = remember { mutableStateMapOf<Int, Float>() }
    val current = rememberUpdatedState(selected)
    val choose = rememberUpdatedState(onSelect)
    val count = rememberUpdatedState(labels.size)
    LaunchedEffect(padStep) {
        if (!padStep) return@LaunchedEffect
        Controller.commands.collect { cmd ->
            if (cmd is Command.Page) {
                val next = (current.value + cmd.delta).coerceIn(0, count.value - 1)
                if (next != current.value) choose.value(next)
            }
        }
    }
    LaunchedEffect(selected, offsets[selected]) {
        offsets[selected]?.let { scroll.animateScrollTo((it - 60f).toInt().coerceAtLeast(0)) }
    }
    Row(
        modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val key = "$keyPrefix:$i"
            val on = i == selected
            val lit = padHighlighted(key) || padHovered(key)
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .onGloballyPositioned { offsets[i] = it.positionInParent().x }
                    .shadow(if (on) 5.dp else 2.dp, shape)
                    .padClickable(key, corner = 14.dp, pad = 3.dp, ring = false) { onSelect(i) }
                    .background(
                        if (on) Brush.verticalGradient(selectedFill) else Brush.verticalGradient(listOf(idleFill.copy(alpha = if (lit) 0.85f else 0.6f), idleFill.copy(alpha = if (lit) 0.6f else 0.35f))),
                        shape,
                    )
                    .gloss(14.dp, if (on) 0.30f else 0.16f)
                    // The bevel: a bright upper edge, a dark lower one. Whichever tab is lit gets a full white edge.
                    .border(
                        if (lit) 2.dp else 1.dp,
                        if (lit) Brush.verticalGradient(listOf(Color.White, Color.White)) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.35f))),
                        shape,
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) selectedText else Color.White, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}
