package com.qita.ui.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Media
import com.qita.ui.MusicPlayer
import com.qita.ui.Settings
import com.qita.ui.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * The Music bubble: the music on the device as a list with search, and a player bar with previous, play or pause, next, shuffle,
 * repeat and a seek bar. Music keeps playing when this screen is closed (until the launcher's process ends).
 */
@Composable
fun MusicScreen(settings: Settings, wallpaper: ImageBitmap?, onClose: () -> Unit) {
    val context = LocalContext.current
    val theme = settings.theme
    var allowed by remember { mutableStateOf(Media.allowed(context, true)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it || Media.allowed(context, true) }
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(allowed) { if (allowed) tracks = withContext(Dispatchers.IO) { Media.tracks(context) } }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = remember(tracks, query) {
        val all = tracks.orEmpty()
        if (query.isBlank()) all else all.filter { it.title.contains(query.trim(), true) || it.artist.contains(query.trim(), true) || it.album.contains(query.trim(), true) }
    }
    val list = rememberLazyListState()
    BackHandler(enabled = true) { onClose() }
    // The position moves while a track plays.
    var position by remember { mutableStateOf(0) }
    LaunchedEffect(MusicPlayer.playing, MusicPlayer.index) {
        position = MusicPlayer.positionMs
        while (MusicPlayer.playing) {
            delay(500)
            position = MusicPlayer.positionMs
        }
    }

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
                Text("Music", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
                if (allowed && tracks != null) {
                    Text("${tracks.orEmpty().size} tracks", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    MediaChip("music:search", if (searching) "Search ✕" else "Search", searching) { searching = !searching; if (!searching) query = "" }
                }
                Box(Modifier.weight(1f))
                BarButton("music:close", "Close", onClose)
            }
            if (searching) {
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 9.dp)) {
                            if (query.isEmpty()) Text("Title, artist or album", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                            inner()
                        }
                    },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !allowed -> NeedPermission("Music needs permission to read the audio files on this device.") { ask.launch(Media.permission(true)) }
                    tracks == null -> Text("Reading music…", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(24.dp))
                    shown.isEmpty() -> Text(if (query.isBlank()) "No music found." else "No tracks match.", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(24.dp))
                    else -> LazyColumn(
                        Modifier.fillMaxSize().padScroller { list.animateScrollBy(it) },
                        state = list,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(shown, key = { _, t -> t.id }) { i, t ->
                            val now = MusicPlayer.current?.id == t.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padClickable("music:t:${t.id}", corner = 10.dp, pad = 2.dp, onClick = { MusicPlayer.play(context, shown, i) })
                                    .background(Color.White.copy(alpha = if (now) 0.30f else 0.10f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(if (now && MusicPlayer.playing) "▶" else "♪", color = Color.White, fontSize = 15.sp)
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.artist + if (t.album.isNotBlank()) " · ${t.album}" else "", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(clock(t.ms), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            // The player bar.
            val current = MusicPlayer.current
            if (current != null) {
                val duration = MusicPlayer.durationMs.coerceAtLeast(1)
                Column(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(current.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(clock(position.toLong()), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                        Slider(
                            value = position.coerceIn(0, duration).toFloat(), onValueChange = { position = it.toInt(); MusicPlayer.seekTo(it.toInt()) },
                            valueRange = 0f..duration.toFloat(), modifier = Modifier.weight(1f).height(24.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                        )
                        Text(clock(duration.toLong()), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BarButton("music:prev", "⏮") { MusicPlayer.previous() }
                        BarButton("music:play", if (MusicPlayer.playing) "Pause" else "Play") { MusicPlayer.toggle() }
                        BarButton("music:next", "⏭") { MusicPlayer.next() }
                        MediaChip("music:shuffle", "Shuffle", MusicPlayer.shuffle) { MusicPlayer.shuffle = !MusicPlayer.shuffle }
                        MediaChip("music:repeat", listOf("Repeat off", "Repeat all", "Repeat one")[MusicPlayer.repeat], MusicPlayer.repeat != 0) { MusicPlayer.repeat = (MusicPlayer.repeat + 1) % 3 }
                        MediaChip("music:stop", "Stop", false) { MusicPlayer.stop() }
                    }
                }
            }
        }
    }
}
