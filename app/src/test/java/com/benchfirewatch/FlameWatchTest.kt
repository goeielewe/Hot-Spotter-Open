package com.benchfirewatch

import com.benchfirewatch.alert.SirenGate
import com.benchfirewatch.engine.Decision
import com.benchfirewatch.engine.FlameWatch
import com.benchfirewatch.engine.Frames
import com.benchfirewatch.engine.WatchEngine
import com.benchfirewatch.engine.extractBlobs
import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.model.WatchSettings
import com.benchfirewatch.model.Zone
import com.benchfirewatch.model.ZoneKind
import com.benchfirewatch.model.sanitized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic frames only. The phone was not used. Detection runs through
 * [WatchEngine.onFrame], which is what the foreground service calls. The UI
 * never classifies a frame.
 */
class FlameWatchTest {
    @Test
    fun selfCheckPassesAndTheStatusLineSaysSo() {
        assertTrue(FlameWatch.selfCheck())
        val engine = WatchEngine(WatchSettings())
        engine.start(0)
        val decision = engine.onFrame(Frames.quiet(200))
        assertEquals(MonitorStatus.OK, decision.status)
        assertTrue(decision.flameNote.contains("Flame watch on"))
        assertTrue(decision.flameNote.contains("whole frame"))
        assertTrue(decision.flameNote.contains("sensitivity 4"))
    }

    @Test
    fun sensitivityDefaultsBiasTowardCatchingTheLighter() {
        val settings = WatchSettings()
        assertEquals(4, settings.flameSensitivity)
        assertTrue(settings.flameWholeFrame)
        assertEquals(5, settings.copy(flameSensitivity = 9).sanitized().flameSensitivity)
        assertEquals(1, settings.copy(flameSensitivity = 0).sanitized().flameSensitivity)
        assertFalse(AppPrefs().showStats)
    }

    @Test
    fun blobTrackerDropsTheLighterTheFlameWatchCatches() {
        val frame = Frames.cluster(0, x = 80, y = 60, count = 3, celsius = 109.3f)
        val blobs = extractBlobs(frame, WatchSettings())
        assertTrue("a 3 pixel lighter must not become a blob", blobs.isEmpty())
        assertFlamesBy(1_000) { t -> Frames.cluster(t, x = 80, y = 60, count = 3, celsius = 109.3f) }
    }

    @Test
    fun steadyLighterAtDefaultSensitivityAlarmsWithinOneSecond() {
        assertFlamesBy(1_000) { t -> Frames.cluster(t, 88, 70, count = 4, celsius = 109f) }
    }

    @Test
    fun lowestSensitivityStillCatchesABenchDistanceLighter() {
        val settings = WatchSettings(flameSensitivity = 1)
        assertFlamesBy(1_000, settings) { t -> Frames.cluster(t, 80, 60, count = 4, celsius = 109f) }
    }

    @Test
    fun singlePixelFlickerAlarmsWithinOneSecond() {
        assertFlamesBy(1_000) { t ->
            Frames.flickering(t, x = 50, y = 40, count = 1, centerC = 112f, ampC = 16f)
        }
    }

    @Test
    fun twoPixelFlameOnEachEdgeAlarms() {
        val spots = listOf(0 to 0, 159 to 0, 0 to 119, 159 to 119, 0 to 60, 159 to 60, 80 to 0, 80 to 119)
        for ((x, y) in spots) {
            assertFlamesBy(1_000, label = "edge $x,$y") { t ->
                Frames.cluster(t, x, y, count = 2, celsius = 112f)
            }
        }
    }

    @Test
    fun fourPixelSteadyFlameAlarms() {
        assertFlamesBy(1_000) { t -> Frames.cluster(t, 20, 20, count = 4, celsius = 115f) }
    }

    @Test
    fun flickeringPlumeBetween100And150AlarmsWithinOneSecond() {
        assertFlamesBy(1_000) { t ->
            Frames.flickering(t, count = 4, centerC = 125f, ampC = 25f)
        }
    }

