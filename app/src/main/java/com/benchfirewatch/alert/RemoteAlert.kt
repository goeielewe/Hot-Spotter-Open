package com.benchfirewatch.alert

import com.benchfirewatch.engine.AlertText
import com.benchfirewatch.model.MonitorStatus
import java.net.HttpURLConnection
import java.net.URL

object RemoteAlert {
    fun send(ntfyUrl: String, webhookUrl: String, status: MonitorStatus, message: String, timeMs: Long, siren: Boolean): String {
        val notes = ArrayList<String>()
        if (ntfyUrl.isNotBlank()) {
            val result = post(
                ntfyUrl.trim(),
                message,
                mapOf(
                    "Title" to AlertText.ntfyTitle(status),
                    "Priority" to AlertText.ntfyPriority(status),
                    "Tags" to if (status == MonitorStatus.WARNING) "warning" else "rotating_light",
                    "Content-Type" to "text/plain; charset=utf-8",
                ),
            )
            notes += "ntfy ${if (result) "ok" else "failed"}"
        }
        if (webhookUrl.isNotBlank()) {
            val body = AlertText.webhookJson(status, message, timeMs, siren)
            val result = post(
                webhookUrl.trim(),
                body,
                mapOf("Content-Type" to "application/json; charset=utf-8"),
            )
            notes += "webhook ${if (result) "ok" else "failed"}"
        }
        return notes.joinToString(", ")
    }

    private fun post(url: String, body: String, headers: Map<String, String>): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        var attempt = 0
        while (attempt < 2) {
            attempt++
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("User-Agent", "HotSpotter/1.0")
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                connection.disconnect()
                if (code in 200..299) return true
            } catch (_: Exception) {
                if (attempt >= 2) return false
            }
        }
        return false
    }
}
