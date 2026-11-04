package com.benchfirewatch.audio

import com.benchfirewatch.model.MonitorStatus
import kotlin.math.abs

/**
 * Click plus a short hiss, the sound of a cell vent. This is a warning only.
 * It is never a full alarm by itself. Audio samples are not stored.
 */
class VentDetector(sensitivity: Int = 3) {
    private val level = sensitivity.coerceIn(1, 5)
    private val pending = ArrayList<Short>(4096)
    private var read = 0
    private val energies = ArrayDeque<Float>()
    private var state = State.IDLE
    private var hissHits = 0
    private var hissSeen = 0
    private var cool = 0

    fun push(pcm: ShortArray, count: Int, sampleRate: Int): Boolean {
        val n = count.coerceIn(0, pcm.size)
        for (i in 0 until n) pending.add(pcm[i])
        val hop = hopSamples(sampleRate)
        if (hop <= 0) return false
        var hit = false
        while (pending.size - read >= hop) {
            val slice = ShortArray(hop) { pending[read + it] }
            read += hop
            if (onHop(energy(slice), zeroCrossings(slice))) hit = true
        }
        if (read > 4096) {
            pending.subList(0, read).clear()
            read = 0
        }
        return hit
    }

    private fun onHop(energy: Float, zcr: Float): Boolean {
        val median = median(energies)
        energies.addLast(energy)
        while (energies.size > HISTORY) energies.removeFirst()
        if (cool > 0) {
            cool -= 1
            state = State.IDLE
            return false
        }
        val click = isClick(energy, zcr, median)
        val hiss = isHiss(energy, zcr)
        when (state) {
            State.IDLE -> if (click) {
                state = State.HISS
                hissHits = 0
                hissSeen = 0
            }
            State.HISS -> {
                hissSeen += 1
                if (hiss) hissHits += 1
                if (hissHits >= HISS_NEED) {
                    state = State.IDLE
                    cool = COOL_HOPS
                    return true
                }
                if (hissSeen >= HISS_WINDOW) state = State.IDLE
            }
        }
        return false
    }

    private fun isClick(energy: Float, zcr: Float, median: Float): Boolean {
        if (zcr < zcrGate()) return false
        val floor = median.coerceAtLeast(1e-6f)
        return energy > 0.01f && energy > floor * jump()
    }

    private fun isHiss(energy: Float, zcr: Float): Boolean = energy > 0.004f && zcr >= zcrGate() * 0.8f

    private fun jump(): Float = when (level) {
        1 -> 14f
        2 -> 10f
        3 -> 6f
        4 -> 4f
        else -> 3f
    }

    private fun zcrGate(): Float = when (level) {
        1 -> 0.28f
        2 -> 0.22f
        3 -> 0.18f
        4 -> 0.15f
        else -> 0.12f
    }

    private fun energy(samples: ShortArray): Float {
        var sum = 0.0
        for (sample in samples) {
            val v = sample.toDouble()
            sum += v * v
        }
        return (sum / samples.size / (32768.0 * 32768.0)).toFloat()
    }

    private fun zeroCrossings(samples: ShortArray): Float {
        if (samples.size < 2) return 0f
        var crosses = 0
        var prev = samples[0]
        for (i in 1 until samples.size) {
            val cur = samples[i]
            if ((prev >= 0 && cur < 0) || (prev < 0 && cur >= 0)) {
                if (abs(prev.toInt()) + abs(cur.toInt()) > 200) crosses += 1
            }
            prev = cur
        }
        return crosses.toFloat() / (samples.size - 1)
    }

    private fun median(values: ArrayDeque<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private enum class State { IDLE, HISS }

    companion object {
        const val BOOST_MS = 60_000L
        private const val HISTORY = 25
        private const val HISS_NEED = 6
        private const val HISS_WINDOW = 20
        private const val COOL_HOPS = 50

        fun hopSamples(sampleRate: Int): Int = sampleRate * 20 / 1000

        /** Warning only. A vent sound must not be the thing that starts the siren. */
        fun tier(): MonitorStatus = MonitorStatus.WARNING

        fun soleAlarmTrigger(): Boolean = false

        fun boostedSensitivity(base: Int, boostActive: Boolean): Int {
            val safe = base.coerceIn(1, 5)
            return if (boostActive) (safe + 1).coerceAtMost(5) else safe
        }
    }
}
