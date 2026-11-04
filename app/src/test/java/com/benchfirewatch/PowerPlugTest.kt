package com.benchfirewatch

import com.benchfirewatch.alert.HomeAssistantPlug
import com.benchfirewatch.alert.KasaCipher
import com.benchfirewatch.alert.KlapCrypto
import com.benchfirewatch.alert.PlugEndpoint
import com.benchfirewatch.alert.PlugKind
import com.benchfirewatch.alert.PlugMessages
import com.benchfirewatch.alert.PowerPlugs
import com.benchfirewatch.alert.ShellyGeneration
import com.benchfirewatch.alert.ShellyPlug
import com.benchfirewatch.alert.TasmotaPlug
import com.benchfirewatch.alert.WemoPlug
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerPlugTest {
    @Test
    fun olderShellySaveBecomesTheShellyKind() {
        assertEquals(PlugKind.SHELLY, PlugKind.fromStored(null, shellyWasEnabled = true))
        assertEquals(PlugKind.NONE, PlugKind.fromStored(null, shellyWasEnabled = false))
        assertEquals(PlugKind.KASA, PlugKind.fromStored("kasa", shellyWasEnabled = true))
        assertEquals(PlugKind.HOME_ASSISTANT, PlugKind.fromStored("home_assistant", false))
    }

    @Test
    fun shellyAlarmUrlsStillOnlyTurnOffAndStatusIsReadBack() {
        assertTrue(ShellyPlug.statusSaysOff("""{"output": false}"""))
        assertTrue(ShellyPlug.statusSaysOff("""{"ison":false}"""))
        assertTrue(ShellyPlug.statusSaysOn("""{"output": true}"""))
        assertFalse(ShellyPlug.statusSaysOff("""{"output": true}"""))
        assertTrue(ShellyPlug.statusUrl("192.168.1.50", ShellyGeneration.GEN2).contains("Switch.GetStatus"))
        assertTrue(ShellyPlug.statusUrl("192.168.1.50", ShellyGeneration.GEN1).endsWith("/relay/0"))
        val alarm = ShellyPlug.alarmUrls("192.168.1.50", ShellyGeneration.AUTO).joinToString(" ")
        assertFalse(alarm.contains("on=true"))
        assertFalse(alarm.contains("turn=on"))
    }

    @Test
    fun kasaCipherRoundTripsAndDoesNotTreatOnAsOff() {
        val plain = KasaCipher.OFF
        val back = KasaCipher.decode(KasaCipher.encode(plain))
        assertEquals(plain, back)
        assertEquals(0xD0.toByte(), KasaCipher.encode("{")[0])
        assertTrue(KasaCipher.saysOff("""{"relay_state":0}"""))
        assertFalse(KasaCipher.saysOff("""{"relay_state":1}"""))
        assertFalse(KasaCipher.OFF.contains("state\":1"))
    }

    @Test
    fun klapHandshakeSwapsTheSeedOrderAndSealsAFrame() {
        val local = ByteArray(16) { it.toByte() }
        val remote = ByteArray(16) { (15 - it).toByte() }
        val auth = KlapCrypto.authHash("person@example.com", "secret-pass")
        val check = KlapCrypto.handshake1Check(local, remote, auth)
        val reply = KlapCrypto.handshake2Payload(local, remote, auth)
        assertEquals(32, check.size)
        assertEquals(32, reply.size)
        assertFalse(check.contentEquals(reply))
        val keys = KlapCrypto.keys(local, remote, auth)
        assertEquals(16, keys.key.size)
        assertEquals(12, keys.ivBase.size)
        assertEquals(28, keys.sig.size)
        val (next, sealed) = KlapCrypto.seal(keys, KlapCrypto.offJson(1L).toByteArray())
        assertTrue(sealed.size > 32)
        val opened = KlapCrypto.open(next, sealed)
        assertEquals(KlapCrypto.offJson(1L), opened!!.toString(Charsets.UTF_8))
        assertTrue(KlapCrypto.saysOff("""{"result":{"device_on":false}}"""))
        assertFalse(KlapCrypto.offJson(1L).contains("true"))
        assertFalse(String(sealed).contains("secret-pass"))
    }

    @Test
    fun tasmotaAndHomeAssistantAndWemoCommandsStayOffAndKeepSecretsOutOfTheText() {
        assertEquals("cmnd=Power+Off", TasmotaPlug.commandQuery("Power Off"))
        assertTrue(TasmotaPlug.saysOff("""{"POWER":"OFF"}"""))
        assertFalse(TasmotaPlug.saysOff("""{"POWER":"ON"}"""))
        assertEquals("/api/services/switch/turn_off", HomeAssistantPlug.servicePath("switch.bench_charger", turnOn = false))
        assertEquals("""{"entity_id":"switch.bench_charger"}""", HomeAssistantPlug.body("switch.bench_charger"))
        assertTrue(HomeAssistantPlug.saysOff("""{"state": "off"}"""))
        assertEquals("http://192.168.1.20:8123", HomeAssistantPlug.baseUrl("http://192.168.1.20:8123/"))
        assertEquals(null, HomeAssistantPlug.baseUrl("http://user:token@192.168.1.20:8123"))
        assertEquals("switch.bench_charger", HomeAssistantPlug.entityId("switch.bench_charger"))
        assertEquals(null, HomeAssistantPlug.entityId("../secrets"))
        val soap = WemoPlug.setEnvelope(on = false)
        assertTrue(soap.contains(">0<"))
        assertTrue(soap.contains("SetBinaryState"))
        assertFalse(soap.contains(">1<"))
        assertTrue(WemoPlug.saysOff("<BinaryState>0|123</BinaryState>"))
        assertFalse(WemoPlug.saysOff("<BinaryState>1</BinaryState>"))
        val endpoint = PlugEndpoint(PlugKind.TAPO, "192.168.1.40", secret = "")
        assertFalse(PowerPlugs.configured(endpoint))
        assertTrue(PowerPlugs.cut(endpoint).detail.contains("password"))
        assertFalse(PowerPlugs.cut(endpoint).detail.contains("secret-pass"))
        assertEquals(
            PlugMessages.UNCONFIRMED,
            "The plug did not confirm it turned off. The charger may still be on.",
        )
    }
}
