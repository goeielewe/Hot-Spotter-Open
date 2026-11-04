package com.benchfirewatch.log

import com.benchfirewatch.model.MonitorStatus
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round

/** One tracked blob at a single sample. */
data class LoggedBlob(
    val id: Int,
    val maxC: Float,
    val areaFraction: Float,
    val classification: String,
    val riseCPerSec: Float?,
)

/**
 * iron: small, sharp, moving or brief hot blob.
 * fresh-solder: a cell still inside the cool-down grace after the iron left.
 * cell: a cell-sized blob, or one the detector has already warned or alarmed on.
 * unknown: anything else that is being tracked.
 */
fun classifyBlob(
    tool: Boolean,
    inGrace: Boolean,
    areaFraction: Float,
    cellMinAreaFraction: Float,
    warnedOrAlarmed: Boolean,
): String = when {
    tool -> "iron"
    inGrace -> "fresh-solder"
    areaFraction >= cellMinAreaFraction || warnedOrAlarmed -> "cell"
    else -> "unknown"
}

fun alertLabel(status: MonitorStatus): String = when (status) {
    MonitorStatus.WARNING -> "warning"
    MonitorStatus.ALARM -> "alarm"
    MonitorStatus.FLAME -> "flame"
    MonitorStatus.FAULT -> "fault"
    else -> "normal"
}

fun clampLogHz(hz: Double): Double = hz.coerceIn(MIN_LOG_HZ, MAX_LOG_HZ)

const val MIN_LOG_HZ = 0.5
const val MAX_LOG_HZ = 10.0

object CsvLog {
    const val HEADER =
        "timestamp_iso,elapsed_s,row_type,alert_state,frame_max_c,blob_id,blob_max_c,area_fraction,classification,rise_c_per_s,event,reference_c,marker"

    private val iso: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun timestamp(epochMs: Long): String = iso.format(Instant.ofEpochMilli(epochMs))

    fun sampleIntervalMs(hz: Double): Long = (1000.0 / clampLogHz(hz)).toLong().coerceAtLeast(1L)

    fun row(
        epochMs: Long,
        startedAtMs: Long,
        rowType: String,
        alert: String,
        frameMaxC: Float?,
        blob: LoggedBlob?,
        event: String?,
        referenceC: Double?,
        marker: String?,
    ): String {
        val elapsed = (epochMs - startedAtMs).coerceAtLeast(0L) / 1000.0
        return listOf(
            timestamp(epochMs),
            formatFixed(elapsed, 3),
            rowType,
            alert,
            frameMaxC?.let { formatFixed(it.toDouble(), 2) }.orEmpty(),
            blob?.id?.toString().orEmpty(),
            blob?.let { formatFixed(it.maxC.toDouble(), 2) }.orEmpty(),
            blob?.let { formatFixed(it.areaFraction.toDouble(), 6) }.orEmpty(),
            blob?.classification.orEmpty(),
            blob?.riseCPerSec?.let { formatFixed(it.toDouble(), 3) }.orEmpty(),
            event.orEmpty(),
            referenceC?.let { formatFixed(it, 2) }.orEmpty(),
            marker.orEmpty(),
        ).joinToString(",") { escape(it) }
    }

    fun escape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun formatFixed(value: Double, digits: Int): String {
        val scale = when (digits) {
            2 -> 100.0
            3 -> 1000.0
            6 -> 1_000_000.0
            else -> 1.0
        }
        val rounded = round(value * scale) / scale
        return String.format(Locale.US, "%.${digits}f", rounded)
    }
}
