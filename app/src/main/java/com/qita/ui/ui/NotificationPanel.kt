package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.shadow
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
    val items = Notifications.items
    val granted = Notifications.granted
    var expanded by remember { mutableStateOf(false) }
    // Back first steps out of the big list, then closes the panel.
    BackHandler { if (expanded) expanded = false else onDismiss() }
    val shown = if (expanded) items.toList() else items.take(5)
    val hidden = items.size - shown.size
    // The Vita's rows are dark slate; the chosen colour tints them.
    val rowColor = lerp(color, Color(0xFF4A4F58), 0.55f)
    CompositionLocalProvider(LocalPadLayer provides 4) {
        BoxWithConstraints(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        ) {
            val screenHeight = maxHeight
            val panelShape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .align(if (expanded) Alignment.TopCenter else Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 22.dp, end = if (expanded) 0.dp else 6.dp)
                    .then(if (expanded) Modifier.fillMaxWidth(0.8f) else Modifier.width(316.dp)),
            ) {
                // The pointer up to the button.
                Canvas(
                    Modifier
                        .align(Alignment.End)
                        .padding(end = if (expanded) 0.dp else 26.dp)
                        .size(width = 20.dp, height = 10.dp),
                ) {
                    val p = Path().apply { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(p, Color(0xFFEDEFF3))
                }
                Column(
                    Modifier
                        .shadow(6.dp, panelShape, clip = false)
                        .clip(panelShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFFF7F8FB), Color(0xFFC9CDD4))))
                        // A bevelled frame: bright along the top, greyer along the bottom.
                        .border(3.dp, Brush.verticalGradient(listOf(Color.White, Color(0xFFB9BEC6))), panelShape)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(4.dp),
                ) {
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            // The rows sit sunk into the frame: a fine dark edge around them.
                            .border(1.dp, Color.Black.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                            .heightIn(max = if (expanded) screenHeight * 0.74f else 290.dp)
                            .verticalScroll(rememberScrollState()),
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
                            expanded -> shown.forEachIndexed { i, n ->
                                if (i > 0) Divider()
                                ExpandedRow(n, i, color) { openNotification(context, n, onLaunch); onDismiss() }
                            }
                            else -> shown.forEachIndexed { i, n ->
                                if (i > 0) Divider()
                                NotificationRow(n, i, rowColor) { openNotification(context, n, onLaunch); onDismiss() }
                            }
                        }
                    }
                    if (!expanded) {
                        Text(
                            if (hidden > 0) "More" else if (granted && items.isNotEmpty()) "Clear all" else "Close",
                            Modifier
                                .fillMaxWidth()
                                .padClickable("notif:more", corner = 0.dp) {
                                    if (hidden > 0) expanded = true
                                    else if (granted && items.isNotEmpty()) Notifications.clearAll()
                                    else onDismiss()
                                }
                                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFD3D7DD))), RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                                .padding(vertical = 9.dp),
                            color = Color(0xFF2A2F3A), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            if (expanded) {
                // The round white button in the corner: clears everything.
                val lit = padHighlighted("notif:all") || padHovered("notif:all")
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 10.dp)
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(if (lit) Color(0xFFDDE6F5) else Color.White)
                        .padClickable("notif:all", corner = null, ring = false) { Notifications.clearAll() }
                        .pointerInput(Unit) { detectTapGestures { } },
                    contentAlignment = Alignment.Center,
                ) { Dots(Color(0xFF6B7078)) }
            }
        }
    }
}

/** A row of the big list, as on the console: a rounded picture, a bold title, a thin progress line, the text, a round "…" button and the age. */
@Composable
private fun ExpandedRow(n: NotificationItem, index: Int, color: Color, onClick: () -> Unit) {
    val key = "notif:$index"
    val lit = padHighlighted(key) || padHovered(key)
    val busy = n.progress >= 0
    // Transfers are mid grey; everything else (like a trophy) is a lighter grey.
    val top = lerp(color, if (busy) Color(0xFF9EA2A9) else Color(0xFFCDD0D5), 0.70f)
    val bottom = lerp(color, if (busy) Color(0xFF7C8088) else Color(0xFFADB1B7), 0.70f)
    val shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 4f)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(if (lit) lerp(top, Color.White, 0.25f) else top, if (lit) lerp(bottom, Color.White, 0.25f) else bottom)))
            .padClickable(key, corner = 0.dp, ring = false, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(8.dp)
        if (n.icon != null) Image(n.icon, null, Modifier.size(44.dp).clip(shape).border(1.dp, Color.White.copy(alpha = 0.8f), shape))
        else Box(Modifier.size(44.dp).clip(shape).background(Color.White.copy(alpha = 0.35f)))
        Column(Modifier.weight(1f)) {
            Text(
                n.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = shadow),
            )
            if (busy) {
                Box(Modifier.padding(vertical = 2.dp).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.55f), CircleShape)) {
                    Box(
                        Modifier.fillMaxWidth(n.progress / 100f).height(4.dp)
                            .background(Brush.verticalGradient(listOf(Color(0xFFD6FF9A), Color(0xFF4DB82A))), CircleShape),
                    )
                }
            }
            if (n.text.isNotBlank()) {
                Text(n.text, color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = shadow))
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padClickable("notif:dots:$index", corner = null, ring = false) { Notifications.dismiss(n.key) },
                contentAlignment = Alignment.Center,
            ) { Dots(Color(0xFF6B7078), small = true) }
            Text(timeAgo(n.time), color = Color.White, fontSize = 11.sp, style = TextStyle(shadow = shadow))
        }
    }
}

/** Three round dots in a row, the console's "…" symbol. */
@Composable
private fun Dots(color: Color, small: Boolean = false) {
    Canvas(Modifier.size(if (small) 16.dp else 30.dp, if (small) 5.dp else 8.dp)) {
        val r = size.height / 2f
        for (i in 0..2) drawCircle(color, r, Offset(r + i * (size.width - 2 * r) / 2f, size.height / 2f))
    }
}

private fun timeAgo(time: Long): String {
    val minutes = ((System.currentTimeMillis() - time) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes Minute${if (minutes == 1L) "" else "s"} Ago"
        minutes < 60 * 24 -> "${minutes / 60} Hour${if (minutes / 60 == 1L) "" else "s"} Ago"
        else -> "${minutes / (60 * 24)} Day${if (minutes / (60 * 24) == 1L) "" else "s"} Ago"
    }
}

/** The groove between two rows: a dark line with a light line under it, so the rows look embossed. */
@Composable
private fun Divider() {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.38f)))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.30f)))
    }
}

/** A row's fill: lighter at the top, darker at the bottom, so it reads as a raised pad. */
private fun rowFill(base: Color, lit: Boolean): Brush {
    val b = if (lit) lerp(base, Color.White, 0.22f) else base
    return Brush.verticalGradient(listOf(lerp(b, Color.White, 0.16f), lerp(b, Color.Black, 0.18f)))
}

@Composable
private fun InfoRow(color: Color, key: String, title: String, text: String, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Column(
        Modifier
            .fillMaxWidth()
            .background(rowFill(color, lit))
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
            .background(rowFill(color, lit))
            .padClickable("notif:$index", corner = 0.dp, ring = false, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The Vita shows a small round picture with a pale rim.
        val shape = CircleShape
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
