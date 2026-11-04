package com.benchfirewatch

import com.benchfirewatch.alert.AlertOutbox
import com.benchfirewatch.alert.NtfyAlert
import com.benchfirewatch.alert.ShellyGeneration
import com.benchfirewatch.alert.ShellyPlug
import com.benchfirewatch.audio.VentDetector
import com.benchfirewatch.evidence.EvidenceFrames
import com.benchfirewatch.evidence.EvidenceRing
import com.benchfirewatch.evidence.EvidenceSample
import com.benchfirewatch.evidence.GifClip
import com.benchfirewatch.health.PhoneHealth
import com.benchfirewatch.health.PhoneHealthInput
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.selftest.CheckMark
import com.benchfirewatch.selftest.SelfTestInput
import com.benchfirewatch.selftest.SelfTestScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ExtrasTest {
    @Test
    fun ntfyMessageCarriesTypeTempTimeAndMaxPriority() {
        val zone = TimeZone.getTimeZone("UTC")
        val message = NtfyAlert.message(MonitorStatus.FLAME, 109.3f, 0L, "bench-topic", zone)
        assertNotNull(message)
        message!!
        assertEquals("https://ntfy.sh/bench-topic", message.url)
        assertTrue(message.body.contains("FLAME"))
        assertTrue(message.body.contains("Max 109°C"))
        assertTrue(message.body.contains("1970-01-01 00:00:00"))
        assertEquals(NtfyAlert.PRIORITY_MAX, message.priority)
        assertEquals("5", message.priority)
        assertTrue(NtfyAlert.repeats(MonitorStatus.FLAME))
        assertTrue(NtfyAlert.repeats(MonitorStatus.ALARM))
        assertTrue(NtfyAlert.repeats(MonitorStatus.FAULT))
        assertFalse(NtfyAlert.repeats(MonitorStatus.WARNING))
        assertNull(NtfyAlert.url("ftp://ntfy.sh", "topic"))
        assertNull(NtfyAlert.url("https://user:pass@ntfy.sh", "topic"))
        assertNull(NtfyAlert.url("https://ntfy.sh", ""))
        assertNull(NtfyAlert.url("https://ntfy.sh", "../other"))
        val day = 24L * 60L * 60L * 1000L
        assertEquals(15 * 60 * 1000L, NtfyAlert.REPEAT_MS)
        assertTrue(day / NtfyAlert.REPEAT_MS <= NtfyAlert.FREE_DAILY_MESSAGES)
        val topic = NtfyAlert.newTopic()
        assertTrue(topic.startsWith("hs"))
        assertTrue(topic.length >= 32)
        assertNotNull(NtfyAlert.url(NtfyAlert.DEFAULT_SERVER, topic))
        assertTrue(NtfyAlert.newTopic() != topic)
        val headers = NtfyAlert.headers(message)
        assertFalse(headers.keys.any { key ->
            val name = key.lowercase()
            name == "authorization" || name.contains("email") || name.contains("call")
        })
        assertEquals("https://ntfy.sh/bench-topic", message.url)
    }

    @Test
    fun outboxRetriesUntilAckAndDropsAFailedTest() {
        val box = AlertOutbox()
        val alarm = NtfyAlert.message(MonitorStatus.ALARM, 80f, 1_000L, "topic")!!
        val item = box.enqueue(alarm, repeat = true, label = "alarm", nowMs = 0L)
        assertEquals(1, box.due(0L).size)
        box.markFailed(item.id, 0L)
        assertTrue(box.due(4_999L).isEmpty())
        assertEquals(1, box.due(5_000L).size)
        box.markSent(item.id, 5_000L, repeatMs = 60_000L)
        assertTrue(box.due(64_999L).isEmpty())
        assertEquals(1, box.due(65_000L).size)
        box.acknowledge()
        assertTrue(box.due(9_999_999L).isEmpty())

        val test = NtfyAlert.testMessage(20f, 0L, "topic")!!
        val once = box.enqueue(test, repeat = false, label = "test", nowMs = 0L)
        repeat(4) { box.markFailed(once.id, 0L) }
        assertTrue(box.snapshot().none { it.id == once.id })

        val again = box.enqueue(alarm, repeat = true, label = "alarm", nowMs = 10L)
        val raw = box.encode()
        val restored = AlertOutbox()
        restored.load(raw)
        assertEquals(again.url, restored.snapshot().single().url)
        assertTrue(restored.snapshot().single().repeat)
    }

    @Test
    fun shellyAlarmUrlsOnlyTurnOff() {
        val host = "192.168.1.50"
        assertEquals(host, ShellyPlug.hostOf("http://192.168.1.50/rpc"))
        assertNull(ShellyPlug.hostOf("http://user:pass@192.168.1.50"))
        assertNull(ShellyPlug.hostOf("not a host"))
        val gen2 = ShellyPlug.alarmUrls(host, ShellyGeneration.GEN2)
        assertEquals(listOf("http://192.168.1.50/rpc/Switch.Set?id=0&on=false"), gen2)
        val gen1 = ShellyPlug.alarmUrls(host, ShellyGeneration.GEN1)
        assertEquals(listOf("http://192.168.1.50/relay/0?turn=off"), gen1)
        val auto = ShellyPlug.alarmUrls(host, ShellyGeneration.AUTO)
        assertEquals(2, auto.size)
        val alarmText = auto.joinToString(" ")
        assertTrue(alarmText.contains("on=false"))
        assertTrue(alarmText.contains("turn=off"))
        assertFalse(alarmText.contains("on=true"))
        assertFalse(alarmText.contains("turn=on"))
        assertTrue(ShellyPlug.onUrls(host, ShellyGeneration.GEN2).single().contains("on=true"))
        assertTrue(ShellyPlug.unreachableMessage(host).contains("Reserve that IP"))
        val both = ShellyPlug.hostsOf("192.168.1.50, 192.168.1.51")
        assertEquals(listOf("192.168.1.50", "192.168.1.51"), both)
        val offs = both.flatMap { ShellyPlug.alarmUrls(it, ShellyGeneration.GEN2) }
        assertEquals(2, offs.size)
        assertTrue(offs.all { it.contains("on=false") && !it.contains("on=true") })
    }

    @Test
    fun evidenceRingDropsSamplesOlderThanTenSecondsAndGifStartsWithTheHeader() {
        val ring = EvidenceRing(10_000L)
        ring.add(sample(0L, 0))
        ring.add(sample(10_000L, 1))
        ring.add(sample(10_001L, 2))
        val kept = ring.copy()
        assertTrue(kept.none { it.timeMs == 0L })
        assertTrue(kept.any { it.timeMs == 10_000L })
        val gif = GifClip.encode(kept)
        assertNotNull(gif)
        val bytes = gif!!
        assertEquals("GIF89a", bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII))
        assertEquals(0x3B, bytes.last().toInt() and 0xFF)
        val decoded = decodeGif(bytes)
        assertEquals(kept.size, decoded.size)
        assertEquals(2, decoded.first().size)
        assertEquals(1, decoded[0][0])
    }

    @Test
    fun ventClickAndHissWarnsAndSilenceOrAToneDoesNot() {
        assertEquals(MonitorStatus.WARNING, VentDetector.tier())
        assertFalse(VentDetector.soleAlarmTrigger())
        assertEquals(4, VentDetector.boostedSensitivity(3, boostActive = true))
        assertEquals(5, VentDetector.boostedSensitivity(5, boostActive = true))
        assertEquals(3, VentDetector.boostedSensitivity(3, boostActive = false))

        val rate = 16_000
        val hop = VentDetector.hopSamples(rate)
        assertTrue(VentDetector(3).push(noise(hop, 20_000, 3) + noise(hop * 8, 8_000, 9), rate * 2, rate))
        assertFalse(VentDetector(3).push(ShortArray(hop * 30), hop * 30, rate))
        assertFalse(VentDetector(3).push(tone(hop * 40, rate, 1_000, 16_000), hop * 40, rate))
        assertFalse(VentDetector(3).push(noise(hop, 20_000, 1) + ShortArray(hop * 20), hop * 21, rate))
    }

    @Test
    fun phoneHealthWarningsAreNotAFullAlarm() {
        assertFalse(PhoneHealth.isFullAlarm())
        val health = PhoneHealth()
        val low = health.poll(input(battery = 15, charging = true, known = true, now = 0L))
        assertEquals(listOf("Phone battery is low (15%)."), low.fresh.map { it.message })
        assertTrue(health.poll(input(battery = 15, charging = true, known = true, now = 1_000L)).fresh.isEmpty())
        val unplugged = health.poll(input(battery = 80, charging = false, known = true, now = 2_000L))
        assertTrue(unplugged.showing.any { it.contains("not charging") })
        assertTrue(unplugged.fresh.none { it.message.contains("FLAME") })

        val lens = PhoneHealth()
        val early = lens.poll(input(range = 0.4f, changing = true, hardware = true, now = 0L))
        assertTrue(early.fresh.none { it.key == "lens" })
        val later = lens.poll(input(range = 0.4f, changing = true, hardware = true, now = 15_000L))
        assertTrue(later.fresh.any { it.key == "lens" })
        val frozen = PhoneHealth()
        val stuck = frozen.poll(input(range = 0.2f, changing = false, hardware = true, now = 20_000L))
        assertTrue(stuck.fresh.none { it.key == "lens" })
        val demo = PhoneHealth()
        val quiet = demo.poll(input(range = 0.2f, changing = true, hardware = false, now = 20_000L))
        assertTrue(quiet.fresh.none { it.key == "lens" })
        val hot = PhoneHealth()
        val warm = hot.poll(input(thermal = PhoneHealth.THERMAL_MODERATE, now = 0L))
        assertTrue(warm.fresh.any { it.key == "thermal" })
    }

    @Test
    fun selfTestScoresPassFailAndSkip() {
        val passed = SelfTestScore.score(
            SelfTestInput(
                watchRunning = true,
                sawHeat = true,
                flamePathOk = true,
                sirenEnabled = true,
                sirenOk = true,
                alarmAlreadySounding = false,
                remoteConfigured = false,
                remoteOk = false,
                plugConfigured = true,
                plugOffOk = true,
                plugOnOk = true,
                leftOffForAlarm = false,
            ),
        )
        assertFalse(passed.failed)
        assertEquals(CheckMark.SKIP, passed.steps.first { it.name == "Remote Alert" }.mark)
        assertEquals(CheckMark.PASS, passed.steps.first { it.name == "Plug" }.mark)
        assertTrue(passed.summary().startsWith("Self-test passed"))

        val failed = SelfTestScore.score(
            SelfTestInput(
                watchRunning = true,
                sawHeat = false,
                flamePathOk = false,
                sirenEnabled = false,
                sirenOk = false,
                alarmAlreadySounding = false,
                remoteConfigured = true,
                remoteOk = false,
                plugConfigured = false,
                plugOffOk = false,
                plugOnOk = false,
                leftOffForAlarm = false,
            ),
        )
        assertTrue(failed.failed)
        assertEquals(CheckMark.FAIL, failed.steps.first { it.name == "Thermal Pickup" }.mark)
        assertEquals(CheckMark.SKIP, failed.steps.first { it.name == "Siren" }.mark)
        assertEquals(CheckMark.SKIP, failed.steps.first { it.name == "Plug" }.mark)
        assertTrue(failed.summary().startsWith("Self-test failed"))

        val leftOff = SelfTestScore.score(
            SelfTestInput(
                watchRunning = true,
                sawHeat = true,
                flamePathOk = true,
                sirenEnabled = true,
                sirenOk = false,
                alarmAlreadySounding = true,
                remoteConfigured = false,
                remoteOk = false,
                plugConfigured = true,
                plugOffOk = true,
                plugOnOk = false,
                leftOffForAlarm = true,
            ),
        )
        assertFalse(leftOff.failed)
        assertTrue(leftOff.steps.first { it.name == "Plug" }.detail.contains("left off"))
    }

    private fun sample(timeMs: Long, index: Int): EvidenceSample =
        EvidenceSample(timeMs, 2, 1, byteArrayOf(index.toByte(), index.toByte()), null)

    private fun input(
        battery: Int? = 80,
        charging: Boolean = true,
        known: Boolean = true,
        thermal: Int = -1,
        range: Float? = 8f,
        changing: Boolean = true,
        hardware: Boolean = false,
        now: Long = 0L,
    ) = PhoneHealthInput(battery, charging, known, thermal, range, changing, hardware, now)

    private fun noise(count: Int, amp: Int, seed: Int): ShortArray {
        var state = seed
        return ShortArray(count) {
            state = state * 1103515245 + 12345
            val unit = ((state ushr 16) and 0x7FFF) / 32767f
            ((unit * 2f - 1f) * amp).toInt().toShort()
        }
    }

    private fun tone(count: Int, sampleRate: Int, hz: Int, amp: Int): ShortArray {
        return ShortArray(count) { i ->
            val angle = 2.0 * Math.PI * hz * i / sampleRate
            (Math.sin(angle) * amp).toInt().toShort()
        }
    }
}

