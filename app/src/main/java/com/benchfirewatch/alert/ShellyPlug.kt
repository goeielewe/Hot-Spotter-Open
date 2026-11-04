package com.benchfirewatch.alert

import java.net.HttpURLConnection
import java.net.URL

enum class ShellyGeneration {
    AUTO,
    GEN1,
    GEN2,
    ;

    companion object {
        fun fromStored(raw: String?): ShellyGeneration = when (raw?.trim()?.lowercase()) {
            "gen1", "1" -> GEN1
            "gen2", "2", "plus" -> GEN2
            else -> AUTO
        }
    }
}

data class ShellyCallResult(
    val ok: Boolean,
    val detail: String,
    val turnedOn: Boolean,
    val offOk: Boolean,
)

/**
 * Local HTTP only. Alarm calls are off commands. Nothing in [alarmUrls] turns
 * a plug on. Turning on is only for the test button, and only when no alarm
 * is active.
 */
object ShellyPlug {
    fun hostsOf(raw: String): List<String> =
        raw.split(',', ';').mapNotNull { hostOf(it) }.distinct()

    fun hostOf(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        text = text.removePrefix("http://").removePrefix("https://")
        text = text.substringBefore('/').substringBefore('?').substringBefore('#')
        if (text.contains('@') || text.contains(' ') || text.contains('\\')) return null
        if (!text.matches(HOST)) return null
        val port = text.substringAfter(':', "")
        if (port.isNotEmpty() && (port.toIntOrNull() == null || port.toInt() !in 1..65535)) return null
        return text
    }

    fun offUrls(host: String, generation: ShellyGeneration): List<String> {
        val safe = hostOf(host) ?: return emptyList()
        val gen2 = "http://$safe/rpc/Switch.Set?id=0&on=false"
        val gen1 = "http://$safe/relay/0?turn=off"
        return when (generation) {
            ShellyGeneration.GEN2 -> listOf(gen2)
            ShellyGeneration.GEN1 -> listOf(gen1)
            ShellyGeneration.AUTO -> listOf(gen2, gen1)
        }
    }

    fun onUrls(host: String, generation: ShellyGeneration): List<String> {
        val safe = hostOf(host) ?: return emptyList()
        val gen2 = "http://$safe/rpc/Switch.Set?id=0&on=true"
        val gen1 = "http://$safe/relay/0?turn=on"
        return when (generation) {
            ShellyGeneration.GEN2 -> listOf(gen2)
            ShellyGeneration.GEN1 -> listOf(gen1)
            ShellyGeneration.AUTO -> listOf(gen2, gen1)
        }
    }

    /** The only URLs an alarm is allowed to request. */
    fun alarmUrls(host: String, generation: ShellyGeneration): List<String> = offUrls(host, generation)

    fun statusUrl(host: String, generation: ShellyGeneration): String {
        val safe = hostOf(host) ?: return ""
        return if (generation == ShellyGeneration.GEN1) {
            "http://$safe/relay/0"
        } else {
            "http://$safe/rpc/Switch.GetStatus?id=0"
        }
    }

    fun statusSaysOff(body: String): Boolean {
        val compact = body.replace(" ", "")
        return compact.contains("\"output\":false") || compact.contains("\"ison\":false")
    }

    fun statusSaysOn(body: String): Boolean {
        val compact = body.replace(" ", "")
        return compact.contains("\"output\":true") || compact.contains("\"ison\":true")
    }

    fun confirmedOff(host: String, generation: ShellyGeneration): Boolean =
        generations(generation).any { statusSaysOff(get(statusUrl(host, it)).body) }

    fun confirmedOn(host: String, generation: ShellyGeneration): Boolean =
        generations(generation).any { statusSaysOn(get(statusUrl(host, it)).body) }

    private fun generations(generation: ShellyGeneration): List<ShellyGeneration> = when (generation) {
        ShellyGeneration.GEN1 -> listOf(ShellyGeneration.GEN1)
        ShellyGeneration.GEN2 -> listOf(ShellyGeneration.GEN2)
        ShellyGeneration.AUTO -> listOf(ShellyGeneration.GEN2, ShellyGeneration.GEN1)
    }

    fun unreachableMessage(host: String): String =
        "The plug at $host did not answer. Reserve that IP on the router so it stays put."

