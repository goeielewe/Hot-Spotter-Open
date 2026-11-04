package com.benchfirewatch.engine

import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.model.Zone
import com.benchfirewatch.model.ZoneKind
import kotlin.math.abs
import kotlin.math.min

/**
 * Open-flame watch. It does not use blob size, motion, or an absolute °C cutoff.
 *
 * A pixel is interesting when it sits above its own rolling bench temperature.
 * Any one of these is a hit, and two hits inside a second latch a flame:
 * a hot delta that also flickers, a very large delta on a small cluster (or a
 * wide area that is not the whole frame warming together), a fast rise on a
 * few pixels, or a pixel pinned at the camera's range ceiling.
 *
 * A compact core at or above [TOOL_CORE_C] with no flicker is a soldering iron
 * and is not called a flame. That exclusion is the false-alarm filter; it is
 * not a gate the other paths have to pass.
 *
 * Duplicate frames and a flat shutter do not wipe the buffers. A duplicate that
 * still shows the hot pixel counts as the confirming hit, so a frozen picture
 * of a flame cannot sit there quietly.
 */
class FlameWatch {
    private var profile = profileFor(4)
    private var baseline: FloatArray? = null
    private var ready = false
    private var width = 0
    private var height = 0
    private var lastPixels: FloatArray? = null
    private var streak = 0
    private var lastHitTime = Long.MIN_VALUE
    private var pending: FlameHit? = null
    private val series = HashMap<Int, Series>()
    private var scratch: FloatArray? = null

    fun configure(sensitivity: Int) {
        profile = profileFor(sensitivity)
    }

    fun reset() {
        baseline = null
        ready = false
        width = 0
        height = 0
        lastPixels = null
        streak = 0
        lastHitTime = Long.MIN_VALUE
        pending = null
        series.clear()
    }

    /**
     * @return a hit only after two confirming observations, so one noisy frame
     * cannot start the siren. Returns null while a candidate is still building,
     * or while a shutter is being coasted.
     */
    fun onFrame(frame: ThermalFrame, zones: List<Zone>, wholeFrame: Boolean): FlameHit? {
        if (frame.width <= 1 || frame.height <= 1 || frame.celsius.size != frame.width * frame.height) return null
        val now = frame.timestampMs
        if (samePixels(frame.celsius)) {
            val confirmed = confirmDuplicate(frame, now, zones, wholeFrame)
            if (confirmed != null) return confirmed
            // Identical pixels while a candidate is live (a frozen flame, or a
            // second copy of the frame that just scored). Don't score twice.
            if (streak >= 1 && now - lastHitTime <= COAST_MS) return null
        }
        if (isShutter(frame)) {
            if (streak >= 1 && now - lastHitTime > COAST_MS) {
                streak = 0
                pending = null
            }
            return null
        }
        ensureBaseline(frame)
        val hit = evaluate(frame, zones, wholeFrame)
        lastPixels = frame.celsius.copyOf()
        if (hit != null) {
            streak = if (streak >= 1 && now - lastHitTime <= CONFIRM_MS) streak + 1 else 1
            lastHitTime = now
            pending = hit
            if (streak >= 2) return hit
            return null
        }
        val dip = streak >= 1 && now - lastHitTime <= CONFIRM_MS && stillWarm(frame)
        if (!dip) {
            streak = 0
            pending = null
        }
        return null
    }

    private fun confirmDuplicate(frame: ThermalFrame, now: Long, zones: List<Zone>, wholeFrame: Boolean): FlameHit? {
        val held = pending ?: return null
        if (streak < 1 || now - lastHitTime > COAST_MS) return null
        if (!allowed(held.x, held.y, frame.width, frame.height, zones, wholeFrame)) return null
        if (!pixelStillHot(frame, held.x, held.y)) return null
        streak = 2
        lastHitTime = now
        return held
    }

