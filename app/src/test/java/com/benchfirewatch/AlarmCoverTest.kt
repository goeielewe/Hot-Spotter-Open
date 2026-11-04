package com.benchfirewatch

import com.benchfirewatch.alert.AlarmCover
import com.benchfirewatch.engine.Frames
import com.benchfirewatch.engine.WatchEngine
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.WatchSettings
import com.benchfirewatch.model.Zone
import com.benchfirewatch.model.ZoneKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full-screen cover follows silence, not the status alone.
 * A USB search stays a banner until the startup timeout.
 */
class AlarmCoverTest {
    private val searching = "Looking for a USB camera. Accept the permission prompt."

    @Test
    fun acknowledgeClosesTheOverlayForEachState() {
        assertTrue(AlarmCover.visible(MonitorStatus.FLAME, silenced = false))
        assertTrue(AlarmCover.visible(MonitorStatus.ALARM, silenced = false))
        assertTrue(AlarmCover.visible(MonitorStatus.FAULT, silenced = false))
        assertFalse(AlarmCover.visible(MonitorStatus.WARNING, silenced = false))
        for (status in listOf(MonitorStatus.FLAME, MonitorStatus.ALARM, MonitorStatus.FAULT, MonitorStatus.WARNING)) {
            assertFalse(AlarmCover.visible(status, silenced = true))
            assertFalse(AlarmCover.fullScreenIntent(status, silenced = true))
        }

        val flame = until(WatchEngine(WatchSettings())) { t, engine ->
            engine.onFrame(Frames.cluster(t, 80, 60, count = 4, celsius = 109f))
        }
        assertEquals(MonitorStatus.FLAME, flame.decision.status)
        assertTrue(AlarmCover.visible(flame.decision.status, flame.decision.silenced))
        val flameAck = flame.engine.acknowledge(flame.time)
        assertEquals(MonitorStatus.FLAME, flameAck.status)
        assertFalse(AlarmCover.visible(flameAck.status, flameAck.silenced))
        val flameHeld = flame.engine.onFrame(Frames.cluster(flame.time + 200, 80, 60, count = 4, celsius = 109f))
        assertEquals(MonitorStatus.FLAME, flameHeld.status)
        assertFalse(AlarmCover.visible(flameHeld.status, flameHeld.silenced))

        val alarm = until(WatchEngine(WatchSettings()), want = MonitorStatus.ALARM) { t, engine ->
            engine.onFrame(Frames.runaway(t))
        }
        assertEquals(MonitorStatus.ALARM, alarm.decision.status)
        assertTrue(AlarmCover.visible(alarm.decision.status, alarm.decision.silenced))
        val alarmAck = alarm.engine.acknowledge(alarm.time)
        assertEquals(MonitorStatus.ALARM, alarmAck.status)
        assertFalse(AlarmCover.visible(alarmAck.status, alarmAck.silenced))
        val alarmHeld = alarm.engine.onFrame(Frames.runaway(alarm.time + 200))
        assertFalse(AlarmCover.visible(alarmHeld.status, alarmHeld.silenced))

        val fault = streamingFault()
        assertEquals(MonitorStatus.FAULT, fault.decision.status)
        assertTrue(AlarmCover.visible(fault.decision.status, fault.decision.silenced))
        val faultAck = fault.engine.acknowledge(fault.time)
        assertEquals(MonitorStatus.FAULT, faultAck.status)
        assertFalse(AlarmCover.visible(faultAck.status, faultAck.silenced))
        val faultHeld = fault.engine.onHealth(fault.time + 500, sourceLive = false, detail = "Camera disconnected")
        assertEquals(MonitorStatus.FAULT, faultHeld.status)
        assertFalse("same fault must stay dismissed", AlarmCover.visible(faultHeld.status, faultHeld.silenced))

        val warning = until(
            WatchEngine(
                WatchSettings(),
                listOf(Zone("c", "Charge rack", ZoneKind.CHARGE, 0.2f, 0.2f, 0.9f, 0.9f)),
            ),
            want = MonitorStatus.WARNING,
        ) { t, engine ->
            engine.onFrame(Frames.stableCell(t, tempC = 52.0, x = 100.0, y = 70.0))
        }
        assertEquals(MonitorStatus.WARNING, warning.decision.status)
        assertFalse(AlarmCover.visible(warning.decision.status, warning.decision.silenced))
        val warningAck = warning.engine.acknowledge(warning.time)
        assertFalse(AlarmCover.visible(warningAck.status, warningAck.silenced))
    }

