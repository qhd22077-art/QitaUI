package com.qita.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** The moments the launcher makes a sound for. [gapMs] is the shortest time between two of the same, [gain] its loudness next to the others. */
enum class Sound(val id: String, val label: String, val gapMs: Long, val gain: Float) {
    TAP("tap", "Tap", 40, 0.9f),
    MOVE("move", "Moving the highlight", 60, 0.5f),
    OPEN("open", "Opening something", 150, 1f),
    BACK("back", "Going back", 150, 1f),
    PAGE("page", "Turning the page", 120, 0.8f),
    PICK("pick", "Picking a bubble up", 100, 1f),
    DROP("drop", "Putting a bubble down", 100, 1f),
    UNLOCK("unlock", "Unlocking", 300, 1f),
}

/**
 * Short interface sounds. Each is a small soft tone made in code (kept as a tiny file in the cache and played with a SoundPool, so
 * there is no delay and nothing to download), or a short clip the user picked, or off. Nothing needs a permission.
 */
object Sounds {
    private const val RATE = 22050

    @Volatile private var on = false
    @Volatile private var volume = 0.6f
    @Volatile private var wantMedia = true
    @Volatile private var builtMedia: Boolean? = null
    @Volatile private var building = false
    private var pool: SoundPool? = null
    private var appContext: Context? = null
    private val ids = ConcurrentHashMap<Sound, Int>()
    private val ready = ConcurrentHashMap.newKeySet<Int>()
    private val last = HashMap<Sound, Long>()

    private fun prefs(c: Context) = c.getSharedPreferences("qita_sounds", Context.MODE_PRIVATE)

    /** 0 the built-in sound, 1 the user's own file, 2 off. */
    fun mode(c: Context, s: Sound): Int = prefs(c).getInt("mode_${s.id}", 0)

    private fun userDir(c: Context) = File(c.filesDir, "sounds").apply { mkdirs() }

    fun userFile(c: Context, s: Sound): File = File(userDir(c), "user_${s.id}")

    /** Applies the settings: switches the sounds on or off, sets the master volume, and whether they follow the media volume. */
    fun configure(c: Context, enabled: Boolean, vol: Float, useMedia: Boolean) {
        appContext = c.applicationContext
        volume = vol.coerceIn(0f, 1f)
        if (!enabled) { on = false; return }
        wantMedia = useMedia
        if (pool == null || builtMedia != useMedia) startBuild()
        on = true
    }

    /** Makes the files and loads them off the main thread (one build at a time); sounds start working as each one finishes loading. */
    @Synchronized private fun startBuild() {
        if (building) return
        building = true
        Thread {
            try {
                while (true) {
                    val m = wantMedia
                    rebuild(m)
                    builtMedia = m
                    if (wantMedia == m) break
                }
            } finally {
                building = false
            }
        }.start()
    }

