package com.benchfirewatch.alert

import com.benchfirewatch.model.MonitorStatus

/**
 * The full-screen alarm and the lock-screen notification. Acknowledge hides
 * both. The status banner keeps the condition. The cover opens again only for
 * a new alarm or a re-arm, which both clear silence.
 */
object AlarmCover {
    fun visible(status: MonitorStatus, silenced: Boolean): Boolean {
        if (silenced) return false
        return when (status) {
            MonitorStatus.FLAME, MonitorStatus.ALARM, MonitorStatus.FAULT -> true
            else -> false
        }
    }

    /** Same gate as [visible]. The notification attaches a full-screen intent only while this is true. */
    fun fullScreenIntent(status: MonitorStatus, silenced: Boolean): Boolean = visible(status, silenced)

    /**
     * Identity of the notification the shade is already showing.
     * Equal keys mean a new frame must not post again.
     */
    fun notificationKey(status: MonitorStatus, headline: String, silenced: Boolean): String =
        "${status.name}|$headline|$silenced|${visible(status, silenced)}"
}