    private fun ensureBaseline(frame: ThermalFrame) {
        val n = frame.celsius.size
        if (baseline?.size != n) {
            baseline = FloatArray(n)
            ready = false
            series.clear()
            width = frame.width
            height = frame.height
        }
        if (ready) return
        val cool = percentile(frame.celsius, 0.35f)
        val base = baseline ?: return
        for (i in base.indices) {
            base[i] = min(frame.celsius[i], cool + 1.5f)
        }
        ready = true
        width = frame.width
        height = frame.height
    }

    private fun evaluate(frame: ThermalFrame, zones: List<Zone>, wholeFrame: Boolean): FlameHit? {
        val base = baseline ?: return null
        val w = frame.width
        val h = frame.height
        val n = frame.celsius.size
        val delta = FloatArray(n)
        var hottest = 0
        var hottestDelta = Float.NEGATIVE_INFINITY
        val cap = frame.rangeMaxC
        for (i in 0 until n) {
            var d = frame.celsius[i] - base[i]
            if (cap != null && frame.celsius[i] >= cap - SATURATION_MARGIN_C) {
                if (d < profile.largeDeltaC) d = profile.largeDeltaC
            }
            delta[i] = d
            if (d > hottestDelta) {
                hottestDelta = d
                hottest = i
            }
        }
        remember(frame, delta, cap, hottest)
        val tool = toolNeighborhood(frame)
        val labels = labelComponents(delta, tool, w, h)
        val found = ArrayList<Candidate>(8)
        consider(hottest, frame, delta, tool, labels, found)
        for (index in series.keys) {
            if (index == hottest) continue
            if (delta[index] < profile.hotDeltaC && !saturated(frame, index)) continue
            consider(index, frame, delta, tool, labels, found)
        }
        adaptBaseline(frame, delta)
        if (found.isEmpty()) return null
        found.sortByDescending { it.rank }
        for (candidate in found) {
            val x = candidate.index % w
            val y = candidate.index / w
            if (!allowed(x, y, w, h, zones, wholeFrame)) continue
            return FlameHit(
                x = x,
                y = y,
                deltaC = delta[candidate.index],
                maxC = frame.celsius[candidate.index],
                reason = candidate.reason,
            )
        }
        return null
    }

    private fun consider(
        index: Int,
        frame: ThermalFrame,
        delta: FloatArray,
        tool: BooleanArray,
        labels: Labels,
        out: MutableList<Candidate>,
    ) {
        val flickering = flickerHot(index)
        val pinned = saturated(frame, index)
        // A pixel next to a very hot compact core is the iron, including while the
        // tip moves. Flicker does not override that: a lighter beside the iron is
        // outside this neighborhood and still counts on its own.
        val masked = tool[index] && !pinned
        if (masked) return
        val d = delta[index]
        val small = locallySmall(index, delta, frame.width, frame.height)
        val comp = if (d >= profile.largeDeltaC) labels.of(index) else null
        val reason = when {
            pinned -> "saturated"
            flickering && d >= profile.hotDeltaC && small -> "flicker"
            comp != null && comp.flameSized && small -> "delta"
            comp != null && comp.wideFire -> "delta"
            riseHot(index, delta) && !tool[index] && small -> "rise"
            else -> return
        }
        val rank = when (reason) {
            "saturated" -> 4
            "flicker" -> 3
            "delta" -> 2
            else -> 1
        }
        out.add(Candidate(index, reason, rank))
    }

