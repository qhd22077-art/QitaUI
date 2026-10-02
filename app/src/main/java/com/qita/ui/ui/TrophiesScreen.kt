package com.qita.ui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.RaProfile
import com.qita.ui.RetroAchievements
import com.qita.ui.Settings
import com.qita.ui.Tier
import com.qita.ui.Trophies
import com.qita.ui.Trophy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * The Trophies bubble's screen: your level and points, the launcher's trophies (earned in colour, the rest grey, secret ones hidden
 * until earned) and a RetroAchievements tab with your profile on that site.
 */
@Composable
fun TrophiesScreen(
    settings: Settings,
    wallpaper: androidx.compose.ui.graphics.ImageBitmap?,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val theme = settings.theme
    var tab by remember { mutableStateOf(0) }
    val rev = Trophies.rev
    val list = rememberLazyListState()
    BackHandler(enabled = true) { onClose() }

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
                Text("Trophies", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
                TabChip("trophy:tab:0", "Launcher", tab == 0) { tab = 0 }
                TabChip("trophy:tab:1", "RetroAchievements", tab == 1) { tab = 1 }
                Box(Modifier.weight(1f))
                BarButton("trophy:close", "Close", onClose)
            }
            LazyColumn(
                Modifier.fillMaxSize().padScroller { list.animateScrollBy(it) },
                state = list,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tab == 0) {
                    item { Summary(rev) }
                    items(Trophies.ALL, key = { it.id }) { t -> TrophyRow(t, Trophies.earnedAt(t.id), rev) }
                } else {
                    item { RaPanel(onOpenSettings) }
                }
            }
        }
    }
}

@Composable
private fun TabChip(key: String, label: String, selected: Boolean, onClick: () -> Unit) {
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

/** Level, points and how many of each cup are earned. */
@Composable
private fun Summary(rev: Int) {
    val earned = remember(rev) { Trophies.earnedCount }
    val total = Trophies.ALL.size
    Column(
        Modifier.fillMaxWidth().vitaPanel(14.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Level ${Trophies.level}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${Trophies.points} of ${Trophies.totalPoints} points", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(earned.toFloat() / total).height(8.dp).background(Color.White.copy(alpha = 0.9f)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$earned of $total earned", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Tier.values().forEach { tier ->
                val n = Trophies.ALL.count { it.tier == tier && Trophies.isEarned(it.id) }
                val of = Trophies.ALL.count { it.tier == tier }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TrophyCup(tier, 20.dp)
                    Text("$n/$of", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TrophyRow(t: Trophy, at: Long?, rev: Int) {
    val earned = at != null
    val hidden = t.secret && !earned
    Row(
        Modifier.fillMaxWidth().vitaPanel(12.dp, if (earned) 1f else 0.6f).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TrophyCup(t.tier, 38.dp, locked = !earned)
        Column(Modifier.weight(1f)) {
            Text(
                if (hidden) "Secret trophy" else t.title,
                color = Color.White.copy(alpha = if (earned) 1f else 0.7f), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Text(
                if (hidden) "Keep using the launcher to find it." else t.text,
                color = Color.White.copy(alpha = if (earned) 0.85f else 0.55f), fontSize = 12.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${t.tier.points}", color = Color.White.copy(alpha = if (earned) 1f else 0.5f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                if (at != null) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at)) else t.tier.label,
                color = Color.White.copy(alpha = if (earned) 0.7f else 0.45f), fontSize = 10.sp,
            )
        }
    }
}

/** The RetroAchievements tab: the account, or how to set it up. */
@Composable
private fun RaPanel(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val rev = RetroAchievements.rev
    val configured = remember(rev) { RetroAchievements.configured(context) }
    var profile by remember { mutableStateOf<RaProfile?>(RetroAchievements.cachedProfile(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(configured) {
        if (configured) {
            status = "Checking…"
            val r = withContext(Dispatchers.IO) { RetroAchievements.check(context) }
            if (r.profile != null) { profile = r.profile; status = null } else status = r.error
        }
    }
    Column(Modifier.fillMaxWidth().vitaPanel(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!configured) {
            Text("RetroAchievements", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "RetroAchievements.org gives achievements to retro games. Unlocking happens in the emulator (RetroArch and others log in on their own). " +
                    "Here the launcher shows what you have earned. Enter your user name and your Web API key (from your profile page on the site) in Settings, Games & Emulators.",
                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
            )
            BarButton("trophy:ra:settings", "Open settings", onOpenSettings)
        } else {
            Text(profile?.user ?: RetroAchievements.user(context), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val p = profile
            if (p != null) {
                Text("${p.points} points" + if (p.rank > 0) " · rank ${p.rank}" else "", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                if (p.recent.isNotEmpty()) {
                    Text("Played lately", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    p.recent.forEach { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp) }
                }
            }
            status?.let { Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp) }
            Text(
                "A game's own achievements show on its page in the Games bubble.",
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
            )
        }
    }
}