    fun switchOff(hostRaw: String, generation: ShellyGeneration): ShellyCallResult {
        val hosts = hostsOf(hostRaw)
        if (hosts.isEmpty()) return ShellyCallResult(false, "The plug address is not a host name or IP.", false, false)
        val failed = ArrayList<String>()
        for (host in hosts) {
            if (!sendAny(urlsFor(host, generation, turnOn = false))) failed.add(host)
        }
        if (failed.isEmpty()) {
            val unconfirmed = hosts.filter { !confirmedOff(it, generation) }
            if (unconfirmed.isNotEmpty()) {
                return ShellyCallResult(false, PlugMessages.UNCONFIRMED, false, false)
            }
            return ShellyCallResult(true, if (hosts.size == 1) "Plug off." else "Plugs off.", false, true)
        }
        return ShellyCallResult(
            false,
            failed.joinToString(" ") { unreachableMessage(it) },
            false,
            failed.size < hosts.size,
        )
    }

    /**
     * Off, then on. When [alarmActive] is true the plug is left off.
     */
    fun runTest(hostRaw: String, generation: ShellyGeneration, alarmActive: Boolean): ShellyCallResult {
        val hosts = hostsOf(hostRaw)
        if (hosts.isEmpty()) return ShellyCallResult(false, "The plug address is not a host name or IP.", false, false)
        val notes = ArrayList<String>()
        var anyOff = false
        var allOff = true
        var allOn = true
        for (host in hosts) {
            if (!sendAny(urlsFor(host, generation, turnOn = false))) {
                allOff = false
                allOn = false
                notes.add(unreachableMessage(host))
                continue
            }
            if (!confirmedOff(host, generation)) {
                allOff = false
                allOn = false
                notes.add(PlugMessages.UNCONFIRMED)
                continue
            }
            anyOff = true
            if (alarmActive) continue
            if (!sendAny(urlsFor(host, generation, turnOn = true)) || !confirmedOn(host, generation)) {
                allOn = false
                notes.add("Turned $host off, but turning it back on failed. It may still be off.")
            }
        }
        if (alarmActive) {
            val detail = if (allOff) {
                "Plug off. Left off because an alarm is active."
            } else {
                notes.joinToString(" ")
            }
            return ShellyCallResult(allOff, detail, false, anyOff)
        }
        if (!anyOff) return ShellyCallResult(false, notes.joinToString(" "), false, false)
        if (!allOn) {
            val detail = notes.joinToString(" ").ifBlank {
                "Turned the plug off, but turning it back on failed. It may still be off."
            }
            return ShellyCallResult(false, detail, false, true)
        }
        val detail = if (hosts.size == 1) "Plug turned off, then back on." else "Plugs turned off, then back on."
        return ShellyCallResult(true, detail, true, true)
    }

    fun probe(hostRaw: String): Boolean = unreachableHosts(hostRaw).isEmpty() && hostsOf(hostRaw).isNotEmpty()

    fun unreachableHosts(hostRaw: String): List<String> = hostsOf(hostRaw).filter { host ->
        get("http://$host/shelly").code !in 200..299
    }

    private fun urlsFor(host: String, generation: ShellyGeneration, turnOn: Boolean): List<String> {
        val resolved = if (generation == ShellyGeneration.AUTO) detect(host) else generation
        val gen = if (resolved == ShellyGeneration.AUTO) ShellyGeneration.AUTO else resolved
        return if (turnOn) onUrls(host, gen) else offUrls(host, gen)
    }

    private fun detect(host: String): ShellyGeneration {
        val http = get("http://$host/shelly")
        if (http.code !in 200..299) return ShellyGeneration.AUTO
        val body = http.body.replace(" ", "")
        return if (body.contains("\"gen\":2") || body.contains("\"gen\":3")) ShellyGeneration.GEN2 else ShellyGeneration.GEN1
    }

    private fun sendAny(urls: List<String>): Boolean {
        for (url in urls) {
            val http = get(url)
            if (http.code in 200..299) return true
        }
        return false
    }

    internal fun get(url: String, timeoutMs: Int = 2000): ShellyHttp {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ShellyHttp(0, "", "bad url")
        }
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", "HotSpotter/1.0")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            connection.disconnect()
            ShellyHttp(code, body, null)
        } catch (e: Exception) {
            ShellyHttp(0, "", e.message ?: "failed")
        }
    }

    private val HOST = Regex("^[A-Za-z0-9._-]+(?::[0-9]{1,5})?$")
}

data class ShellyHttp(val code: Int, val body: String, val error: String?)
