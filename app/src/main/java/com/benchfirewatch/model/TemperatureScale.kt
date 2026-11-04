package com.benchfirewatch.model

/**
 * Settings are stored and detected in Celsius. The editor can show Fahrenheit.
 * Absolute temperatures use °F = °C × 9/5 + 32. A difference or a rate scales
 * by 9/5 only, with no offset.
 */
object TemperatureScale {
    const val SLIDER_MIN_C = -40f
    const val SLIDER_MAX_C = 1000f
    const val RATE_SLIDER_MIN_C = -100f
    const val RATE_SLIDER_MAX_C = 100f

    fun toDisplay(celsius: Float, fahrenheit: Boolean, absolute: Boolean): Double {
        val c = celsius.toDouble()
        if (!fahrenheit) return c
        val scaled = c * 9.0 / 5.0
        return if (absolute) scaled + 32.0 else scaled
    }

    fun fromDisplay(shown: Double, fahrenheit: Boolean, absolute: Boolean): Float {
        val c = if (!fahrenheit) {
            shown
        } else if (absolute) {
            (shown - 32.0) * 5.0 / 9.0
        } else {
            shown * 5.0 / 9.0
        }
        return c.toFloat()
    }

    fun sliderRange(fahrenheit: Boolean, absolute: Boolean, rate: Boolean): Pair<Float, Float> {
        val lo = if (rate) RATE_SLIDER_MIN_C else SLIDER_MIN_C
        val hi = if (rate) RATE_SLIDER_MAX_C else SLIDER_MAX_C
        val scaleAbsolute = absolute && !rate
        return toDisplay(lo, fahrenheit, scaleAbsolute).toFloat() to
            toDisplay(hi, fahrenheit, scaleAbsolute).toFloat()
    }

    /** Where the slider thumb sits. The stored number is not changed. */
    fun sliderThumb(value: Float, min: Float, max: Float): Float {
        if (!value.isFinite()) return min
        return value.coerceIn(min, max)
    }

    /**
     * A typed temperature. Any finite number is accepted, including decimals
     * and values outside the slider. Blank text and words are refused.
     */
    fun acceptedTemperature(text: String): Double? {
        var t = text.trim()
        if (t.endsWith('.')) t = t.dropLast(1)
        if (t.isEmpty() || t == "-" || t == "+" || t == "-." || t == "+.") return null
        val value = t.toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        return value
    }

    /** Live readout. Detection and the CSV stay in Celsius. */
    fun formatAbsolute(celsius: Float, fahrenheit: Boolean, places: Int = 1): String {
        if (!celsius.isFinite()) return "—"
        if (!fahrenheit && places <= 0) return "${celsius.toInt()}°C"
        val shown = toDisplay(celsius, fahrenheit, absolute = true)
        val number = if (places <= 0) {
            kotlin.math.round(shown).toLong().toString()
        } else {
            java.lang.String.format(java.util.Locale.US, "%.${places}f", shown)
        }
        return number + if (fahrenheit) "°F" else "°C"
    }

    fun formatRate(celsiusPerSec: Float, fahrenheit: Boolean): String {
        val shown = if (fahrenheit) toDisplay(celsiusPerSec, true, absolute = false) else celsiusPerSec.toDouble()
        val rounded = kotlin.math.round(shown * 10.0) / 10.0
        val number = if (rounded > 0) "+$rounded" else rounded.toString()
        return number + if (fahrenheit) "°F/s" else "°C/s"
    }
}

/**
 * A note when warning, alarm, and runaway are out of the usual order.
 * It does not change the saved numbers.
 */
fun temperatureOrderHint(settings: WatchSettings): String? {
    val notes = buildList {
        if (settings.warnTempC >= settings.alarmTempC) add("Warning is not below the alarm.")
        if (settings.runawayTempC <= settings.alarmTempC) add("Runaway is not above the alarm.")
        if (settings.solderWarnC >= settings.solderAlarmC) add("The solder-zone warning is not below its alarm.")
        if (settings.chargeWarnC >= settings.chargeAlarmC) add("The charge warning is not below its alarm.")
        if (settings.chargeRunawayC <= settings.chargeAlarmC) add("Charge runaway is not above its alarm.")
    }
    if (notes.isEmpty()) return null
    return notes.joinToString(" ") + " These stay as typed."
}

/**
 * The settings file stores each temperature as a JSON number and reads it
 * back as a float. This is that round trip, without the Android JSON stub.
 */
fun WatchSettings.roundTripStoredTemperatures(): WatchSettings {
    fun Float.trip(): Float = if (!isFinite()) this else toDouble().toString().toDouble().toFloat()
    return copy(
        blobDeltaC = blobDeltaC.trip(),
        toolContrastC = toolContrastC.trip(),
        toolAbsC = toolAbsC.trip(),
        toolSharpnessC = toolSharpnessC.trip(),
        coreTempC = coreTempC.trip(),
        haloCapC = haloCapC.trip(),
        selfHeatMinTempC = selfHeatMinTempC.trip(),
        selfHeatRateCPerSec = selfHeatRateCPerSec.trip(),
        warnTempC = warnTempC.trip(),
        alarmTempC = alarmTempC.trip(),
        runawayRateCPerSec = runawayRateCPerSec.trip(),
        runawayTempC = runawayTempC.trip(),
        solderWarnC = solderWarnC.trip(),
        solderAlarmC = solderAlarmC.trip(),
        solderSelfHeatRate = solderSelfHeatRate.trip(),
        minCoolingSlope = minCoolingSlope.trip(),
        chargeWarnC = chargeWarnC.trip(),
        chargeAlarmC = chargeAlarmC.trip(),
        chargeRunawayC = chargeRunawayC.trip(),
    )
}
