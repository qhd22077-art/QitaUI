package com.qita.ui.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Media
import com.qita.ui.Photo
import com.qita.ui.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Photos bubble: the pictures on the device in a grid, by album, and a full-screen viewer with a button to use one as the
 * wallpaper. Reads Android's media database (asks for permission the first time). Nothing is copied.
 */
@Composable
fun PhotosScreen(
    settings: Settings,
    wallpaper: ImageBitmap?,
    onWallpaper: (Uri) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val theme = settings.theme
    var allowed by remember { mutableStateOf(Media.allowed(context, false)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it || Media.allowed(context, false) }
    var photos by remember { mutableStateOf<List<Photo>?>(null) }
    LaunchedEffect(allowed) { if (allowed) photos = withContext(Dispatchers.IO) { Media.photos(context) } }
    DisposableEffectClear()
    var album by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    val albums = remember(photos) { photos.orEmpty().groupingBy { it.album }.eachCount().entries.sortedByDescending { it.value } }
    val shown = remember(photos, album) { photos.orEmpty().filter { album == null || it.album == album } }
    val grid = rememberLazyGridState()
    BackHandler(enabled = true) { if (viewing != null) viewing = null else onClose() }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        BubbleBackground(
            top = theme.top, mid = theme.mid, bottom = theme.bottom, wallpaper = wallpaper, dim = settings.dim,
            scene = { SceneMix(theme.scene, theme.scene, 0f, Triple(theme.top, theme.mid, theme.bottom), Triple(theme.top, theme.mid, theme.bottom)) },
        )
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, showHome = false)
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Photos", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowed && photos != null) {
                        MediaChip("photos:album:all", "All ${photos.orEmpty().size}", album == null) { album = null }
                        albums.take(12).forEachIndexed { i, e -> MediaChip("photos:album:$i", "${e.key} ${e.value}", album == e.key) { album = e.key } }
                    }
                }
                BarButton("photos:close", "Close", onClose)
            }
            when {
                !allowed -> NeedPermission("Photos needs permission to read the pictures on this device.") { ask.launch(Media.permission(false)) }
                photos == null -> Text("Reading pictures…", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(24.dp))
                shown.isEmpty() -> Text("No pictures found.", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(24.dp))
                else -> LazyVerticalGrid(
                    GridCells.Adaptive(112.dp),
                    Modifier.fillMaxSize().padScroller { grid.animateScrollBy(it) },
                    state = grid,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(shown.size, key = { shown[it].id }) { i ->
                        val p = shown[i]
                        val thumb by produceState<ImageBitmap?>(null, p.id) { value = withContext(Dispatchers.IO) { Media.thumb(context, p.uri, 220) } }
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .padClickable("photos:p:${p.id}", corner = 8.dp, pad = 2.dp, onClick = { viewing = i })
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.3f)),
                        ) {
                            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                    }
                }
            }
        }
        viewing?.let { start -> Viewer(shown, start, onWallpaper) { viewing = null } }
    }
}

/** Frees the picture cache when the screen closes (the thumbnails are only needed while it is open). */
@Composable
private fun DisposableEffectClear() {
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Media.clearThumbs() } }
}

@Composable
internal fun NeedPermission(text: String, onAsk: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, color = Color.White, fontSize = 15.sp)
        BarButton("media:allow", "Allow", onAsk)
    }
}

@Composable
internal fun MediaChip(key: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padClickable(key, corner = 16.dp, pad = 2.dp, ring = false, onClick = onClick)
            .litEdge(lit, 16.dp)
            .background(if (selected) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = if (lit) 0.28f else 0.14f), RoundedCornerShape(16.dp))
            .border(if (lit) 2.dp else 1.dp, Color.White.copy(alpha = if (lit || selected) 0.95f else 0.45f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
    )
}

/** The full-screen viewer: swipe between pictures, the buttons set the wallpaper or close. */
@Composable
private fun Viewer(photos: List<Photo>, start: Int, onWallpaper: (Uri) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(start.coerceIn(0, photos.lastIndex)) { photos.size }
    var done by remember { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f)).pointerInput(Unit) { detectTapGestures { } }) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                val p = photos.getOrNull(i)
                val bmp by produceState<ImageBitmap?>(null, p?.id) { value = p?.let { withContext(Dispatchers.IO) { Media.decode(context, it.uri, 2048)?.asImageBitmap() } } }
                bmp?.let { Image(it, null, Modifier.fillMaxSize().padding(bottom = 64.dp), contentScale = ContentScale.Fit) }
            }
            Text(
                done ?: "${pager.currentPage + 1} / ${photos.size}",
                Modifier.align(Alignment.TopCenter).padding(top = 28.dp), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
            )
            Row(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BarButton("viewer:prev", "◀") { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }
                BarButton("viewer:next", "▶") { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(photos.lastIndex)) } }
                BarButton("viewer:wall", "Use as wallpaper") {
                    photos.getOrNull(pager.currentPage)?.let { onWallpaper(it.uri); done = "Wallpaper set" }
                }
                BarButton("viewer:close", "Close", onClose)
            }
        }
    }
}
