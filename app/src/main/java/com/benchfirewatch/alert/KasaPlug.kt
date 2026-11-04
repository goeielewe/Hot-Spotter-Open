package com.benchfirewatch.alert

import java.net.InetSocketAddress
import java.net.Socket

/**
 * TP-Link Kasa local protocol on TCP port 9999.
 * The autokey cipher and the relay JSON are the documented local command set.
 * This is not the python-kasa client. That project is GPL and is not included.
 */
object KasaCipher {
    fun encode(plain: String): ByteArray {
        val data = plain.toByteArray(Charsets.UTF_8)
        var key = 171
        val out = ByteArray(data.size)
        for (i in data.indices) {
            val mixed = key xor (data[i].toInt() and 0xFF)
            key = mixed
            out[i] = mixed.toByte()
        }
        return out
    }

    fun decode(payload: ByteArray): String {
        var key = 171
        val out = ByteArray(payload.size)
        for (i in payload.indices) {
            val value = payload[i].toInt() and 0xFF
            out[i] = (key xor value).toByte()
            key = value
        }
        return out.toString(Charsets.UTF_8)
    }

    const val OFF = """{"system":{"set_relay_state":{"state":0}}}"""
    const val ON = """{"system":{"set_relay_state":{"state":1}}}"""
    const val INFO = """{"system":{"get_sysinfo":{}}}"""

    fun saysOff(json: String): Boolean = !saysOn(json) && json.replace(" ", "").contains("\"relay_state\":0")

    fun saysOn(json: String): Boolean = json.replace(" ", "").contains("\"relay_state\":1")
}

object KasaPlug {
    fun cut(address: String): PlugOutcome {
        val info = exchange(address, KasaCipher.OFF) ?: return PlugOutcome(false, false, PlugMessages.UNCONFIRMED)
        if (KasaCipher.saysOff(info) || KasaCipher.saysOff(exchange(address, KasaCipher.INFO).orEmpty())) {
            return PlugOutcome(true, false, "Plug off.")
        }
        return PlugOutcome(false, false, PlugMessages.UNCONFIRMED)
    }

    fun test(address: String, alarmActive: Boolean): PlugOutcome {
        val off = cut(address)
        if (!off.confirmedOff || alarmActive) {
            val detail = if (alarmActive && off.confirmedOff) "Plug off. Left off because an alarm is active." else off.detail
            return off.copy(detail = detail)
        }
        val back = exchange(address, KasaCipher.ON)
        if (back != null && (KasaCipher.saysOn(back) || KasaCipher.saysOn(exchange(address, KasaCipher.INFO).orEmpty()))) {
            return PlugOutcome(true, true, "Plug turned off, then back on.")
        }
        return PlugOutcome(true, false, "Turned the plug off, but turning it back on failed. It may still be off.")
    }

    fun isOff(address: String): Boolean = KasaCipher.saysOff(exchange(address, KasaCipher.INFO).orEmpty())

    fun reachable(address: String): Boolean = exchange(address, KasaCipher.INFO) != null

    internal fun exchange(address: String, json: String): String? {
        val safe = ShellyPlug.hostOf(address) ?: return null
        val host = safe.substringBefore(':')
        val port = safe.substringAfter(':', "9999").toIntOrNull() ?: return null
        val payload = KasaCipher.encode(json)
        val packet = ByteArray(4 + payload.size)
        packet[0] = (payload.size shr 24).toByte()
        packet[1] = (payload.size shr 16).toByte()
        packet[2] = (payload.size shr 8).toByte()
        packet[3] = payload.size.toByte()
        payload.copyInto(packet, 4)
        return try {
            Socket().use { socket ->
                socket.soTimeout = 2500
                socket.connect(InetSocketAddress(host, port), 2500)
                socket.getOutputStream().write(packet)
                val header = ByteArray(4)
                if (!readFully(socket, header)) return null
                val size = ((header[0].toInt() and 0xFF) shl 24) or
                    ((header[1].toInt() and 0xFF) shl 16) or
                    ((header[2].toInt() and 0xFF) shl 8) or
                    (header[3].toInt() and 0xFF)
                if (size <= 0 || size > 1_000_000) return null
                val body = ByteArray(size)
                if (!readFully(socket, body)) return null
                KasaCipher.decode(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFully(socket: Socket, dest: ByteArray): Boolean {
        val input = socket.getInputStream()
        var at = 0
        while (at < dest.size) {
            val n = input.read(dest, at, dest.size - at)
            if (n < 0) return false
            at += n
        }
        return true
    }
}
