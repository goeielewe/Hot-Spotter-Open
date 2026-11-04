package com.benchfirewatch.alert

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tasmota's local HTTP command endpoint. A password, when set, is sent on
 * the request and is not written into the result text.
 */
object TasmotaPlug {
    fun commandQuery(command: String): String = "cmnd=" + URLEncoder.encode(command, Charsets.UTF_8.name())

    fun saysOff(body: String): Boolean {
        val compact = body.replace(" ", "")
        return compact.contains("\"POWER\":\"OFF\"") || compact.contains("\"POWER1\":\"OFF\"")
    }

    fun saysOn(body: String): Boolean {
        val compact = body.replace(" ", "")
        return compact.contains("\"POWER\":\"ON\"") || compact.contains("\"POWER1\":\"ON\"")
    }

    fun cut(address: String, username: String, password: String): PlugOutcome {
        val sent = get(address, username, password, "Power Off")
        val status = get(address, username, password, "Power")
        return if (saysOff(sent.orEmpty()) || saysOff(status.orEmpty())) {
            PlugOutcome(true, false, "Plug off.")
        } else {
            PlugOutcome(false, false, PlugMessages.UNCONFIRMED)
        }
    }

    fun test(address: String, username: String, password: String, alarmActive: Boolean): PlugOutcome {
        val off = cut(address, username, password)
        if (!off.confirmedOff || alarmActive) {
            val detail = if (alarmActive && off.confirmedOff) "Plug off. Left off because an alarm is active." else off.detail
            return off.copy(detail = detail)
        }
        val back = get(address, username, password, "Power On")
        val status = get(address, username, password, "Power")
        return if (saysOn(back.orEmpty()) || saysOn(status.orEmpty())) {
            PlugOutcome(true, true, "Plug turned off, then back on.")
        } else {
            PlugOutcome(true, false, "Turned the plug off, but turning it back on failed. It may still be off.")
        }
    }

    fun isOff(address: String, username: String, password: String): Boolean =
        saysOff(get(address, username, password, "Power").orEmpty())

    fun reachable(address: String, username: String, password: String): Boolean =
        get(address, username, password, "Power") != null

    internal fun get(address: String, username: String, password: String, command: String): String? {
        val safe = ShellyPlug.hostOf(address) ?: return null
        val query = StringBuilder(commandQuery(command))
        if (password.isNotEmpty()) {
            val user = URLEncoder.encode(username.ifBlank { "admin" }, Charsets.UTF_8.name())
            val pass = URLEncoder.encode(password, Charsets.UTF_8.name())
            query.append("&user=").append(user).append("&password=").append(pass)
        }
        val url = "http://$safe/cm?$query"
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
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