private fun decodeGif(bytes: ByteArray): List<IntArray> {
    var i = 13
    val packed = bytes[10].toInt() and 0xFF
    if (packed and 0x80 != 0) {
        val colors = 1 shl ((packed and 7) + 1)
        i += colors * 3
    }
    val frames = ArrayList<IntArray>()
    while (i < bytes.size) {
        when (bytes[i].toInt() and 0xFF) {
            0x3B -> break
            0x21 -> {
                i += 2
                i = skipBlocks(bytes, i)
            }
            0x2C -> {
                i += 9
                val local = bytes[i].toInt() and 0xFF
                i += 1
                if (local and 0x80 != 0) i += (1 shl ((local and 7) + 1)) * 3
                val minCode = bytes[i].toInt() and 0xFF
                i += 1
                val lzw = ArrayList<Byte>()
                while (i < bytes.size) {
                    val n = bytes[i].toInt() and 0xFF
                    i += 1
                    if (n == 0) break
                    repeat(n) { lzw.add(bytes[i++]) }
                }
                val width = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
                val height = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
                frames += lzwDecode(lzw.toByteArray(), minCode, width * height)
            }
            else -> break
        }
    }
    return frames
}

private fun skipBlocks(bytes: ByteArray, start: Int): Int {
    var i = start
    while (i < bytes.size) {
        val n = bytes[i].toInt() and 0xFF
        i += 1 + n
        if (n == 0) break
    }
    return i
}

