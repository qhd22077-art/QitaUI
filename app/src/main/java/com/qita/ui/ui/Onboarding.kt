package com.qita.ui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First-run welcome card: how to add apps, the gestures, and the controller buttons. */
@Composable
fun Onboarding(psLabels: Boolean, onDone: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(560.dp).background(Color(0xFF1F2A44), RoundedCornerShape(22.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Welcome to QitaUI", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Your home screen starts empty: you choose which apps live here.",
                color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
            )
            Tip("Swipe up", "or tap the desktop icon to open the desktop and add apps.")
            Tip("Swipe down", "to search every app on the device.")
            Tip("Long-press", "a bubble and drag to rearrange; hold without moving for options.")
            Tip("Corner peel", "drag an app card's top-right corner to close the app.")
            Text("Controller", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            HintBar(
                listOf("A" to "Open", "X" to "Options", "Y" to "Move", "L2" to "Desktop", "R2" to "Search", "START" to "Settings"),
                psLabels,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    "Got it",
                    Modifier
                        .padClickable("onboard:ok", corner = null, onClick = onDone)
                        .background(Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 32.dp, vertical = 10.dp),
                    color = Color(0xFF0B3D91), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun Tip(head: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(head, color = Color(0xFFFFD54F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
    }
}
