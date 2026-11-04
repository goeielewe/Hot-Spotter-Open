package com.benchfirewatch.engine

import com.benchfirewatch.model.PreviewImage
import com.benchfirewatch.model.ThermalFrame
import kotlin.math.exp
import kotlin.math.sin

data class Spot(
    val x: Double,
    val y: Double,
    val sigma: Double,
    val peak: Double,
)

/**
 * Synthetic 160×120 radiometric frames. The same generators drive unit tests
 * and the in-app demo, so a scenario that passes here is the one on screen.
 *
 * The demo frame is 160×120. A cell from roughly 40 cm overhead is a
 * blob several pixels across; an iron tip is smaller, hotter, and moves.
 */
object Frames {
    const val WIDTH = 160
    const val HEIGHT = 120
    const val BACKGROUND = 27.0

    fun quiet(tMs: Long): ThermalFrame = render(tMs, emptyList(), noise = 0.2f)

    fun soldering(tMs: Long): ThermalFrame {
        val sec = (tMs % 14_000L) / 1000.0
        val spots = ArrayList<Spot>(1)
        if (sec in 0.4..8.2) {
            val x = 18.0 + (142.0 - 18.0) * ((sec - 0.4) / 7.8)
            spots += Spot(x, 62.0, sigma = 1.7, peak = 370.0)
        }
        return render(tMs, spots, noise = 0.15f)
    }

    fun spotWeld(tMs: Long): ThermalFrame {
        val sec = (tMs % 8_000L) / 1000.0
        val spots = if (sec in 2.0..2.25) {
            listOf(Spot(96.0, 70.0, sigma = 1.15, peak = 430.0))
        } else {
            emptyList()
        }
        return render(tMs, spots, noise = 0.15f)
    }

    /**
     * Iron comes in, dwells, and leaves. [tauSec] null holds the cell at [peakC].
     * A finite tau cools exponentially back toward bench ambient.
     */
    fun solderedCell(tMs: Long, peakC: Double, tauSec: Double?): ThermalFrame {
        val sec = tMs / 1000.0
        val spots = ArrayList<Spot>(2)
        val cellX = 80.0
        val cellY = 64.0
        if (sec in 0.4..6.0) {
            val x = when {
                sec < 1.8 -> 16.0 + (cellX - 16.0) * ((sec - 0.4) / 1.4)
                sec < 4.6 -> cellX
                else -> cellX + (175.0 - cellX) * ((sec - 4.6) / 1.4)
            }
            spots += Spot(x, cellY, sigma = 1.65, peak = 370.0)
        }
        if (sec >= 1.6) {
            val cellPeak = when {
                sec < 4.6 -> {
                    val u = ((sec - 1.6) / 3.0).coerceIn(0.0, 1.0)
                    BACKGROUND + (peakC - BACKGROUND) * u
                }
                tauSec == null -> peakC
                else -> BACKGROUND + (peakC - BACKGROUND) * exp(-(sec - 4.6) / tauSec)
            }
            spots += Spot(cellX, cellY, sigma = 5.8, peak = cellPeak)
        }
        return render(tMs, spots, noise = 0.12f)
    }

    fun selfHeating(tMs: Long, rateCPerSec: Double = 0.25, startC: Double = 30.0): ThermalFrame {
        val sec = tMs / 1000.0
        val peak = startC + rateCPerSec * sec
        return render(tMs, listOf(Spot(84.0, 62.0, sigma = 5.8, peak = peak)), noise = 0.1f)
    }

    fun runaway(tMs: Long, rateCPerSec: Double = 6.0, startC: Double = 36.0): ThermalFrame {
        val sec = tMs / 1000.0
        val peak = startC + rateCPerSec * sec
        return render(tMs, listOf(Spot(84.0, 62.0, sigma = 5.8, peak = peak)), noise = 0.08f)
    }

    fun parkedIron(tMs: Long): ThermalFrame =
        render(tMs, listOf(Spot(36.0, 28.0, sigma = 1.7, peak = 360.0)), noise = 0.1f)

    fun stableCell(tMs: Long, tempC: Double, x: Double = 100.0, y: Double = 70.0): ThermalFrame =
        render(tMs, listOf(Spot(x, y, sigma = 5.8, peak = tempC)), noise = 0.05f)

    private var frozenCache: FloatArray? = null

    fun frozen(tMs: Long): ThermalFrame {
        val cached = frozenCache ?: render(0L, listOf(Spot(90.0, 60.0, 5.0, 34.0)), noise = 0.2f).celsius.also {
            frozenCache = it
        }
        return ThermalFrame(tMs, WIDTH, HEIGHT, cached, "frozen")
    }

    /**
     * A few pixels at one temperature on a flat bench. This is the lighter the
     * blob tracker drops: too small to be a cell, and often far below an iron.
     */
    fun cluster(
        tMs: Long,
        x: Int,
        y: Int,
        count: Int = 3,
        celsius: Float = 110f,
        background: Float = BACKGROUND.toFloat(),
        rangeMaxC: Float? = null,
        label: String = "flame",
    ): ThermalFrame {
        val temps = FloatArray(WIDTH * HEIGHT) { background }
        paint(temps, WIDTH, HEIGHT, x, y, count, celsius)
        return ThermalFrame(tMs, WIDTH, HEIGHT, temps, label, rangeMaxC = rangeMaxC)
    }

