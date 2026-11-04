package com.benchfirewatch.render

import com.benchfirewatch.model.ThermalFrame

/**
 * Fixed palette so a rising cell looks hotter instead of the image rescaling
 * around it. 20°C is bench-dark, 60°C is the default warning color, above
 * 120°C goes white.
 */
object ThermalPalette {
    private val stops = floatArrayOf(18f, 28f, 38f, 50f, 60f, 75f, 100f, 140f, 220f)
    private val colors = intArrayOf(
        rgb(8, 18, 38),
        rgb(16, 70, 92),
        rgb(28, 120, 86),
        rgb(214, 176, 62),
        rgb(224, 112, 42),
        rgb(198, 40, 36),
        rgb(255, 196, 80),
        rgb(255, 244, 220),
        rgb(255, 255, 255),
    )

    fun argb(tempC: Float): Int {
        if (tempC <= stops[0]) return colors[0]
        if (tempC >= stops.last()) return colors.last()
        var i = 0
        while (i < stops.lastIndex && tempC > stops[i + 1]) i++
        val span = stops[i + 1] - stops[i]
        val f = if (span <= 0f) 0f else (tempC - stops[i]) / span
        return lerp(colors[i], colors[i + 1], f)
    }

    fun render(frame: ThermalFrame): IntArray {
        val out = IntArray(frame.celsius.size)
        for (i in frame.celsius.indices) out[i] = argb(frame.celsius[i])
        return out
    }

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun lerp(a: Int, b: Int, f: Float): Int {
        fun ch(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
