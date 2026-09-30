package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Vita lock screen, as on the console: the wallpaper behind a thin-bordered, almost clear panel inset from the edges, the
 * date above a large thin clock (bottom right by default), and a small curled corner at the top right. Peel the corner away
 * (or tap it, or double-tap anywhere, or press the confirm button) to reveal the home screen. This is in-app only: Android
 * does not let a launcher replace the phone's real lock screen, so it is visual and not a security lock.
 */
@Composable
fun LockScreen(
    settings: Settings,
    wallpaper: ImageBitmap?,
    lockWallpaper: ImageBitmap?,
    fontFor: (Int) -> FontFamily,
    onUnlock: () -> Unit,
) {
    val density = LocalDensity.current
    val peel = remember { Animatable(0f) }
    var pageWidth by remember { mutableStateOf(1) }
    // The curl is a small corner, about a tenth of the panel's width.
    val baseFold = pageWidth * 0.095f
    val radius = with(density) { 12.dp.toPx() }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(5_000)
        }
    }
    // Tapping the corner only unlocks when the user has chosen that; by default it needs a real drag.
    val tapUnlock by rememberUpdatedState(settings.lockTapPeel)
    // The clock and date ease in.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(480, easing = VitaMotion.Ease)) }

    Column(
        Modifier
            .fillMaxSize()
            // Nothing underneath should be touchable while locked; double-tapping is a fallback unlock.
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (tapUnlock) onUnlock() }) }
            .statusBarsPadding(),
    ) {
        StatusBar(settings.use24h, settings.showBattery, showHome = false)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The wallpaper stays put; only the framed panel peels away.
            // The background is the home one, a theme of its own, or the user's own picture.
            val mode = if (settings.lockBgMode == 2 && lockWallpaper == null) 0 else settings.lockBgMode
            val theme = if (mode == 1) com.qita.ui.THEMES[settings.lockTheme.coerceIn(com.qita.ui.THEMES.indices)] else settings.theme
            val sky = Triple(theme.top, theme.mid, theme.bottom)
            BubbleBackground(
                top = theme.top, mid = theme.mid, bottom = theme.bottom,
                wallpaper = when (mode) { 0 -> wallpaper; 2 -> lockWallpaper; else -> null },
                scene = { SceneMix(theme.scene, theme.scene, 0f, sky, sky) },
            )
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { pageWidth = it.width }
                        .graphicsLayer {
                            shape = PeelShape(baseFold + peel.value, radius)
                            clip = true
                        }
                        .drawBehind { drawLockPanel(settings.lockPanelTint, settings.lockFrame, settings.lockBorder, settings.lockBevel, radius) },
                ) {
                    val clockSize = (maxHeight.value * 0.27f * settings.lockClockSize).sp
                    val dateSize = (maxHeight.value * 0.072f * settings.lockClockSize).sp
                    val clockColor = Color(settings.lockClockColor)
                    val family = fontFor(settings.lockFont)
                    val clockWeight = if (settings.lockFont == 0) FontWeight.ExtraLight else FontWeight.Light
                    val lockNotifs = com.qita.ui.Notifications.items
                    val ampmPad = (maxHeight.value * 0.03f).dp
                    // The content fades as the sheet peels, so nothing is cut off abruptly.
                    val fade = { (1f - peel.value / (pageWidth * 0.35f)).coerceIn(0f, 1f) }
                    val shadow = Shadow(Color.Black.copy(alpha = 0.30f), Offset(0f, 3f), 8f)
                    val pos = settings.lockClockPos
                    Column(
                        Modifier
                            .align(if (pos == 0) Alignment.BottomEnd else if (pos == 1) Alignment.BottomStart else Alignment.TopStart)
                            .padding(horizontal = 22.dp, vertical = 10.dp)
                            .graphicsLayer {
                                alpha = enter.value * fade()
                                translationY = (1f - enter.value) * 12.dp.toPx()
                            },
                        horizontalAlignment = if (pos == 0) Alignment.End else Alignment.Start,
                    ) {
                        if (settings.lockShowDate) Text(
                            SimpleDateFormat("MMMM d (EEEE)", Locale.getDefault()).format(now),
                            color = clockColor.copy(alpha = 0.92f), fontSize = dateSize, fontWeight = FontWeight.Light,
                            fontFamily = family, style = TextStyle(shadow = shadow),
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                SimpleDateFormat(if (settings.use24h) "HH : mm" else "h : mm", Locale.getDefault()).format(now),
                                color = clockColor, fontSize = clockSize, fontWeight = clockWeight,
                                fontFamily = family, style = TextStyle(shadow = shadow),
                            )
                            if (!settings.use24h) {
                                Text(
                                    SimpleDateFormat(" a", Locale.getDefault()).format(now).uppercase(Locale.getDefault()),
                                    Modifier.padding(bottom = ampmPad),
                                    color = clockColor, fontSize = dateSize * 1.3f, fontWeight = FontWeight.Light,
                                    fontFamily = family, style = TextStyle(shadow = shadow),
                                )
                            }
                        }
                    }
                    // The newest notifications, if the user wants them on the lock screen, in the corner the clock is not in.
                    if (settings.lockNotifs && com.qita.ui.Notifications.granted && lockNotifs.isNotEmpty()) {
                        Column(
                            Modifier
                                .align(if (pos == 2) Alignment.BottomStart else Alignment.TopStart)
                                .padding(start = 18.dp, top = 12.dp, bottom = 12.dp)
                                .fillMaxWidth(0.42f)
                                .graphicsLayer { alpha = enter.value * fade() },
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            lockNotifs.take(settings.lockNotifCount.coerceIn(1, 5)).forEach { n ->
                                Row(
                                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(10.dp)).padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (n.icon != null) Image(n.icon, null, Modifier.size(24.dp).clip(CircleShape))
                                    Column {
                                        Text(n.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (n.text.isNotBlank()) Text(n.text, color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }

                // The deep blue under the curl, and the silvery flap.
                PeelBack(baseFold + peel.value, Color(0xFF0B2E9E), radius)

                // Above everything else in the panel, so nothing can cover it.
                PeelCorner(
                    peel = peel,
                    pageWidth = pageWidth,
                    padKey = "lock:peel",
                    hint = true,
                    repeatHint = true,
                    tapToPeel = settings.lockTapPeel,
                    onPeeled = onUnlock,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

/**
 * The lock screen's pane of glass: an almost clear fill, a bevelled frame (bright edge top left, dark edge bottom right, a fine
 * inset line inside it), a sheen across the top and a soft shade along the bottom. [bevel] (0..1) says how deep it looks.
 */
internal fun DrawScope.drawLockPanel(tint: Float, frame: Boolean, border: Float, bevel: Float, radius: Float) {
    val r = CornerRadius(radius, radius)
    drawRoundRect(Color.White.copy(alpha = tint), cornerRadius = r)
    if (!frame) return
    val w = 1.4.dp.toPx()
    val corner = Offset(size.width, size.height)
    // The outer edge: lit from the top left.
    drawRoundRect(
        Brush.linearGradient(listOf(Color.White.copy(alpha = border), Color.White.copy(alpha = border * 0.3f), Color.Black.copy(alpha = 0.40f * bevel)), Offset.Zero, corner),
        cornerRadius = r, style = Stroke(width = w * (1f + bevel)),
    )
    // A fine inset line, dark then light, so the edge looks cut into the glass.
    val inset = w * (2.2f + bevel)
    drawRoundRect(
        Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.30f * bevel), Color.White.copy(alpha = 0.35f * bevel)), Offset.Zero, corner),
        topLeft = Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius((radius - inset).coerceAtLeast(2f)), style = Stroke(width = w),
    )
    // Sheen across the top, shade along the bottom.
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f * bevel), Color.Transparent), 0f, size.height * 0.24f), cornerRadius = r)
    drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.20f * bevel)), size.height * 0.72f, size.height), cornerRadius = r)
}

