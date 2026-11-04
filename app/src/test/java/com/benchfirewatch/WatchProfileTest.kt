package com.benchfirewatch

import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.model.WatchProfile
import com.benchfirewatch.model.WatchSettings
import com.benchfirewatch.model.sanitized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProfileTest {
    @Test
    fun eachProfileKeepsFlameOnAndUsesItsTemperatures() {
        val expected = mapOf(
            WatchProfile.bench to 120f,
            WatchProfile.charging to 80f,
            WatchProfile.printer to 340f,
            WatchProfile.electrical to 100f,
            WatchProfile.general to 180f,
        )
        expected.forEach { (profile, runaway) ->
            val used = profile.settings!!.sanitized()
            assertEquals(profile.title, runaway, used.runawayTempC)
            assertEquals(4, used.flameSensitivity)
            assertTrue(used.flameWholeFrame)
        }
        assertEquals(45f, WatchProfile.charging.settings!!.sanitized().warnTempC)
        assertEquals(60f, WatchProfile.charging.settings!!.sanitized().alarmTempC)
        assertEquals(130f, WatchProfile.printer.settings!!.sanitized().warnTempC)
        assertEquals(50f, WatchProfile.electrical.settings!!.sanitized().warnTempC)
        assertEquals(80f, WatchProfile.general.settings!!.sanitized().warnTempC)
    }

    @Test
    fun pickingAProfileFillsSettingsAndCustomDoesNot() {
        val edited = AppPrefs(settings = WatchSettings(runawayTempC = 55f), watchProfileId = WatchProfile.CUSTOM)
        val charged = WatchProfile.charging.applyTo(edited)
        assertEquals(80f, charged.settings.runawayTempC)
        assertEquals(WatchProfile.CHARGING, charged.watchProfileId)
        val again = WatchProfile.custom.applyTo(charged)
        assertEquals(80f, again.settings.runawayTempC)
        assertEquals(WatchProfile.CUSTOM, again.watchProfileId)
        assertEquals(WatchProfile.bench, WatchProfile.matching(WatchSettings()))
        assertEquals(WatchProfile.custom, WatchProfile.matching(WatchSettings(runawayTempC = 55f)))
    }
}
