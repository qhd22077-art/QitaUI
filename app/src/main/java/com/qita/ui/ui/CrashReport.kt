package com.qita.ui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown on the launch after a crash: the saved stack trace, with Copy and Dismiss. */
@Composable
fun CrashReport(trace: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(620.dp)
                .fillMaxHeight(0.92f)
                .background(Color(0xFF2B1A1A), RoundedCornerShape(18.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("QitaUI crashed last time", color = Color(0xFFFF8A80), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Copy this and send it so the cause can be fixed.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            Text(
                trace.take(6000),
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                color = Color(0xFFFFCDD2), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                Text(
                    "Copy",
                    Modifier
                        .padClickable("crash:copy", corner = null, onClick = onCopy)
                        .background(Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    color = Color(0xFF7A1F1F), fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
                Text(
                    "Dismiss",
                    Modifier
                        .padClickable("crash:dismiss", corner = null, onClick = onDismiss)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
            }
        }
    }
}
