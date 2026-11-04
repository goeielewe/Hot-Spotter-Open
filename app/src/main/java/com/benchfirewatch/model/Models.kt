package com.benchfirewatch.model

enum class MonitorStatus {
    OFF,
    SEARCHING,
    OK,
    WARNING,
    ALARM,
    /** Open flame. Louder than a hot cell: the screen, notification, and log all say FLAME. */
    FLAME,
    FAULT,
}

enum class ZoneKind {
    IGNORE,
    SOLDER,
    CHARGE,
}

data class Zone(
    val id: String,
    val name: String,
    val kind: ZoneKind,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun normalized(): Zone {
        val l = minOf(left, right).coerceIn(0f, 1f)
        val r = maxOf(left, right).coerceIn(0f, 1f)
        val t = minOf(top, bottom).coerceIn(0f, 1f)
        val b = maxOf(top, bottom).coerceIn(0f, 1f)
        return copy(left = l, top = t, right = r, bottom = b)
    }

    fun contains(x: Float, y: Float): Boolean {
        val z = normalized()
        return x in z.left..z.right && y in z.top..z.bottom
    }
}

/**
 * Colorized picture for the screen. [contentLeft]..[contentBottom] is the radiometric
 * image inside this picture, in 0..1 of the picture. Picture-in-picture is an inset.
 * MSX, thermal, and the aligned visible image use the full picture.
 */
class PreviewImage(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val contentLeft: Float = 0f,
    val contentTop: Float = 0f,
    val contentRight: Float = 1f,
    val contentBottom: Float = 1f,
    val note: String = "",
) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height) {
            "Preview ${width}x$height cannot hold ${argb.size} pixels"
        }
    }
}

class ThermalFrame(
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val celsius: FloatArray,
    val label: String,
    val preview: PreviewImage? = null,
    val displayNote: String = "",
    /**
     * Selected measurement-range ceiling in °C, when the camera reports one.
     * Pixels pinned at this ceiling are treated as saturated. Null when the
     * camera did not say, which is every demo frame unless a test sets it.
     */
    val rangeMaxC: Float? = null,
) {
    init {
        require(celsius.size == width * height) {
            "Frame ${width}x$height cannot hold ${celsius.size} samples"
        }
    }

    fun at(x: Int, y: Int): Float = celsius[y * width + x]
}

/**
 * Every threshold the watch uses. The engine runs [sanitized]. Temperatures,
 * rise rates, and temperature differences are kept as typed. A non-number
 * falls back to the default. Times, fractions, and flame sensitivity stay in
 * the ranges the tracker needs. Flame detection stays on.
 */
data class WatchSettings(
    val blobDeltaC: Float = 10f,
    val backgroundPercentile: Float = 0.35f,
    val minBlobPixels: Int = 8,
    val cellMinAreaFraction: Float = 0.0015f,
    val toolMaxAreaFraction: Float = 0.008f,
    val movingToolMaxAreaFraction: Float = 0.025f,
    val toolContrastC: Float = 28f,
    val toolAbsC: Float = 180f,
    val toolSharpnessC: Float = 45f,
    val toolMinSpeed: Float = 0.04f,
    val toolTransientSec: Float = 4f,
    val toolProximity: Float = 0.18f,
    val coreTempC: Float = 200f,
    val coreHaloPx: Float = 3f,
    val haloCapC: Float = 95f,
    val matchDistance: Float = 0.12f,
    val coastSec: Float = 0.8f,
    val selfHeatMinTempC: Float = 38f,
    val selfHeatRateCPerSec: Float = 0.05f,
    val selfHeatWindowSec: Float = 5f,
    val selfHeatSustainSec: Float = 12f,
    val warnTempC: Float = 60f,
    val warnSustainSec: Float = 10f,
    val alarmTempC: Float = 75f,
    val alarmSustainSec: Float = 12f,
    val warningEscalateSec: Float = 25f,
    val runawayRateCPerSec: Float = 2f,
    val runawayWindowSec: Float = 0.8f,
    val runawaySustainSec: Float = 0.45f,
    val runawayTempC: Float = 120f,
    val solderGraceSec: Float = 90f,
    val solderWarnC: Float = 70f,
    val solderAlarmC: Float = 90f,
    val solderSelfHeatRate: Float = 0.12f,
    val minCoolingSlope: Float = -0.03f,
    val coolingWatchSec: Float = 180f,
    val chargeWarnC: Float = 45f,
    val chargeAlarmC: Float = 60f,
    val chargeRunawayC: Float = 80f,
    val chargeGraceSec: Float = 8f,
    val chargeSustainSec: Float = 8f,
    val chargeEscalateSec: Float = 30f,
    val restingHotWarnSec: Float = 25f,
    val restingHotAlarmSec: Float = 50f,
    val frozenFrameSec: Float = 8f,
    val reconnectGraceSec: Float = 12f,
    val firstConnectGraceSec: Float = 30f,
    val rearmAfterAckSec: Float = 120f,
    val memoryKeepSec: Float = 300f,
    /**
     * 1 is the least sensitive flame watch, 5 the most. Default 4.
     * The trigger is degrees above the local bench, not an absolute cutoff.
     */
    val flameSensitivity: Int = 4,
    /** When true, a flame anywhere in the frame counts, including inside an ignore zone. */
    val flameWholeFrame: Boolean = true,
)

