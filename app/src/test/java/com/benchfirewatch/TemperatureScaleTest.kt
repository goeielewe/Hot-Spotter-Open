package com.benchfirewatch

import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.model.TemperatureScale
import com.benchfirewatch.model.WatchSettings
import com.benchfirewatch.model.roundTripStoredTemperatures
import com.benchfirewatch.model.sanitized
import com.benchfirewatch.model.temperatureOrderHint
import com.benchfirewatch.ui.trimNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureScaleTest {
    @Test
    fun fiftyFiveCelsiusIs131FahrenheitAndRoundTrips() {
        assertEquals(131.0, TemperatureScale.toDisplay(55f, fahrenheit = true, absolute = true), 0.0)
        assertEquals(55f, TemperatureScale.fromDisplay(131.0, fahrenheit = true, absolute = true))
        assertEquals(55.0, TemperatureScale.toDisplay(55f, fahrenheit = false, absolute = true), 0.0)
        assertEquals(55f, TemperatureScale.fromDisplay(55.0, fahrenheit = false, absolute = true))

        val shown = TemperatureScale.toDisplay(55.5f, fahrenheit = true, absolute = true)
        val back = TemperatureScale.fromDisplay(shown, fahrenheit = true, absolute = true)
        assertEquals(55.5f, back, 0.0001f)
        assertEquals("55.5", trimNumber(back))

        val typedF = TemperatureScale.fromDisplay(55.0, fahrenheit = true, absolute = true)
        val again = TemperatureScale.toDisplay(typedF, fahrenheit = true, absolute = true)
        assertEquals("55", trimNumber(again.toFloat()))
    }

    @Test
    fun differencesAndRatesScaleWithoutTheOffset() {
        assertEquals(18.0, TemperatureScale.toDisplay(10f, fahrenheit = true, absolute = false), 1e-9)
        assertEquals(10f, TemperatureScale.fromDisplay(18.0, fahrenheit = true, absolute = false))
        assertEquals(3.6, TemperatureScale.toDisplay(2f, fahrenheit = true, absolute = false), 1e-6)
        assertEquals(2f, TemperatureScale.fromDisplay(3.6, fahrenheit = true, absolute = false), 0.0001f)
        assertEquals(-0.054, TemperatureScale.toDisplay(-0.03f, fahrenheit = true, absolute = false), 1e-6)
    }

    @Test
    fun typedNumbersAreNotClampedAndWordsAreRefused() {
        assertEquals(55.0, TemperatureScale.acceptedTemperature("55")!!, 0.0)
        assertEquals(55.5, TemperatureScale.acceptedTemperature("55.5")!!, 0.0)
        assertEquals(55.0, TemperatureScale.acceptedTemperature("55.")!!, 0.0)
        assertEquals(-40.0, TemperatureScale.acceptedTemperature("-40")!!, 0.0)
        assertEquals(1500.0, TemperatureScale.acceptedTemperature("1500")!!, 0.0)
        assertEquals(12.25, TemperatureScale.acceptedTemperature("12.25")!!, 0.0)
        assertNull(TemperatureScale.acceptedTemperature(""))
        assertNull(TemperatureScale.acceptedTemperature("hot"))
        assertNull(TemperatureScale.acceptedTemperature("NaN"))
        assertNull(TemperatureScale.acceptedTemperature("Infinity"))
        assertNull(TemperatureScale.acceptedTemperature("-"))

        assertEquals(1500f, TemperatureScale.fromDisplay(1500.0, fahrenheit = false, absolute = true))
        val (min, max) = TemperatureScale.sliderRange(fahrenheit = false, absolute = true, rate = false)
        assertEquals(-40f, min)
        assertEquals(1000f, max)
        assertEquals(1000f, TemperatureScale.sliderThumb(1500f, min, max))
        assertEquals(55f, TemperatureScale.sliderThumb(55f, min, max))

        val fRange = TemperatureScale.sliderRange(fahrenheit = true, absolute = true, rate = false)
        assertEquals(-40f, fRange.first)
        assertEquals(1832f, fRange.second)
        val rate = TemperatureScale.sliderRange(fahrenheit = true, absolute = false, rate = true)
        assertEquals(-180f, rate.first)
        assertEquals(180f, rate.second)
    }

    @Test
    fun arbitraryTemperaturesSaveLoadAndAreNotRewritten() {
        val typed = WatchSettings(
            warnTempC = 90f,
            alarmTempC = 55f,
            runawayTempC = 55f,
            runawayRateCPerSec = 0f,
            blobDeltaC = 1234.5f,
            toolAbsC = -40f,
            toolContrastC = 0.5f,
            toolSharpnessC = 2500f,
            coreTempC = 1000f,
            haloCapC = -5f,
            selfHeatMinTempC = 12.25f,
            selfHeatRateCPerSec = -2.5f,
            solderWarnC = 1000f,
            solderAlarmC = 1f,
            solderSelfHeatRate = 50f,
            minCoolingSlope = -40f,
            chargeWarnC = 55.5f,
            chargeAlarmC = 55.5f,
            chargeRunawayC = -10f,
            flameSensitivity = 9,
        )
        val used = typed.roundTripStoredTemperatures().sanitized()
        assertEquals(55f, used.runawayTempC)
        assertEquals(55f, used.alarmTempC)
        assertEquals(90f, used.warnTempC)
        assertEquals(0f, used.runawayRateCPerSec)
        assertEquals(1234.5f, used.blobDeltaC)
        assertEquals(-40f, used.toolAbsC)
        assertEquals(0.5f, used.toolContrastC)
        assertEquals(2500f, used.toolSharpnessC)
        assertEquals(1000f, used.coreTempC)
        assertEquals(-5f, used.haloCapC)
        assertEquals(12.25f, used.selfHeatMinTempC)
        assertEquals(-2.5f, used.selfHeatRateCPerSec)
        assertEquals(1000f, used.solderWarnC)
        assertEquals(1f, used.solderAlarmC)
        assertEquals(50f, used.solderSelfHeatRate)
        assertEquals(-40f, used.minCoolingSlope)
        assertEquals(55.5f, used.chargeWarnC)
        assertEquals(55.5f, used.chargeAlarmC)
        assertEquals(-10f, used.chargeRunawayC)
        assertEquals(5, used.flameSensitivity)
        assertEquals(120f, WatchSettings(runawayTempC = Float.NaN).sanitized().runawayTempC)

        val before = used
        val hint = temperatureOrderHint(used)
        assertTrue(hint!!.contains("Warning is not below the alarm."))
        assertTrue(hint.contains("Runaway is not above the alarm."))
        assertTrue(hint.contains("These stay as typed."))
        assertEquals(before, used)
        assertTrue(!AppPrefs().useFahrenheit)
        assertNull(temperatureOrderHint(WatchSettings()))
    }
}
