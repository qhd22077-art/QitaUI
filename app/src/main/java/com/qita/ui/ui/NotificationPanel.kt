package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qita.ui.NotificationItem
import com.qita.ui.Notifications

/**
 * The Vita's notification list: a light rounded panel with a little pointer up to the top-right button, dark rows (rounded
 * app icon, bold title, text, green bar for progress) and a white "More" footer. The rows are tinted with [color].
 */
@Composable
fun NotificationPanel(color: Color, onLaunch: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Notifications.checkAccess(context) }
    BackHandler { onDismiss() }
    val items = Notifications.items
    val granted = Notifications.granted
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) items.toList() else items.take(5)
    val hidden = items.size - shown.size
    // The Vita's rows are dark slate; the chosen colour tints them.
    val rowColor = lerp(color, Color(0xFF4A4F58), 0.55f)
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        ) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 22.dp, end = 6.dp)
                    .width(316.dp),
            ) {
                // The pointer up to the button.
                Canvas(Modifier.align(Alignment.End).padding(end = 26.dp).size(width = 20.dp, height = 10.dp)) {
                    val p = Path().apply { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(p, Color(0xFFEDEFF3))
                }
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEDEFF3))
                        .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(3.dp),
                ) {
                    Column(
                        Modifier.clip(RoundedCornerShape(9.dp)).heightIn(max = 290.dp).verticalScroll(rememberScrollState()),
                    ) {
                        when {
                            !granted -> {
                                InfoRow(rowColor, "notif:access", "Allow notification access", "Tap to open Android's settings and switch QitaUI on.") {
                                    openAccessSettings(context); onDismiss()
                                }
                                Divider()
                                InfoRow(
                                    rowColor, "notif:blocked", "Android won't let you switch it on?",
                                    "Tap to open App info, then ⋮ at the top right, then \"Allow restricted settings\". Go back and try again.",
                                ) { openAppInfo(context); onDismiss() }
                            }
                            items.isEmpty() -> Box(Modifier.fillMaxWidth().height(64.dp).background(rowColor), contentAlignment = Alignment.Center) {
                                Text("No notifications", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            }
                            else -> shown.forEachIndexed { i, n ->
                                if (i > 0) Divider()
                                NotificationRow(n, i, rowColor) { openNotification(context, n, onLaunch); onDismiss() }
                            }
                        }
                    }
                    Text(
                        if (hidden > 0) "More" else if (granted && items.isNotEmpty()) "Clear all" else "Close",
                        Modifier
                            .fillMaxWidth()
                            .padClickable("notif:more", corner = 0.dp) {
                                if (hidden > 0) expanded = true
                                else if (granted && items.isNotEmpty()) Notifications.clearAll()
                                else onDismiss()
                            }
                            .padding(vertical = 9.dp),
                        color = Color(0xFF2A2F3A), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.25f)))
}

@Composable
private fun InfoRow(color: Color, key: String, title: String, text: String, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (lit) lerp(color, Color.White, 0.22f) else color)
            .padClickable(key, corner = 0.dp, ring = false, onClick = onClick)
            .padding(12.dp),
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
private fun NotificationRow(n: NotificationItem, index: Int, color: Color, onClick: () -> Unit) {
    val lit = padHighlighted("notif:$index") || padHovered("notif:$index")
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (lit) lerp(color, Color.White, 0.22f) else color)
            .padClickable("notif:$index", corner = 0.dp, ring = false, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The Vita shows a small rounded-square picture with a pale rim.
        val shape = RoundedCornerShape(7.dp)
        if (n.icon != null) Image(n.icon, null, Modifier.size(34.dp).clip(shape).border(1.dp, Color.White.copy(alpha = 0.7f), shape))
        else Box(Modifier.size(34.dp).clip(shape).background(Color.White.copy(alpha = 0.3f)))
        Column(Modifier.weight(1f)) {
            Text(n.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.progress >= 0) {
                Box(Modifier.padding(top = 3.dp, bottom = 1.dp).fillMaxWidth().height(7.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))) {
                    Box(
                        Modifier.fillMaxWidth(n.progress / 100f).height(7.dp)
                            .background(Brush.verticalGradient(listOf(Color(0xFFC8F58A), Color(0xFF4DB82A)))),
                    )
                }
            }
            if (n.text.isNotBlank()) {
                Text(n.text, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun openAppInfo(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun openAccessSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openNotification(context: Context, n: NotificationItem, onLaunch: (String) -> Unit) {
    val sent = runCatching { n.intent?.send(); n.intent != null }.getOrDefault(false)
    if (!sent) onLaunch(n.packageName)
}
