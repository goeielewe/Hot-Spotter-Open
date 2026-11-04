package com.benchfirewatch.data

import android.content.Context
import com.benchfirewatch.alert.NtfyAlert
import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.model.CameraChoice
import com.benchfirewatch.model.WatchSettings
import com.benchfirewatch.model.Zone
import com.benchfirewatch.model.PaletteChoice
import com.benchfirewatch.model.ViewMode
import com.benchfirewatch.model.WatchProfile
import com.benchfirewatch.model.ZoneKind
import com.benchfirewatch.render.PictureRotation
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): AppPrefs {
        val raw = prefs.getString(KEY_PREFS, null) ?: return rememberTopic(AppPrefs())
        return try {
            rememberTopic(decode(JSONObject(raw)))
        } catch (_: Exception) {
            AppPrefs()
        }
    }

    fun save(prefsModel: AppPrefs) {
        prefs.edit().putString(KEY_PREFS, encode(prefsModel).toString()).apply()
    }

    private fun rememberTopic(model: AppPrefs): AppPrefs {
        if (model.ntfyTopic.isNotBlank()) return model
        val filled = model.copy(ntfyTopic = NtfyAlert.newTopic())
        prefs.edit().putString(KEY_PREFS, encode(filled).toString()).apply()
        return filled
    }

    fun hasSavedPrefs(): Boolean = prefs.contains(KEY_PREFS)

    private fun encode(model: AppPrefs): JSONObject {
        val s = model.settings
        return JSONObject().apply {
            put("settings", JSONObject().apply {
                put("blobDeltaC", s.blobDeltaC)
                put("backgroundPercentile", s.backgroundPercentile)
                put("minBlobPixels", s.minBlobPixels)
                put("cellMinAreaFraction", s.cellMinAreaFraction)
                put("toolMaxAreaFraction", s.toolMaxAreaFraction)
                put("movingToolMaxAreaFraction", s.movingToolMaxAreaFraction)
                put("toolContrastC", s.toolContrastC)
                put("toolAbsC", s.toolAbsC)
                put("toolSharpnessC", s.toolSharpnessC)
                put("toolMinSpeed", s.toolMinSpeed)
                put("toolTransientSec", s.toolTransientSec)
                put("toolProximity", s.toolProximity)
                put("coreTempC", s.coreTempC)
                put("coreHaloPx", s.coreHaloPx)
                put("haloCapC", s.haloCapC)
                put("matchDistance", s.matchDistance)
                put("coastSec", s.coastSec)
                put("selfHeatMinTempC", s.selfHeatMinTempC)
                put("selfHeatRateCPerSec", s.selfHeatRateCPerSec)
                put("selfHeatWindowSec", s.selfHeatWindowSec)
                put("selfHeatSustainSec", s.selfHeatSustainSec)
                put("warnTempC", s.warnTempC)
                put("warnSustainSec", s.warnSustainSec)
                put("alarmTempC", s.alarmTempC)
                put("alarmSustainSec", s.alarmSustainSec)
                put("warningEscalateSec", s.warningEscalateSec)
                put("runawayRateCPerSec", s.runawayRateCPerSec)
                put("runawayWindowSec", s.runawayWindowSec)
                put("runawaySustainSec", s.runawaySustainSec)
                put("runawayTempC", s.runawayTempC)
                put("solderGraceSec", s.solderGraceSec)
                put("solderWarnC", s.solderWarnC)
                put("solderAlarmC", s.solderAlarmC)
                put("solderSelfHeatRate", s.solderSelfHeatRate)
                put("minCoolingSlope", s.minCoolingSlope)
                put("coolingWatchSec", s.coolingWatchSec)
                put("chargeWarnC", s.chargeWarnC)
                put("chargeAlarmC", s.chargeAlarmC)
                put("chargeRunawayC", s.chargeRunawayC)
                put("chargeGraceSec", s.chargeGraceSec)
                put("chargeSustainSec", s.chargeSustainSec)
                put("chargeEscalateSec", s.chargeEscalateSec)
                put("restingHotWarnSec", s.restingHotWarnSec)
                put("restingHotAlarmSec", s.restingHotAlarmSec)
                put("frozenFrameSec", s.frozenFrameSec)
                put("reconnectGraceSec", s.reconnectGraceSec)
                put("firstConnectGraceSec", s.firstConnectGraceSec)
                put("rearmAfterAckSec", s.rearmAfterAckSec)
                put("memoryKeepSec", s.memoryKeepSec)
                put("flameSensitivity", s.flameSensitivity)
                put("flameWholeFrame", s.flameWholeFrame)
            })
            put("zones", JSONArray().apply {
                model.zones.forEach { z ->
                    put(JSONObject().apply {
                        put("id", z.id)
                        put("name", z.name)
                        put("kind", z.kind.name)
                        put("left", z.left)
                        put("top", z.top)
                        put("right", z.right)
                        put("bottom", z.bottom)
                    })
                }
            })
            put("useCamera", model.useCamera)
            put("cameraId", model.cameraId)
            put("scenarioId", model.scenarioId)
            put("demoSpeed", model.demoSpeed)
            put("ntfyUrl", model.ntfyUrl)
            put("webhookUrl", model.webhookUrl)
            put("sirenEnabled", model.sirenEnabled)
            put("keepScreenOn", model.keepScreenOn)
            put("startOnBoot", model.startOnBoot)
            put("emissivity", model.emissivity)
            put("distanceM", model.distanceM)
            put("logHz", model.logHz)
            put("logAlarmSnapshots", model.logAlarmSnapshots)
            put("viewMode", model.viewMode.id)
            put("palette", model.palette.id)
            put("rotationDegrees", model.rotationDegrees)
            put("showStats", model.showStats)
            put("remoteAlertsEnabled", model.remoteAlertsEnabled)
            put("ntfyServer", model.ntfyServer)
            put("ntfyTopic", model.ntfyTopic)
            put("plugKind", model.plugKind)
            put("plugHost", model.plugHost)
            put("plugUsername", model.plugUsername)
            put("plugEntity", model.plugEntity)
            put("shellyEnabled", model.shellyEnabled)
            put("shellyHost", model.shellyHost)
            put("shellyGeneration", model.shellyGeneration)
            put("alarmClipsEnabled", model.alarmClipsEnabled)
            put("ventListenerEnabled", model.ventListenerEnabled)
            put("ventSensitivity", model.ventSensitivity)
            put("selfTestReminder", model.selfTestReminder)
            put("nightView", model.nightView)
            put("useFahrenheit", model.useFahrenheit)
            put("watchProfileId", model.watchProfileId)
            put("tutorialSeen", model.tutorialSeen)
        }
    }

    private fun decode(json: JSONObject): AppPrefs {
        val d = WatchSettings()
        val s = json.optJSONObject("settings") ?: JSONObject()
        val settings = WatchSettings(
            blobDeltaC = s.optDouble("blobDeltaC", d.blobDeltaC.toDouble()).toFloat(),
            backgroundPercentile = s.optDouble("backgroundPercentile", d.backgroundPercentile.toDouble()).toFloat(),
            minBlobPixels = s.optInt("minBlobPixels", d.minBlobPixels),
            cellMinAreaFraction = s.optDouble("cellMinAreaFraction", d.cellMinAreaFraction.toDouble()).toFloat(),
            toolMaxAreaFraction = s.optDouble("toolMaxAreaFraction", d.toolMaxAreaFraction.toDouble()).toFloat(),
            movingToolMaxAreaFraction = s.optDouble("movingToolMaxAreaFraction", d.movingToolMaxAreaFraction.toDouble()).toFloat(),
            toolContrastC = s.optDouble("toolContrastC", d.toolContrastC.toDouble()).toFloat(),
            toolAbsC = s.optDouble("toolAbsC", d.toolAbsC.toDouble()).toFloat(),
            toolSharpnessC = s.optDouble("toolSharpnessC", d.toolSharpnessC.toDouble()).toFloat(),
            toolMinSpeed = s.optDouble("toolMinSpeed", d.toolMinSpeed.toDouble()).toFloat(),
            toolTransientSec = s.optDouble("toolTransientSec", d.toolTransientSec.toDouble()).toFloat(),
            toolProximity = s.optDouble("toolProximity", d.toolProximity.toDouble()).toFloat(),
            coreTempC = s.optDouble("coreTempC", d.coreTempC.toDouble()).toFloat(),
            coreHaloPx = s.optDouble("coreHaloPx", d.coreHaloPx.toDouble()).toFloat(),
            haloCapC = s.optDouble("haloCapC", d.haloCapC.toDouble()).toFloat(),
            matchDistance = s.optDouble("matchDistance", d.matchDistance.toDouble()).toFloat(),
            coastSec = s.optDouble("coastSec", d.coastSec.toDouble()).toFloat(),
            selfHeatMinTempC = s.optDouble("selfHeatMinTempC", d.selfHeatMinTempC.toDouble()).toFloat(),
            selfHeatRateCPerSec = s.optDouble("selfHeatRateCPerSec", d.selfHeatRateCPerSec.toDouble()).toFloat(),
            selfHeatWindowSec = s.optDouble("selfHeatWindowSec", d.selfHeatWindowSec.toDouble()).toFloat(),
            selfHeatSustainSec = s.optDouble("selfHeatSustainSec", d.selfHeatSustainSec.toDouble()).toFloat(),
            warnTempC = s.optDouble("warnTempC", d.warnTempC.toDouble()).toFloat(),
            warnSustainSec = s.optDouble("warnSustainSec", d.warnSustainSec.toDouble()).toFloat(),
            alarmTempC = s.optDouble("alarmTempC", d.alarmTempC.toDouble()).toFloat(),
            alarmSustainSec = s.optDouble("alarmSustainSec", d.alarmSustainSec.toDouble()).toFloat(),
            warningEscalateSec = s.optDouble("warningEscalateSec", d.warningEscalateSec.toDouble()).toFloat(),
            runawayRateCPerSec = s.optDouble("runawayRateCPerSec", d.runawayRateCPerSec.toDouble()).toFloat(),
            runawayWindowSec = s.optDouble("runawayWindowSec", d.runawayWindowSec.toDouble()).toFloat(),
            runawaySustainSec = s.optDouble("runawaySustainSec", d.runawaySustainSec.toDouble()).toFloat(),
            runawayTempC = s.optDouble("runawayTempC", d.runawayTempC.toDouble()).toFloat(),
            solderGraceSec = s.optDouble("solderGraceSec", d.solderGraceSec.toDouble()).toFloat(),
            solderWarnC = s.optDouble("solderWarnC", d.solderWarnC.toDouble()).toFloat(),
            solderAlarmC = s.optDouble("solderAlarmC", d.solderAlarmC.toDouble()).toFloat(),
            solderSelfHeatRate = s.optDouble("solderSelfHeatRate", d.solderSelfHeatRate.toDouble()).toFloat(),
            minCoolingSlope = s.optDouble("minCoolingSlope", d.minCoolingSlope.toDouble()).toFloat(),
            coolingWatchSec = s.optDouble("coolingWatchSec", d.coolingWatchSec.toDouble()).toFloat(),
            chargeWarnC = s.optDouble("chargeWarnC", d.chargeWarnC.toDouble()).toFloat(),
            chargeAlarmC = s.optDouble("chargeAlarmC", d.chargeAlarmC.toDouble()).toFloat(),
            chargeRunawayC = s.optDouble("chargeRunawayC", d.chargeRunawayC.toDouble()).toFloat(),
            chargeGraceSec = s.optDouble("chargeGraceSec", d.chargeGraceSec.toDouble()).toFloat(),
            chargeSustainSec = s.optDouble("chargeSustainSec", d.chargeSustainSec.toDouble()).toFloat(),
            chargeEscalateSec = s.optDouble("chargeEscalateSec", d.chargeEscalateSec.toDouble()).toFloat(),
            restingHotWarnSec = s.optDouble("restingHotWarnSec", d.restingHotWarnSec.toDouble()).toFloat(),
            restingHotAlarmSec = s.optDouble("restingHotAlarmSec", d.restingHotAlarmSec.toDouble()).toFloat(),
            frozenFrameSec = s.optDouble("frozenFrameSec", d.frozenFrameSec.toDouble()).toFloat(),
            reconnectGraceSec = s.optDouble("reconnectGraceSec", d.reconnectGraceSec.toDouble()).toFloat(),
            firstConnectGraceSec = s.optDouble("firstConnectGraceSec", d.firstConnectGraceSec.toDouble()).toFloat(),
            rearmAfterAckSec = s.optDouble("rearmAfterAckSec", d.rearmAfterAckSec.toDouble()).toFloat(),
            memoryKeepSec = s.optDouble("memoryKeepSec", d.memoryKeepSec.toDouble()).toFloat(),
            flameSensitivity = s.optInt("flameSensitivity", d.flameSensitivity),
            flameWholeFrame = s.optBoolean("flameWholeFrame", d.flameWholeFrame),
        )
        val zonesJson = json.optJSONArray("zones") ?: JSONArray()
        val zones = buildList {
            for (i in 0 until zonesJson.length()) {
                val z = zonesJson.optJSONObject(i) ?: continue
                val kind = runCatching { ZoneKind.valueOf(z.getString("kind")) }.getOrNull() ?: continue
                add(
                    Zone(
                        id = z.optString("id", "z$i"),
                        name = z.optString("name", kind.name),
                        kind = kind,
                        left = z.optDouble("left").toFloat(),
                        top = z.optDouble("top").toFloat(),
                        right = z.optDouble("right").toFloat(),
                        bottom = z.optDouble("bottom").toFloat(),
                    ).normalized(),
                )
            }
        }
        val base = AppPrefs()
        val camera = CameraChoice.fromStored(
            cameraId = if (json.has("cameraId")) json.optString("cameraId") else null,
            legacyUseCamera = json.optBoolean("useCamera", base.useCamera),
        )
        return AppPrefs(
            settings = settings,
            zones = zones,
            useCamera = camera.hardware,
            cameraId = camera.id,
            scenarioId = json.optString("scenarioId", base.scenarioId),
            demoSpeed = json.optDouble("demoSpeed", base.demoSpeed.toDouble()).toFloat(),
            ntfyUrl = json.optString("ntfyUrl", ""),
            webhookUrl = json.optString("webhookUrl", ""),
            sirenEnabled = json.optBoolean("sirenEnabled", true),
            keepScreenOn = json.optBoolean("keepScreenOn", true),
            startOnBoot = json.optBoolean("startOnBoot", false),
            emissivity = json.optDouble("emissivity", 0.95).toFloat(),
            distanceM = json.optDouble("distanceM", 0.45).toFloat(),
            logHz = json.optDouble("logHz", 1.0).toFloat(),
            logAlarmSnapshots = json.optBoolean("logAlarmSnapshots", true),
            viewMode = ViewMode.fromId(json.optString("viewMode", ViewMode.MSX.id)),
            palette = PaletteChoice.fromId(json.optString("palette", PaletteChoice.IRON.id)),
            rotationDegrees = PictureRotation.fromDegrees(json.optInt("rotationDegrees", 0)).degrees,
            showStats = json.optBoolean("showStats", false),
            remoteAlertsEnabled = json.optBoolean("remoteAlertsEnabled", false),
            ntfyServer = json.optString("ntfyServer", "https://ntfy.sh").ifBlank { "https://ntfy.sh" },
            ntfyTopic = json.optString("ntfyTopic", ""),
            plugKind = storedPlugKind(json),
            plugHost = json.optString("plugHost", "").ifBlank { json.optString("shellyHost", "") },
            plugUsername = json.optString("plugUsername", ""),
            plugEntity = json.optString("plugEntity", ""),
            shellyEnabled = storedPlugKind(json) == "shelly",
            shellyHost = json.optString("plugHost", "").ifBlank { json.optString("shellyHost", "") },
            shellyGeneration = json.optString("shellyGeneration", "auto").ifBlank { "auto" },
            alarmClipsEnabled = json.optBoolean("alarmClipsEnabled", false),
            ventListenerEnabled = json.optBoolean("ventListenerEnabled", false),
            ventSensitivity = json.optInt("ventSensitivity", 3).coerceIn(1, 5),
            selfTestReminder = json.optBoolean("selfTestReminder", false),
            nightView = json.optBoolean("nightView", false),
            useFahrenheit = json.optBoolean("useFahrenheit", false),
            watchProfileId = storedProfileId(json, settings),
            tutorialSeen = if (json.has("tutorialSeen")) json.optBoolean("tutorialSeen") else true,
        )
    }

    companion object {
        private const val FILE = "com.hotspotter.open"
        private const val KEY_PREFS = "prefs"

        private fun storedPlugKind(json: org.json.JSONObject): String {
            val stored = json.optString("plugKind", "")
            if (stored.isNotBlank()) return stored
            return if (json.optBoolean("shellyEnabled", false)) "shelly" else "none"
        }

        /** An older save has no profile id. Match the numbers, or call them custom. */
        private fun storedProfileId(json: org.json.JSONObject, settings: com.benchfirewatch.model.WatchSettings): String {
            val stored = json.optString("watchProfileId", "")
            if (stored.isNotBlank()) return stored
            return WatchProfile.matching(settings).id
        }
    }
}
