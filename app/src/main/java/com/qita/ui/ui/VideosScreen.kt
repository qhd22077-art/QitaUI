package com.qita.ui.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.qita.ui.Media
import com.qita.ui.Settings
import com.qita.ui.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Videos bubble: the videos on the device (screen recordings included) in a grid, and a simple full-screen player. */
@Composable
fun VideosScreen(settings: Settings, wallpaper: ImageBitmap?, onClose: () -> Unit) {
    val context = LocalContext.current
    val theme = settings.theme
    var allowed by remember { mutableStateOf(Media.videosAllowed(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it || Media.videosAllowed(context) }
    var videos by remember { mutableStateOf<List<Video>?>(null) }
    LaunchedEffect(allowed) { if (allowed) videos = withContext(Dispatchers.IO) { Media.videos(context) } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Media.clearThumbs() } }
    var album by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<Video?>(null) }
    val albums = remember(videos) { videos.orEmpty().groupingBy { it.album }.eachCount().entries.sortedByDescending { it.value } }
    val shown = remember(videos, album) { videos.orEmpty().filter { album == null || it.album == album } }
    val grid = rememberLazyGridState()
    BackHandler(enabled = true) { if (playing != null) playing = null else onClose() }

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
                Text("Videos", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
                Row(Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowed && videos != null) {
                        MediaChip("videos:album:all", "All ${videos.orEmpty().size}", album == null) { album = null }
                        albums.take(12).forEachIndexed { i, e -> MediaChip("videos:album:$i", "${e.key} ${e.value}", album == e.key) { album = e.key } }
                    }
                }
                BarButton("videos:close", "Close", onClose)
            }
            when {
                !allowed -> NeedPermission("Videos needs permission to read the videos on this device.") { ask.launch(Media.videoPermission()) }
                videos == null -> Text("Reading videos…", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(24.dp))
                shown.isEmpty() -> Text("No videos found.", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(24.dp))
                else -> LazyVerticalGrid(
                    GridCells.Adaptive(160.dp),
                    Modifier.fillMaxSize().padScroller { grid.animateScrollBy(it) },
                    state = grid,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown.size, key = { shown[it].id }) { i ->
                        val v = shown[i]
                        val thumb by produceState<ImageBitmap?>(null, v.id) { value = withContext(Dispatchers.IO) { Media.thumb(context, v.uri, 320) } }
                        Box(
                            Modifier
                                .aspectRatio(16f / 9f)
                                .padClickable("videos:v:${v.id}", corner = 8.dp, pad = 2.dp, onClick = { playing = v })
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.4f)),
                        ) {
                            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            Text(
                                clock(v.ms), Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                                color = Color.White, fontSize = 11.sp,
                            )
                            Text(
                                v.title, Modifier.align(Alignment.BottomStart).padding(4.dp).fillMaxWidth(0.62f), color = Color.White, fontSize = 11.sp, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        playing?.let { Player(it) { playing = null } }
    }
}


private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Full-screen playback with Android's own controls (tap the picture to show them). */
@Composable
private fun Player(video: Video, onClose: () -> Unit) {
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { c ->
                    VideoView(c).apply {
                        val controls = MediaController(c)
                        controls.setAnchorView(this)
                        setMediaController(controls)
                        setVideoURI(video.uri)
                        setOnPreparedListener { start() }
                        setOnErrorListener { _, _, _ -> onClose(); true }
                        setOnCompletionListener { onClose() }
                    }
                },
                modifier = Modifier.align(Alignment.Center).fillMaxSize(),
                onRelease = { it.stopPlayback() },
            )
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) { BarButton("player:close", "Close", onClose) }
        }
    }
}