/** A small live copy of the lock screen for the settings page, so each change can be seen at once. */
@Composable
fun LockPreview(settings: Settings, modifier: Modifier = Modifier) {
    val theme = if (settings.lockBgMode == 1) com.qita.ui.THEMES[settings.lockTheme.coerceIn(com.qita.ui.THEMES.indices)] else settings.theme
    val now = remember { Date() }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(theme.top, theme.mid, theme.bottom)))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
    ) {
        val panelRadius = with(LocalDensity.current) { 8.dp.toPx() }
        // Read here: inside the nested Box, "maxHeight" would be taken from the wrong scope.
        val previewHeight = maxHeight.value
        Box(
            Modifier.fillMaxSize().padding(8.dp).drawBehind { drawLockPanel(settings.lockPanelTint, settings.lockFrame, settings.lockBorder, settings.lockBevel, panelRadius) },
        ) {
            val clockSize = (previewHeight * 0.27f * settings.lockClockSize).sp
            val dateSize = (previewHeight * 0.075f * settings.lockClockSize).sp
            val color = Color(settings.lockClockColor)
            val pos = settings.lockClockPos
            Column(
                Modifier.align(if (pos == 0) Alignment.BottomEnd else if (pos == 1) Alignment.BottomStart else Alignment.TopStart).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = if (pos == 0) Alignment.End else Alignment.Start,
            ) {
                if (settings.lockShowDate) Text(SimpleDateFormat("MMMM d (EEEE)", Locale.getDefault()).format(now), color = color.copy(alpha = 0.92f), fontSize = dateSize, fontWeight = FontWeight.Light)
                Text(SimpleDateFormat(if (settings.use24h) "HH : mm" else "h : mm", Locale.getDefault()).format(now), color = color, fontSize = clockSize, fontWeight = FontWeight.ExtraLight)
            }
        }
    }
}
