package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.LaunchableApp
import com.qita.ui.Settings
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.absoluteValue

/**
 * The open LiveArea pages laid out side by side, like the real Vita: swipe left or right to move
 * between them. The page before the first one is the home screen (transparent), so swiping back
 * past it returns home.
 */
@Composable
fun LiveAreaHost(
    pages: List<LaunchableApp>,
    current: Int,
    settings: Settings,
    counts: Map<String, Int>,
    onSettle: (Int) -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onClosePage: (LaunchableApp) -> Unit,
    onInfo: (LaunchableApp) -> Unit,
) {
    val settle by rememberUpdatedState(onSettle)
    val pagerState = rememberPagerState(initialPage = (current + 1).coerceIn(0, pages.size)) { pages.size + 1 }
    // Report where the pager comes to rest: -1 is the home screen, 0.. are the open pages.
    LaunchedEffect(pagerState) { snapshotFlow { pagerState.settledPage }.collect { settle(it - 1) } }
    // Opening or closing a page from elsewhere moves the pager to it.
    LaunchedEffect(current, pages.size) {
        val target = (current + 1).coerceIn(0, pages.size)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The page sits inside side margins where the wallpaper shows and the neighbouring pages barely peek in.
        // Held true by a page's peel corner while a finger is on it, so swiping does not steal the peel.
        val peelLock = remember { mutableStateOf(false) }
        // Each open page's peel amount lives here, so the peel corner can sit above the pager, outside its reach.
        val peels = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }
        CompositionLocalProvider(LocalPeelLock provides peelLock) {
        HorizontalPager(
            pagerState,
            Modifier.fillMaxSize(),
            userScrollEnabled = !peelLock.value,
            contentPadding = PaddingValues(horizontal = maxWidth * 0.07f),
            key = { i -> if (i == 0) "home" else pages.getOrNull(i - 1)?.packageName ?: i },
        ) { page ->
            val app = pages.getOrNull(page - 1)
            if (app == null) {
                Box(Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val signed = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val distance = signed.absoluteValue.coerceIn(0f, 1f)
                        // The page in the middle floats above its neighbours, which are smaller, dimmer and drift slower.
                        val s = 1f - 0.12f * distance
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.85f * distance
                        translationX = signed.coerceIn(-1f, 1f) * size.width * 0.05f
                        // Cover-flow: the neighbours turn to face the middle and sink back in depth.
                        cameraDistance = 14f * density
                        rotationY = signed.coerceIn(-1f, 1f) * 38f
                    },
                ) {
                    LiveAreaPage(
                        app, settings,
                        launches = counts[app.packageName] ?: 0,
                        onLaunch = { onLaunch(app) },
                        onCloseApp = { onClosePage(app) },
                        onInfo = { onInfo(app) },
                        peel = peels.getOrPut(app.packageName) { Animatable(0f) },
                    )
                }
            }
        }
        // The peel corner of the page in the middle is drawn above the pager as a sibling. A touch that lands on it goes only to
        // it, never to the pager underneath, so a peel can no longer turn into a swipe to the next app.
        val current = pages.getOrNull(pagerState.currentPage - 1)
        if (current != null) {
            key(current.packageName) {
                PeelCorner(
                    peel = peels.getOrPut(current.packageName) { Animatable(0f) },
                    pageWidth = (constraints.maxWidth * 0.86f).toInt(),
                    padKey = "card:peel:${current.packageName}",
                    hint = true,
                    repeatHint = false,
                    tapToPeel = false,
                    onPeeled = { onClosePage(current); peels.remove(current.packageName) },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 28.dp, end = maxWidth * 0.07f),
                )
            }
        }
        }
        // Arrows on the edges show there is another page (or home) that way. They are drawn only, so swipes reach the pager.
        if (pagerState.currentPage > 0) EdgeArrow(true, Modifier.align(Alignment.CenterStart))
        if (pagerState.currentPage < pages.size) EdgeArrow(false, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun EdgeArrow(left: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.padding(horizontal = 13.dp).size(width = 14.dp, height = 26.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            if (left) { moveTo(w, 0f); lineTo(0f, h / 2f); lineTo(w, h) } else { moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h) }
        }
        drawPath(p, Color.White.copy(alpha = 0.9f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * One app's LiveArea: a full-screen sheet under the information bar, coloured from the app's icon,
 * with a translucent panel holding the big launch gate and information banners. Drag the curled
 * top-right corner toward the bottom-left to peel the sheet away and close the app.
 */
@Composable
fun LiveAreaPage(
    app: LaunchableApp,
    settings: Settings,
    launches: Int,
    onLaunch: () -> Unit,
    onCloseApp: () -> Unit,
    onInfo: () -> Unit,
    /** Extra px the corner has been peeled beyond its resting size. The host owns it and drives it from the peel corner. */
    peel: Animatable<Float, AnimationVector1D>,
) {
    val density = LocalDensity.current
    val baseFold = with(density) { 56.dp.toPx() }
    val tint = app.tint

    Column(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding()) {
        // The information bar is drawn once by the home screen, so it stays put while pages are swiped.
        Spacer(Modifier.height(28.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        shape = PeelShape(baseFold + peel.value)
                        clip = true
                    },
            ) {
                // Each app gets its own sky, tinted from its icon.
                BubbleBackground(
                    top = lerp(tint, Color.Black, 0.55f),
                    mid = lerp(tint, Color.Black, 0.12f),
                    bottom = lerp(tint, Color.White, 0.60f),
                    particles = settings.particles,
                    particleCount = settings.particleCount,
                )
                // A huge, soft copy of the app's art behind everything, so each page has its own look.
                Image(
                    app.icon, null,
                    Modifier.fillMaxSize().graphicsLayer { scaleX = 2.6f; scaleY = 2.6f; rotationZ = -14f; alpha = 0.22f },
                    contentScale = ContentScale.Crop,
                )
                // The translucent panel that frames the page, like the one on the lock screen.
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp, vertical = 14.dp)
                        .vitaPanel(16.dp, 0.75f)
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Gate(app, onLaunch, Modifier.weight(0.95f).fillMaxHeight().staggerIn(0, scaleFrom = 0.88f))
                    Column(Modifier.weight(1.05f).fillMaxHeight().staggerIn(1, fromX = 70f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val action = app.action
                        if (action != null) {
                            Banner("About") { Line(action.blurb) }
                            Line("Drag the curled corner to close this page.", dim = true)
                        } else {
                            val installed = if (app.installTime > 0) DateFormat.getDateInstance().format(Date(app.installTime)) else "Unknown"
                            Banner("Details") {
                                Line("Version ${app.version.ifBlank { "unknown" }}")
                                Line("Installed $installed")
                                Line("Opened from here $launches time${if (launches == 1) "" else "s"}")
                            }
                            Banner("Package") { Line(app.packageName, mono = true) }
                            Line("Drag the curled corner to close the app.", dim = true)
                        }
                    }
                }
            }

            // The sheet's edges fall into shadow, so it looks like it floats above the wallpaper.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val edge = 18.dp.toPx()
                        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent), 0f, edge), size = Size(edge, size.height))
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)), size.width - edge, size.width),
                            topLeft = Offset(size.width - edge, 0f), size = Size(edge, size.height),
                        )
                    },
            )
            // Square action tiles hang from the top edge, like the Vita's Update / Help tiles.
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                if (app.action == null) {
                    ActionTile("card:info:${app.packageName}", "i", Color(0xFF2E7DD7), onInfo)
                    ActionTile("card:close:${app.packageName}", "\u2715", Color(0xFFE0453A), onCloseApp)
                }
            }
            PeelBack(baseFold + peel.value, tint)
        }
    }
}

