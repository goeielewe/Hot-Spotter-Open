package com.benchfirewatch

import com.benchfirewatch.engine.Decision
import com.benchfirewatch.engine.Frames
import com.benchfirewatch.engine.WatchDisplay
import com.benchfirewatch.engine.WatchEngine
import com.benchfirewatch.engine.extractBlobs
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.StartReason
import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.model.WatchSettings
import com.benchfirewatch.model.Zone
import com.benchfirewatch.model.ZoneKind
import com.benchfirewatch.model.faultOnStart
import com.benchfirewatch.model.sanitized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchEngineTest {
    @Test
    fun smallWarmPatchesAreTrackedButNotDrawn() {
        val width = 160
        val height = 120
        val temps = FloatArray(width * height) { 25f }
        for (i in 0 until 15) {
            val x0 = 8 + (i % 5) * 28
            val y0 = 8 + (i / 5) * 30
            for (dy in 0 until 2) {
                for (dx in 0 until 5) {
                    temps[(y0 + dy) * width + (x0 + dx)] = 36f
                }
            }
        }
        val decision = WatchEngine(WatchSettings()).onFrame(ThermalFrame(1_000, width, height, temps, "face"))
        assertTrue("expected several tracks, got ${decision.tracks.size}", decision.tracks.size >= 8)
        assertTrue(decision.tracks.none { it.candidate })
    }

    @Test
    fun aCellAboveTheWarningTemperatureIsDrawn() {
        val decision = WatchEngine(WatchSettings()).onFrame(Frames.stableCell(1_000, tempC = 70.0))
        assertTrue(decision.tracks.any { it.candidate && it.bodyC >= 60f })
    }

    @Test
    fun solderingPassDoesNotAlarm() {
        val decisions = play(totalMs = 14_000, dtMs = 125, frameAt = Frames::soldering)
        assertNeverAbove(decisions, MonitorStatus.OK)
    }

    @Test
    fun spotWeldDoesNotAlarm() {
        val decisions = play(totalMs = 8_000, dtMs = 100, frameAt = Frames::spotWeld)
        assertNeverAbove(decisions, MonitorStatus.OK)
    }

    @Test
    fun coolingSolderedCellDoesNotAlarm() {
        val settings = WatchSettings().copy(solderGraceSec = 8f, coolingWatchSec = 40f)
        val decisions = play(
            totalMs = 36_000,
            dtMs = 200,
            settings = settings,
        ) { t -> Frames.solderedCell(t, peakC = 78.0, tauSec = 40.0) }
        assertNeverAbove(decisions, MonitorStatus.OK)
        val late = decisions.last()
        val cell = late.tracks.firstOrNull { !it.tool }
        if (cell != null) {
            assertTrue("still-hot cell should be cooling, rise=${cell.riseCPerSec}", (cell.riseCPerSec ?: -1f) < 0f)
        }
    }

    @Test
    fun defaultGraceCoolingCellDoesNotAlarm() {
        val decisions = play(totalMs = 30_000, dtMs = 250) { t ->
            Frames.solderedCell(t, peakC = 74.0, tauSec = 14.0)
        }
        assertNeverAbove(decisions, MonitorStatus.OK)
    }

    @Test
    fun solderedCellThatDoesNotCoolAlarmsAfterGrace() {
        val settings = WatchSettings().copy(solderGraceSec = 8f, coolingWatchSec = 30f)
        val decisions = play(totalMs = 24_000, dtMs = 200, settings = settings) { t ->
            Frames.solderedCell(t, peakC = 78.0, tauSec = null)
        }
        val alarm = decisions.firstOrNull { it.status == MonitorStatus.ALARM }
        assertNotNull("expected an alarm once the joint stopped cooling\n${dump(decisions)}", alarm)
        assertTrue(alarm!!.timeMs > 10_000)
        assertTrue(decisions.none { it.timeMs < 10_000 && it.status == MonitorStatus.ALARM })
    }

    @Test
    fun selfHeatingWarnsThenAlarms() {
        val decisions = play(totalMs = 210_000, dtMs = 250) { t -> Frames.selfHeating(t) }
        val warning = decisions.firstOrNull { it.status == MonitorStatus.WARNING }
        val alarm = decisions.firstOrNull { it.status == MonitorStatus.ALARM }
        assertNotNull("expected a warning\n${dump(decisions)}", warning)
        assertNotNull("expected an alarm after the warning\n${dump(decisions)}", alarm)
        assertTrue(warning!!.timeMs < alarm!!.timeMs)
        assertTrue("warning should not be instant: ${warning.timeMs}", warning.timeMs > 15_000)
        assertTrue(decisions.none { it.status == MonitorStatus.FAULT })
    }

    @Test
    fun runawayAlarmsWithinACoupleOfSeconds() {
        val decisions = play(totalMs = 4_000, dtMs = 100) { t -> Frames.runaway(t) }
        val alarm = decisions.firstOrNull { it.status == MonitorStatus.ALARM }
        assertNotNull("expected runaway alarm\n${dump(decisions)}", alarm)
        assertTrue("alarm at ${alarm!!.timeMs}ms\n${alarm.headline}", alarm.timeMs <= 2_500)
    }

    @Test
    fun ironHolderMaskStaysQuiet() {
        val holder = Zone("h", "Iron holder", ZoneKind.IGNORE, 0.1f, 0.05f, 0.4f, 0.45f)
        val decisions = play(totalMs = 60_000, dtMs = 250, zones = listOf(holder), frameAt = Frames::parkedIron)
        assertNeverAbove(decisions, MonitorStatus.OK)
    }

    @Test
    fun parkedIronOutsideTheHolderWarnsThenAlarms() {
        val decisions = play(totalMs = 62_000, dtMs = 250, frameAt = Frames::parkedIron)
        val warning = decisions.firstOrNull { it.status == MonitorStatus.WARNING || it.status == MonitorStatus.ALARM }
        val alarm = decisions.firstOrNull { it.status == MonitorStatus.ALARM }
        assertNotNull("expected the parked iron to be noticed\n${dump(decisions)}", warning)
        assertTrue(warning!!.timeMs > 8_000)
        assertNotNull(alarm)
        assertTrue(alarm!!.timeMs > warning.timeMs)
    }

    @Test
    fun chargeZoneIsStricterThanTheSolderZone() {
        val charge = Zone("c", "Charge rack", ZoneKind.CHARGE, 0.45f, 0.35f, 0.85f, 0.85f)
        val solder = Zone("s", "Solder mat", ZoneKind.SOLDER, 0.45f, 0.35f, 0.85f, 0.85f)
        val hot = { t: Long -> Frames.stableCell(t, tempC = 52.0, x = 100.0, y = 70.0) }
        val onCharger = play(totalMs = 20_000, dtMs = 250, zones = listOf(charge), frameAt = hot)
        val onMat = play(totalMs = 20_000, dtMs = 250, zones = listOf(solder), frameAt = hot)
        assertTrue(
            "charge zone should warn\n${dump(onCharger)}",
            onCharger.any { it.status == MonitorStatus.WARNING || it.status == MonitorStatus.ALARM },
        )
        assertNeverAbove(onMat, MonitorStatus.OK)
    }

    @Test
    fun sustainedChargeZoneHeatEscalates() {
        val charge = Zone("c", "Charge rack", ZoneKind.CHARGE, 0.45f, 0.35f, 0.85f, 0.85f)
        val decisions = play(totalMs = 40_000, dtMs = 250, zones = listOf(charge)) { t ->
            Frames.stableCell(t, tempC = 52.0, x = 100.0, y = 70.0)
        }
        val warningAt = decisions.firstOrNull { it.status == MonitorStatus.WARNING }?.timeMs
        val alarmAt = decisions.firstOrNull { it.status == MonitorStatus.ALARM }?.timeMs
        assertNotNull(warningAt)
        assertNotNull("expected escalation\n${dump(decisions)}", alarmAt)
        assertTrue(warningAt!! < alarmAt!!)
    }

    @Test
    fun cameraDisconnectFaultsAfterGrace() {
        val engine = WatchEngine(WatchSettings())
        engine.start(0)
        var last = engine.onFrame(Frames.quiet(0))
        last = engine.onFrame(Frames.quiet(500))
        assertEquals(MonitorStatus.OK, last.status)
        last = engine.onHealth(1_000, sourceLive = false, detail = "Camera disconnected")
        assertTrue(last.status == MonitorStatus.OK || last.status == MonitorStatus.SEARCHING)
        last = engine.onHealth(8_000, sourceLive = false, detail = "Camera disconnected")
        assertTrue(last.status != MonitorStatus.FAULT)
        last = engine.onHealth(14_000, sourceLive = false, detail = "Camera disconnected")
        assertEquals(MonitorStatus.FAULT, last.status)
        assertTrue(last.siren)
        assertTrue(last.events.any { it.status == MonitorStatus.FAULT })
    }

    @Test
    fun frozenFramesFault() {
        val decisions = play(totalMs = 12_000, dtMs = 250, frameAt = Frames::frozen)
        val early = decisions.filter { it.timeMs < 7_000 }
        assertTrue(early.none { it.status == MonitorStatus.FAULT })
        val fault = decisions.firstOrNull { it.status == MonitorStatus.FAULT }
        assertNotNull(fault)
        assertTrue(fault!!.headline.contains("frozen", ignoreCase = true))
    }

    @Test
    fun unconfirmedPlugStaysALoudFaultUntilItIsOff() {
        val settings = WatchSettings().copy(rearmAfterAckSec = 2f)
        val engine = WatchEngine(settings)
        engine.start(0)
        val raised = engine.raisePlugFault(1_000, "The plug did not confirm it turned off. The charger may still be on.")
        assertEquals(MonitorStatus.FAULT, raised.status)
        assertTrue(raised.siren)
        assertTrue(raised.headline.contains("did not confirm"))
        val acked = engine.acknowledge(1_000)
        assertTrue(acked.silenced)
        assertTrue(!acked.siren)
        val still = engine.onHealth(2_000, sourceLive = true, detail = "Live")
        assertTrue(still.silenced)
        assertEquals(MonitorStatus.FAULT, still.status)
        val rearmed = engine.onHealth(4_000, sourceLive = true, detail = "Live")
        assertTrue(rearmed.siren)
        assertTrue(!rearmed.silenced)
        val cleared = engine.clearPlugFault(5_000)
        assertTrue(!cleared.siren)
        assertTrue(cleared.status != MonitorStatus.FAULT)
    }

    @Test
    fun acknowledgeSilencesButRearmsIfStillAlarming() {
        val settings = WatchSettings().copy(rearmAfterAckSec = 2f)
        val engine = WatchEngine(settings)
        engine.start(0)
        var last = Decision(0, MonitorStatus.OFF, "", false, false, emptyList(), emptyList())
        var t = 0L
        while (t <= 4_000) {
            last = engine.onFrame(Frames.runaway(t))
            if (last.status == MonitorStatus.ALARM) break
            t += 100
        }
        assertEquals(MonitorStatus.ALARM, last.status)
        assertTrue(last.siren)
        val acked = engine.acknowledge(t)
        assertEquals(MonitorStatus.ALARM, acked.status)
        assertTrue(acked.silenced)
        assertTrue(!acked.siren)
        val held = engine.onFrame(Frames.runaway(t + 500))
        assertTrue(held.silenced)
        assertTrue(!held.siren)
        val rearmed = engine.onFrame(Frames.runaway(t + 2_500))
        assertEquals(MonitorStatus.ALARM, rearmed.status)
        assertTrue(!rearmed.silenced)
        assertTrue(rearmed.siren)
        assertTrue(rearmed.events.any { it.status == MonitorStatus.ALARM })
    }

    @Test
    fun cooledAlarmDoesNotRearmTheSiren() {
        val settings = WatchSettings().copy(rearmAfterAckSec = 2f)
        val engine = WatchEngine(settings)
        engine.start(0)
        var last = engine.onFrame(Frames.quiet(0))
        var t = 0L
        while (t <= 4_000) {
            last = engine.onFrame(Frames.runaway(t))
            if (last.status == MonitorStatus.ALARM) break
            t += 100
        }
        assertEquals(MonitorStatus.ALARM, last.status)
        val acked = engine.acknowledge(t)
        assertTrue(!acked.siren)
        var quiet = acked
        var q = t + 200
        while (q <= t + 4_000) {
            quiet = engine.onFrame(Frames.quiet(q))
            q += 200
        }
        assertEquals(MonitorStatus.ALARM, quiet.status)
        assertTrue(quiet.silenced)
        assertTrue(!quiet.siren)
    }

    @Test
    fun silencedCameraFaultRearmsOnTheHealthClock() {
        val settings = WatchSettings().copy(rearmAfterAckSec = 2f, reconnectGraceSec = 3f)
        val engine = WatchEngine(settings)
        engine.start(0)
        engine.onFrame(Frames.quiet(0))
        engine.onHealth(1_000, sourceLive = false, detail = "Camera disconnected")
        val fault = engine.onHealth(4_500, sourceLive = false, detail = "Camera disconnected")
        assertEquals(MonitorStatus.FAULT, fault.status)
        assertTrue(fault.siren)
        val acked = engine.acknowledge(4_500)
        assertTrue(acked.silenced)
        assertTrue(!acked.siren)
        val early = engine.onHealth(5_500, sourceLive = false, detail = "Camera disconnected")
        assertTrue(!early.siren)
        val rearmed = engine.onHealth(7_000, sourceLive = false, detail = "Camera disconnected")
        assertEquals(MonitorStatus.FAULT, rearmed.status)
        assertTrue(rearmed.siren)
        assertTrue(rearmed.events.any { it.message.contains("Still in fault") })
    }

    @Test
    fun cameraReturningDuringSilenceDoesNotRestartTheSiren() {
        val settings = WatchSettings().copy(rearmAfterAckSec = 2f, reconnectGraceSec = 3f)
        val engine = WatchEngine(settings)
        engine.start(0)
        engine.onFrame(Frames.quiet(0))
        engine.onHealth(1_000, sourceLive = false, detail = "Camera disconnected")
        val fault = engine.onHealth(4_500, sourceLive = false, detail = "Camera disconnected")
        assertEquals(MonitorStatus.FAULT, fault.status)
        engine.acknowledge(4_500)
        val back = engine.onHealth(5_000, sourceLive = true, detail = "USB camera")
        assertEquals(MonitorStatus.FAULT, back.status)
        assertTrue(!back.siren)
        val later = engine.onHealth(8_000, sourceLive = true, detail = "USB camera")
        assertTrue(later.silenced)
        assertTrue(!later.siren)
    }

    @Test
    fun killedProcessIsAFaultAndACleanStopIsNot() {
        assertNotNull(faultOnStart(StartReason.RECOVERY, monitoring = true, cleanStop = false))
        assertNotNull(faultOnStart(StartReason.BOOT, monitoring = true, cleanStop = false))
        assertNull(faultOnStart(StartReason.USER, monitoring = true, cleanStop = false))
        assertNull(faultOnStart(StartReason.RECOVERY, monitoring = true, cleanStop = true))
        assertNull(faultOnStart(StartReason.BOOT, monitoring = false, cleanStop = true))
    }

    @Test
    fun ironCoreDoesNotBecomeTheCellTemperature() {
        val settings = WatchSettings().sanitized()
        val frame = Frames.solderedCell(3_200, peakC = 60.0, tauSec = 30.0)
        val blobs = extractBlobs(frame, settings)
        assertTrue("expected a hot blob, got ${blobs.size}", blobs.isNotEmpty())
        val hottest = blobs.maxBy { it.maxC }
        assertTrue("max ${hottest.maxC} should see the iron", hottest.maxC > 200f)
        assertTrue("body ${hottest.bodyC} should be the cell, not the tip", hottest.bodyC < 110f)
    }

    @Test
    fun hotWorkKeepsAFlameAndDropsSlowHeatInsideASolderZone() {
        val solder = Zone("s", "Solder area", ZoneKind.SOLDER, 0.40f, 0.35f, 0.90f, 0.90f)
        val quiet = playHot(210_000, 250, untilMs = 500_000, zones = listOf(solder)) { t ->
            Frames.selfHeating(t)
        }
        assertTrue(
            "slow heat inside the solder zone must stay quiet\n${dump(quiet)}",
            quiet.none {
                it.status == MonitorStatus.WARNING ||
                    it.status == MonitorStatus.ALARM ||
                    it.status == MonitorStatus.FLAME ||
                    it.status == MonitorStatus.FAULT
            },
        )
        val flame = playHot(3_000, 100, untilMs = 500_000, zones = listOf(solder), frameAt = Frames::lighter)
        assertTrue("flame must still alarm during hot work\n${dump(flame)}", flame.any { it.status == MonitorStatus.FLAME })
    }

    @Test
    fun hotWorkDoesNotHideHeatOutsideTheWorkZone() {
        val decisions = playHot(210_000, 250, untilMs = 500_000) { t -> Frames.selfHeating(t) }
        assertTrue(decisions.any { it.status == MonitorStatus.WARNING || it.status == MonitorStatus.ALARM })
        val charge = Zone("c", "Charge rack", ZoneKind.CHARGE, 0.40f, 0.35f, 0.90f, 0.90f)
        val onRack = playHot(20_000, 250, untilMs = 500_000, zones = listOf(charge)) { t ->
            Frames.stableCell(t, tempC = 52.0, x = 100.0, y = 70.0)
        }
        assertTrue(onRack.any { it.status == MonitorStatus.WARNING || it.status == MonitorStatus.ALARM })
    }

    @Test
    fun hotWorkStillAlarmsOnRunawayInsideASolderZone() {
        val solder = Zone("s", "Solder area", ZoneKind.SOLDER, 0.40f, 0.35f, 0.90f, 0.90f)
        val decisions = playHot(4_000, 100, untilMs = 500_000, zones = listOf(solder)) { t -> Frames.runaway(t) }
        val alarm = decisions.firstOrNull { it.status == MonitorStatus.ALARM }
        assertNotNull("runaway must still alarm\n${dump(decisions)}", alarm)
        assertTrue(alarm!!.timeMs <= 2_500)
    }

    @Test
    fun hotWorkExpiresAndDoesNotResume() {
        val solder = Zone("s", "Solder area", ZoneKind.SOLDER, 0.40f, 0.35f, 0.90f, 0.90f)
        val decisions = playHot(210_000, 250, untilMs = 90_000, zones = listOf(solder)) { t ->
            Frames.selfHeating(t)
        }
        assertTrue(decisions.none { it.timeMs < 90_000 && (it.status == MonitorStatus.WARNING || it.status == MonitorStatus.ALARM) })
        assertTrue(
            "slow heat must alarm again after the timer\n${dump(decisions)}",
            decisions.any { it.timeMs >= 90_000 && (it.status == MonitorStatus.WARNING || it.status == MonitorStatus.ALARM) },
        )
        val engine = WatchEngine(WatchSettings(), listOf(solder))
        engine.start(0)
        engine.setHotWorkUntil(10_000)
        val mid = engine.onFrame(Frames.quiet(4_000))
        assertEquals(10_000L, mid.hotWorkUntilMs)
        val done = engine.onFrame(Frames.quiet(10_000))
        assertNull(done.hotWorkUntilMs)
        val later = engine.onFrame(Frames.quiet(12_000))
        assertNull(later.hotWorkUntilMs)
        engine.setHotWorkUntil(50_000)
        engine.start(60_000)
        assertNull(engine.hotWorkUntil())
        assertNull(engine.onFrame(Frames.quiet(61_000)).hotWorkUntilMs)
    }

    @Test
    fun hotWorkDoesNotHideACameraBlindFault() {
        val decisions = playHot(12_000, 250, untilMs = 500_000, frameAt = Frames::frozen)
        assertTrue(decisions.any { it.status == MonitorStatus.FAULT })
    }

    @Test
    fun freshFrameAgeIgnoresAFrozenDuplicate() {
        val engine = WatchEngine(WatchSettings())
        val first = Frames.quiet(1_000)
        val opened = engine.onFrame(first)
        assertEquals(1_000L, opened.lastFreshFrameMs)
        val dup = ThermalFrame(4_000, first.width, first.height, first.celsius, "dup")
        val frozen = engine.onFrame(dup)
        assertEquals(1_000L, frozen.lastFreshFrameMs)
        val moved = engine.onFrame(Frames.quiet(5_000))
        assertEquals(5_000L, moved.lastFreshFrameMs)
        assertEquals(0L, WatchDisplay.heartbeatAgeMs(1_000, 1_000)!!)
        assertEquals("Watching · updated 0.1 s ago", WatchDisplay.heartbeatLabel(100))
        assertTrue(WatchDisplay.heartbeatAmber(2_000))
        assertTrue(WatchDisplay.heartbeatBlind(8_000, 8f))
        assertEquals("Hot-work mode · 12:34 left · flame watch still on", WatchDisplay.hotWorkLabel(12 * 60_000L + 34_000L))
    }

    @Test
    fun runawayAt55CelsiusAlarmsAndFlameStaysOn() {
        val settings = WatchSettings(
            runawayTempC = 55f,
            warnTempC = 400f,
            alarmTempC = 400f,
            runawayRateCPerSec = 1000f,
            selfHeatRateCPerSec = 1000f,
            selfHeatMinTempC = 400f,
            blobDeltaC = 28f,
        ).sanitized()
        assertEquals(55f, settings.runawayTempC)
        val hot = play(2_000, 200, settings) { t -> Frames.stableCell(t, tempC = 62.0) }
        assertTrue("a cell at 62°C must alarm when runaway is 55°C\n${dump(hot)}", hot.any { it.status == MonitorStatus.ALARM })
        assertTrue(hot.none { it.status == MonitorStatus.FLAME })
        val cool = play(2_000, 200, settings) { t -> Frames.stableCell(t, tempC = 40.0) }
        assertTrue(
            "a 40°C cell must stay quiet\n${dump(cool)}",
            cool.none { it.status == MonitorStatus.ALARM || it.status == MonitorStatus.WARNING || it.status == MonitorStatus.FLAME },
        )
        val flame = play(3_000, 100, settings, frameAt = Frames::lighter)
        assertTrue("flame detection stays on\n${dump(flame)}", flame.any { it.status == MonitorStatus.FLAME })
    }
}