    private fun remember(frame: ThermalFrame, delta: FloatArray, cap: Float?, hottest: Int) {
        val now = frame.timestampMs
        val hot = ArrayList<Int>()
        for (i in delta.indices) {
            if (delta[i] >= profile.hotDeltaC || (cap != null && frame.celsius[i] >= cap - SATURATION_MARGIN_C)) {
                hot.add(i)
            }
        }
        if (hottest !in hot && delta[hottest] >= profile.hotDeltaC * 0.5f) hot.add(hottest)
        if (hot.size > MAX_TRACKED) {
            hot.sortByDescending { delta[it] }
            while (hot.size > MAX_TRACKED) hot.removeAt(hot.lastIndex)
        }
        val keep = hot.toHashSet()
        for ((index, samples) in series) {
            if (index !in 0 until frame.celsius.size) continue
            push(samples, now, frame.celsius[index])
        }
        for (index in keep) {
            val samples = series.getOrPut(index) { Series(now) }
            if (samples.samples.isEmpty() || samples.samples.last().t != now) {
                push(samples, now, frame.celsius[index])
            }
            samples.lastHot = now
        }
        if (series.size > MAX_TRACKED + 50) {
            val ranked = series.entries.sortedBy { it.value.lastHot }
            val extra = series.size - MAX_TRACKED
            for (i in 0 until extra) series.remove(ranked[i].key)
        }
        series.entries.removeAll { now - it.value.lastHot > HISTORY_MS }
    }

    private fun push(series: Series, now: Long, temp: Float) {
        series.samples.addLast(FlameSample(now, temp))
        while (series.samples.size > 36) series.samples.removeFirst()
        while (series.samples.isNotEmpty() && now - series.samples.first().t > HISTORY_MS) {
            series.samples.removeFirst()
        }
    }

    private fun flickerHot(index: Int): Boolean {
        val samples = series[index]?.samples ?: return false
        if (samples.size < 5) return false
        if (samples.last().t - samples.first().t < 350L) return false
        var energy = 0.0
        var n = 0
        var prev = 0.0
        var crossings = 0
        var have = false
        for (k in 1 until samples.size - 1) {
            val hp = -0.25 * samples[k - 1].temp + 0.5 * samples[k].temp - 0.25 * samples[k + 1].temp
            energy += hp * hp
            n++
            if (have && prev * hp < 0.0 && abs(hp) > 0.35 && abs(prev) > 0.35) crossings++
            prev = hp
            have = true
        }
        if (n == 0) return false
        return energy / n >= profile.flickerEnergy && crossings >= 1
    }

    /** Rise is only a flame when it is a few pixels. A cell-sized warm-up stays on the cell path. */
    private fun locallySmall(index: Int, delta: FloatArray, w: Int, h: Int): Boolean {
        val seen = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(index)
        seen.add(index)
        while (queue.isNotEmpty()) {
            if (seen.size > 48) return false
            val i = queue.removeFirst()
            val x = i % w
            val y = i / w
            fun look(n: Int) {
                if (n !in seen && delta[n] >= profile.hotDeltaC) {
                    seen.add(n)
                    queue.add(n)
                }
            }
            if (x > 0) look(i - 1)
            if (x + 1 < w) look(i + 1)
            if (y > 0) look(i - w)
            if (y + 1 < h) look(i + w)
        }
        return seen.isNotEmpty()
    }

    private fun riseHot(index: Int, delta: FloatArray): Boolean {
        if (delta[index] < profile.hotDeltaC) return false
        val samples = series[index]?.samples ?: return false
        if (samples.size < 2) return false
        val newest = samples.last()
        var oldest = samples.first()
        for (sample in samples) {
            if (newest.t - sample.t <= 1_000L) {
                oldest = sample
                break
            }
        }
        val dt = (newest.t - oldest.t) / 1000f
        if (dt < 0.12f) return false
        val step = newest.temp - oldest.temp
        val rate = step / dt
        if (rate < profile.riseCPerSec || step < profile.riseStepC) return false
        return true
    }

    private fun toolNeighborhood(frame: ThermalFrame): BooleanArray {
        val w = frame.width
        val h = frame.height
        val mark = BooleanArray(frame.celsius.size)
        for (i in frame.celsius.indices) {
            if (frame.celsius[i] < TOOL_CORE_C) continue
            val x = i % w
            val y = i / w
            for (dy in -TOOL_RADIUS..TOOL_RADIUS) {
                val yy = y + dy
                if (yy !in 0 until h) continue
                for (dx in -TOOL_RADIUS..TOOL_RADIUS) {
                    val xx = x + dx
                    if (xx !in 0 until w) continue
                    mark[yy * w + xx] = true
                }
            }
        }
        return mark
    }

