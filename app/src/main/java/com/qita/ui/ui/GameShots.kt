package com.qita.ui.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.qita.ui.Game
import com.qita.ui.GameLibrary
import com.qita.ui.GameStats
import com.qita.ui.systemById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** A small decoded copy of a picture, for the strip on a game's page. */
private fun thumb(file: File): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 360) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/**
 * The banners of a game's LiveArea page: how much it was played, its pictures (in-game and title screen from the cover server,
 * plus the user's own), the user's notes and their tags. [onView] opens a picture, [onEdit] asks to edit "notes" or "tags".
 */
@Composable
internal fun GameBanners(game: Game, onView: (Int) -> Unit, onEdit: (String) -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val rev = GameStats.rev
    val stat = remember(rev) { GameStats.get(c, game.id) }
    val pics = remember(rev) { GameStats.pictures(c, game.id) }
    val last = remember(rev) { GameLibrary.lastPlayed(c)[game.id] }
    // The first time a game's page is opened its pictures are looked for online (if its console has any).
    LaunchedEffect(game.id) {
        if (!GameStats.fetched(c, game.id) && systemById(game.systemId)?.thumbs != null) withContext(Dispatchers.IO) { GameStats.fetchPictures(c, game) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) { GameStats.addPicture(c, game.id, uri) }
    }

    Banner("Played") {
        Line("Started from here ${stat.launches} time${if (stat.launches == 1) "" else "s"}")
        Line(if (stat.playMs > 0) "Played for ${GameStats.duration(stat.playMs)}" else "Play time shows here after you have played")
        if (last != null) Line("Last played ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last))}")
        if (game.systemId != "flash") Line("The time is counted from starting the game until you come back to the launcher, so it is an estimate.", dim = true)
    }

    Banner("Pictures") {
        if (pics.isEmpty()) Line("None yet. Pictures are looked for online when this page opens; you can add your own.", dim = true)
        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pics.forEachIndexed { i, f ->
                val bmp = remember(f.path, f.lastModified()) { thumb(f) }
                Box(
                    Modifier
                        .width(110.dp).height(70.dp)
                        .padClickable("game:shot:$i", corner = 8.dp, pad = 2.dp, onClick = { onView(i) })
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                ) {
                    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        MiniButton("game:shot:add", "Add a screenshot") { picker.launch("image/*") }
    }

    Banner("Notes") {
        Text(
            stat.notes.ifBlank { "Nothing written yet." },
            color = Color.White.copy(alpha = if (stat.notes.isBlank()) 0.65f else 0.92f), fontSize = 13.sp, maxLines = 6,
        )
        MiniButton("game:notes", if (stat.notes.isBlank()) "Write a note" else "Edit note") { onEdit("notes") }
    }

    Banner("Tags") {
        if (stat.tags.isEmpty()) Line("No tags. Tags like “co-op” or “finished” let you filter the games list.", dim = true)
        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stat.tags.forEach { t ->
                Text(
                    t,
                    Modifier.background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color.White, fontSize = 12.sp, maxLines = 1,
                )
            }
        }
        MiniButton("game:tags", "Edit tags") { onEdit("tags") }
    }
}

@Composable
private fun MiniButton(key: String, label: String, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padding(top = 4.dp)
            .padClickable(key, corner = 14.dp, pad = 2.dp, ring = false, onClick = onClick)
            .litEdge(lit, 14.dp)
            .background(Color.White.copy(alpha = if (lit) 0.32f else 0.16f), RoundedCornerShape(14.dp))
            .border(if (lit) 2.dp else 1.dp, Color.White.copy(alpha = if (lit) 0.95f else 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
    )
}

/** Full-screen viewer for a game's pictures: swipe between them (or use the arrows), Delete on one the user added. */
@Composable
internal fun ShotViewer(game: Game, start: Int, onClose: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val rev = GameStats.rev
    val files = remember(rev) { GameStats.pictures(c, game.id) }
    if (files.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pager = rememberPagerState(start.coerceIn(0, files.lastIndex)) { files.size }
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).pointerInput(Unit) { detectTapGestures { onClose() } }) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                val f = files.getOrNull(i)
                val bmp = remember(f?.path, f?.lastModified()) { f?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() } }
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 64.dp), contentScale = ContentScale.Fit)
            }
            Text(
                "${pager.currentPage + 1} / ${files.size}",
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
            )
            Row(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BarButton("shot:prev", "◀") { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }
                BarButton("shot:next", "▶") { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(files.lastIndex)) } }
                val current = files.getOrNull(pager.currentPage)
                if (current != null && GameStats.isOwn(c, game.id, current)) BarButton("shot:del", "Delete") { GameStats.removePicture(c, game.id, current) }
                BarButton("shot:close", "Close", onClose)
            }
        }
    }
}
