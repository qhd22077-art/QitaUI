package com.qita.ui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.Settings

/** The Folders app (a first stub: the file manager follows). */
@Composable
fun FoldersScreen(settings: Settings, onToast: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(enabled = true) { onClose() }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF22428F), Color(0xFF1A3379), Color(0xFF142460)))).pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusBar(settings.use24h, settings.showBattery, showHome = false)
            Text("Folders", color = Color.White, fontSize = 24.sp, modifier = Modifier.padding(24.dp))
            Text("The file manager is on its way.", color = SoftText, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp))
        }
        BackButton(onClick = onClose, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp), key = "folders:back")
    }
}