    @Test
    fun lowReadingThinFlameFrom80To150Alarms() {
        assertFlamesBy(1_000) { t ->
            Frames.flickering(t, x = 90, y = 40, count = 3, centerC = 115f, ampC = 35f)
        }
        assertFlamesBy(1_000, label = "steady 85C") { t ->
            Frames.cluster(t, 90, 80, count = 3, celsius = 85f)
        }
    }

    @Test
    fun flickerBelowTheLargeJumpStillAlarms() {
        // Peak delta stays under the default large-delta line, so this has to be the flicker path.
        assertFlamesBy(1_000) { t ->
            Frames.flickering(t, x = 70, y = 55, count = 2, centerC = 46f, ampC = 10f)
        }
    }

    @Test
    fun partialSaturationAlarms() {
        assertFlamesBy(1_000) { t ->
            val base = Frames.cluster(t, 70, 50, count = 2, celsius = 120f, rangeMaxC = 120f)
            val temps = base.celsius.copyOf()
            temps[50 * Frames.WIDTH + 74] = 100f
            temps[51 * Frames.WIDTH + 74] = 100f
            ThermalFrame(t, Frames.WIDTH, Frames.HEIGHT, temps, "saturated", rangeMaxC = 120f)
        }
    }

    @Test
    fun clippedCeilingAlarmsAndAnUnclippedIronDoesNot() {
        assertFlamesBy(1_000, label = "clipped") { t ->
            val iron = Frames.parkedIron(t)
            val clipped = FloatArray(iron.celsius.size) { index -> iron.celsius[index].coerceAtMost(120f) }
            ThermalFrame(t, iron.width, iron.height, clipped, "clipped-iron", rangeMaxC = 120f)
        }
        val steady = play(8_000, 250) { t ->
            val iron = Frames.parkedIron(t)
            ThermalFrame(t, iron.width, iron.height, iron.celsius, "iron", rangeMaxC = 400f)
        }
        assertTrue(steady.none { it.status == MonitorStatus.FLAME })
    }

    @Test
    fun flameBesideAHotIronAlarmsAndTheIronAloneDoesNot() {
        assertFlamesBy(1_000) { Frames.flameBesideIron(it) }
        val iron = play(8_000, 250, frameAt = Frames::parkedIron)
        assertTrue("a steady iron must not be called a flame\n${iron.last().headline}", iron.none { it.status == MonitorStatus.FLAME })
    }

    @Test
    fun flameOnAnAlreadyWarmBenchAlarms() {
        assertFlamesBy(1_000) { t ->
            Frames.cluster(t, 60, 60, count = 3, celsius = 115f, background = 45f)
        }
    }

    @Test
    fun wideHotAreaAlarms() {
        assertFlamesBy(1_000) { t ->
            val temps = FloatArray(Frames.WIDTH * Frames.HEIGHT) { Frames.BACKGROUND.toFloat() }
            for (y in 20 until 60) {
                for (x in 20 until 60) temps[y * Frames.WIDTH + x] = 220f
            }
            ThermalFrame(t, Frames.WIDTH, Frames.HEIGHT, temps, "wide")
        }
    }

    @Test
    fun fastRiseOnAFewPixelsAlarms() {
        assertFlamesBy(1_000) { t ->
            val temp = 32f + 40f * (t / 800f).coerceAtMost(1f)
            Frames.cluster(t, 100, 40, count = 3, celsius = temp)
        }
    }

    @Test
    fun intermittentDropsStillAlarmWithinOneSecond() {
        val engine = WatchEngine(WatchSettings())
        engine.start(0)
        var last = engine.onFrame(Frames.quiet(0))
        var t = 0L
        var i = 0
        while (t <= 1_000) {
            if (i % 3 != 2) {
                last = engine.onFrame(Frames.flickering(t, count = 3, centerC = 120f, ampC = 18f))
            }
            if (last.status == MonitorStatus.FLAME) break
            i++
            t += 115
        }
        assertEquals(MonitorStatus.FLAME, last.status)
        assertTrue("alarm at ${last.timeMs}", last.timeMs <= 1_000)
    }

