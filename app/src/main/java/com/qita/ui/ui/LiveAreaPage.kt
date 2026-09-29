package com.qita.ui.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
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
    HorizontalPager(
        pagerState,
        Modifier.fillMaxSize(),
        key = { i -> if (i == 0) "home" else pages.getOrNull(i - 1)?.packageName ?: i },
    ) { page ->
        val app = pages.getOrNull(page - 1)
        if (app == null) {
            Box(Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    val s = 1f - 0.06f * distance
                    scaleX = s
                    scaleY = s
                },
            ) {
                LiveAreaPage(
                    app, settings,
                    launches = counts[app.packageName] ?: 0,
                    onLaunch = { onLaunch(app) },
                    onCloseApp = { onClosePage(app) },
                    onInfo = { onInfo(app) },
                )
            }
        }
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
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Extra px the corner has been peeled beyond its resting size.
    val peel = remember { Animatable(0f) }
    var pageWidth by remember { mutableStateOf(1) }
    val baseFold = with(density) { 56.dp.toPx() }
    val tint = app.tint

    Column(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding()) {
        StatusBar(settings.use24h, settings.showBattery)
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { pageWidth = it.width }) {
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
                // The translucent panel that frames the page, like the one on the lock screen.
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp, vertical = 14.dp)
                        .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Gate(app, onLaunch, Modifier.weight(0.95f).fillMaxHeight())
                    Column(Modifier.weight(1.05f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val installed = if (app.installTime > 0) DateFormat.getDateInstance().format(Date(app.installTime)) else "Unknown"
                        Banner("Details") {
                            Line("Version ${app.version.ifBlank { "unknown" }}")
                            Line("Installed $installed")
                            Line("Opened from here $launches time${if (launches == 1) "" else "s"}")
                        }
                        Banner("Package") { Line(app.packageName, mono = true) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionPill("card:info:${app.packageName}", "App info", onInfo)
                            ActionPill("card:close:${app.packageName}", "Close app", onCloseApp)
                        }
                        Line("Drag the curled corner to close the app.", dim = true)
                    }
                }
            }

            PeelBack(baseFold + peel.value, tint)
            PeelCorner(
                peel = peel,
                pageWidth = pageWidth,
                padKey = "card:peel:${app.packageName}",
                hint = true,
                repeatHint = false,
                tapToPeel = false,
                onPeeled = onCloseApp,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/** The big launch gate: the app's sphere and name over a translucent card, with Start along the bottom. */
@Composable
private fun Gate(app: LaunchableApp, onLaunch: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .padClickable("card:start:${app.packageName}", corner = 22.dp, onClick = onLaunch)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.10f))), shape)
            .border(1.5.dp, Color.White.copy(alpha = 0.55f), shape),
    ) {
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
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.38f))),
                    RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("Start", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Banner(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.13f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape)
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

@Composable
private fun ActionPill(key: String, label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .padClickable(key, corner = null, onClick = onClick)
            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 22.dp, vertical = 9.dp),
        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
    )
}
