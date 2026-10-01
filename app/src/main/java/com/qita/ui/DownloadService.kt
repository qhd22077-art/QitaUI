package com.qita.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Keeps the process alive while downloads run, so they carry on when the launcher is in the background or its window is swiped
 * away. It shows one quiet notification with the count and overall progress and stops itself when nothing is downloading.
 */
class DownloadService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null

    private val tick = object : Runnable {
        override fun run() {
            val running = DownloadEngine.items.filter { it.state == DlState.RUNNING || it.finishing }
            if (running.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            notify(buildNotification(running))
            handler.postDelayed(this, 1500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        DownloadEngine.init(applicationContext)
        val first = buildNotification(DownloadEngine.items.filter { it.state == DlState.RUNNING || it.finishing })
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, first)
        if (wake == null) {
            wake = runCatching {
                (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "qitaui:downloads").apply { acquire(6 * 60 * 60 * 1000L) }
            }.getOrNull()
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1500)
        // Sticky: if Android kills the process, it brings the service back and the engine resumes what was running.
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        super.onDestroy()
    }

    private fun notify(n: Notification) = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID, n)

    private fun buildNotification(running: List<DownloadItem>): Notification {
        val total = running.sumOf { it.total.coerceAtLeast(0) }
        val got = running.sumOf { it.bytes }
        val open = PendingIntentFor.launcher(this)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (running.size == 1) running[0].name else "${running.size} downloads")
            .setContentText(if (running.any { it.finishing }) "Unpacking…" else if (total > 0) "${(got * 100 / total).toInt()}%" else "Downloading")
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
        if (total > 0) b.setProgress(100, (got * 100 / total).toInt().coerceIn(0, 100), false) else b.setProgress(0, 0, true)
        return b.build()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val ID = 4107

        /** A one-off notification saying how filing a finished download went. */
        fun notifyResult(context: Context, title: String, text: String) {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
                val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)
                nm.notify(title.hashCode(), b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(PendingIntentFor.launcher(context)).build())
            }
        }

        /** Starts the service if it is not running. Safe to call often; does nothing if Android refuses a start from the background. */
        fun ensure(context: Context) {
            runCatching {
                val i = Intent(context, DownloadService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            }
        }
    }
}

private object PendingIntentFor {
    fun launcher(context: Context): android.app.PendingIntent {
        val i = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        return android.app.PendingIntent.getActivity(context, 0, i, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