    private fun labelComponents(delta: FloatArray, tool: BooleanArray, w: Int, h: Int): Labels {
        val label = IntArray(delta.size) { -1 }
        val info = ArrayList<Comp>()
        val queue = ArrayDeque<Int>()
        var id = 0
        for (start in delta.indices) {
            if (label[start] != -1 || tool[start] || delta[start] < profile.largeDeltaC) continue
            val comp = Comp()
            queue.add(start)
            label[start] = id
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                comp.count++
                if (delta[i] > comp.peak) comp.peak = delta[i]
                val x = i % w
                val y = i / w
                if (x > 0) enqueue(i - 1, delta, tool, label, id, queue)
                if (x + 1 < w) enqueue(i + 1, delta, tool, label, id, queue)
                if (y > 0) enqueue(i - w, delta, tool, label, id, queue)
                if (y + 1 < h) enqueue(i + w, delta, tool, label, id, queue)
            }
            comp.flameSized = comp.count in 1..SMALL_CLUSTER_MAX
            comp.wideFire = comp.count in (SMALL_CLUSTER_MAX + 1) until (delta.size * 85 / 100) &&
                comp.peak >= profile.largeDeltaC + WIDE_EXTRA_C
            info.add(comp)
            id++
        }
        return Labels(label, info)
    }

    private fun enqueue(
        index: Int,
        delta: FloatArray,
        tool: BooleanArray,
        label: IntArray,
        id: Int,
        queue: ArrayDeque<Int>,
    ) {
        if (label[index] != -1 || tool[index] || delta[index] < profile.largeDeltaC) return
        label[index] = id
        queue.add(index)
    }

    private fun adaptBaseline(frame: ThermalFrame, delta: FloatArray) {
        val base = baseline ?: return
        for (i in base.indices) {
            val sample = frame.celsius[i]
            if (delta[i] <= 3f) {
                base[i] = base[i] * 0.9f + sample * 0.1f
            } else if (i !in series) {
                base[i] = base[i] * 0.998f + sample * 0.002f
            }
        }
    }

    private fun stillWarm(frame: ThermalFrame): Boolean {
        val base = baseline ?: return false
        val cap = frame.rangeMaxC
        var warm = 0
        for (index in series.keys) {
            if (index !in frame.celsius.indices) continue
            val d = frame.celsius[index] - base[index]
            val pinned = cap != null && frame.celsius[index] >= cap - SATURATION_MARGIN_C
            if (d >= profile.hotDeltaC * 0.45f || pinned) warm++
        }
        return warm > 0
    }

    private fun pixelStillHot(frame: ThermalFrame, x: Int, y: Int): Boolean {
        val base = baseline ?: return false
        if (x !in 0 until frame.width || y !in 0 until frame.height) return false
        val index = y * frame.width + x
        val cap = frame.rangeMaxC
        if (cap != null && frame.celsius[index] >= cap - SATURATION_MARGIN_C) return true
        return frame.celsius[index] - base[index] >= profile.hotDeltaC * 0.45f
    }

    private fun saturated(frame: ThermalFrame, index: Int): Boolean {
        val cap = frame.rangeMaxC ?: return false
        return frame.celsius[index] >= cap - SATURATION_MARGIN_C
    }

    private fun isShutter(frame: ThermalFrame): Boolean {
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        for (value in frame.celsius) {
            if (value < min) min = value
            if (value > max) max = value
        }
        if (max - min > 1.2f) return false
        val cap = frame.rangeMaxC
        if (cap != null && max >= cap - SATURATION_MARGIN_C) return false
        return true
    }

    private fun samePixels(pixels: FloatArray): Boolean {
        val prev = lastPixels ?: return false
        if (prev.size != pixels.size) return false
        for (i in prev.indices) if (prev[i] != pixels[i]) return false
        return true
    }

    private fun percentile(src: FloatArray, p: Float): Float {
        val tmp = scratch?.takeIf { it.size == src.size } ?: FloatArray(src.size).also { scratch = it }
        src.copyInto(tmp)
        tmp.sort()
        val idx = ((tmp.size - 1) * p).toInt().coerceIn(0, tmp.lastIndex)
        return tmp[idx]
    }

    private class Series(var lastHot: Long) {
        val samples = ArrayDeque<FlameSample>(12)
    }

    private class Comp {
        var count = 0
        var peak = 0f
        var flameSized = false
        var wideFire = false
    }

    private class Labels(val label: IntArray, val info: List<Comp>) {
        fun of(index: Int): Comp? {
            val id = label[index]
            if (id < 0 || id > info.lastIndex) return null
            return info[id]
        }
    }

    private data class Candidate(val index: Int, val reason: String, val rank: Int)

    companion object {
        /** Compact core this hot, with no flicker, is a soldering iron rather than a flame. */
        const val TOOL_CORE_C = 240f
        private const val TOOL_RADIUS = 3
        private const val SATURATION_MARGIN_C = 1.5f
        private const val SMALL_CLUSTER_MAX = 250
        private const val WIDE_EXTRA_C = 40f
        private const val CONFIRM_MS = 1_000L
        private const val COAST_MS = 1_500L
        private const val HISTORY_MS = 4_000L
        private const val MAX_TRACKED = 500

        fun describe(sensitivity: Int, wholeFrame: Boolean): String {
            val where = if (wholeFrame) "whole frame" else "drawn zones only"
            return "Flame watch on · $where · sensitivity ${sensitivity.coerceIn(1, 5)}"
        }

        /**
         * A fresh watcher, not the live one, must catch a canned 3-pixel spot
         * within a second. Failure means the flame path itself is dead.
         */
        fun selfCheck(): Boolean {
            val watch = FlameWatch()
            watch.configure(4)
            var hit: FlameHit? = null
            for (i in 0..8) {
                val t = i * 120L
                val frame = Frames.cluster(t, x = 40, y = 30, count = 3, celsius = 120f)
                hit = watch.onFrame(frame, emptyList(), wholeFrame = true)
                if (hit != null) return t <= 1_000L
            }
            return hit != null
        }

        private fun profileFor(level: Int): Profile {
            val n = level.coerceIn(1, 5)
            // Index 0 unused. 1 is the least sensitive, 5 the most.
            // largeDelta at 1 is still under a 109°C lighter on a ~27°C bench (~80°).
            val hot = floatArrayOf(0f, 28f, 20f, 14f, 10f, 8f)
            val large = floatArrayOf(0f, 48f, 42f, 38f, 36f, 28f)
            val rise = floatArrayOf(0f, 4f, 2.5f, 1.5f, 1f, 0.8f)
            val step = floatArrayOf(0f, 36f, 28f, 22f, 18f, 14f)
            val energy = floatArrayOf(0f, 3.5f, 2.2f, 1.4f, 0.8f, 0.45f)
            return Profile(hot[n], large[n], rise[n], step[n], energy[n])
        }
    }
}

data class FlameHit(
    val x: Int,
    val y: Int,
    val deltaC: Float,
    val maxC: Float,
    val reason: String,
)

private data class Profile(
    val hotDeltaC: Float,
    val largeDeltaC: Float,
    val riseCPerSec: Float,
    val riseStepC: Float,
    val flickerEnergy: Float,
)

private data class FlameSample(val t: Long, val temp: Float)

private fun allowed(x: Int, y: Int, w: Int, h: Int, zones: List<Zone>, wholeFrame: Boolean): Boolean {
    val nx = (x + 0.5f) / w
    val ny = (y + 0.5f) / h
    if (wholeFrame) return true
    val watch = zones.filter { it.kind != ZoneKind.IGNORE }
    if (watch.isEmpty()) {
        return zones.none { it.kind == ZoneKind.IGNORE && it.contains(nx, ny) }
    }
    return watch.any { it.contains(nx, ny) }
}
