package com.benchfirewatch.alert

/**
 * One local plug the watch can turn off. Alarm calls only [PowerPlugs.cut].
 * Turning a plug back on is the test button, and only when no alarm is active.
 * A cut counts only when the plug's own status read says off.
 */
enum class PlugKind(val id: String, val label: String) {
    NONE("none", "Off"),
    SHELLY("shelly", "Shelly"),
    KASA("kasa", "Kasa"),
    TAPO("tapo", "Tapo"),
    TASMOTA("tasmota", "Tasmota"),
    HOME_ASSISTANT("home_assistant", "Home Assistant"),
    WEMO("wemo", "Wemo"),
    ;

    companion object {
        fun fromStored(kind: String?, shellyWasEnabled: Boolean): PlugKind {
            val found = entries.firstOrNull { it.id == kind?.trim()?.lowercase() }
            if (found != null && found != NONE) return found
            if (kind.isNullOrBlank() && shellyWasEnabled) return SHELLY
            return found ?: NONE
        }
    }
}

data class PlugEndpoint(
    val kind: PlugKind,
    val address: String,
    val shellyGeneration: ShellyGeneration = ShellyGeneration.AUTO,
    val username: String = "",
    val secret: String = "",
    val entityId: String = "",
)

data class PlugOutcome(
    val confirmedOff: Boolean,
    val confirmedOn: Boolean,
    val detail: String,
)

object PlugMessages {
    const val UNCONFIRMED = "The plug did not confirm it turned off. The charger may still be on."
}

object PowerPlugs {
    fun configured(endpoint: PlugEndpoint): Boolean = when (endpoint.kind) {
        PlugKind.NONE -> false
        PlugKind.SHELLY -> ShellyPlug.hostsOf(endpoint.address).isNotEmpty()
        PlugKind.HOME_ASSISTANT ->
            HomeAssistantPlug.baseUrl(endpoint.address) != null &&
                HomeAssistantPlug.entityId(endpoint.entityId) != null &&
                endpoint.secret.isNotBlank()
        PlugKind.TAPO -> ShellyPlug.hostOf(endpoint.address) != null && endpoint.secret.isNotBlank()
        PlugKind.KASA, PlugKind.TASMOTA, PlugKind.WEMO -> ShellyPlug.hostOf(endpoint.address) != null
    }

    fun cut(endpoint: PlugEndpoint): PlugOutcome {
        if (!configured(endpoint)) {
            return PlugOutcome(false, false, missing(endpoint))
        }
        val outcome = when (endpoint.kind) {
            PlugKind.SHELLY -> shellyCut(endpoint)
            PlugKind.KASA -> KasaPlug.cut(endpoint.address)
            PlugKind.TAPO -> TapoPlug.cut(endpoint.address, endpoint.username, endpoint.secret)
            PlugKind.TASMOTA -> TasmotaPlug.cut(endpoint.address, endpoint.username, endpoint.secret)
            PlugKind.HOME_ASSISTANT -> HomeAssistantPlug.cut(endpoint.address, endpoint.entityId, endpoint.secret)
            PlugKind.WEMO -> WemoPlug.cut(endpoint.address)
            PlugKind.NONE -> PlugOutcome(false, false, missing(endpoint))
        }
        return if (outcome.confirmedOff) outcome else outcome.copy(confirmedOff = false, detail = outcome.detail.ifBlank { PlugMessages.UNCONFIRMED })
    }

    fun test(endpoint: PlugEndpoint, alarmActive: Boolean): PlugOutcome {
        if (!configured(endpoint)) return PlugOutcome(false, false, missing(endpoint))
        return when (endpoint.kind) {
            PlugKind.SHELLY -> shellyTest(endpoint, alarmActive)
            PlugKind.KASA -> KasaPlug.test(endpoint.address, alarmActive)
            PlugKind.TAPO -> TapoPlug.test(endpoint.address, endpoint.username, endpoint.secret, alarmActive)
            PlugKind.TASMOTA -> TasmotaPlug.test(endpoint.address, endpoint.username, endpoint.secret, alarmActive)
            PlugKind.HOME_ASSISTANT -> HomeAssistantPlug.test(endpoint.address, endpoint.entityId, endpoint.secret, alarmActive)
            PlugKind.WEMO -> WemoPlug.test(endpoint.address, alarmActive)
            PlugKind.NONE -> PlugOutcome(false, false, missing(endpoint))
        }
    }

    /** Status read only. Does not send an off command. */
    fun confirmedOff(endpoint: PlugEndpoint): Boolean {
        if (!configured(endpoint)) return false
        return when (endpoint.kind) {
            PlugKind.SHELLY -> ShellyPlug.hostsOf(endpoint.address).all {
                ShellyPlug.confirmedOff(it, endpoint.shellyGeneration)
            }
            PlugKind.KASA -> KasaPlug.isOff(endpoint.address)
            PlugKind.TAPO -> TapoPlug.isOff(endpoint.address, endpoint.username, endpoint.secret)
            PlugKind.TASMOTA -> TasmotaPlug.isOff(endpoint.address, endpoint.username, endpoint.secret)
            PlugKind.HOME_ASSISTANT -> HomeAssistantPlug.isOff(endpoint.address, endpoint.entityId, endpoint.secret)
            PlugKind.WEMO -> WemoPlug.isOff(endpoint.address)
            PlugKind.NONE -> false
        }
    }

    fun reachable(endpoint: PlugEndpoint): Boolean {
        if (!configured(endpoint)) return false
        return when (endpoint.kind) {
            PlugKind.SHELLY -> ShellyPlug.probe(endpoint.address)
            PlugKind.KASA -> KasaPlug.reachable(endpoint.address)
            PlugKind.TAPO -> TapoPlug.reachable(endpoint.address, endpoint.username, endpoint.secret)
            PlugKind.TASMOTA -> TasmotaPlug.reachable(endpoint.address, endpoint.username, endpoint.secret)
            PlugKind.HOME_ASSISTANT -> HomeAssistantPlug.reachable(endpoint.address, endpoint.entityId, endpoint.secret)
            PlugKind.WEMO -> WemoPlug.reachable(endpoint.address)
            PlugKind.NONE -> false
        }
    }

    private fun shellyCut(endpoint: PlugEndpoint): PlugOutcome {
        val result = ShellyPlug.switchOff(endpoint.address, endpoint.shellyGeneration)
        return PlugOutcome(result.ok && result.offOk, false, result.detail)
    }

    private fun shellyTest(endpoint: PlugEndpoint, alarmActive: Boolean): PlugOutcome {
        val result = ShellyPlug.runTest(endpoint.address, endpoint.shellyGeneration, alarmActive)
        return PlugOutcome(result.offOk, result.turnedOn, result.detail)
    }

    private fun missing(endpoint: PlugEndpoint): String = when (endpoint.kind) {
        PlugKind.NONE -> "Pick a plug in Settings, then try again."
        PlugKind.HOME_ASSISTANT -> "Enter the Home Assistant address, the entity id, and a long-lived token."
        PlugKind.TAPO -> "Enter the Tapo address and the local password."
        else -> "Enter the plug address, then try again."
    }
}
