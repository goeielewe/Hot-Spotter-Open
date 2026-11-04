package com.benchfirewatch

import com.benchfirewatch.source.CameraBattery
import com.benchfirewatch.source.CameraBatteryWatch
import com.benchfirewatch.source.ChargeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraBatteryTest {
    @Test
    fun firstReadingAlreadyLowWarnsOnce() {
        assertEquals(listOf(20), CameraBatteryWatch.notices(null, 18).map { it.level })
        assertEquals(listOf(20, 10), CameraBatteryWatch.notices(null, 5).map { it.level })
    }

    @Test
    fun jumpAcrossBothThresholdsEmitsBoth() {
        assertEquals(listOf(20, 10), CameraBatteryWatch.notices(25, 8).map { it.level })
    }

    @Test
    fun alreadyUnderTwentyOnlyWarnsAtTen() {
        assertEquals(listOf(10), CameraBatteryWatch.notices(15, 8).map { it.level })
    }

    @Test
    fun stayingLowDoesNotRepeat() {
        assertTrue(CameraBatteryWatch.notices(15, 12).isEmpty())
        assertTrue(CameraBatteryWatch.notices(8, 9).isEmpty())
        assertTrue(CameraBatteryWatch.notices(8, 8).isEmpty())
    }

    @Test
    fun recoveryAboveTwentyRearms() {
        assertTrue(CameraBatteryWatch.notices(8, 25).isEmpty())
        assertEquals(listOf(20), CameraBatteryWatch.notices(25, 19).map { it.level })
        assertEquals(listOf(20, 10), CameraBatteryWatch.notices(25, 9).map { it.level })
    }

    @Test
    fun boundariesAreStrictlyBelow() {
        assertTrue(CameraBatteryWatch.notices(30, 20).isEmpty())
        assertEquals(listOf(20), CameraBatteryWatch.notices(20, 19).map { it.level })
        assertTrue(CameraBatteryWatch.notices(15, 10).isEmpty())
        assertEquals(listOf(10), CameraBatteryWatch.notices(10, 9).map { it.level })
        assertEquals(0, CameraBatteryWatch.warnLevel(20))
        assertEquals(20, CameraBatteryWatch.warnLevel(19))
        assertEquals(10, CameraBatteryWatch.warnLevel(9))
        assertEquals(0, CameraBatteryWatch.warnLevel(null))
        assertEquals(0, CameraBatteryWatch.warnLevel(-1))
    }

    @Test
    fun unavailableReadingDoesNotRefireOrRearm() {
        assertTrue(CameraBatteryWatch.notices(18, null).isEmpty())
        assertTrue(CameraBatteryWatch.notices(18, -1).isEmpty())
        assertEquals(18, CameraBatteryWatch.remember(18, null))
        assertEquals(18, CameraBatteryWatch.remember(18, -3))
        val kept = CameraBatteryWatch.remember(18, null)
        assertTrue(CameraBatteryWatch.notices(kept, 17).isEmpty())
        assertEquals(17, CameraBatteryWatch.remember(kept, 17))
    }

    @Test
    fun lines() {
        assertEquals("Camera — demo", CameraBattery(placeholder = true).line())
        assertEquals("Camera battery unavailable", CameraBattery().line())
        assertEquals("Camera battery unavailable", CameraBattery(percent = -1).line())
        assertEquals("Camera 64% · charging", CameraBattery(64, ChargeKind.CHARGING).line())
        assertEquals("Camera 64% · not charging", CameraBattery(64, ChargeKind.NOT_CHARGING).line())
        assertEquals(
            "Camera 12% · not charging, powering the phone",
            CameraBattery(12, ChargeKind.POWERING_PHONE).line(),
        )
        assertEquals("Camera 4% · charge fault", CameraBattery(4, ChargeKind.FAULT).line())
        assertEquals("Camera 40%", CameraBattery(40, ChargeKind.UNKNOWN).line())
    }
}