    @Test
    fun everyRotationStillAlarms() {
        for (degrees in intArrayOf(0, 90, 180, 270)) {
            assertFlamesBy(1_000, label = "rotation $degrees") { t ->
                Frames.rotate(Frames.cluster(t, 4, 6, count = 3, celsius = 118f), degrees)
            }
        }
    }

    @Test
    fun everyPictureModeStillAlarms() {
        for (mode in listOf("MSX", "Thermal", "Visual", "PiP", "Blend")) {
            assertFlamesBy(1_000, label = mode) { t ->
                Frames.withPreview(Frames.cluster(t, 77, 66, count = 3, celsius = 116f), mode)
            }
        }
    }

    @Test
    fun duplicateFfcFrameDoesNotHideTheFlame() {
        val engine = WatchEngine(WatchSettings())
        engine.start(0)
        val first = Frames.cluster(0, 30, 90, count = 3, celsius = 111f)
        var last = engine.onFrame(first)
        last = engine.onFrame(ThermalFrame(120, first.width, first.height, first.celsius.copyOf(), "ffc"))
        assertEquals(MonitorStatus.FLAME, last.status)
        assertTrue(last.timeMs <= 1_000)
        assertTrue(last.siren)
    }

    @Test
    fun flatShutterCoastsAFlameCandidate() {
        val engine = WatchEngine(WatchSettings())
        engine.start(0)
        var last = engine.onFrame(Frames.cluster(0, 44, 44, count = 3, celsius = 113f))
        assertTrue(last.status != MonitorStatus.FLAME)
        last = engine.onFrame(Frames.shutter(160))
        last = engine.onFrame(Frames.shutter(320))
        last = engine.onFrame(Frames.shutter(480))
        last = engine.onFrame(Frames.cluster(640, 44, 44, count = 3, celsius = 113f))
        assertEquals(MonitorStatus.FLAME, last.status)
        assertTrue("shutter delayed the flame to ${last.timeMs}", last.timeMs <= 1_000)
    }

    @Test
    fun frozenFlameCannotStayQuietAndFrozenQuietStillFaults() {
        val engine = WatchEngine(WatchSettings())
        engine.start(0)
        val hot = Frames.cluster(0, 12, 12, count = 4, celsius = 109f)
        var last = engine.onFrame(hot)
        var sawFault = false
        var t = 115L
        while (t <= 10_000) {
            last = engine.onFrame(ThermalFrame(t, hot.width, hot.height, hot.celsius.copyOf(), "frozen-flame"))
            if (last.events.any { it.status == MonitorStatus.FAULT }) sawFault = true
            t += 250
        }
        assertEquals(MonitorStatus.FLAME, last.status)
        assertTrue(sawFault)
        val quiet = play(12_000, 250, frameAt = Frames::frozen)
        val fault = quiet.firstOrNull { it.status == MonitorStatus.FAULT }
        assertNotNull(fault)
        assertTrue(fault!!.headline.contains("frozen", ignoreCase = true) || fault.headline.contains("blind", ignoreCase = true))
        assertTrue(quiet.none { it.status == MonitorStatus.FLAME })
    }

    @Test
    fun wholeFrameCatchesAFlameInsideAnIgnoreZone() {
        val holder = Zone("h", "Iron holder", ZoneKind.IGNORE, 0.2f, 0.2f, 0.6f, 0.6f)
        assertFlamesBy(1_000, zones = listOf(holder)) { t ->
            Frames.cluster(t, 64, 48, count = 3, celsius = 114f)
        }
    }

    @Test
    fun drawnZonesOnlySkipsAFlameOutsideThem() {
        val settings = WatchSettings(flameWholeFrame = false)
        val zone = Zone("z", "Mat", ZoneKind.SOLDER, 0.05f, 0.05f, 0.25f, 0.3f)
        val outside = play(2_000, 115, settings, listOf(zone)) { t ->
            Frames.cluster(t, 140, 100, count = 3, celsius = 130f)
        }
        assertTrue(outside.none { it.status == MonitorStatus.FLAME })
        assertFlamesBy(1_000, settings, listOf(zone), "inside zone") { t ->
            Frames.cluster(t, 16, 16, count = 3, celsius = 114f)
        }
    }

