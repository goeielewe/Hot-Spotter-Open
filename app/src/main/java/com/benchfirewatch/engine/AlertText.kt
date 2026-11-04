package com.benchfirewatch.engine

import com.benchfirewatch.model.MonitorStatus

object AlertText {
    fun ntfyTitle(status: MonitorStatus): String = when (status) {
        MonitorStatus.ALARM -> "Hot Spotter ALARM"
        MonitorStatus.FLAME -> "Hot Spotter FLAME"
        MonitorStatus.FAULT -> "Hot Spotter FAULT"
        MonitorStatus.WARNING -> "Hot Spotter Warning"
        else -> "Hot Spotter"
    }

    fun ntfyPriority(status: MonitorStatus): String = when (status) {
        MonitorStatus.ALARM, MonitorStatus.FLAME, MonitorStatus.FAULT -> "5"
        MonitorStatus.WARNING -> "4"
        else -> "3"
    }

    fun webhookJson(status: MonitorStatus, message: String, timestampMs: Long, siren: Boolean): String {
        val esc = buildString {
            for (ch in message) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(ch)
                }
            }
        }
        return buildString {
            append("{\"source\":\"hot-spotter\"")
            append(",\"status\":\"").append(status.name).append('"')
            append(",\"message\":\"").append(esc).append('"')
            append(",\"timestampMs\":").append(timestampMs)
            append(",\"siren\":").append(siren)
            append('}')
        }
    }
}