private fun lzwDecode(data: ByteArray, minCodeSize: Int, expected: Int): IntArray {
    val clear = 1 shl minCodeSize
    val eoi = clear + 1
    var bits = minCodeSize + 1
    var next = eoi + 1
    val dict = HashMap<Int, IntArray>()
    for (c in 0 until clear) dict[c] = intArrayOf(c)
    var acc = 0
    var accBits = 0
    var pos = 0
    fun read(): Int {
        while (accBits < bits) {
            if (pos >= data.size) return -1
            acc = acc or ((data[pos].toInt() and 0xFF) shl accBits)
            accBits += 8
            pos += 1
        }
        val mask = (1 shl bits) - 1
        val code = acc and mask
        acc = acc ushr bits
        accBits -= bits
        return code
    }
    val out = ArrayList<Int>()
    fun take(code: Int) {
        val entry = dict[code] ?: return
        entry.forEach { out.add(it) }
    }
    var code = read()
    if (code != clear) return IntArray(0)
    code = read()
    if (code < 0 || code == eoi) return IntArray(0)
    take(code)
    var prev = code
    while (out.size < expected) {
        val sizeBefore = bits
        code = read()
        if (code < 0 || code == eoi) break
        if (code == clear) {
            for (c in clear until next) dict.remove(c)
            next = eoi + 1
            bits = minCodeSize + 1
            val first = read()
            if (first < 0 || first == eoi) break
            take(first)
            prev = first
            continue
        }
        val entry = when {
            dict[code] != null -> dict[code]!!
            code == next -> dict[prev]!! + dict[prev]!![0]
            else -> break
        }
        entry.forEach { out.add(it) }
        if (next < 4096 && prev >= 0) {
            val prefix = dict[prev] ?: intArrayOf()
            dict[next] = prefix + entry[0]
            next += 1
            if (next > (1 shl bits) && bits < 12) bits += 1
        }
        prev = code
        if (sizeBefore != bits) {
            // The code just read used the old width. The bump applies to the next read.
        }
    }
    return out.toIntArray()
}