    @Test
    fun coldShinyStripDoesNotAlarm() {
        val decisions = play(3_000, 125) { t ->
            Frames.cluster(t, 24, 80, count = 6, celsius = 32f)
        }
        assertTrue(decisions.none { it.status == MonitorStatus.FLAME || it.status == MonitorStatus.ALARM })
    }

    @Test
    fun mutedSirenStillShowsFlame() {
        val engine = WatchEngine(WatchSettings())
        engine.start(0)
        var last = engine.onFrame(Frames.cluster(0, 50, 50, count = 3, celsius = 120f))
        last = engine.onFrame(Frames.cluster(120, 50, 50, count = 3, celsius = 120f))
        assertEquals(MonitorStatus.FLAME, last.status)
        assertTrue(last.siren)
        assertTrue(SirenGate.shouldSound(last.siren, soundEnabled = true))
        assertFalse(SirenGate.shouldSound(last.siren, soundEnabled = false))
        assertTrue(last.events.any { it.status == MonitorStatus.FLAME })
        assertTrue(last.tracks.any { it.label == "Flame" && it.candidate })
    }

    @Test
    fun noFlameLikeInputWaitsLongerThanOneSecond() {
        val cases = listOf(
            "steady-109" to { t: Long -> Frames.cluster(t, 80, 60, count = 3, celsius = 109f) },
            "steady-85" to { t: Long -> Frames.cluster(t, 10, 10, count = 2, celsius = 85f) },
            "single-pixel" to { t: Long -> Frames.flickering(t, 150, 5, count = 1, centerC = 120f, ampC = 14f) },
            "edge" to { t: Long -> Frames.cluster(t, 159, 119, count = 2, celsius = 130f) },
            "flicker-100-150" to { t: Long -> Frames.flickering(t, centerC = 125f, ampC = 25f) },
            "thin-80-150" to { t: Long -> Frames.flickering(t, centerC = 115f, ampC = 35f, count = 3) },
            "beside-iron" to { t: Long -> Frames.flameBesideIron(t) },
            "warm-bench" to { t: Long -> Frames.cluster(t, 40, 40, count = 4, celsius = 120f, background = 42f) },
            "rotation-90" to { t: Long -> Frames.rotate(Frames.cluster(t, 8, 8, count = 3, celsius = 117f), 90) },
            "rotation-180" to { t: Long -> Frames.rotate(Frames.cluster(t, 8, 8, count = 3, celsius = 117f), 180) },
            "rotation-270" to { t: Long -> Frames.rotate(Frames.cluster(t, 8, 8, count = 3, celsius = 117f), 270) },
            "msx-preview" to { t: Long -> Frames.withPreview(Frames.lighter(t), "MSX") },
        )
        val late = ArrayList<String>()
        for ((name, frameAt) in cases) {
            val decisions = play(1_000, 115, frameAt = frameAt)
            val hit = decisions.firstOrNull { it.status == MonitorStatus.FLAME }
            if (hit == null || hit.timeMs > 1_000) late.add("$name at ${hit?.timeMs ?: "never"}")
        }
        assertTrue(late.joinToString(), late.isEmpty())
    }
}

private fun assertFlamesBy(
    limitMs: Long,
    settings: WatchSettings = WatchSettings(),
    zones: List<Zone> = emptyList(),
    label: String = "",
    frameAt: (Long) -> ThermalFrame,
) {
    val decisions = play(limitMs, 115, settings, zones, frameAt)
    val hit = decisions.firstOrNull { it.status == MonitorStatus.FLAME }
    assertNotNull("${label.ifBlank { "flame" }} did not alarm\n${decisions.lastOrNull()?.headline}", hit)
    assertTrue("${label.ifBlank { "flame" }} at ${hit!!.timeMs}ms: ${hit.headline}", hit.timeMs <= limitMs)
    assertTrue(hit.siren)
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
