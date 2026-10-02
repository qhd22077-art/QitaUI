package com.qita.ui.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Tier
import com.qita.ui.Trophies
import com.qita.ui.Trophy
import kotlinx.coroutines.delay

/** The colours of a cup: light, mid and dark. */
fun tierColors(tier: Tier): Triple<Color, Color, Color> = when (tier) {
    Tier.BRONZE -> Triple(Color(0xFFF0B27A), Color(0xFFC97B3A), Color(0xFF7A4318))
    Tier.SILVER -> Triple(Color(0xFFF4F6F8), Color(0xFFB8C0C8), Color(0xFF6E7780))
    Tier.GOLD -> Triple(Color(0xFFFFE680), Color(0xFFF0B800), Color(0xFF9A6A00))
    Tier.PLATINUM -> Triple(Color(0xFFFFFFFF), Color(0xFF9FE6F5), Color(0xFF4A86B8))
}

/** A small trophy cup drawn in code, in the colours of its [tier]; [locked] draws it grey. */
@Composable
fun TrophyCup(tier: Tier, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, locked: Boolean = false) {
    val (light, mid, dark) = if (locked) Triple(Color(0xFF8A9098), Color(0xFF666C74), Color(0xFF3E4249)) else tierColors(tier)
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val fill = Brush.verticalGradient(listOf(light, mid, dark), startY = h * 0.1f, endY = h * 0.75f)
        // The handles.
        val handle = Stroke(width = w * 0.07f)
        drawArc(mid, 90f, 180f, false, Offset(w * 0.08f, h * 0.16f), Size(w * 0.28f, h * 0.3f), style = handle)
        drawArc(mid, -90f, 180f, false, Offset(w * 0.64f, h * 0.16f), Size(w * 0.28f, h * 0.3f), style = handle)
        // The bowl.
        val bowl = Path().apply {
            moveTo(w * 0.22f, h * 0.08f); lineTo(w * 0.78f, h * 0.08f)
            quadraticBezierTo(w * 0.8f, h * 0.55f, w * 0.5f, h * 0.62f)
            quadraticBezierTo(w * 0.2f, h * 0.55f, w * 0.22f, h * 0.08f)
            close()
        }
        drawPath(bowl, fill)
        // A light streak down the left of the bowl.
        drawPath(
            Path().apply { moveTo(w * 0.3f, h * 0.14f); lineTo(w * 0.38f, h * 0.14f); lineTo(w * 0.42f, h * 0.46f); lineTo(w * 0.36f, h * 0.44f); close() },
            Color.White.copy(alpha = if (locked) 0.18f else 0.55f),
        )
        // Stem and base.
        drawRect(fill, Offset(w * 0.45f, h * 0.6f), Size(w * 0.1f, h * 0.2f))
        drawRoundRect(fill, Offset(w * 0.3f, h * 0.78f), Size(w * 0.4f, h * 0.14f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f))
    }
}

/**
 * The banner that slides down from the top when a trophy is earned: a cup, the name and the points. It stays about three and a half
 * seconds; if several are earned together they follow one another. Drawn above everything, takes no touches.
 */
@Composable
fun TrophyToast(modifier: Modifier = Modifier) {
    val shown = Trophies.shown
    LaunchedEffect(shown) {
        if (shown != null) {
            delay(3500)
            Trophies.finishShown()
        } else if (Trophies.hasQueued()) {
            delay(450)
            Trophies.next()
        }
    }
    AnimatedVisibility(
        visible = shown != null,
        modifier = modifier.statusBarsPadding().padding(top = 26.dp),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val t: Trophy? = shown ?: Trophies.lastShown
        if (t != null) {
            val (light, _, dark) = tierColors(t.tier)
            Row(
                Modifier
                    .widthIn(max = 380.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF2A3038), Color(0xFF0E1013))), RoundedCornerShape(14.dp))
                    .border(1.dp, Brush.verticalGradient(listOf(light.copy(alpha = 0.9f), dark.copy(alpha = 0.5f))), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrophyCup(t.tier, 40.dp)
                Column {
                    Text(t.tier.label + " trophy earned", color = light, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(t.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.tier.points} points", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        } else Box(Modifier.size(1.dp))
    }
}
