package com.benchfirewatch.engine

import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.model.WatchSettings
import kotlin.math.sqrt

data class Blob(
    val cx: Float,
    val cy: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val areaFraction: Float,
    val pixelCount: Int,
    val maxC: Float,
    val bodyC: Float,
    val meanC: Float,
)

fun extractBlobs(frame: ThermalFrame, settings: WatchSettings): List<Blob> {
    val temps = frame.celsius
    val w = frame.width
    val h = frame.height
    val n = temps.size
    if (n == 0) return emptyList()
    val background = percentileSample(temps, settings.backgroundPercentile)
    val warm = background + settings.blobDeltaC
    val visited = BooleanArray(n)
    val blobs = ArrayList<Blob>()
    val stack = ArrayDeque<Int>()

    fun consider(idx: Int) {
        if (!visited[idx] && temps[idx] >= warm) {
            visited[idx] = true
            stack.addLast(idx)
        }
    }

    for (start in 0 until n) {
        if (visited[start] || temps[start] < warm) continue
        visited[start] = true
        stack.addLast(start)
        var count = 0
        var sum = 0.0
        var sumX = 0.0
        var sumY = 0.0
        var maxT = Float.NEGATIVE_INFINITY
        var maxI = start
        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        val pixels = ArrayList<Int>(64)
        while (stack.isNotEmpty()) {
            val p = stack.removeLast()
            val t = temps[p]
            val x = p % w
            val y = p / w
            pixels.add(p)
            count++
            sum += t
            sumX += x
            sumY += y
            if (t > maxT) {
                maxT = t
                maxI = p
            }
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (x > 0) consider(p - 1)
            if (x + 1 < w) consider(p + 1)
            if (y > 0) consider(p - w)
            if (y + 1 < h) consider(p + w)
        }
        if (count < settings.minBlobPixels) continue
        val body = bodyTemperature(temps, pixels, w, h, maxI, maxT, settings)
        blobs.add(
            Blob(
                cx = (sumX / count).toFloat() / w,
                cy = (sumY / count).toFloat() / h,
                left = minX.toFloat() / w,
                top = minY.toFloat() / h,
                right = (maxX + 1f) / w,
                bottom = (maxY + 1f) / h,
                areaFraction = count.toFloat() / n,
                pixelCount = count,
                maxC = maxT,
                bodyC = body,
                meanC = (sum / count).toFloat(),
            ),
        )
    }
    return blobs
}

/**
 * Temperature of the object, not the iron tip.
 *
 * A soldering iron is a tiny core that can read hundreds of degrees (or, on
 * bare metal, falsely cold). When a small core above [WatchSettings.coreTempC]
 * is present, the body temperature is taken from the warm pixels outside that
 * core and below [WatchSettings.haloCapC]. A cell-sized region that is itself
 * that hot is not treated as a tip.
 */
internal fun bodyTemperature(
    temps: FloatArray,
    pixels: List<Int>,
    width: Int,
    height: Int,
    hottestIndex: Int,
    maxT: Float,
    settings: WatchSettings,
): Float {
    if (maxT < settings.coreTempC) {
        return percentileOf(pixels.map { temps[it] }, 0.9f)
    }
    val hx = hottestIndex % width
    val hy = hottestIndex / width
    var coreR = 0f
    var coreCount = 0
    for (p in pixels) {
        if (temps[p] < settings.coreTempC) continue
        coreCount++
        val dx = (p % width) - hx
        val dy = (p / width) - hy
        val r = sqrt((dx * dx + dy * dy).toFloat())
        if (r > coreR) coreR = r
    }
    val frameArea = width * height
    if (coreCount.toFloat() / frameArea > settings.toolMaxAreaFraction) {
        return percentileOf(pixels.map { temps[it] }, 0.9f)
    }
    val minR = coreR + settings.coreHaloPx
    val outer = ArrayList<Float>()
    for (p in pixels) {
        val dx = (p % width) - hx
        val dy = (p / width) - hy
        val r = sqrt((dx * dx + dy * dy).toFloat())
        val t = temps[p]
        if (r >= minR && t <= settings.haloCapC) outer.add(t)
    }
    if (outer.size < settings.minBlobPixels) {
        val sorted = pixels.map { temps[it] }.sorted()
        val coolCount = (sorted.size * 0.3f).toInt().coerceAtLeast(1)
        return sorted[coolCount / 2]
    }
    return percentileOf(outer, 0.75f)
}

internal fun percentileSample(values: FloatArray, p: Float): Float {
    val step = 2
    val sample = FloatArray((values.size + step - 1) / step)
    var j = 0
    var i = 0
    while (i < values.size) {
        sample[j++] = values[i]
        i += step
    }
    sample.sort(0, j)
    val idx = ((j - 1) * p).toInt().coerceIn(0, j - 1)
    return sample[idx]
}

internal fun percentileOf(values: List<Float>, p: Float): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sorted()
    val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
    return sorted[idx]
}
