package com.qita.ui

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import android.util.DisplayMetrics
import android.view.Display
import android.view.PixelCopy
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screenshots and screen recording. Everything is saved under Pictures/QitaUI and Movies/QitaUI, where the Photos and Videos
 * bubbles find it. Three ways to capture: the launcher's own window (instant, no permission), anything on screen through a small
 * accessibility service the user switches on once in Android's settings, and a screen recording through Android's screen capture.
 */
object Capture {
    /** True while a recording is running. */
    var recording by mutableStateOf(false)

    private val main = Handler(Looper.getMainLooper())

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** A place to write a new picture or video: a MediaStore row on Android 10 and later, otherwise a file. Call [Target.finish] when done. */
    class Target(val stream: java.io.OutputStream?, val pfd: ParcelFileDescriptor?, private val finish: (Boolean) -> Unit) {
        fun finish(ok: Boolean) = finish.invoke(ok)
    }

    private fun target(c: Context, video: Boolean, name: String, mime: String): Target? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/QitaUI")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri: Uri = c.contentResolver.insert(collection, v) ?: return null
            val done = { ok: Boolean ->
                if (ok) c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                else runCatching { c.contentResolver.delete(uri, null, null) }
                Unit
            }
            if (video) Target(null, c.contentResolver.openFileDescriptor(uri, "rw"), done)
            else Target(c.contentResolver.openOutputStream(uri), null, done)
        } else {
            val dir = File(c.getExternalFilesDir(if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES), "QitaUI").apply { mkdirs() }
            val f = File(dir, name)
            if (video) Target(null, ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)) { ok -> if (!ok) f.delete() }
            else Target(f.outputStream(), null) { ok -> if (!ok) f.delete() }
        }
    }.getOrNull()

    /** Saves [bmp] as a PNG. Blocks; returns true if it was written. */
    fun saveImage(c: Context, bmp: Bitmap): Boolean {
        val t = target(c, false, "QitaUI_${stamp()}.png", "image/png") ?: return false
        val ok = runCatching { t.stream!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }.getOrDefault(false)
        t.finish(ok)
        return ok
    }

    /** A picture of the launcher's own window, saved. [done] is called on the main thread. */
    fun launcherShot(activity: Activity, done: (Boolean) -> Unit) {
        val window = activity.window
        val view = window.decorView
        if (view.width <= 0 || view.height <= 0) { done(false); return }
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val app = activity.applicationContext
        runCatching {
            PixelCopy.request(window, bmp, { result ->
                if (result == PixelCopy.SUCCESS) {
                    Thread {
                        val ok = saveImage(app, bmp)
                        main.post { done(ok) }
                    }.start()
                } else done(false)
            }, main)
        }.onFailure { done(false) }
    }

    /** Whether the accessibility service that can photograph any screen is switched on. */
    fun anyScreenReady(): Boolean = Build.VERSION.SDK_INT >= 30 && QitaCaptureService.instance != null

    fun openAccessibilitySettings(c: Context) {
        runCatching { c.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Photographs whatever is on screen after [delayMs] (time to switch to a game). Needs [anyScreenReady]. */
    fun anyScreenShot(c: Context, delayMs: Long) {
        val app = c.applicationContext
        main.postDelayed({
            val s = QitaCaptureService.instance
            if (s == null) Toast.makeText(app, "The screenshot service is off", Toast.LENGTH_SHORT).show() else s.shoot()
        }, delayMs)
    }

    /** The screen capture request Android shows before a recording starts. */
    fun recordRequest(c: Context): Intent =
        (c.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()

    /** [audio]: 0 no sound, 1 what the device plays, 2 the microphone. */
    fun startRecording(c: Context, resultCode: Int, data: Intent, audio: Int = 0) {
        val i = Intent(c, RecordService::class.java).putExtra("code", resultCode).putExtra("data", data).putExtra("audio", audio)
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
    }

    fun stopRecording(c: Context) {
        c.startService(Intent(c, RecordService::class.java).setAction(RecordService.STOP))
    }

    internal fun newVideoTarget(c: Context): Target? = target(c, true, "QitaUI_${stamp()}.mp4", "video/mp4")
}

/**
 * The accessibility service behind "screenshot of anything". It reads nothing from the screen: it only asks Android for a picture
 * of it (Android 11 and later), which is why it has to be switched on once in Accessibility settings.
 */
class QitaCaptureService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    override fun onDestroy() { instance = null; super.onDestroy() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun shoot() {
        if (Build.VERSION.SDK_INT < 30) return
        val app = applicationContext
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hw = result.hardwareBuffer
                val shot = runCatching { Bitmap.wrapHardwareBuffer(hw, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                hw.close()
                Thread {
                    val ok = shot != null && Capture.saveImage(app, shot)
                    Handler(Looper.getMainLooper()).post { Toast.makeText(app, if (ok) "Screenshot saved" else "Screenshot failed", Toast.LENGTH_SHORT).show() }
                }.start()
            }

            override fun onFailure(errorCode: Int) {
                Toast.makeText(app, "Screenshot failed", Toast.LENGTH_SHORT).show()
            }
        })
    }

    companion object {
        @Volatile var instance: QitaCaptureService? = null
    }
}

/**
 * Records the screen with MediaProjection into Movies/QitaUI, with a notification that has a Stop button. With sound on, the video goes
 * to a temporary file first and, when the recording stops, is joined with the sound ([SoundCapture]) into the final file.
 */
class RecordService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var target: Capture.Target? = null
    private var sound: SoundCapture? = null
    private var videoFile: File? = null
    /** True while the finished recording is being joined with its sound. */
    @Volatile private var busy = false
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Screen recording", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 7, Intent(this, RecordService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("Recording the screen")
            .setContentText("Tap Stop to finish and save").setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finish(); return START_NOT_STICKY }
        val audio = intent?.getIntExtra("audio", 0) ?: 0
        val note = notification()
        // Must be announced right away as a media projection service (Android ends an app that starts one and does not), even if one is running already.
        // The microphone type is only added when the permission is there: Android 14 refuses it otherwise.
        if (Build.VERSION.SDK_INT >= 29) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (audio != 0 && Build.VERSION.SDK_INT >= 30 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(ID, note, type)
        } else startForeground(ID, note)
        if (Capture.recording) return START_NOT_STICKY
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("data", Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>("data")
        if (data == null || !begin(code, data, audio)) { fail("Could not start the recording"); return START_NOT_STICKY }
        return START_NOT_STICKY
    }

    private fun begin(code: Int, data: Intent, audio: Int): Boolean = runCatching {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data) ?: return false
        projection = p
        // Android 14 requires a callback before the projection is used; it also tells us if the user ends the recording from the system.
        p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { finish() } }, main)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        // A little smaller than the screen on big displays keeps files and effort modest; sizes must be even.
        val scale = minOf(1f, 1280f / maxOf(metrics.widthPixels, metrics.heightPixels))
        val w = (metrics.widthPixels * scale).toInt() and 1.inv()
        val h = (metrics.heightPixels * scale).toInt() and 1.inv()
        val t = Capture.newVideoTarget(this) ?: return false
        target = t
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        recorder = r
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        if (audio != 0) {
            val temp = File(cacheDir, "rec_video.mp4")
            videoFile = temp
            r.setOutputFile(temp.path)
        } else {
            r.setOutputFile(t.pfd!!.fileDescriptor)
        }
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        r.setVideoSize(w, h)
        r.setVideoFrameRate(30)
        r.setVideoEncodingBitRate(6_000_000)
        r.prepare()
        display = p.createVirtualDisplay("QitaUI", w, h, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
        r.start()
        Capture.recording = true
        if (audio != 0) {
            val s = SoundCapture(p, audio, File(cacheDir, "rec_audio.m4a"))
            if (s.start()) sound = s else Toast.makeText(this, "No sound could be recorded on this device, so the recording has none", Toast.LENGTH_LONG).show()
        }
        true
    }.getOrElse {
        // Something failed part way: drop the empty file and let go of everything that was opened.
        runCatching { recorder?.release() }
        runCatching { display?.release() }
        runCatching { projection?.stop() }
        runCatching { target?.pfd?.close() }
        target?.finish(false)
        videoFile?.delete()
        recorder = null; display = null; projection = null; target = null; videoFile = null; sound = null
        false
    }

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish(silent = true)
    }

    private fun finish(silent: Boolean = false) {
        if (busy) return
        val rec = recorder
        val disp = display
        val proj = projection
        val tgt = target
        val snd = sound
        val video = videoFile
        recorder = null; display = null; projection = null; target = null; sound = null; videoFile = null
        Capture.recording = false
        // stop() throws if almost nothing was recorded; then the file is dropped.
        val ok = rec != null && runCatching { rec.stop() }.isSuccess
        runCatching { rec?.release() }
        runCatching { disp?.release() }
        runCatching { proj?.stop() }
        snd?.stop()
        if (video == null) {
            runCatching { tgt?.pfd?.close() }
            tgt?.finish(ok)
            if (!silent && rec != null) Toast.makeText(this, if (ok) "Recording saved to Videos" else "Recording was too short to save", Toast.LENGTH_SHORT).show()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // With sound: join the video and the sound into the final file, off the main thread.
        busy = true
        Thread {
            var saved = false
            if (ok && tgt != null) {
                saved = runCatching { join(video, snd?.takeIf { it.ok }, tgt) }.getOrDefault(false)
                // If joining failed, keep the picture without sound rather than lose the recording.
                if (!saved) saved = runCatching { copyPlain(video, tgt) }.getOrDefault(false)
            }
            runCatching { tgt?.pfd?.close() }
            tgt?.finish(saved)
            video.delete()
            snd?.file?.delete()
            val note = if (!saved) "Recording was too short to save" else if (snd == null || !snd.ok) "Recording saved to Videos (without sound)" else "Recording saved to Videos"
            main.post {
                if (!silent) Toast.makeText(this, note, Toast.LENGTH_SHORT).show()
                busy = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
    }

    /** Writes [video] and [sound] (if there is any) as one MP4 into the gallery file. */
    private fun join(video: File, sound: SoundCapture?, tgt: Capture.Target): Boolean {
        val fd = tgt.pfd?.fileDescriptor ?: return false
        val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vx = MediaExtractor().apply { setDataSource(video.path) }
        val ax = sound?.let { MediaExtractor().apply { setDataSource(it.file.path) } }
        var started = false
        try {
            fun trackOf(x: MediaExtractor, prefix: String) = (0 until x.trackCount).first { x.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(prefix) }
            val vIn = trackOf(vx, "video/")
            vx.selectTrack(vIn)
            val vOut = muxer.addTrack(vx.getTrackFormat(vIn))
            var aOut = -1
            if (ax != null) {
                val aIn = trackOf(ax, "audio/")
                ax.selectTrack(aIn)
                aOut = muxer.addTrack(ax.getTrackFormat(aIn))
            }
            muxer.start()
            started = true
            copySamples(vx, muxer, vOut)
            if (ax != null) copySamples(ax, muxer, aOut)
            muxer.stop()
            started = false
            return true
        } finally {
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { vx.release() }
            runCatching { ax?.release() }
        }
    }

    private fun copySamples(x: MediaExtractor, muxer: MediaMuxer, track: Int) {
        val buf = ByteBuffer.allocate(2 shl 20)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buf.clear()
            val n = x.readSampleData(buf, 0)
            if (n < 0) break
            info.set(0, n, x.sampleTime, x.sampleFlags)
            muxer.writeSampleData(track, buf, info)
            x.advance()
        }
    }

    /** The video as it is, into the gallery file (used when joining with the sound did not work). */
    private fun copyPlain(video: File, tgt: Capture.Target): Boolean {
        val fd = tgt.pfd?.fileDescriptor ?: return false
        val out = FileOutputStream(fd)
        out.channel.truncate(0)
        out.channel.position(0)
        video.inputStream().use { it.copyTo(out) }
        out.flush()
        return true
    }

    override fun onDestroy() {
        if (recorder != null) finish(silent = true)
        super.onDestroy()
    }

    companion object {
        const val STOP = "com.qita.ui.STOP_RECORDING"
        private const val CHANNEL = "qita_record"
        private const val ID = 4207
    }
}
