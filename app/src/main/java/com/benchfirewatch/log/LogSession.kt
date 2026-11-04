package com.benchfirewatch.log

import java.io.BufferedWriter
import java.io.Writer

/**
 * Synchronous CSV session. Call it from one thread. [BackgroundLog] does that
 * so the detector only enqueues work.
 *
 * A sample is one timestamp. Each tracked blob gets its own row. A frame with
 * no blobs still gets one row so the frame maximum and alert state are kept.
 * Events that land on a sample are written in that sample's event column.
 * Events that arrive between samples, and faults that arrive with no frame,
 * are written immediately as row_type=event.
 */
class LogSession(
    out: Writer,
    sampleHz: Double,
) {
    private val buffered = BufferedWriter(out, 8 * 1024)
    private var hz = clampLogHz(sampleHz)
    private var startedAtMs = 0L
    private var opened = false
    private var lastSampleAtMs = Long.MIN_VALUE
    private var referenceC: Double? = null
    private var closed = false

    var sampleCount: Int = 0
        private set

    val isOpen: Boolean get() = opened && !closed

    fun start(nowMs: Long) {
        if (closed || opened) return
        opened = true
        startedAtMs = nowMs
        buffered.write(CsvLog.HEADER)
        buffered.newLine()
        buffered.flush()
    }

    fun setHz(sampleHz: Double) {
        hz = clampLogHz(sampleHz)
    }

    fun onFrame(
        nowMs: Long,
        frameMaxC: Float,
        alert: String,
        blobs: List<LoggedBlob>,
        events: List<String>,
    ) {
        if (!isOpen) return
        val eventText = events.filter { it.isNotBlank() }.joinToString(" | ").ifBlank { null }
        val interval = CsvLog.sampleIntervalMs(hz)
        val due = lastSampleAtMs == Long.MIN_VALUE || nowMs - lastSampleAtMs >= interval
        if (eventText != null && !due) {
            write(
                CsvLog.row(nowMs, startedAtMs, "event", alert, frameMaxC, null, eventText, referenceC, null),
            )
            return
        }
        if (!due) return
        lastSampleAtMs = nowMs
        sampleCount += 1
        if (blobs.isEmpty()) {
            write(CsvLog.row(nowMs, startedAtMs, "sample", alert, frameMaxC, null, eventText, referenceC, null))
        } else {
            for (blob in blobs) {
                write(CsvLog.row(nowMs, startedAtMs, "sample", alert, frameMaxC, blob, eventText, referenceC, null))
            }
        }
    }

    /** A camera fault or other event that did not arrive with a thermal frame. */
    fun forceEvent(nowMs: Long, alert: String, frameMaxC: Float?, message: String) {
        if (!isOpen || message.isBlank()) return
        write(CsvLog.row(nowMs, startedAtMs, "event", alert, frameMaxC, null, message, referenceC, null))
    }

    fun reference(nowMs: Long, celsius: Double) {
        if (!isOpen) return
        referenceC = celsius
        write(CsvLog.row(nowMs, startedAtMs, "reference", "normal", null, null, null, celsius, null))
        buffered.flush()
    }

    fun mark(nowMs: Long, label: String) {
        if (!isOpen) return
        val text = label.trim().ifBlank { return }
        write(CsvLog.row(nowMs, startedAtMs, "mark", "normal", null, null, null, referenceC, text))
        buffered.flush()
    }

    fun flush() {
        if (!closed && opened) buffered.flush()
    }

    fun stop() {
        if (closed || !opened) return
        closed = true
        buffered.flush()
        buffered.close()
    }

    private fun write(line: String) {
        buffered.write(line)
        buffered.newLine()
    }
}