/** The launch gate: a framed card of the app's art with its sphere and name, and a Start bar along the bottom. */
@Composable
private fun Gate(app: LaunchableApp, onLaunch: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .padClickable("card:start:${app.packageName}", corner = 22.dp, onClick = onLaunch)
            .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(app.tint, Color.White, 0.35f), lerp(app.tint, Color.Black, 0.35f)))),
    ) {
        // The art, enlarged and soft, fills the card.
        Image(
            app.icon, null,
            Modifier.fillMaxSize().graphicsLayer { scaleX = 2.0f; scaleY = 2.0f; alpha = 0.40f },
            contentScale = ContentScale.Crop,
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = 46.dp)) {
            val sphere = minOf(maxHeight * 0.66f, maxWidth * 0.62f)
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Sphere(app, sphere, elevation = 12.dp)
                Text(
                    app.label,
                    Modifier.padding(top = 10.dp),
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 5f)),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(46.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.50f)))),
            contentAlignment = Alignment.Center,
        ) {
            // A fine light line along the top of the bar.
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.45f)))
            Text("Start", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        // The white frame goes on top of everything.
        Box(Modifier.fillMaxSize().border(2.5.dp, Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.55f))), shape))
    }
}

@Composable
private fun Banner(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .vitaPanel(14.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Line(text: String, mono: Boolean = false, dim: Boolean = false) {
    Text(
        text,
        color = Color.White.copy(alpha = if (dim) 0.65f else 0.92f),
        fontSize = if (mono) 11.sp else 13.sp,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
    )
}

/** A square glossy tile with a round symbol on it, hanging from the top of the page. */
@Composable
private fun ActionTile(key: String, glyph: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
    Box(
        Modifier
            .size(58.dp)
            .padClickable(key, corner = 8.dp, onClick = onClick)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFD6DCE6))))
            .border(1.dp, Color.White.copy(alpha = 0.9f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.35f), color))),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionPill(key: String, label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .padClickable(key, corner = null, onClick = onClick)
            .vitaPanel(20.dp, 1.4f)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
    )
}
