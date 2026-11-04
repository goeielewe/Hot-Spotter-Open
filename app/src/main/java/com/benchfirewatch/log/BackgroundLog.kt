package com.benchfirewatch.log

import com.benchfirewatch.model.ThermalFrame
import java.io.Writer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Queues log work onto one background thread. [offer] never waits on disk.
 * [stop] joins that thread; call it from the button that ends the session,
 * not from the frame callback.
 */
class BackgroundLog(
    private val openWriter: () -> Writer,
    sampleHz: Double,
    private val onSnapshot: (ThermalFrame) -> Unit = {},
) {
    private val queue = LinkedBlockingQueue<Work>()
    private val sampleCountAtomic = AtomicInteger(0)
    private var thread: Thread? = null
    private var session: LogSession? = null

    @Volatile var active: Boolean = false
        private set

    @Volatile var startedAtMs: Long = 0L
        private set

    @Volatile var error: String? = null
        private set

    @Volatile private var hz = clampLogHz(sampleHz)

    val sampleCount: Int get() = sampleCountAtomic.get()

    fun start(nowMs: Long) {
        if (active) return
        error = null
        startedAtMs = nowMs
        active = true
        sampleCountAtomic.set(0)
        thread = Thread({ loop() }, "hot-spotter-log").also {
            it.isDaemon = true
            it.start()
        }
        queue.offer(Work.Begin(nowMs))
    }

    fun setHz(sampleHz: Double) {
        hz = clampLogHz(sampleHz)
        if (active) queue.offer(Work.Hz(hz))
    }

    fun offerSample(
        nowMs: Long,
        frameMaxC: Float,
        alert: String,
        blobs: List<LoggedBlob>,
        events: List<String>,
    ) {
        if (!active) return
        queue.offer(Work.Sample(nowMs, frameMaxC, alert, blobs, events))
    }

    fun offerEvent(nowMs: Long, alert: String, frameMaxC: Float?, message: String) {
        if (!active || message.isBlank()) return
        queue.offer(Work.Event(nowMs, alert, frameMaxC, message))
    }

    fun offerReference(nowMs: Long, celsius: Double) {
        if (!active) return
        queue.offer(Work.Reference(nowMs, celsius))
    }

    fun offerMark(nowMs: Long, label: String) {
        if (!active || label.isBlank()) return
        queue.offer(Work.Mark(nowMs, label))
    }

    fun offerSnapshot(frame: ThermalFrame) {
        if (!active) return
        val copy = ThermalFrame(
            frame.timestampMs,
            frame.width,
            frame.height,
            frame.celsius.copyOf(),
            frame.label,
        )
        queue.offer(Work.Shot(copy))
    }

    fun flush() {
        if (active) queue.offer(Work.Flush)
    }

    fun stop() {
        if (thread == null && !active) return
        active = false
        queue.offer(Work.Stop)
        try {
            thread?.join(4_000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
    }

    private fun loop() {
        try {
            while (true) {
                when (val work = queue.take()) {
                    is Work.Begin -> open(work.nowMs)
                    is Work.Hz -> session?.setHz(work.hz)
                    is Work.Sample -> {
                        session?.onFrame(work.nowMs, work.frameMaxC, work.alert, work.blobs, work.events)
                        sampleCountAtomic.set(session?.sampleCount ?: 0)
                    }
                    is Work.Event -> session?.forceEvent(work.nowMs, work.alert, work.frameMaxC, work.message)
                    is Work.Reference -> session?.reference(work.nowMs, work.celsius)
                    is Work.Mark -> session?.mark(work.nowMs, work.label)
                    is Work.Shot -> writeShot(work.frame)
                    Work.Flush -> session?.flush()
                    Work.Stop -> break
                }
            }
        } catch (t: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            error = t.message ?: "Log write failed"
        } finally {
            try {
                session?.stop()
            } catch (t: Throwable) {
                if (error == null) error = t.message ?: "Log close failed"
            }
            session = null
            active = false
        }
    }

    private fun open(nowMs: Long) {
        val opened = LogSession(openWriter(), hz)
        opened.start(nowMs)
        session = opened
    }

    private fun writeShot(frame: ThermalFrame) {
        try {
            onSnapshot(frame)
        } catch (t: Throwable) {
            error = t.message ?: "Snapshot failed"
        }
    }

    private sealed interface Work {
        data class Begin(val nowMs: Long) : Work
        data class Hz(val hz: Double) : Work
        data class Sample(
            val nowMs: Long,
            val frameMaxC: Float,
            val alert: String,
            val blobs: List<LoggedBlob>,
            val events: List<String>,
        ) : Work
        data class Event(val nowMs: Long, val alert: String, val frameMaxC: Float?, val message: String) : Work
        data class Reference(val nowMs: Long, val celsius: Double) : Work
        data class Mark(val nowMs: Long, val label: String) : Work
        data class Shot(val frame: ThermalFrame) : Work
        data object Flush : Work
        data object Stop : Work
    }
}
