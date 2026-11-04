package com.benchfirewatch.alert

import com.benchfirewatch.engine.AlertText
import com.benchfirewatch.model.MonitorStatus
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class NtfyMessage(
    val url: String,
    val body: String,
    val title: String,
    val priority: String,
)

/**
 * Repeating push for a second phone on the free public ntfy.sh server.
 * No login, no reserved topic, and no paid feature (no e-mail, no phone call).
 * Building the message does no network. Sending is a separate call so the
 * watch thread never waits on it.
 *
 * A repeat every 15 minutes is 96 messages in a day if nobody acknowledges,
 * under the public server's limit of [FREE_DAILY_MESSAGES] a day.
 */
object NtfyAlert {
    const val DEFAULT_SERVER = "https://ntfy.sh"
    const val PRIORITY_MAX = "5"
    const val REPEAT_MS = 15 * 60 * 1000L
    const val FREE_DAILY_MESSAGES = 250

    fun repeats(status: MonitorStatus): Boolean = when (status) {
        MonitorStatus.FLAME, MonitorStatus.ALARM, MonitorStatus.FAULT -> true
        else -> false
    }

    fun url(server: String, topic: String): String? {
        val base = server.trim().trimEnd('/')
        if (!base.startsWith("https://") && !base.startsWith("http://")) return null
        if (base.substringAfter("://").contains('@')) return null
        val clean = topic.trim().trim('/')
        if (!clean.matches(TOPIC)) return null
        return "$base/$clean"
    }

    /** 128 bits, hex, so the public topic is hard to guess. Stable once saved. */
    fun newTopic(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val hex = buildString(32) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }
        return "hs$hex"
    }

    fun message(
        status: MonitorStatus,
        maxTempC: Float,
        timeMs: Long,
        topic: String,
        zone: TimeZone = TimeZone.getDefault(),
    ): NtfyMessage? {
        val target = url(DEFAULT_SERVER, topic) ?: return null
        return NtfyMessage(
            url = target,
            body = body(status.name, maxTempC, timeMs, zone),
            title = AlertText.ntfyTitle(status),
            priority = PRIORITY_MAX,
        )
    }

    fun testMessage(
        maxTempC: Float,
        timeMs: Long,
        topic: String,
        zone: TimeZone = TimeZone.getDefault(),
    ): NtfyMessage? {
        val target = url(DEFAULT_SERVER, topic) ?: return null
        return NtfyMessage(
            url = target,
            body = body("TEST", maxTempC, timeMs, zone),
            title = "Hot Spotter Test",
            priority = PRIORITY_MAX,
        )
    }

    /** Headers for a free public publish. No login, e-mail, or phone call. */
    fun headers(message: NtfyMessage): Map<String, String> = linkedMapOf(
        "User-Agent" to "HotSpotter/1.0",
        "Title" to message.title,
        "Priority" to message.priority,
        "Tags" to "rotating_light",
        "Content-Type" to "text/plain; charset=utf-8",
    )

    fun body(kind: String, maxTempC: Float, timeMs: Long, zone: TimeZone): String {
        val temp = if (maxTempC.isNaN()) "unknown" else "${maxTempC.toInt()}°C"
        return "$kind\nMax $temp\n${formatTime(timeMs, zone)}"
    }

    fun formatTime(timeMs: Long, zone: TimeZone): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        fmt.timeZone = zone
        return fmt.format(Date(timeMs))
    }

    fun post(message: NtfyMessage): Boolean {
        if (!message.url.startsWith("http://") && !message.url.startsWith("https://")) return false
        return try {
            val connection = (URL(message.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                headers(message).forEach { (key, value) -> setRequestProperty(key, value) }
            }
            connection.outputStream.use { it.write(message.body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private val TOPIC = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
    private const val HEX = "0123456789abcdef"
}
