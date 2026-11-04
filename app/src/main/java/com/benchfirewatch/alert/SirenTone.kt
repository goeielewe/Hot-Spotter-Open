package com.benchfirewatch.alert

import kotlin.math.PI
import kotlin.math.sin

/**
 * Original two-tone siren. The APK also ships this shape as res/raw/siren.wav.
 * Nothing here is a system ringtone or a copied recording.
 */
object SirenTone {
    const val SAMPLE_RATE = 44100

    fun pcm(seconds: Double = 1.2): ShortArray {
        val n = (SAMPLE_RATE * seconds).toInt().coerceAtLeast(SAMPLE_RATE / 5)
        val fade = (SAMPLE_RATE * 0.02).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        val dt = 1.0 / SAMPLE_RATE
        for (i in 0 until n) {
            val t = i * dt
            val cycle = (t % 0.5) / 0.5
            val tri = if (cycle < 0.5) cycle * 2 else (1 - cycle) * 2
            val freq = 640.0 + 980.0 * tri
            phase += 2.0 * PI * freq * dt
            val chop = if ((t / 0.14).toInt() % 2 == 0) 1.0 else 0.28
            var sample = sin(phase) * 0.92 * chop
            if (i < fade) sample *= i.toDouble() / fade
            val tail = n - 1 - i
            if (tail < fade) sample *= tail.toDouble() / fade
            out[i] = (sample * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}

/** The speaker follows an alarm only when Siren sound is left on. The screen does not. */
object SirenGate {
    const val TEST_MS = 3_000L

    fun shouldSound(engineSiren: Boolean, soundEnabled: Boolean): Boolean = engineSiren && soundEnabled
}
