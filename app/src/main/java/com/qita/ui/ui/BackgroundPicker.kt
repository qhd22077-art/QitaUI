package com.qita.ui.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Controller
import com.qita.ui.PAGE_PHOTO
import com.qita.ui.THEMES

private val Aqua = Color(0xFF40E0E0)

/**
 * "Background select" for one home page: a strip of wallpaper choices along the bottom that applies
 * at once, so the page behind it previews the result. Swipe the pages behind it to change another one.
 * [override] is null for the default background, a theme index, or [PAGE_PHOTO].
 */
@Composable
fun BackgroundPicker(
    page: Int,
    override: Int?,
    defaultThemeIndex: Int,
    onTheme: (Int) -> Unit,
    onDefault: () -> Unit,
    onPhoto: (Uri) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onPhoto(uri) }
    val scroll = rememberScrollState()
    // Leave room for the button hints while the gamepad is in use.
    val bottom by animateDpAsState(if (Controller.padActive) 54.dp else 14.dp, tween(200), label = "pickerBottom")
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.66f))))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(start = 18.dp, end = 18.dp, top = 30.dp, bottom = bottom.coerceAtLeast(0.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Background · page ${page + 1}",
                    Modifier.weight(1f),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                val lit = padHighlighted("bg:done") || padHovered("bg:done")
                Text(
                    "Done",
                    Modifier
                        .padClickable("bg:done", corner = null, onClick = onDone)
                        .background(Color.White, RoundedCornerShape(50))
                        .then(if (lit) Modifier.border(3.dp, Aqua, RoundedCornerShape(50)) else Modifier)
                        .padding(horizontal = 22.dp, vertical = 6.dp),
                    color = Color(0xFF0B3D91), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padScroller { scroll.animateScrollBy(it) }
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val base = THEMES[defaultThemeIndex.coerceIn(THEMES.indices)]
                Tile("bg:default", "Default", override == null, Brush.verticalGradient(listOf(base.top, base.mid, base.bottom)), onClick = onDefault)
                THEMES.forEachIndexed { i, t ->
                    Tile("bg:$i", t.name, override == i, Brush.verticalGradient(listOf(t.top, t.mid, t.bottom))) { onTheme(i) }
                }
                Tile("bg:photo", "Photo", override == PAGE_PHOTO, null, glyph = "+") { picker.launch("image/*") }
            }
        }
    }
}

@Composable
private fun Tile(key: String, label: String, selected: Boolean, brush: Brush?, glyph: String? = null, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    val shape = RoundedCornerShape(8.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(width = 86.dp, height = 50.dp)
                .padClickable(key, corner = 8.dp, onClick = onClick)
                .clip(shape)
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(Color.White.copy(alpha = 0.18f)))
                .border(if (selected || lit) 3.dp else 1.dp, if (selected || lit) Aqua else Color.White.copy(alpha = 0.6f), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (glyph != null) Text(glyph, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** The small wallpaper button of the page edit view: a frame with a curve, half filled with white. */
@Composable
fun WallpaperButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lit = padHighlighted("edit:bg") || padHovered("edit:bg")
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(width = 60.dp, height = 42.dp)
            .padClickable("edit:bg", corner = 8.dp, onClick = onClick)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.22f))
            .border(if (lit) 3.dp else 2.dp, if (lit) Aqua else Color.White.copy(alpha = 0.9f), shape),
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val w = size.width
            val h = size.height
            val fill = Path().apply {
                moveTo(0f, h * 0.75f)
                cubicTo(w * 0.35f, h * 0.30f, w * 0.65f, h * 1.00f, w, h * 0.30f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fill, Color.White)
            drawRoundRect(Color.White, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}