fun WatchSettings.sanitized(): WatchSettings {
    val d = WatchSettings()
    return copy(
        blobDeltaC = blobDeltaC.orDefault(d.blobDeltaC),
        backgroundPercentile = backgroundPercentile.coerceIn(0.05f, 0.6f),
        minBlobPixels = minBlobPixels.coerceIn(3, 80),
        cellMinAreaFraction = cellMinAreaFraction.coerceIn(0.0004f, 0.05f),
        toolMaxAreaFraction = toolMaxAreaFraction.coerceIn(0.0008f, 0.04f),
        movingToolMaxAreaFraction = movingToolMaxAreaFraction.coerceIn(0.002f, 0.08f),
        toolContrastC = toolContrastC.orDefault(d.toolContrastC),
        toolAbsC = toolAbsC.orDefault(d.toolAbsC),
        toolSharpnessC = toolSharpnessC.orDefault(d.toolSharpnessC),
        toolMinSpeed = toolMinSpeed.coerceIn(0.01f, 0.5f),
        toolTransientSec = toolTransientSec.coerceIn(0.2f, 20f),
        toolProximity = toolProximity.coerceIn(0.04f, 0.4f),
        coreTempC = coreTempC.orDefault(d.coreTempC),
        coreHaloPx = coreHaloPx.coerceIn(1f, 12f),
        haloCapC = haloCapC.orDefault(d.haloCapC),
        matchDistance = matchDistance.coerceIn(0.03f, 0.3f),
        coastSec = coastSec.coerceIn(0.2f, 3f),
        selfHeatMinTempC = selfHeatMinTempC.orDefault(d.selfHeatMinTempC),
        selfHeatRateCPerSec = selfHeatRateCPerSec.orDefault(d.selfHeatRateCPerSec),
        selfHeatWindowSec = selfHeatWindowSec.coerceIn(2f, 20f),
        selfHeatSustainSec = selfHeatSustainSec.coerceIn(3f, 120f),
        warnTempC = warnTempC.orDefault(d.warnTempC),
        warnSustainSec = warnSustainSec.coerceIn(2f, 120f),
        alarmTempC = alarmTempC.orDefault(d.alarmTempC),
        alarmSustainSec = alarmSustainSec.coerceIn(2f, 180f),
        warningEscalateSec = warningEscalateSec.coerceIn(5f, 300f),
        runawayRateCPerSec = runawayRateCPerSec.orDefault(d.runawayRateCPerSec),
        runawayWindowSec = runawayWindowSec.coerceIn(0.4f, 3f),
        runawaySustainSec = runawaySustainSec.coerceIn(0.2f, 3f),
        runawayTempC = runawayTempC.orDefault(d.runawayTempC),
        solderGraceSec = solderGraceSec.coerceIn(2f, 600f),
        solderWarnC = solderWarnC.orDefault(d.solderWarnC),
        solderAlarmC = solderAlarmC.orDefault(d.solderAlarmC),
        solderSelfHeatRate = solderSelfHeatRate.orDefault(d.solderSelfHeatRate),
        minCoolingSlope = minCoolingSlope.orDefault(d.minCoolingSlope),
        coolingWatchSec = coolingWatchSec.coerceIn(5f, 900f),
        chargeWarnC = chargeWarnC.orDefault(d.chargeWarnC),
        chargeAlarmC = chargeAlarmC.orDefault(d.chargeAlarmC),
        chargeRunawayC = chargeRunawayC.orDefault(d.chargeRunawayC),
        chargeGraceSec = chargeGraceSec.coerceIn(0f, 120f),
        chargeSustainSec = chargeSustainSec.coerceIn(2f, 120f),
        chargeEscalateSec = chargeEscalateSec.coerceIn(5f, 300f),
        restingHotWarnSec = restingHotWarnSec.coerceIn(5f, 180f),
        restingHotAlarmSec = restingHotAlarmSec.coerceAtLeast(restingHotWarnSec + 5f).coerceAtMost(400f),
        frozenFrameSec = frozenFrameSec.coerceIn(3f, 60f),
        reconnectGraceSec = reconnectGraceSec.coerceIn(3f, 60f),
        firstConnectGraceSec = firstConnectGraceSec.coerceIn(8f, 120f),
        rearmAfterAckSec = rearmAfterAckSec.coerceIn(1f, 600f),
        memoryKeepSec = memoryKeepSec.coerceIn(10f, 900f),
        flameSensitivity = flameSensitivity.coerceIn(1, 5),
    )
}

/** A typed temperature is kept. NaN and infinities fall back to the default. */
private fun Float.orDefault(fallback: Float): Float = if (isFinite()) this else fallback