    /**
     * Same spot, temperature swinging at [hz]. 1.3 Hz is where a 10 Hz flame
     * lands when an 8.7 fps camera aliases it. No absolute-temperature assumption:
     * [centerC] can sit near 100°C, which is what a lighter reads at bench distance.
     */
    fun flickering(
        tMs: Long,
        x: Int = 80,
        y: Int = 60,
        count: Int = 3,
        centerC: Float = 110f,
        ampC: Float = 16f,
        hz: Double = 1.3,
        background: Float = BACKGROUND.toFloat(),
        rangeMaxC: Float? = null,
    ): ThermalFrame {
        val wave = sin(2.0 * Math.PI * hz * (tMs / 1000.0))
        val temp = (centerC + ampC * wave).toFloat()
        return cluster(tMs, x, y, count, temp, background, rangeMaxC, label = "flicker")
    }

    /** A lighter-sized flickering plume, plus a cooler pixel above it, for the demo. */
    fun lighter(tMs: Long): ThermalFrame {
        val frame = flickering(tMs, x = 78, y = 64, count = 4, centerC = 112f, ampC = 18f)
        val temps = frame.celsius
        val plume = (70f + 10f * sin(2.0 * Math.PI * 1.3 * (tMs / 1000.0))).toFloat()
        paint(temps, WIDTH, HEIGHT, 78, 58, count = 2, celsius = plume)
        return ThermalFrame(tMs, WIDTH, HEIGHT, temps, "lighter", rangeMaxC = frame.rangeMaxC)
    }

    /** Steady iron plus a separate flickering flame far enough away that the blobs do not merge. */
    fun flameBesideIron(tMs: Long): ThermalFrame {
        val base = render(tMs, listOf(Spot(36.0, 28.0, sigma = 1.7, peak = 360.0)), noise = 0.05f)
        val temps = base.celsius.copyOf()
        val wave = (14f * sin(2.0 * Math.PI * 1.3 * (tMs / 1000.0))).toFloat()
        paint(temps, WIDTH, HEIGHT, 96, 28, count = 3, celsius = 114f + wave)
        return ThermalFrame(tMs, WIDTH, HEIGHT, temps, "flame-and-iron")
    }

    /** Shutter / flat FFC stand-in: one temperature, no scene. */
    fun shutter(tMs: Long, celsius: Float = BACKGROUND.toFloat()): ThermalFrame =
        ThermalFrame(tMs, WIDTH, HEIGHT, FloatArray(WIDTH * HEIGHT) { celsius }, "shutter")

    /** Clockwise quarter-turns of the radiometric grid. Detection must not care which way is up. */
    fun rotate(frame: ThermalFrame, degrees: Int): ThermalFrame {
        val turns = ((degrees % 360) + 360) % 360 / 90
        var w = frame.width
        var h = frame.height
        var src = frame.celsius
        repeat(turns) {
            val dst = FloatArray(src.size)
            val nw = h
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dx = h - 1 - y
                    val dy = x
                    dst[dy * nw + dx] = src[y * w + x]
                }
            }
            src = dst
            val oldW = w
            w = h
            h = oldW
        }
        return ThermalFrame(
            frame.timestampMs,
            w,
            h,
            src,
            frame.label,
            preview = frame.preview,
            displayNote = frame.displayNote,
            rangeMaxC = frame.rangeMaxC,
        )
    }

    fun withPreview(frame: ThermalFrame, note: String): ThermalFrame {
        val preview = PreviewImage(width = 2, height = 2, argb = IntArray(4), note = note)
        return ThermalFrame(
            frame.timestampMs,
            frame.width,
            frame.height,
            frame.celsius,
            frame.label,
            preview = preview,
            displayNote = note,
            rangeMaxC = frame.rangeMaxC,
        )
    }

    private val clusterOffsets = arrayOf(
        0 to 0, 1 to 0, 0 to 1, -1 to 0, 0 to -1,
        1 to 1, -1 to 1, 1 to -1, -1 to -1, 2 to 0, 0 to 2, -2 to 0,
    )

    private fun paint(temps: FloatArray, width: Int, height: Int, x: Int, y: Int, count: Int, celsius: Float) {
        var painted = 0
        for ((dx, dy) in clusterOffsets) {
            if (painted >= count) break
            val xx = x + dx
            val yy = y + dy
            if (xx !in 0 until width || yy !in 0 until height) continue
            temps[yy * width + xx] = celsius
            painted++
        }
    }

    fun render(tMs: Long, spots: List<Spot>, noise: Float): ThermalFrame {
        val temps = FloatArray(WIDTH * HEIGHT)
        val rng = Rng((tMs / 80L).toInt() xor 0x51A7)
        var i = 0
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val n = (rng.next() - 0.5f) * 2f * noise
                var hottest = (BACKGROUND + n).toFloat()
                for (s in spots) {
                    val dx = x - s.x
                    val dy = y - s.y
                    val g = exp(-0.5 * (dx * dx + dy * dy) / (s.sigma * s.sigma))
                    val v = (BACKGROUND + (s.peak - BACKGROUND) * g + n * 0.15f).toFloat()
                    if (v > hottest) hottest = v
                }
                temps[i++] = hottest
            }
        }
        return ThermalFrame(tMs, WIDTH, HEIGHT, temps, "sim")
    }
}

internal class Rng(seed: Int) {
    private var state = seed xor 0x2F6E2B1

    fun next(): Float {
        state = state * 1664525 + 1013904223
        val bits = (state ushr 8) and 0xFFFFFF
        return bits / 16777215f
    }
}
