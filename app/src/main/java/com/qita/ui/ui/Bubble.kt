package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import kotlin.math.sin

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
    editing: Boolean = false,
    removable: Boolean = true,
    onRemove: () -> Unit = {},
    enterDelay: Int = 0,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onPositioned: (Rect) -> Unit = {},
    /** How far the bubble is rolled by scrolling (radians of pitch), read while drawing. */
    scrollRoll: () -> Float = { 0f },
    /** 0 (far, top row) .. 1 (near, bottom row): nearer bubbles are a little bigger with a longer shadow. */
    depth: Float = 0.5f,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused = padHighlighted(padKey)
    val lit = focused || moving
    val pulse = rememberPulse(moving)
    // Vita-style selection: a white halo and ring with a slow cyan pulse, instead of the yellow ring.
    val hot = focused || padHovered(padKey)
    val glow by animateFloatAsState(if (hot) 1f else 0f, tween(VitaMotion.Short), label = "glow")
    val glowPulse = rememberPulse(hot)

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

    // Edit mode: the bubble wiggles, each with its own rhythm, and shows a remove badge.
    val wiggle: State<Float>? = if (editing) {
        rememberInfiniteTransition(label = "wiggle").animateFloat(
            -2.4f, 2.4f,
            infiniteRepeatable(tween(120 + (app.packageName.hashCode() and 0x3F), easing = LinearEasing), RepeatMode.Reverse),
            label = "wiggleAngle",
        )
    } else null

    // Rolling of the 3D ball: a slow idle sway, a full turn when pressed, a nudge when selected, and the scroll.
    val clock = LocalBallClock.current
    val spin = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            spin.snapTo(0f)
            spin.animateTo(6.2832f, tween(VitaMotion.Long + 300, easing = VitaMotion.Ease))
            spin.snapTo(0f)
        }
    }
    val seed = remember(app.packageName) { (app.packageName.hashCode() and 0xFFFF) / 10430f }
    val roll: () -> Offset = {
        val t = clock.value
        Offset(
            0.16f * sin(t + seed) + spin.value + 0.15f * glow,
            0.10f * sin(t * 1.3f + seed * 1.7f) + scrollRoll() - 0.20f * glow,
        )
    }
    // A soft ring spreads from the bubble when it is pressed.
    val ring = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            ring.snapTo(0f)
            ring.animateTo(1f, tween(VitaMotion.Long + 120, easing = VitaMotion.Ease))
        }
    }
    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    Column(
        modifier
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .drawBehind {
                if (glow > 0.01f) {
                    val c = Offset(this.size.width / 2f, size.toPx() / 2f)
                    val r = size.toPx() / 2f
                    drawCircle(
                        Brush.radialGradient(
                            0.50f to Color(0xFF16E0FF).copy(alpha = 0.55f * glow), 1f to Color.Transparent,
                            center = c, radius = r * 1.55f,
                        ),
                        radius = r * 1.55f, center = c,
                    )
                    drawCircle(Color(0xFF3FE6FF).copy(alpha = 0.85f * glow * glowPulse), radius = r + 6.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = glow), radius = r + 2.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                }
                val p = ring.value
                if (p < 1f) {
                    drawCircle(
                        Color.White.copy(alpha = 0.55f * (1f - p)),
                        radius = size.toPx() / 2f * (1f + 0.45f * p),
                        center = Offset(this.size.width / 2f, size.toPx() / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx() * (1f - p) + 1f),
                    )
                }
            }
            .graphicsLayer {
                val s = scale * (0.6f + 0.4f * appear.value) * (0.94f + 0.12f * depth)
                scaleX = s
                scaleY = s
                rotationZ = wiggle?.value ?: 0f
                alpha = (if (hidden) 0f else 1f) * appear.value.coerceIn(0f, 1f)
            }
            .clickable(interaction, indication = null, onClick = onClick)
            // After clickable in the chain so it sees events first and consumes the final "up" of a
            // long-press drag, which stops the clickable from also firing a tap.
            .pointerInput(editing) {
                if (editing) {
                    // While editing, a bubble can be picked up straight away.
                    detectDragGestures(
                        onDragStart = { start(it) },
                        onDrag = { change, amount -> change.consume(); drag(amount) },
                        onDragEnd = { end() },
                        onDragCancel = { cancel() },
                    )
                } else {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start(it) },
                        onDrag = { change, amount -> change.consume(); drag(amount) },
                        onDragEnd = { end() },
                        onDragCancel = { cancel() },
                    )
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Sphere(
                app, size,
                // Registered on the sphere itself so the gamepad ring hugs it, not the label.
                modifier = Modifier.padTarget(padKey, corner = null, app = app, pad = 10.dp, bring = false, ring = false, onClick = onClick),
                shape = shape,
                glow = { glow },
                roll = roll,
                elevation = if (lit) (12 + 6 * pulse).dp else (5 + 5 * depth).dp,
                spot = if (moving) MoveCyan else Color(0xFF0A2A6A),
                rim = if (moving) Brush.linearGradient(listOf(MoveCyan, MoveCyan)) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f))),
                rimWidth = if (moving) 4.dp else 2.dp,
            )
            if (editing && removable) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .border(2.dp, Color.White, CircleShape)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("\u2715", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showLabel) {
            Text(
                app.label,
                Modifier
                    .padding(top = 6.dp)
                    .then(
                        if (lit) Modifier.background(if (moving) MoveCyan else Color.White, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 1.dp)
                        else Modifier,
                    ),
                color = if (lit) Color(0xFF0B3D91) else Color.White,
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
