package com.benchfirewatch

import com.benchfirewatch.log.BackgroundLog
import com.benchfirewatch.log.CsvLog
import com.benchfirewatch.log.LogSession
import com.benchfirewatch.log.LoggedBlob
import com.benchfirewatch.log.alertLabel
import com.benchfirewatch.log.classifyBlob
import com.benchfirewatch.log.clampLogHz
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.ThermalFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.io.Writer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LogSessionTest {
    @Test
    fun headerAndOneRowPerBlob() {
        val out = StringWriter()
        val session = LogSession(out, 1.0)
        session.start(1_000)
        session.onFrame(
            nowMs = 1_000,
            frameMaxC = 310f,
            alert = "normal",
            blobs = listOf(
                LoggedBlob(1, 305f, 0.004f, "iron", 0f),
                LoggedBlob(2, 48.2f, 0.018f, "cell", 0.25f),
            ),
            events = emptyList(),
        )
        session.stop()
        val lines = lines(out)
        assertEquals(CsvLog.HEADER, lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("1970-01-01T00:00:01.000Z,0.000,sample,normal,310.00,1,305.00,0.004000,iron,0.000,,"))
        assertTrue(lines[2].contains(",2,48.20,0.018000,cell,0.250,,"))
        assertEquals(1, session.sampleCount)
    }

    @Test
    fun emptyFrameStillRecordsTheFrameMaximum() {
        val out = StringWriter()
        val session = LogSession(out, 1.0)
        session.start(0)
        session.onFrame(0, 27.4f, "normal", emptyList(), emptyList())
        session.stop()
        val row = lines(out)[1]
        assertEquals("27.40,,,,,,,,", row.substringAfter("normal,"))
    }

    @Test
    fun rateLimitDropsFramesInsideTheInterval() {
        val out = StringWriter()
        val session = LogSession(out, 1.0)
        session.start(0)
        listOf(0L, 400L, 999L, 1000L, 1500L, 2000L).forEach { t ->
            session.onFrame(t, 30f, "normal", emptyList(), emptyList())
        }
        session.stop()
        assertEquals(3, session.sampleCount)
        val stamps = lines(out).drop(1).map { it.substringBefore(',') }
        assertEquals(
            listOf("1970-01-01T00:00:00.000Z", "1970-01-01T00:00:01.000Z", "1970-01-01T00:00:02.000Z"),
            stamps,
        )
    }

    @Test
    fun halfHertzAndClamp() {
        assertEquals(2_000L, CsvLog.sampleIntervalMs(0.5))
        assertEquals(100L, CsvLog.sampleIntervalMs(10.0))
        assertEquals(0.5, clampLogHz(0.0), 0.0)
        assertEquals(10.0, clampLogHz(40.0), 0.0)
        val out = StringWriter()
        val session = LogSession(out, 0.5)
        session.start(0)
        session.onFrame(0, 20f, "normal", emptyList(), emptyList())
        session.onFrame(1999, 21f, "normal", emptyList(), emptyList())
        session.onFrame(2000, 22f, "warning", emptyList(), emptyList())
        session.stop()
        assertEquals(2, session.sampleCount)
        assertTrue(lines(out)[2].contains(",warning,"))
    }

    @Test
    fun eventBetweenSamplesIsItsOwnRowAndDoesNotCountAsASample() {
        val out = StringWriter()
        val session = LogSession(out, 1.0)
        session.start(0)
        session.onFrame(0, 40f, "normal", emptyList(), emptyList())
        session.onFrame(500, 80f, "alarm", emptyList(), listOf("Rapid rise +6.0°C/s on a cell-sized spot"))
        session.stop()
        assertEquals(1, session.sampleCount)
        val event = lines(out)[2]
        assertTrue(event.contains(",event,alarm,80.00,,,,,"))
        assertTrue(event.contains("Rapid rise"))
    }

    @Test
    fun eventOnASampleIsNotDuplicated() {
        val out = StringWriter()
        val session = LogSession(out, 1.0)
        session.start(0)
        session.onFrame(0, 90f, "alarm", listOf(LoggedBlob(4, 88f, 0.02f, "cell", 6f)), listOf("Still hot"))
        session.stop()
        val body = lines(out).drop(1)
        assertEquals(1, body.size)
        assertTrue(body[0].contains(",sample,alarm,"))
        assertTrue(body[0].contains(",Still hot,"))
    }

    @Test
    fun referenceIsTimestampedAndCopiedOntoLaterSamples() {
        val out = StringWriter()
        val session = LogSession(out, 1.0)
        session.start(0)
        session.reference(250, 61.5)
        session.onFrame(1000, 63f, "warning", listOf(LoggedBlob(3, 62f, 0.01f, "cell", 0.1f)), emptyList())
        session.flush()
        val text = out.toString()
        assertTrue(text.contains("1970-01-01T00:00:00.250Z,0.250,reference,normal,,,,,,,,61.50,"))
        assertTrue(text.contains(",warning,63.00,3,62.00,0.010000,cell,0.100,,61.50,"))
        session.stop()
    }

    @Test
    fun markQuotesCommasAndStopsCleanly() {
        val out = StringWriter()
        val session = LogSession(out, 1.0)
        session.start(5_000)
        session.mark(5_100, "heater on 20W, \"kapton\"")
        session.onFrame(9_000, 10f, "normal", emptyList(), emptyList())
        session.stop()
        assertFalse(session.isOpen)
        session.onFrame(10_000, 10f, "normal", emptyList(), emptyList())
        assertEquals(1, session.sampleCount)
        val text = out.toString()
        assertTrue(text.contains("\"heater on 20W, \"\"kapton\"\"\""))
        assertFalse(text.contains("1970-01-01T00:00:10.000Z"))
    }

    @Test
    fun forceEventRecordsAFaultWithNoFrame() {
        val out = StringWriter()
        val session = LogSession(out, 1.0)
        session.start(0)
        session.forceEvent(1200, "fault", null, "Camera disconnected")
        session.stop()
        assertTrue(lines(out)[1].contains(",event,fault,,,,,,,Camera disconnected,,"))
        assertEquals(0, session.sampleCount)
    }

    @Test
    fun classificationAndAlertLabels() {
        assertEquals("iron", classifyBlob(tool = true, inGrace = true, areaFraction = 0.02f, cellMinAreaFraction = 0.0015f, warnedOrAlarmed = false))
        assertEquals("fresh-solder", classifyBlob(false, true, 0.02f, 0.0015f, false))
        assertEquals("cell", classifyBlob(false, false, 0.01f, 0.0015f, false))
        assertEquals("cell", classifyBlob(false, false, 0.0001f, 0.0015f, true))
        assertEquals("unknown", classifyBlob(false, false, 0.0001f, 0.0015f, false))
        assertEquals("normal", alertLabel(MonitorStatus.OK))
        assertEquals("normal", alertLabel(MonitorStatus.SEARCHING))
        assertEquals("warning", alertLabel(MonitorStatus.WARNING))
        assertEquals("alarm", alertLabel(MonitorStatus.ALARM))
        assertEquals("fault", alertLabel(MonitorStatus.FAULT))
    }

    @Test
    fun backgroundWriterDoesNotBlockTheCallerAndFlushesOnStop() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writes = AtomicInteger(0)
        val sink = StringWriter()
        val blocking = object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                writes.incrementAndGet()
                entered.countDown()
                assertTrue(release.await(2, TimeUnit.SECONDS))
                sink.write(cbuf, off, len)
            }
            override fun flush() {}
            override fun close() {}
        }
        val log = BackgroundLog(openWriter = { blocking }, sampleHz = 10.0)
        log.start(0)
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        val started = System.nanoTime()
        repeat(40) { index ->
            log.offerSample(index * 100L, 30f, "normal", emptyList(), emptyList())
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("offers took ${elapsedMs}ms", elapsedMs < 200)
        release.countDown()
        log.flush()
        log.stop()
        assertFalse(log.active)
        assertTrue(sink.toString().startsWith(CsvLog.HEADER))
        assertTrue(log.sampleCount >= 1)
    }

    @Test
    fun snapshotIsCopiedOntoTheLogThreadAndNotTheCaller() {
        val seen = AtomicInteger(0)
        val sink = StringWriter()
        val log = BackgroundLog(
            openWriter = { sink },
            sampleHz = 1.0,
            onSnapshot = {
                assertEquals("hot-spotter-log", Thread.currentThread().name)
                seen.incrementAndGet()
            },
        )
        log.start(0)
        log.offerSnapshot(ThermalFrame(0, 2, 2, floatArrayOf(1f, 2f, 3f, 4f), "t"))
        log.stop()
        assertEquals(1, seen.get())
    }
}

private fun lines(out: StringWriter): List<String> =
    out.toString().trimEnd().split("\n").map { it.trimEnd('\r') }
