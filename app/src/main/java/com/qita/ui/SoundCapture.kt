package com.qita.ui

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import java.io.File

/**
 * The sound for a screen recording: either what the device is playing (game audio, Android 10 and newer, only from apps that allow
 * it) or the microphone. It is read with [AudioRecord], encoded to AAC and written to [file] (an .m4a); the recorder joins that file
 * with the video when the recording stops. Any trouble on the way leaves [ok] false and the recording goes on without sound.
 */
internal class SoundCapture(private val projection: MediaProjection, private val mode: Int, val file: File) {
    /** True once the sound file holds something that can be joined with the video. */
    @Volatile var ok = false
        private set

    @Volatile private var running = false
    private var thread: Thread? = null
    private val rate = 44100
    private val channels = if (mode == 1) 2 else 1

    /** Starts capturing. Returns false (and leaves nothing running) if the device or the app would not allow it. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        var record: AudioRecord? = null
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            val mask = if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
            val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(mask).build()
            val bufSize = (AudioRecord.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT) * 4).coerceAtLeast(32768)
            val rec = if (mode == 1) {
                if (Build.VERSION.SDK_INT < 29) return false
                val cfg = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(bufSize).setAudioPlaybackCaptureConfig(cfg).build()
            } else {
                AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC).setAudioFormat(format).setBufferSizeInBytes(bufSize).build()
            }
            record = rec
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
            val aac = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufSize)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec = enc
            enc.configure(aac, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            val mux = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            rec.startRecording()
            running = true
            thread = Thread { pump(rec, enc, mux, bufSize) }.also { it.start() }
            return true
        } catch (e: Throwable) {
            runCatching { record?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.release() }
            return false
        }
    }

    private fun pump(rec: AudioRecord, enc: MediaCodec, mux: MediaMuxer, bufSize: Int) {
        val info = MediaCodec.BufferInfo()
        var track = -1
        var started = false
        var bytes = 0L
        fun pts() = bytes * 1_000_000L / (rate.toLong() * channels * 2)
        // Takes what the encoder has finished; with [untilEnd] waits (a few seconds at most) for its last piece.
        fun drain(untilEnd: Boolean) {
            val deadline = System.currentTimeMillis() + 3000
            while (true) {
                val idx = enc.dequeueOutputBuffer(info, if (untilEnd) 10_000L else 0L)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEnd || System.currentTimeMillis() > deadline) return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = mux.addTrack(enc.outputFormat)
                        mux.start()
                        started = true
                    }
                    idx >= 0 -> {
                        val buf = enc.getOutputBuffer(idx)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started && buf != null) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            mux.writeSampleData(track, buf, info)
                        }
                        enc.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
        try {
            val pcm = ByteArray(4096)
            while (running) {
                val n = rec.read(pcm, 0, pcm.size)
                if (n < 0) break
                var off = 0
                while (off < n) {
                    val inIdx = enc.dequeueInputBuffer(10_000L)
                    if (inIdx < 0) { drain(false); continue }
                    val ib = enc.getInputBuffer(inIdx) ?: break
                    ib.clear()
                    val len = minOf(n - off, ib.remaining())
                    ib.put(pcm, off, len)
                    enc.queueInputBuffer(inIdx, 0, len, pts(), 0)
                    bytes += len
                    off += len
                    drain(false)
                }
            }
            val inIdx = enc.dequeueInputBuffer(100_000L)
            if (inIdx >= 0) enc.queueInputBuffer(inIdx, 0, 0, pts(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            ok = started && bytes > 0
        } catch (e: Throwable) {
            ok = false
        } finally {
            runCatching { rec.stop() }
            runCatching { rec.release() }
            runCatching { enc.stop() }
            runCatching { enc.release() }
            runCatching { if (started) mux.stop() }
            runCatching { mux.release() }
        }
    }

    /** Stops capturing and waits for the file to be finished. */
    fun stop() {
        running = false
        runCatching { thread?.join(6000) }
    }
}
