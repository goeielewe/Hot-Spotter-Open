package com.benchfirewatch.alert

import java.net.HttpURLConnection
import java.net.URL

/**
 * Belkin Wemo local SOAP. The phone talks to the plug's own IP. There is no
 * Belkin account. Classic Mini, Switch, and Insight plugs answer this. A
 * plug that only answers Belkin's cloud fails the status read and the watch
 * alarms instead of pretending the charger is off.
 */
object WemoPlug {
    val ports = intArrayOf(49153, 49152, 49154, 49155, 49151)

    fun setEnvelope(on: Boolean): String = envelope(
        "SetBinaryState",
        "<BinaryState>${if (on) 1 else 0}</BinaryState>",
    )

    fun getEnvelope(): String = envelope("GetBinaryState", "<BinaryState></BinaryState>")

    fun saysOff(body: String): Boolean = binaryState(body) == "0"

    fun saysOn(body: String): Boolean = binaryState(body) == "1"

    fun cut(address: String): PlugOutcome {
        val port = findPort(address) ?: return PlugOutcome(false, false, PlugMessages.UNCONFIRMED)
        post(address, port, "SetBinaryState", setEnvelope(on = false))
        val state = post(address, port, "GetBinaryState", getEnvelope()).orEmpty()
        return if (saysOff(state)) PlugOutcome(true, false, "Plug off.") else PlugOutcome(false, false, PlugMessages.UNCONFIRMED)
    }

    fun test(address: String, alarmActive: Boolean): PlugOutcome {
        val off = cut(address)
        if (!off.confirmedOff || alarmActive) {
            val detail = if (alarmActive && off.confirmedOff) "Plug off. Left off because an alarm is active." else off.detail
            return off.copy(detail = detail)
        }
        val port = findPort(address) ?: return PlugOutcome(true, false, "Turned the plug off, but turning it back on failed. It may still be off.")
        post(address, port, "SetBinaryState", setEnvelope(on = true))
        val state = post(address, port, "GetBinaryState", getEnvelope()).orEmpty()
        return if (saysOn(state)) {
            PlugOutcome(true, true, "Plug turned off, then back on.")
        } else {
            PlugOutcome(true, false, "Turned the plug off, but turning it back on failed. It may still be off.")
        }
    }

    fun isOff(address: String): Boolean {
        val port = findPort(address) ?: return false
        return saysOff(post(address, port, "GetBinaryState", getEnvelope()).orEmpty())
    }

    fun reachable(address: String): Boolean = findPort(address) != null

    private fun binaryState(body: String): String? {
        val start = body.indexOf("<BinaryState>")
        if (start < 0) return null
        val from = start + "<BinaryState>".length
        val end = body.indexOf("</BinaryState>", from)
        if (end < 0) return null
        return body.substring(from, end).trim().substringBefore('|').take(1)
    }

    private fun envelope(action: String, inner: String): String =
        """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:$action xmlns:u="urn:Belkin:service:basicevent:1">$inner</u:$action></s:Body></s:Envelope>"""

    private fun findPort(address: String): Int? {
        val safe = ShellyPlug.hostOf(address) ?: return null
        val host = safe.substringBefore(':')
        val explicit = safe.substringAfter(':', "")
        val candidates = if (explicit.isNotEmpty()) intArrayOf(explicit.toInt()) else ports
        for (port in candidates) {
            if (setup(host, port)) return port
        }
        return null
    }

    private fun setup(host: String, port: Int): Boolean = try {
        val connection = (URL("http://$host:$port/setup.xml").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 800
            readTimeout = 800
        }
        val code = connection.responseCode
        connection.disconnect()
        code in 200..299
    } catch (_: Exception) {
        false
    }

    private fun post(address: String, port: Int, action: String, xml: String): String? {
        val host = ShellyPlug.hostOf(address)?.substringBefore(':') ?: return null
        return try {
            val connection = (URL("http://$host:$port/upnp/control/basicevent1").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2500
                readTimeout = 2500
                doOutput = true
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"urn:Belkin:service:basicevent:1#$action\"")
                outputStream.use { it.write(xml.toByteArray(Charsets.UTF_8)) }
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
