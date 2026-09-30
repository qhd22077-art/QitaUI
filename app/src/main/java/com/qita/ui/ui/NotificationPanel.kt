package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
 * The Vita's notification list: a rounded panel that drops down from the top-right button, one row per notification
 * (app icon, bold title, text, and a green bar for progress) and a white footer. It is tinted with [color].
 */
@Composable
fun NotificationPanel(color: Color, onLaunch: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Notifications.checkAccess(context) }
    BackHandler { onDismiss() }
    val items = Notifications.items
    val granted = Notifications.granted
    val rowColor = lerp(color, Color.Black, 0.25f)
    val rowAlt = lerp(color, Color.Black, 0.40f)
    CompositionLocalProvider(LocalPadLayer provides 4) {
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        ) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 30.dp, end = 10.dp)
                    .width(310.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEDEFF3))
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    when {
                        !granted -> Row(
                            Modifier.fillMaxWidth().background(rowColor)
                                .padClickable("notif:access", corner = 0.dp) { openAccessSettings(context); onDismiss() }
                                .padding(12.dp),
                        ) {
                            Column {
                                Text("Allow notification access", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Tap to open Android's settings and switch QitaUI on.", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                            }
                        }
                        items.isEmpty() -> Box(Modifier.fillMaxWidth().height(64.dp).background(rowColor), contentAlignment = Alignment.Center) {
                            Text("No notifications", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        }
                        else -> items.forEachIndexed { i, n ->
                            NotificationRow(n, i, if (i % 2 == 0) rowColor else rowAlt) {
                                openNotification(context, n, onLaunch); onDismiss()
                            }
                        }
                    }
                }
                Text(
                    if (granted && items.isNotEmpty()) "Clear all" else "Close",
                    Modifier
                        .fillMaxWidth()
                        .padClickable("notif:more", corner = 0.dp) {
                            if (granted && items.isNotEmpty()) Notifications.clearAll() else onDismiss()
                        }
                        .padding(vertical = 10.dp),
                    color = Color(0xFF2A2F3A), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (n.icon != null) Image(n.icon, null, Modifier.size(32.dp).clip(CircleShape))
        else Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
        Column(Modifier.weight(1f)) {
            Text(n.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.progress >= 0) {
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                    Box(
                        Modifier.fillMaxWidth(n.progress / 100f).height(6.dp)
                            .background(Brush.verticalGradient(listOf(Color(0xFFB8F070), Color(0xFF4DB82A)))),
                    )
                }
            }
            if (n.text.isNotBlank()) {
                Text(n.text, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
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
