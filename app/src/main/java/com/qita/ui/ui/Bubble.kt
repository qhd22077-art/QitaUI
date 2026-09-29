package com.qita.ui.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import com.qita.ui.LaunchableApp

/** Glossy circular app icon with its label underneath. */
@Composable
fun Bubble(app: LaunchableApp, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    Column(
        modifier
            .scale(scale)
            .clickable(interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFD5EBFA))))
                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                app.icon, app.label,
                Modifier.size(size * 0.66f).clip(CircleShape),
            )
            // Gloss highlight on the upper half.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = size * 0.04f)
                    .size(size * 0.8f, size * 0.4f)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent))),
            )
        }
        Text(
            app.label,
            Modifier.padding(top = 6.dp),
            color = Color.White, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
