package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.LaunchableApp
import kotlinx.coroutines.delay

/**
 * Glossy app bubble with its label underneath.
 *
 * Tap opens the app's page. Long-press then drag reports drag callbacks (offsets are local to
 * the bubble); the parent decides what a long-press without movement means. When the gamepad
 * highlight is on it ([padKey] registers it with PadNav) the bubble grows and its name lights up
 * (the ring itself is drawn by PadRing). [moving] marks the bubble being carried in controller
 * move mode (cyan glow, lifted).
 */
@Composable
fun Bubble(
    app: LaunchableApp,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padKey: Any? = null,
    hidden: Boolean = false,
    shape: Shape = CircleShape,
    showLabel: Boolean = true,
    moving: Boolean = false,
    enterDelay: Int = 0,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onPositioned: (Rect) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused = padHighlighted(padKey)
    val lit = focused || moving
    val pulse = rememberPulse(moving)

    // Springy grow/shrink for press, highlight and lift.
    val scale by animateFloatAsState(
        if (moving) 1.22f else if (pressed) 0.92f else if (focused) 1.15f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )
    // Entrance: bubbles pop in one after another.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(enterDelay.toLong())
        appear.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 300f))
    }

    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    Column(
        modifier
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .graphicsLayer {
                val s = scale * (0.6f + 0.4f * appear.value)
                scaleX = s
                scaleY = s
                alpha = (if (hidden) 0f else 1f) * appear.value.coerceIn(0f, 1f)
            }
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
                // Registered on the circle itself so the gamepad ring hugs the icon, not the label.
                .padTarget(padKey, corner = null, app = app, pad = 10.dp, bring = false, onClick = onClick)
                .shadow(if (lit) (10 + 6 * pulse).dp else 4.dp, shape, ambientColor = Color(0xFFFFD54F), spotColor = if (moving) MoveCyan else Color.Black)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFD5EBFA))))
                .border(if (moving) 4.dp else 2.dp, if (moving) MoveCyan else Color.White.copy(alpha = 0.8f), shape),
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
        if (showLabel) {
            Text(
                app.label,
                Modifier
                    .padding(top = 6.dp)
                    .then(
                        if (lit) Modifier.background(if (moving) MoveCyan else FocusYellow, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp)
                        else Modifier,
                    ),
                color = if (lit) Color(0xFF1B1B1B) else Color.White.copy(alpha = 0.95f),
                fontSize = 12.sp,
                fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