private fun playHot(
    totalMs: Long,
    dtMs: Long,
    untilMs: Long?,
    settings: WatchSettings = WatchSettings(),
    zones: List<Zone> = emptyList(),
    frameAt: (Long) -> ThermalFrame,
): List<Decision> {
    val engine = WatchEngine(settings, zones)
    engine.start(0)
    engine.setHotWorkUntil(untilMs)
    val out = ArrayList<Decision>()
    var t = 0L
    while (t <= totalMs) {
        out += engine.onFrame(frameAt(t))
        t += dtMs
    }
    return out
}

private fun play(
    totalMs: Long,
    dtMs: Long,
    settings: WatchSettings = WatchSettings(),
    zones: List<Zone> = emptyList(),
    frameAt: (Long) -> ThermalFrame,
): List<Decision> {
    val engine = WatchEngine(settings, zones)
    engine.start(0)
    val out = ArrayList<Decision>()
    var t = 0L
    while (t <= totalMs) {
        out += engine.onFrame(frameAt(t))
        t += dtMs
    }
    return out
}

private fun assertNeverAbove(decisions: List<Decision>, allowed: MonitorStatus) {
    val rank = listOf(
        MonitorStatus.OFF,
        MonitorStatus.SEARCHING,
        MonitorStatus.OK,
        MonitorStatus.WARNING,
        MonitorStatus.ALARM,
        MonitorStatus.FLAME,
        MonitorStatus.FAULT,
    )
    val limit = rank.indexOf(allowed)
    val bad = decisions.firstOrNull { rank.indexOf(it.status) > limit }
    if (bad != null) {
        throw AssertionError("status ${bad.status} at ${bad.timeMs}ms: ${bad.headline}\n${dump(decisions)}")
    }
}

private fun dump(decisions: List<Decision>): String {
    val changes = decisions.filterIndexed { i, d -> i == 0 || d.status != decisions[i - 1].status || d.events.isNotEmpty() }
    return changes.take(12).joinToString("\n") { d ->
        val tracks = d.tracks.joinToString { t ->
            "id=${t.id} tool=${t.tool} body=${t.bodyC.toInt()} max=${t.maxC.toInt()} area=${"%.4f".format(t.areaFraction)} rise=${t.riseCPerSec} grace=${t.inGrace}"
        }
        "t=${d.timeMs} ${d.status} ${d.headline} [$tracks]"
    }
}
