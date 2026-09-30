package com.qita.ui

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat

/** One entry of the notifications panel. */
class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val time: Long,
    /** 0..100 for a notification that shows progress, else -1. */
    val progress: Int,
    val icon: ImageBitmap?,
    val intent: PendingIntent?,
)

/** Android hands notifications only to a listener service the user has allowed; this service feeds [Notifications]. */
class QitaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { Notifications.attach(this) }
    override fun onListenerDisconnected() { Notifications.detach() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { Notifications.refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { Notifications.refresh() }
}

object Notifications {
    private var service: NotificationListenerService? = null
    private val icons = HashMap<String, ImageBitmap?>()

    /** The current notifications, newest first. */
    val items = mutableStateListOf<NotificationItem>()

    /** Whether the user has allowed this launcher to read notifications. */
    var granted by mutableStateOf(false)
        private set

    fun attach(s: NotificationListenerService) { service = s; granted = true; refresh() }

    fun detach() { service = null; items.clear() }

    /** Re-checks the permission (the user may have just come back from Android's settings). */
    fun checkAccess(context: Context) {
        granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (granted) refresh()
    }

    fun refresh() {
        val s = service ?: return
        val list = runCatching { s.activeNotifications.toList() }.getOrDefault(emptyList())
            .filter { it.packageName != s.packageName && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .sortedByDescending { it.postTime }
            .mapNotNull { sbn ->
                val n = sbn.notification
                val extras = n.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
                val progress = if (max > 0 && !extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) {
                    extras.getInt(Notification.EXTRA_PROGRESS, 0) * 100 / max
                } else -1
                NotificationItem(
                    key = sbn.key, packageName = sbn.packageName, title = title.ifBlank { appLabel(s, sbn.packageName) }, text = text,
                    time = sbn.postTime, progress = progress, icon = iconOf(s, sbn.packageName), intent = n.contentIntent,
                )
            }
        items.clear()
        items.addAll(list)
    }

    fun clearAll() { runCatching { service?.cancelAllNotifications() } }

    private fun appLabel(context: Context, pkg: String): String =
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun iconOf(context: Context, pkg: String): ImageBitmap? = icons.getOrPut(pkg) {
        runCatching {
            val d = context.packageManager.getApplicationIcon(pkg)
            val bmp = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 72, 72)
            d.draw(Canvas(bmp))
            bmp.asImageBitmap()
        }.getOrNull()
    }
}
