package com.qita.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A small music player for the Music bubble. One track at a time through Android's MediaPlayer, with a queue, shuffle and repeat.
 * It plays while the launcher's process is alive (also with the screen off), and pauses when another app takes the sound.
 * There is no notification or lock screen control; it is a simple player, not a replacement for a full music app.
 */
object MusicPlayer {
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    var playing by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
    /** 0 off, 1 the whole queue, 2 one track. */
    var repeat by mutableIntStateOf(0)

    private var player: MediaPlayer? = null
    private var appContext: Context? = null
    private var focus: AudioFocusRequest? = null
    private var pausedByFocus = false

    val current: Track? get() = queue.getOrNull(index)

    val positionMs: Int get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    val durationMs: Int get() = runCatching { player?.duration ?: 0 }.getOrDefault(0).coerceAtLeast(0)

    private fun audio(c: Context) = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun requestFocus(c: Context): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (playing) { pausedByFocus = true; pause() }
                    AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) { pausedByFocus = false; resume() }
                }
            }
            .build()
        focus = req
        return audio(c).requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Plays [list] from track [at]. */
    fun play(c: Context, list: List<Track>, at: Int) {
        appContext = c.applicationContext
        queue = list
        start(at)
    }

    private fun start(at: Int) {
        val c = appContext ?: return
        val track = queue.getOrNull(at) ?: return
        index = at
        player?.release()
        player = null
        if (!requestFocus(c)) { playing = false; return }
        val p = MediaPlayer()
        runCatching {
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            p.setWakeMode(c, PowerManager.PARTIAL_WAKE_LOCK)
            p.setDataSource(c, track.uri)
            p.setOnCompletionListener { finished() }
            p.setOnErrorListener { _, _, _ -> playing = false; next(); true }
            p.prepare()
            p.start()
            player = p
            playing = true
        }.onFailure { runCatching { p.release() }; playing = false }
    }

    private fun finished() {
        when {
            repeat == 2 -> start(index)
            else -> next(auto = true)
        }
    }

    fun next(auto: Boolean = false) {
        if (queue.isEmpty()) return
        val n = when {
            shuffle && queue.size > 1 -> (queue.indices - index).random()
            index + 1 < queue.size -> index + 1
            repeat == 1 || !auto -> 0
            else -> { stopPlayback(); return }
        }
        start(n)
    }

    fun previous() {
        if (queue.isEmpty()) return
        // Past the first few seconds, "previous" goes back to the start of the track.
        if (positionMs > 3000) { seekTo(0); return }
        start(if (index > 0) index - 1 else queue.lastIndex)
    }

    fun pause() {
        runCatching { player?.pause() }
        playing = false
    }

    fun resume() {
        val p = player ?: run { if (index >= 0) start(index); return }
        if (appContext?.let { requestFocus(it) } == false) return
        runCatching { p.start() }
        playing = true
    }

    fun toggle() { if (playing) pause() else resume() }

    fun seekTo(ms: Int) { runCatching { player?.seekTo(ms) } }

    private fun stopPlayback() {
        runCatching { player?.release() }
        player = null
        playing = false
    }

    /** Stops and forgets the queue. */
    fun stop() {
        stopPlayback()
        queue = emptyList()
        index = -1
        focus?.let { f -> appContext?.let { audio(it).abandonAudioFocusRequest(f) } }
    }
}
