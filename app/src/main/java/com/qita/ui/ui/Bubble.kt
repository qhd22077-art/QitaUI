package com.qita.ui.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.LaunchableApp

/**
 * Glossy circular app icon with its label underneath.
 *
 * Tap opens the app's page. Long-press then drag reports drag callbacks (offsets are local to
 * the bubble); the parent decides what a long-press without movement means.
 */
@Composable
fun Bubble(
    app: LaunchableApp,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    shape: Shape = CircleShape,
    showLabel: Boolean = true,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onPositioned: (Rect) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    Column(
        modifier
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .alpha(if (hidden) 0f else 1f)
            .scale(scale)
            .clickable(interaction, indication = null, onClick = onClick)
            // After clickable in the chain so it sees events first and consumes the final "up" of a
            // long-press drag, which stops the clickable from also firing a tap.
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start(it) },
                    onDrag = { change, amount -> change.consume(); drag(amount) },
                    onDragEnd = { end() },
                    onDragCancel = { cancel() },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFD5EBFA))))
                .border(2.dp, Color.White.copy(alpha = 0.8f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                app.icon, app.label,
                Modifier.size(size * 0.66f).clip(shape),
            )
            // Gloss highlight on the upper half.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = size * 0.04f)
                    .size(size * 0.8f, size * 0.4f)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent))),
            )
        }
        if (showLabel) Text(
            app.label,
            Modifier.padding(top = 6.dp),
            color = Color.White, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
