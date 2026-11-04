package com.benchfirewatch.alert

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tapo local KLAP, version 2: SHA-256 of the SHA-1 password material, then
 * AES-128-CBC on the plug's own HTTP port. The handshake is the published
 * KLAP v2 sequence. python-kasa is GPL and is not included.
 */
object KlapCrypto {
    data class Keys(val key: ByteArray, val ivBase: ByteArray, val seq: Int, val sig: ByteArray)

    fun authHash(username: String, password: String): ByteArray {
        val user = sha1(username.toByteArray(Charsets.UTF_8))
        val pass = sha1(password.toByteArray(Charsets.UTF_8))
        return sha256(user + pass)
    }

    fun handshake1Check(localSeed: ByteArray, remoteSeed: ByteArray, auth: ByteArray): ByteArray =
        sha256(localSeed + remoteSeed + auth)

    fun handshake2Payload(localSeed: ByteArray, remoteSeed: ByteArray, auth: ByteArray): ByteArray =
        sha256(remoteSeed + localSeed + auth)

    fun keys(localSeed: ByteArray, remoteSeed: ByteArray, auth: ByteArray): Keys {
        val ivFull = sha256("iv".toByteArray(Charsets.UTF_8) + localSeed + remoteSeed + auth)
        return Keys(
            key = sha256("lsk".toByteArray(Charsets.UTF_8) + localSeed + remoteSeed + auth).copyOf(16),
            ivBase = ivFull.copyOf(12),
            seq = readBe32(ivFull, 28),
            sig = sha256("ldk".toByteArray(Charsets.UTF_8) + localSeed + remoteSeed + auth).copyOf(28),
        )
    }

    fun seal(keys: Keys, plaintext: ByteArray): Pair<Keys, ByteArray> {
        val seq = keys.seq + 1
        val iv = keys.ivBase + be32(seq)
        val cipher = aes(keys.key, iv, plaintext, Cipher.ENCRYPT_MODE)
        val mac = sha256(keys.sig + be32(seq) + cipher)
        return keys.copy(seq = seq) to (mac + cipher)
    }

    fun open(keys: Keys, body: ByteArray): ByteArray? {
        if (body.size <= 32) return null
        val mac = body.copyOfRange(0, 32)
        val cipher = body.copyOfRange(32, body.size)
        val expect = sha256(keys.sig + be32(keys.seq) + cipher)
        if (!mac.contentEquals(expect)) return null
        return aes(keys.key, keys.ivBase + be32(keys.seq), cipher, Cipher.DECRYPT_MODE)
    }

    fun offJson(nowMs: Long): String =
        """{"method":"set_device_info","params":{"device_on":false},"request_time_milis":$nowMs}"""

    fun onJson(nowMs: Long): String =
        """{"method":"set_device_info","params":{"device_on":true},"request_time_milis":$nowMs}"""

    const val INFO = """{"method":"get_device_info"}"""

    fun saysOff(json: String): Boolean {
        val compact = json.replace(" ", "")
        return compact.contains("\"device_on\":false") || compact.contains("\"device_on\":0")
    }

    fun saysOn(json: String): Boolean {
        val compact = json.replace(" ", "")
        return compact.contains("\"device_on\":true") || compact.contains("\"device_on\":1")
    }

    private fun aes(key: ByteArray, iv: ByteArray, data: ByteArray, mode: Int): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun readBe32(data: ByteArray, at: Int): Int =
        ((data[at].toInt() and 0xFF) shl 24) or
            ((data[at + 1].toInt() and 0xFF) shl 16) or
            ((data[at + 2].toInt() and 0xFF) shl 8) or
            (data[at + 3].toInt() and 0xFF)

    private fun be32(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )
}

object TapoPlug {
    fun cut(address: String, username: String, password: String): PlugOutcome {
        val session = session(address, username, password) ?: return PlugOutcome(false, false, PlugMessages.UNCONFIRMED)
        session.request(KlapCrypto.offJson(System.currentTimeMillis()))
        val info = session.request(KlapCrypto.INFO).orEmpty()
        return if (KlapCrypto.saysOff(info)) PlugOutcome(true, false, "Plug off.") else PlugOutcome(false, false, PlugMessages.UNCONFIRMED)
    }

    fun test(address: String, username: String, password: String, alarmActive: Boolean): PlugOutcome {
        val off = cut(address, username, password)
        if (!off.confirmedOff || alarmActive) {
            val detail = if (alarmActive && off.confirmedOff) "Plug off. Left off because an alarm is active." else off.detail
            return off.copy(detail = detail)
        }
        val session = session(address, username, password) ?: return PlugOutcome(true, false, "Turned the plug off, but turning it back on failed. It may still be off.")
        session.request(KlapCrypto.onJson(System.currentTimeMillis()))
        val info = session.request(KlapCrypto.INFO).orEmpty()
        return if (KlapCrypto.saysOn(info)) {
            PlugOutcome(true, true, "Plug turned off, then back on.")
        } else {
            PlugOutcome(true, false, "Turned the plug off, but turning it back on failed. It may still be off.")
        }
    }

    fun isOff(address: String, username: String, password: String): Boolean =
        KlapCrypto.saysOff(session(address, username, password)?.request(KlapCrypto.INFO).orEmpty())

    fun reachable(address: String, username: String, password: String): Boolean =
        session(address, username, password)?.request(KlapCrypto.INFO) != null

    private fun session(address: String, username: String, password: String): KlapSession? {
        val safe = ShellyPlug.hostOf(address) ?: return null
        if (password.isBlank()) return null
        val local = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val auth = KlapCrypto.authHash(username.ifBlank { "admin" }, password)
        val first = post("http://$safe/app/handshake1", local, null) ?: return null
        if (first.body.size < 48) return null
        val remote = first.body.copyOfRange(0, 16)
        val server = first.body.copyOfRange(16, 48)
        if (!server.contentEquals(KlapCrypto.handshake1Check(local, remote, auth))) return null
        val cookie = cookieOf(first.cookie) ?: return null
        val second = post("http://$safe/app/handshake2", KlapCrypto.handshake2Payload(local, remote, auth), cookie) ?: return null
        if (second.code !in 200..299) return null
        return KlapSession(safe, cookie, KlapCrypto.keys(local, remote, auth))
    }

    private class KlapSession(val host: String, val cookie: String, var keys: KlapCrypto.Keys) {
        fun request(json: String): String? {
            val (next, body) = KlapCrypto.seal(keys, json.toByteArray(Charsets.UTF_8))
            keys = next
            val response = post("http://$host/app/request", body, cookie) ?: return null
            val plain = KlapCrypto.open(keys, response.body) ?: return null
            return plain.toString(Charsets.UTF_8)
        }
    }

    private data class HttpBytes(val code: Int, val body: ByteArray, val cookie: String?)

    private fun post(url: String, body: ByteArray, cookie: String?): HttpBytes? = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 4000
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            if (cookie != null) setRequestProperty("Cookie", cookie)
        }
        connection.outputStream.use { it.write(body) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        val setCookie = connection.getHeaderField("Set-Cookie")
        connection.disconnect()
        if (code !in 200..299 && bytes.isEmpty()) null else HttpBytes(code, bytes, setCookie)
    } catch (_: Exception) {
        null
    }

    private fun cookieOf(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val part = header.split(';').firstOrNull { it.trim().startsWith("TP_SESSIONID=") } ?: return null
        return part.trim()
    }
}
