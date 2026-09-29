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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.drawBehind
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
        val light = lerp(app.tint, Color.White, 0.42f)
        val dark = lerp(app.tint, Color.Black, 0.38f)
        Box(
            Modifier
                .size(size)
                // Registered on the circle itself so the gamepad ring hugs the sphere, not the label.
                .padTarget(padKey, corner = null, app = app, pad = 10.dp, bring = false, onClick = onClick)
                // Soft white halo just outside the rim.
                .drawBehind { drawCircle(Color.White.copy(alpha = 0.20f), radius = this.size.minDimension / 2f + 5.dp.toPx()) }
                .shadow(if (lit) (12 + 6 * pulse).dp else 7.dp, shape, ambientColor = Color(0xFF0A2A6A), spotColor = if (moving) MoveCyan else Color(0xFF0A2A6A))
                .clip(shape)
                // The sphere: lit from the upper left, darker toward the lower right edge.
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(light, app.tint, dark),
                            center = Offset(this.size.width * 0.42f, this.size.height * 0.30f),
                            radius = this.size.width * 0.85f,
                        ),
                    )
                }
                .border(
                    if (moving) 4.dp else 2.dp,
                    if (moving) Brush.linearGradient(listOf(MoveCyan, MoveCyan)) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f))),
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                app.icon, app.label,
                Modifier.size(size * 0.66f).clip(CircleShape),
            )
            // Glossy highlight across the top, and a soft bounce of light along the bottom.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = size * 0.045f)
                    .size(size * 0.80f, size * 0.44f)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.04f)))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = size * 0.03f)
                    .size(size * 0.74f, size * 0.30f)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.30f)))),
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
                color = if (lit) Color(0xFF1B1B1B) else Color.White,
                fontSize = 13.sp,
                fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
                style = TextStyle(shadow = if (lit) null else Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 5f)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