    @Test
    fun sameFaultDoesNotReopenTheOverlayUntilRearm() {
        val engine = WatchEngine(WatchSettings().copy(rearmAfterAckSec = 2f, reconnectGraceSec = 3f))
        engine.start(0)
        engine.onFrame(Frames.quiet(0))
        engine.onHealth(1_000, sourceLive = false, detail = "Camera disconnected")
        val fault = engine.onHealth(4_500, sourceLive = false, detail = "Camera disconnected")
        assertTrue(AlarmCover.visible(fault.status, fault.silenced))
        val acked = engine.acknowledge(4_500)
        assertFalse(AlarmCover.visible(acked.status, acked.silenced))
        val held = engine.onHealth(5_500, sourceLive = false, detail = "Camera disconnected")
        assertEquals(MonitorStatus.FAULT, held.status)
        assertFalse(AlarmCover.visible(held.status, held.silenced))
        val rearmed = engine.onHealth(7_000, sourceLive = false, detail = "Camera disconnected")
        assertTrue(AlarmCover.visible(rearmed.status, rearmed.silenced))
        assertTrue(rearmed.events.any { it.message.contains("Still in fault") })
    }

    @Test
    fun stopWatchClearsTheAlarmAndClosesTheOverlay() {
        val fault = streamingFault()
        assertTrue(AlarmCover.visible(fault.decision.status, fault.decision.silenced))
        val stopped = fault.engine.stop(fault.time + 100)
        assertEquals(MonitorStatus.OFF, stopped.status)
        assertFalse(stopped.silenced)
        assertFalse(stopped.siren)
        assertFalse(AlarmCover.visible(stopped.status, stopped.silenced))
        val later = fault.engine.onHealth(fault.time + 20_000, sourceLive = false, detail = searching)
        assertEquals(MonitorStatus.OFF, later.status)
        assertFalse(AlarmCover.visible(later.status, later.silenced))
    }

    @Test
    fun startupSearchStaysABannerUntilTheTimeout() {
        val engine = WatchEngine(WatchSettings())
        engine.start(0)
        val early = engine.onHealth(1_000, sourceLive = false, detail = searching)
        assertEquals(MonitorStatus.SEARCHING, early.status)
        assertFalse(AlarmCover.visible(early.status, early.silenced))
        assertFalse(AlarmCover.fullScreenIntent(early.status, early.silenced))
        val mid = engine.onHealth(29_000, sourceLive = false, detail = searching)
        assertEquals(MonitorStatus.SEARCHING, mid.status)
        assertFalse(AlarmCover.visible(mid.status, mid.silenced))
        val fault = engine.onHealth(30_000, sourceLive = false, detail = searching)
        assertEquals(MonitorStatus.FAULT, fault.status)
        assertTrue(AlarmCover.visible(fault.status, fault.silenced))
        val acked = engine.acknowledge(30_000)
        assertEquals(MonitorStatus.FAULT, acked.status)
        assertFalse(AlarmCover.visible(acked.status, acked.silenced))
        val again = engine.onHealth(40_000, sourceLive = false, detail = searching)
        assertEquals(MonitorStatus.FAULT, again.status)
        assertFalse(AlarmCover.visible(again.status, again.silenced))
        assertFalse(AlarmCover.fullScreenIntent(again.status, again.silenced))
    }

    @Test
    fun aRepeatedFrameDoesNotRepostTheSameNotification() {
        val open = AlarmCover.notificationKey(MonitorStatus.FAULT, searching, silenced = false)
        val held = AlarmCover.notificationKey(MonitorStatus.FAULT, searching, silenced = false)
        val acked = AlarmCover.notificationKey(MonitorStatus.FAULT, "Silenced — $searching", silenced = true)
        assertEquals(open, held)
        assertTrue(open != acked)
        assertTrue(AlarmCover.fullScreenIntent(MonitorStatus.FAULT, silenced = false))
        assertFalse(AlarmCover.fullScreenIntent(MonitorStatus.FAULT, silenced = true))
        assertFalse(AlarmCover.fullScreenIntent(MonitorStatus.SEARCHING, silenced = false))
        assertFalse(AlarmCover.fullScreenIntent(MonitorStatus.WARNING, silenced = false))
    }

    private fun streamingFault(): Held {
        val engine = WatchEngine(WatchSettings().copy(reconnectGraceSec = 3f))
        engine.start(0)
        engine.onFrame(Frames.quiet(0))
        engine.onHealth(1_000, sourceLive = false, detail = "Camera disconnected")
        val fault = engine.onHealth(4_500, sourceLive = false, detail = "Camera disconnected")
        return Held(engine, 4_500, fault)
    }

    private fun until(
        engine: WatchEngine,
        want: MonitorStatus = MonitorStatus.FLAME,
        stepMs: Long = 100,
        limitMs: Long = 25_000,
        frame: (Long, WatchEngine) -> com.benchfirewatch.engine.Decision,
    ): Held {
        engine.start(0)
        var last = engine.onFrame(Frames.quiet(0))
        var t = stepMs
        while (t <= limitMs && last.status != want) {
            last = frame(t, engine)
            if (last.status == want) break
            t += stepMs
        }
        return Held(engine, t, last)
    }

    private data class Held(
        val engine: WatchEngine,
        val time: Long,
        val decision: com.benchfirewatch.engine.Decision,
    )
}