data class AppPrefs(
    val settings: WatchSettings = WatchSettings(),
    val zones: List<Zone> = emptyList(),
    val useCamera: Boolean = false,
    val cameraId: String = "demo",
    val scenarioId: String = "quiet",
    val demoSpeed: Float = 1f,
    val ntfyUrl: String = "",
    val webhookUrl: String = "",
    val sirenEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val startOnBoot: Boolean = false,
    val emissivity: Float = 0.95f,
    val distanceM: Float = 0.45f,
    val logHz: Float = 1f,
    val logAlarmSnapshots: Boolean = true,
    val viewMode: ViewMode = ViewMode.MSX,
    val palette: PaletteChoice = PaletteChoice.IRON,
    /** Clockwise display rotation of the live picture. 0, 90, 180, or 270. Detection ignores it. */
    val rotationDegrees: Int = 0,
    /** The monospace blob readout on the watch screen. Display only. */
    val showStats: Boolean = false,
    /** Repeating ntfy push until the alarm is acknowledged. Off until turned on. */
    val remoteAlertsEnabled: Boolean = false,
    val ntfyServer: String = "https://ntfy.sh",
    val ntfyTopic: String = "",
    /**
     * Local charger plug. none, shelly, kasa, tapo, tasmota, home_assistant, or wemo.
     * An alarm only sends off. Passwords and tokens are not stored in this object.
     */
    val plugKind: String = "none",
    val plugHost: String = "",
    val plugUsername: String = "",
    val plugEntity: String = "",
    /** Older saves. True meant a Shelly plug. Kept in step with [plugKind]. */
    val shellyEnabled: Boolean = false,
    val shellyHost: String = "",
    /** auto, gen1, or gen2. Shelly only. */
    val shellyGeneration: String = "auto",
    /** Rolling thermal clip saved when an alarm fires. Off until turned on. */
    val alarmClipsEnabled: Boolean = false,
    /** Microphone vent listener. Off until turned on. Warning only. */
    val ventListenerEnabled: Boolean = false,
    val ventSensitivity: Int = 3,
    /** Daily self-test reminder. Off until turned on. */
    val selfTestReminder: Boolean = false,
    /** Dim red-on-black chrome. Display only. Alarms keep their own colours. */
    val nightView: Boolean = false,
    /** Advanced detection shows Fahrenheit. Stored thresholds stay Celsius. */
    val useFahrenheit: Boolean = false,
    /** Which [com.benchfirewatch.model.WatchProfile] last filled the detection numbers. */
    val watchProfileId: String = "bench",
    /** The first-launch tour has been finished or skipped. */
    val tutorialSeen: Boolean = false,
)

/**
 * How the live picture is drawn. Detection ignores this and always uses Celsius.
 * MSX is the default: thermal color with visible-camera edges, when the camera provides them.
 */
enum class ViewMode(val id: String, val shortLabel: String) {
    MSX("msx", "MSX"),
    THERMAL("thermal", "Thermal"),
    VISUAL("visual", "Visual"),
    PIP("pip", "PiP"),
    BLEND("blend", "Blend"),
    ;

    companion object {
        fun fromId(id: String?): ViewMode = entries.firstOrNull { it.id == id } ?: MSX
    }
}

/** Iron is the default. White-hot falls back to gray when that is the name the camera offers. */
enum class PaletteChoice(val id: String, val shortLabel: String, val matchKeys: List<String>) {
    IRON("iron", "Iron", listOf("iron")),
    RAINBOW("rainbow", "Rainbow", listOf("rainbow")),
    WHITE_HOT("white-hot", "White-hot", listOf("white hot", "gray", "grey")),
    ;

    companion object {
        fun fromId(id: String?): PaletteChoice = entries.firstOrNull { it.id == id } ?: IRON
    }
}

/**
 * Picks a palette name from the names a camera offers. Keys are tried in order.
 * Comparison ignores case and spaces, so "White Hot" matches "whitehot".
 */
fun pickPaletteName(names: List<String>, keys: List<String>): String? {
    for (key in keys) {
        val needle = key.lowercase().replace(" ", "")
        val hit = names.firstOrNull { it.lowercase().replace(" ", "").contains(needle) }
        if (hit != null) return hit
    }
    return names.firstOrNull()
}

enum class StartReason {
    USER,
    BOOT,
    RECOVERY,
}

/**
 * A fire watch that dies quietly is worse than none. Crash, swipe-kill, and
 * reboot mid-session are faults. A clean Stop is not.
 */
fun faultOnStart(reason: StartReason, monitoring: Boolean, cleanStop: Boolean): String? {
    if (reason == StartReason.USER) return null
    if (!monitoring || cleanStop) return null
    return if (reason == StartReason.BOOT) {
        "The phone restarted while the watch was running. Check the bench before trusting it."
    } else {
        "The watch process was killed and has restarted. Check the bench before trusting it."
    }
}
