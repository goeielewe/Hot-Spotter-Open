package com.benchfirewatch.alert

import java.net.HttpURLConnection
import java.net.URL

/**
 * Home Assistant REST on the local network. The long-lived token is a header,
 * never part of the URL. Any switch Home Assistant already controls can be
 * turned off here, including a Matter plug that is paired to Home Assistant.
 */
object HomeAssistantPlug {
    fun baseUrl(raw: String): String? {
        val text = raw.trim().trimEnd('/')
        if (!text.startsWith("http://") && !text.startsWith("https://")) return null
        if (text.contains('@') || text.contains(' ') || text.contains('\\')) return null
        val host = text.substringAfter("://").substringBefore('/')
        if (host.isEmpty() || !host.matches(Regex("^[A-Za-z0-9._:-]+$"))) return null
        return text
    }

    fun entityId(raw: String): String? {
        val text = raw.trim()
        if (!text.matches(Regex("^[A-Za-z0-9_]+\\.[A-Za-z0-9_]+$"))) return null
        return text
    }

    fun servicePath(entity: String, turnOn: Boolean): String {
        val domain = entity.substringBefore('.')
        val action = if (turnOn) "turn_on" else "turn_off"
        return "/api/services/$domain/$action"
    }

    fun body(entity: String): String = """{"entity_id":"$entity"}"""

    fun saysOff(body: String): Boolean = body.replace(" ", "").contains("\"state\":\"off\"")

    fun cut(address: String, entity: String, token: String): PlugOutcome {
        val base = baseUrl(address) ?: return PlugOutcome(false, false, PlugMessages.UNCONFIRMED)
        val id = entityId(entity) ?: return PlugOutcome(false, false, PlugMessages.UNCONFIRMED)
        post(base, servicePath(id, turnOn = false), body(id), token)
        val state = get(base, "/api/states/$id", token).orEmpty()
        return if (saysOff(state)) PlugOutcome(true, false, "Plug off.") else PlugOutcome(false, false, PlugMessages.UNCONFIRMED)
    }

    fun test(address: String, entity: String, token: String, alarmActive: Boolean): PlugOutcome {
        val off = cut(address, entity, token)
        if (!off.confirmedOff || alarmActive) {
            val detail = if (alarmActive && off.confirmedOff) "Plug off. Left off because an alarm is active." else off.detail
            return off.copy(detail = detail)
        }
        val base = baseUrl(address) ?: return PlugOutcome(true, false, "Turned the plug off, but turning it back on failed. It may still be off.")
        val id = entityId(entity) ?: return off
        post(base, servicePath(id, turnOn = true), body(id), token)
        val state = get(base, "/api/states/$id", token).orEmpty()
        return if (state.replace(" ", "").contains("\"state\":\"on\"")) {
            PlugOutcome(true, true, "Plug turned off, then back on.")
        } else {
            PlugOutcome(true, false, "Turned the plug off, but turning it back on failed. It may still be off.")
        }
    }

    fun isOff(address: String, entity: String, token: String): Boolean {
        val base = baseUrl(address) ?: return false
        val id = entityId(entity) ?: return false
        return saysOff(get(base, "/api/states/$id", token).orEmpty())
    }

    fun reachable(address: String, entity: String, token: String): Boolean {
        val base = baseUrl(address) ?: return false
        val id = entityId(entity) ?: return false
        return get(base, "/api/states/$id", token) != null
    }

    private fun post(base: String, path: String, json: String, token: String): String? =
        call(base, path, token, "POST", json)

    private fun get(base: String, path: String, token: String): String? =
        call(base, path, token, "GET", null)

    private fun call(base: String, path: String, token: String, method: String, json: String?): String? {
        if (token.isBlank()) return null
        return try {
            val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                if (json != null) {
                    doOutput = true
                    outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            connection.disconnect()
            if (code in 200..299) body else null
        } catch (_: Exception) {
            null
        }
    }
}
