package com.benchfirewatch.render

import com.benchfirewatch.model.PaletteChoice
import com.benchfirewatch.model.PreviewImage
import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.model.ViewMode
import kotlin.math.abs

/**
 * Demo pictures. There is no visible-light camera, so MSX is the thermal image
 * with a dark edge where neighboring pixels disagree, and Visual is gray.
 * Picture-in-picture puts the thermal image in the center so zone boxes can be checked.
 */
object PreviewFrames {
    /**
     * Used when the camera colorizer did not return a picture. Stretches the
     * selected palette across this frame so a narrow temperature range stays readable.
     */
    fun standIn(frame: ThermalFrame, palette: PaletteChoice): PreviewImage {
        return PreviewImage(
            width = frame.width,
            height = frame.height,
            argb = colorizeScaled(frame, palette),
        )
    }

    fun forDemo(frame: ThermalFrame, mode: ViewMode, palette: PaletteChoice): PreviewImage {
        val thermal = colorize(frame, palette)
        return when (mode) {
            ViewMode.THERMAL -> PreviewImage(
                frame.width,
                frame.height,
                thermal,
                note = "Demo thermal.",
            )
            ViewMode.MSX -> PreviewImage(
                frame.width,
                frame.height,
                outline(thermal, frame.width, frame.height),
                note = "Demo has no visible camera. Edges are drawn from the temperature image.",
            )
            ViewMode.VISUAL -> PreviewImage(
                frame.width,
                frame.height,
                grayscale(frame),
                note = "Demo has no visible camera. This is a gray stand-in.",
            )
            ViewMode.BLEND -> PreviewImage(
                frame.width,
                frame.height,
                blend(thermal, grayscale(frame)),
                note = "Demo blend of the thermal colors and the gray stand-in.",
            )
            ViewMode.PIP -> pip(frame, thermal)
        }
    }

    private fun colorize(frame: ThermalFrame, palette: PaletteChoice): IntArray {
        val out = IntArray(frame.celsius.size)
        val min = frame.celsius.minOrNull() ?: 0f
        val max = frame.celsius.maxOrNull() ?: min
        val span = (max - min).coerceAtLeast(0.5f)
        for (i in frame.celsius.indices) {
            val t = ((frame.celsius[i] - min) / span).coerceIn(0f, 1f)
            out[i] = when (palette) {
                PaletteChoice.IRON -> ThermalPalette.argb(frame.celsius[i])
                PaletteChoice.RAINBOW -> rainbow(t)
                PaletteChoice.WHITE_HOT -> gray(t)
            }
        }
        return out
    }

    private fun colorizeScaled(frame: ThermalFrame, palette: PaletteChoice): IntArray {
        val out = IntArray(frame.celsius.size)
        val min = frame.celsius.minOrNull() ?: 0f
        val max = frame.celsius.maxOrNull() ?: min
        val span = (max - min).coerceAtLeast(0.5f)
        for (i in frame.celsius.indices) {
            val t = ((frame.celsius[i] - min) / span).coerceIn(0f, 1f)
            out[i] = when (palette) {
                PaletteChoice.IRON -> iron(t)
                PaletteChoice.RAINBOW -> rainbow(t)
                PaletteChoice.WHITE_HOT -> gray(t)
            }
        }
        return out
    }

    private fun iron(t: Float): Int {
        val stops = floatArrayOf(0f, 0.15f, 0.35f, 0.55f, 0.75f, 0.9f, 1f)
        val colors = intArrayOf(
            rgb(0, 0, 0),
            rgb(20, 0, 90),
            rgb(140, 0, 120),
            rgb(210, 30, 20),
            rgb(240, 140, 20),
            rgb(250, 230, 60),
            rgb(255, 255, 255),
        )
        return ramp(t, stops, colors)
    }

    private fun grayscale(frame: ThermalFrame): IntArray {
        val out = IntArray(frame.celsius.size)
        val min = frame.celsius.minOrNull() ?: 0f
        val max = frame.celsius.maxOrNull() ?: min
        val span = (max - min).coerceAtLeast(0.5f)
        for (i in frame.celsius.indices) {
            out[i] = gray(((frame.celsius[i] - min) / span).coerceIn(0f, 1f))
        }
        return out
    }

    private fun pip(frame: ThermalFrame, thermal: IntArray): PreviewImage {
        val w = frame.width * 2
        val h = frame.height * 2
        val pixels = IntArray(w * h) { 0xFF1A1A1A.toInt() }
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val dx = x + frame.width / 2
                val dy = y + frame.height / 2
                pixels[dy * w + dx] = thermal[y * frame.width + x]
            }
        }
        return PreviewImage(
            width = w,
            height = h,
            argb = pixels,
            contentLeft = 0.25f,
            contentTop = 0.25f,
            contentRight = 0.75f,
            contentBottom = 0.75f,
            note = "Demo picture-in-picture. The thermal image is the center inset.",
        )
    }

    private fun outline(src: IntArray, width: Int, height: Int): IntArray {
        val out = src.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val here = luminance(src[index])
                val neighbor = luminance(src[index - 1])
                if (abs(here - neighbor) > 28) {
                    out[index] = 0xFF101010.toInt()
                }
            }
        }
        return out
    }

    private fun blend(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(a.size)
        for (i in a.indices) {
            out[i] = mix(a[i], b[i])
        }
        return out
    }

    private fun rainbow(t: Float): Int {
        val stops = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val colors = intArrayOf(
            rgb(40, 40, 180),
            rgb(40, 180, 180),
            rgb(40, 180, 40),
            rgb(230, 180, 30),
            rgb(200, 30, 30),
        )
        return ramp(t, stops, colors)
    }

    private fun gray(t: Float): Int {
        val v = (18 + t * 230).toInt().coerceIn(0, 255)
        return rgb(v, v, v)
    }

    private fun ramp(t: Float, stops: FloatArray, colors: IntArray): Int {
        if (t <= stops.first()) return colors.first()
        if (t >= stops.last()) return colors.last()
        var i = 0
        while (i < stops.lastIndex && t > stops[i + 1]) i++
        val span = stops[i + 1] - stops[i]
        val f = if (span <= 0f) 0f else (t - stops[i]) / span
        return mix(colors[i], colors[i + 1], f)
    }

    private fun luminance(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 3 + g * 6 + b) / 10
    }

    private fun mix(a: Int, b: Int, f: Float = 0.5f): Int {
        fun ch(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