    @Synchronized private fun rebuild(media: Boolean) {
        val c = appContext ?: return
        pool?.release()
        ids.clear()
        ready.clear()
        val attrs = AudioAttributes.Builder()
            // Media volume is the safe choice (always audible); the system sounds volume follows the phone's silent mode.
            .setUsage(if (media) AudioAttributes.USAGE_GAME else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
        pool = p
        Sound.values().forEach { load(c, p, it) }
    }

    private fun builtinFile(c: Context, s: Sound): File {
        val f = File(File(c.cacheDir, "sounds").apply { mkdirs() }, "${s.id}_1.wav")
        if (!f.exists() || f.length() < 100) runCatching { f.writeBytes(wav(synth(s))) }
        return f
    }

    private fun load(c: Context, p: SoundPool, s: Sound) {
        ids.remove(s)?.let { old -> ready.remove(old); runCatching { p.unload(old) } }
        when (mode(c, s)) {
            2 -> return
            1 -> {
                val f = userFile(c, s)
                if (f.exists() && f.length() > 0) { ids[s] = p.load(f.path, 1); return }
            }
        }
        ids[s] = p.load(builtinFile(c, s).path, 1)
    }

    /** Plays [s] if sounds are on and it is not switched off. Safe to call from anywhere (it is quick). */
    fun play(s: Sound) {
        if (!on) return
        val id = ids[s] ?: return
        if (id !in ready) return
        val now = SystemClock.uptimeMillis()
        if (now - (last[s] ?: 0L) < s.gapMs) return
        last[s] = now
        val v = (volume * s.gain).coerceIn(0f, 1f)
        pool?.play(id, v, v, 1, 0, 1f)
    }

    fun setMode(c: Context, s: Sound, mode: Int) {
        prefs(c).edit().putInt("mode_${s.id}", mode).apply()
        pool?.let { p -> Thread { synchronized(this) { load(c.applicationContext, p, s) } }.start() }
    }

    /** Copies a picked clip in as the sound for [s] and uses it. Returns false if it could not be read or is too big (more than 6 MB). */
    fun setUserFile(c: Context, s: Sound, uri: Uri): Boolean {
        val ok = runCatching {
            val out = ByteArrayOutputStream()
            c.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 6 * 1024 * 1024) return false
                }
            } ?: return false
            userFile(c, s).writeBytes(out.toByteArray())
            true
        }.getOrDefault(false)
        if (ok) setMode(c, s, 1)
        return ok
    }

    /** Back to the built-in sound for every event, deleting the user's clips. */
    fun resetEvents(c: Context) {
        prefs(c).edit().clear().apply()
        Sound.values().forEach { userFile(c, it).delete() }
        pool?.let { p -> Thread { synchronized(this) { Sound.values().forEach { load(c.applicationContext, p, it) } } }.start() }
    }

    // --- The built-in sounds: small soft tones, made in code. ---

    private fun wav(samples: ShortArray): ByteArray {
        val data = samples.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        samples.forEach { b.putShort(it) }
        return b.array()
    }

    /** A rise of 6 ms (so there is no click) and then a smooth fade, over [dur] seconds. */
    private fun env(t: Float, dur: Float): Float = (if (t < 0.006f) t / 0.006f else 1f) * exp(-5f * t / dur)

    private fun pcm(v: Float): Short = (v * 32767f).toInt().coerceIn(-32767, 32767).toShort()

    /** A tone that glides from [f0] to [f1] Hz, with a touch of its second harmonic for warmth. */
    private fun sweep(ms: Int, f0: Float, f1: Float, amp: Float): ShortArray {
        val n = RATE * ms / 1000
        val dur = ms / 1000f
        var phase = 0.0
        return ShortArray(n) { i ->
            val t = i.toFloat() / RATE
            val f = f0 + (f1 - f0) * (t / dur)
            phase += 2.0 * PI * f / RATE
            pcm(((sin(phase) + 0.25 * sin(2 * phase)).toFloat() * env(t, dur) * amp) / 1.25f)
        }
    }

    /** Notes (start in ms, frequency, length in ms) played over each other, a bell-like chime. */
    private fun chime(totalMs: Int, amp: Float, vararg notes: Triple<Int, Float, Int>): ShortArray {
        val n = RATE * totalMs / 1000
        val mix = FloatArray(n)
        for ((start, freq, ms) in notes) {
            val from = RATE * start / 1000
            val len = RATE * ms / 1000
            val dur = ms / 1000f
            for (i in 0 until len) {
                val idx = from + i
                if (idx >= n) break
                val t = i.toFloat() / RATE
                val w = 2.0 * PI * freq * t
                mix[idx] += ((sin(w) + 0.3 * sin(2 * w) + 0.1 * sin(3 * w)).toFloat() * env(t, dur) * amp) / 1.4f
            }
        }
        return ShortArray(n) { pcm(mix[it]) }
    }

    /** Soft filtered noise that swells and fades, like a page of air. */
    private fun swish(ms: Int, amp: Float): ShortArray {
        val n = RATE * ms / 1000
        var seed = 12345
        var low = 0f
        return ShortArray(n) { i ->
            seed = seed * 1103515245 + 12345
            val noise = ((seed shr 16) and 0x7FFF) / 16384f - 1f
            val x = i.toFloat() / n
            // The filter opens as the sound goes on, then the whole thing fades.
            val k = 0.04f + 0.30f * x
            low += k * (noise - low)
            val bell = sin(PI * x).toFloat()
            pcm(low * bell * bell * amp * 2.2f)
        }
    }

    private fun synth(s: Sound): ShortArray = when (s) {
        Sound.TAP -> sweep(90, 560f, 820f, 0.5f)
        Sound.MOVE -> sweep(28, 1250f, 1100f, 0.28f)
        Sound.OPEN -> chime(320, 0.42f, Triple(0, 523f, 220), Triple(70, 784f, 250))
        Sound.BACK -> sweep(130, 720f, 440f, 0.45f)
        Sound.PAGE -> swish(170, 0.30f)
        Sound.PICK -> sweep(110, 320f, 640f, 0.5f)
        Sound.DROP -> sweep(130, 620f, 260f, 0.5f)
        Sound.UNLOCK -> chime(520, 0.40f, Triple(0, 523f, 260), Triple(110, 659f, 260), Triple(220, 784f, 300))
    }
}
