package com.benchfirewatch.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.benchfirewatch.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Looping siren owned by the foreground service.
 *
 * The old player built a static AudioTrack and called setLoopPoints. On the
 * Builder path that call fails when the track's frame count is still 0, the
 * error was ignored, and the clip either never started or played once and
 * stopped. This plays the bundled wav with MediaPlayer looping, and falls
 * back to a streaming write loop if that player will not start.
 */
class SirenPlayer(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        app.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        app.getSystemService(Vibrator::class.java)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var player: MediaPlayer? = null
    private var track: AudioTrack? = null
    private var streamThread: Thread? = null
    private val streamStop = AtomicBoolean(true)
    private var focus: AudioFocusRequest? = null
    private var savedAlarmVolume: Int? = null
    private var mode = Mode.IDLE

    private val endTest = Runnable { stopTest() }

    @Synchronized
    fun startAlarm() {
        handler.removeCallbacks(endTest)
        if (mode == Mode.ALARM && (player != null || track != null)) return
        releaseLocked()
        mode = Mode.ALARM
        beginLocked(loop = true)
    }

    /** Drops the alarm loop. A three-second test is left running. */
    @Synchronized
    fun stopAlarm() {
        if (mode != Mode.ALARM) return
        releaseLocked()
    }

    @Synchronized
    fun playTest() {
        if (mode == Mode.ALARM && isAudible()) return
        handler.removeCallbacks(endTest)
        releaseLocked()
        mode = Mode.TEST
        beginLocked(loop = true)
        handler.postDelayed(endTest, SirenGate.TEST_MS)
    }

    @Synchronized
    fun stopTest() {
        if (mode != Mode.TEST) return
        releaseLocked()
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(endTest)
        releaseLocked()
    }

    private fun beginLocked(loop: Boolean) {
        raiseAlarmVolume()
        requestFocus()
        if (!startMedia(loop)) startStream()
        vibrate()
    }

    private fun startMedia(loop: Boolean): Boolean {
        return try {
            val created = MediaPlayer.create(app, R.raw.siren, attributes, 0) ?: return false
            created.isLooping = loop
            created.setVolume(1f, 1f)
            created.setWakeMode(app, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            created.setOnErrorListener { _, _, _ ->
                handler.post { failOverToStream() }
                true
            }
            created.start()
            player = created
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun failOverToStream() {
        synchronized(this) {
            if (mode == Mode.IDLE) return
            player?.let { releaseMedia(it) }
            player = null
            if (track == null) startStream()
        }
    }

    private fun startStream() {
        streamStop.set(false)
        val min = AudioTrack.getMinBufferSize(
            SirenTone.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SirenTone.SAMPLE_RATE / 5 * 2)
        val stream = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SirenTone.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(min)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        stream.play()
        track = stream
        val pcm = SirenTone.pcm()
        val thread = Thread({
            var offset = 0
            while (!streamStop.get()) {
                val wrote = try {
                    stream.write(pcm, offset, pcm.size - offset)
                } catch (_: IllegalStateException) {
                    break
                }
                if (wrote <= 0) break
                offset += wrote
                if (offset >= pcm.size) offset = 0
            }
        }, "hot-spotter-siren")
        thread.start()
        streamThread = thread
    }

    private fun isAudible(): Boolean {
        val media = player
        if (media != null) {
            return try {
                media.isPlaying
            } catch (_: IllegalStateException) {
                false
            }
        }
        return track?.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    private fun raiseAlarmVolume() {
        if (savedAlarmVolume != null) return
        try {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        } catch (_: SecurityException) {
        }
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT < 26 || focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    handler.post { keepFocus() }
                }
            }, handler)
            .build()
        focus = request
        audio.requestAudioFocus(request)
    }

    private fun keepFocus() {
        synchronized(this) {
            val request = focus ?: return
            if (mode == Mode.IDLE) return
            audio.requestAudioFocus(request)
        }
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 400, 200, 400, 200, 700)
        val effect = if (Build.VERSION.SDK_INT >= 26) {
            VibrationEffect.createWaveform(pattern, 0)
        } else {
            null
        }
        try {
            if (Build.VERSION.SDK_INT >= 33 && effect != null) {
                vibrator?.vibrate(
                    effect,
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build(),
                )
            } else if (Build.VERSION.SDK_INT >= 26 && effect != null) {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(effect, attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: SecurityException) {
        }
    }

    private fun releaseLocked() {
        val media = player
        player = null
        streamStop.set(true)
        val stream = track
        track = null
        val thread = streamThread
        streamThread = null
        media?.let { releaseMedia(it) }
        if (stream != null) {
            try {
                stream.pause()
                stream.flush()
                stream.release()
            } catch (_: IllegalStateException) {
            }
        }
        thread?.join(400)
        if (Build.VERSION.SDK_INT >= 26) {
            focus?.let { audio.abandonAudioFocusRequest(it) }
        }
        focus = null
        vibrator?.cancel()
        savedAlarmVolume?.let { volume ->
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
            } catch (_: SecurityException) {
            }
        }
        savedAlarmVolume = null
        mode = Mode.IDLE
    }

    private fun releaseMedia(media: MediaPlayer) {
        try {
            media.setOnErrorListener(null)
            if (media.isPlaying) media.stop()
            media.reset()
            media.release()
        } catch (_: IllegalStateException) {
            try {
                media.release()
            } catch (_: Throwable) {
            }
        }
    }

    private enum class Mode { IDLE, ALARM, TEST }
}
