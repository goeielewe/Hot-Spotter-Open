package com.benchfirewatch.engine

import java.util.Locale

/** Display strings for the watch strip. Nothing here changes detection. */
object WatchDisplay {
    const val HEARTBEAT_AMBER_MS = 2_000L

    fun heartbeatAgeMs(nowMs: Long, lastFreshMs: Long): Long? {
        if (lastFreshMs == Long.MIN_VALUE || nowMs < lastFreshMs) return null
        return nowMs - lastFreshMs
    }

    fun heartbeatAmber(ageMs: Long): Boolean = ageMs >= HEARTBEAT_AMBER_MS

    fun heartbeatBlind(ageMs: Long, blindSec: Float): Boolean = ageMs >= (blindSec * 1000f).toLong()

    fun heartbeatLabel(ageMs: Long): String {
        val sec = ageMs / 1000.0
        val shown = if (sec < 10.0) String.format(Locale.US, "%.1f", sec) else sec.toLong().toString()
        return "Watching · updated $shown s ago"
    }

    fun hotWorkLabel(remainingMs: Long): String {
        val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        return "Hot-work mode · %d:%02d left · flame watch still on".format(minutes, seconds)
    }
}
