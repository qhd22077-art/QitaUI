package com.qita.ui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class TutorialPage(val title: String, val intro: String, val tips: List<Pair<String, String>>, val pad: List<Pair<String, String>> = emptyList())

private val PAGES = listOf(
    TutorialPage(
        "Welcome to QitaUI",
        "A PlayStation Vita style home screen for any Android device. Your apps and games are bubbles you arrange yourself.",
        listOf(
            "Swipe sideways" to "to move between pages of bubbles.",
            "Tap a bubble" to "to open its LiveArea page, then tap the page to start it.",
            "Swipe up" to "(or the desktop bubble) for every app on the device, to add to the home screen.",
            "Swipe down" to "to search every app, game and setting.",
            "Hold the background" to "for the launcher menu: edit, desktop, search, settings, screenshots and recording.",
        ),
    ),
    TutorialPage(
        "Arranging bubbles",
        "Make the home screen yours.",
        listOf(
            "Hold a bubble" to "and drag it to move it. Drop it on another bubble to make a folder.",
            "Hold without moving" to "for the options: rename, change its picture, remove it, uninstall.",
            "Folders" to "open as clear glass; drag bubbles in and out, and rename them from their options.",
            "Edit home screen" to "from the launcher menu shows a small cross on every bubble to remove it quickly.",
            "Notification counts" to "appear as a red number on a bubble (and the total on a folder). Turn them off in Settings, Home screen.",
        ),
    ),
    TutorialPage(
        "Controller",
        "Everything works with a gamepad as well as touch. The D-pad or the left stick moves a highlight; with Cursor mode on (Settings, Controller) a pointer moves instead.",
        emptyList(),
        listOf("A" to "Open", "B" to "Back", "X" to "Options", "Y" to "Move", "L2" to "Desktop", "R2" to "Search", "START" to "Settings"),
    ),
    TutorialPage(
        "The built-in bubbles",
        "These are always available (add any that are missing from the desktop).",
        listOf(
            "Store" to "emulators and free games to download, a browser tab for any page, and your downloads.",
            "Games" to "every game from every console, with covers, play time, notes and screenshots.",
            "Browser" to "tabs, bookmarks and history. Downloads go through the launcher.",
            "Folders" to "browse the files on the device, copy, move, unpack or send them to a game folder.",
            "Photos, Music, Videos" to "your pictures, songs and videos, including screenshots and recordings. A picture can become the wallpaper.",
            "Trophies" to "earned by using the launcher, plus your RetroAchievements progress.",
            "System Settings" to "Android's own settings: Wi-Fi, Bluetooth, sound, apps and storage.",
        ),
    ),
    TutorialPage(
        "Make it look yours",
        "Settings, Themes and Bubbles & Icons change almost everything you see.",
        listOf(
            "Themes" to "change the colours, wallpaper and scenes. Vita theme files can be imported.",
            "Hold a built-in bubble" to "and choose Customise for a picture, a tint, translucency or clear glass.",
            "Bubble transparency" to "makes every bubble see-through.",
            "Live 3D bubbles" to "need Android 13 or newer. Rows such as shine, dome and glass clearness only act while Live 3D and Full-art are on and Rounded bubbles is off; Settings says so.",
            "Lock screen" to "drag to peel it away. It can show notifications and a clock you can place and size.",
        ),
    ),
    TutorialPage(
        "Games and Flash",
        "Open Games for your library. A game's page has its cover, notes, tags, play time and screenshots.",
        listOf(
            "Emulators" to "are launched with the game for you; set which one a console uses from the game's options.",
            "Flash games" to "play inside the launcher with Ruffle, with on-screen controls for touch.",
            "RetroAchievements" to "(Settings, Games) shows each game's achievements once you enter your name and Web API key.",
            "Play time and trophies" to "are counted by the launcher while a game runs.",
        ),
    ),
    TutorialPage(
        "Screenshots and recording",
        "Open the launcher menu (hold the background).",
        listOf(
            "Screenshot of the launcher" to "saves the home screen to Photos instantly.",
            "Screenshot in 5 seconds" to "captures any screen, such as a game. Turn on QitaUI screenshots once in Android's Accessibility settings; the menu opens it for you.",
            "Record the screen" to "asks Android for permission each time and saves a video to Videos. Stop it from the menu or the notification. Sound is not recorded.",
        ),
    ),
    TutorialPage(
        "Battery and tips",
        "Settings, Motion has a battery saver.",
        listOf(
            "Battery saver" to "acts like light mode and also stops tilt, particles, sway and sounds. Choose Off, Automatic (at your low-battery level, when not charging) or On. ECO shows in the top bar.",
            "Reduce motion and Light mode" to "make everything calmer and lighter on memory.",
            "Reset buttons" to "at the bottom of each Settings page put that page back to its defaults.",
            "This tutorial" to "can be opened again from Settings, System.",
        ),
    ),
)

/** The welcome tutorial: a few short pages (buttons, swipe or gamepad) covering the home screen, controller and every feature. */
@Composable
fun Onboarding(psLabels: Boolean, onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val last = PAGES.lastIndex
    val p = PAGES[page]
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 620.dp).fillMaxWidth(0.94f).fillMaxHeight(0.94f)
                .background(Color(0xFF1F2A44), RoundedCornerShape(22.dp)).padding(22.dp)
                .pointerInput(page) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -80f && page < last) page++ else if (total > 80f && page > 0) page-- },
                    ) { _, d -> total += d }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f, fill = true).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(p.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(p.intro, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                p.tips.forEach { (head, body) -> Tip(head, body) }
                if (p.pad.isNotEmpty()) HintBar(p.pad, psLabels)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                PAGES.indices.forEach { i ->
                    Box(Modifier.padding(horizontal = 3.dp).size(8.dp).background(Color.White.copy(alpha = if (i == page) 0.95f else 0.3f), CircleShape))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Skip",
                    Modifier.padClickable("onboard:skip", corner = null, onClick = onDone).padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp,
                )
                Box(Modifier.weight(1f))
                if (page > 0) Pill("onboard:back", "Back", filled = false) { page-- }
                Pill("onboard:next", if (page == last) "Got it" else "Next", filled = true) { if (page == last) onDone() else page++ }
            }
        }
    }
}

@Composable
private fun Pill(key: String, label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .padClickable(key, corner = null, onClick = onClick)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 28.dp, vertical = 10.dp),
        color = if (filled) Color(0xFF0B3D91) else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
    )
}

@Composable
private fun Tip(head: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(head, color = Color(0xFFFFD54F), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(max = 190.dp))
        Text(body, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}
