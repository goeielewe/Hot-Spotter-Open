package com.benchfirewatch

import com.benchfirewatch.alert.SirenGate
import com.benchfirewatch.alert.SirenTone
import com.benchfirewatch.engine.Frames
import com.benchfirewatch.engine.WatchEngine
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.WatchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SirenGateTest {
    @Test
    fun soundToggleDoesNotClearTheVisualAlarm() {
        val engine = WatchEngine(WatchSettings())
        engine.start(0)
        var last = engine.onFrame(Frames.quiet(0))
        var t = 0L
        while (t <= 4_000) {
            last = engine.onFrame(Frames.runaway(t))
            if (last.status == MonitorStatus.ALARM) break
            t += 100
        }
        assertEquals(MonitorStatus.ALARM, last.status)
        assertTrue(SirenGate.shouldSound(last.siren, soundEnabled = true))
        assertFalse(SirenGate.shouldSound(last.siren, soundEnabled = false))
        assertEquals(MonitorStatus.ALARM, last.status)
        assertTrue(last.siren)
    }

    @Test
    fun rearmedSirenRespectsTheToggle() {
        val engine = WatchEngine(WatchSettings().copy(rearmAfterAckSec = 2f))
        engine.start(0)
        var t = 0L
        var last = engine.onFrame(Frames.quiet(0))
        while (t <= 4_000) {
            last = engine.onFrame(Frames.runaway(t))
            if (last.status == MonitorStatus.ALARM) break
            t += 100
        }
        engine.acknowledge(t)
        val rearmed = engine.onFrame(Frames.runaway(t + 2_500))
        assertTrue(rearmed.siren)
        assertTrue(SirenGate.shouldSound(rearmed.siren, soundEnabled = true))
        assertFalse(SirenGate.shouldSound(rearmed.siren, soundEnabled = false))
        assertEquals(MonitorStatus.ALARM, rearmed.status)
    }

    @Test
    fun bundledToneIsLoudEnoughToLoop() {
        val pcm = SirenTone.pcm()
        assertTrue(pcm.size > SirenTone.SAMPLE_RATE)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak > 20_000)
        assertEquals(3_000L, SirenGate.TEST_MS)
    }
}
