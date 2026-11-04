package com.benchfirewatch.model

/**
 * A named set of detection numbers. Picking one copies those numbers in.
 * Flame detection stays on. Afterward every number can still be typed freely.
 */
data class WatchProfile(
    val id: String,
    val title: String,
    val detail: String,
    val settings: WatchSettings?,
) {
    fun applyTo(prefs: AppPrefs): AppPrefs {
        val filled = settings ?: return prefs.copy(watchProfileId = CUSTOM)
        return prefs.copy(settings = filled, watchProfileId = id)
    }

    companion object {
        const val BENCH = "bench"
        const val CHARGING = "charging"
        const val PRINTER = "printer"
        const val ELECTRICAL = "electrical"
        const val GENERAL = "general"
        const val CUSTOM = "custom"

        val bench = WatchProfile(
            BENCH,
            "Lithium Rebuild Bench",
            "Soldering, spot welds, and cells. A normal joint can cool before it alarms. An open flame still alarms at once.",
            WatchSettings(),
        )
        val charging = WatchProfile(
            CHARGING,
            "Charging Batteries",
            "Packs that only sit and charge. Warns at 45 °C, alarms at 60 °C, and treats 80 °C as runaway. The long solder grace is shortened.",
            WatchSettings(
                warnTempC = 45f,
                alarmTempC = 60f,
                runawayTempC = 80f,
                chargeWarnC = 45f,
                chargeAlarmC = 60f,
                chargeRunawayC = 80f,
                solderWarnC = 45f,
                solderAlarmC = 60f,
                solderGraceSec = 8f,
                runawayRateCPerSec = 1f,
                selfHeatMinTempC = 35f,
                selfHeatRateCPerSec = 0.04f,
                warnSustainSec = 8f,
                alarmSustainSec = 8f,
            ),
        )
        val printer = WatchProfile(
            PRINTER,
            "3D Printer",
            "A heated bed and a hotend stay quiet. A large spot above 130 °C warns, 180 °C alarms, and 340 °C is immediate. Flame detection stays on.",
            WatchSettings(
                warnTempC = 130f,
                alarmTempC = 180f,
                runawayTempC = 340f,
                runawayRateCPerSec = 4f,
                toolAbsC = 360f,
                solderGraceSec = 20f,
                selfHeatMinTempC = 50f,
            ),
        )
        val electrical = WatchProfile(
            ELECTRICAL,
            "Electrical Panel",
            "A breaker or a lug that is hot against a cool panel. Warns at 50 °C, alarms at 70 °C, and treats 100 °C as runaway, without waiting out a solder grace.",
            WatchSettings(
                warnTempC = 50f,
                alarmTempC = 70f,
                runawayTempC = 100f,
                blobDeltaC = 6f,
                warnSustainSec = 6f,
                alarmSustainSec = 8f,
                solderGraceSec = 5f,
                runawayRateCPerSec = 1.5f,
                selfHeatMinTempC = 35f,
            ),
        )
        val general = WatchProfile(
            GENERAL,
            "General Watch",
            "Point it at a machine or a pile of packs. A spot at 80 °C warns, 100 °C alarms, and 180 °C is immediate. Change any number if that is too loud or too quiet.",
            WatchSettings(
                warnTempC = 80f,
                alarmTempC = 100f,
                runawayTempC = 180f,
                runawayRateCPerSec = 1.5f,
                solderGraceSec = 20f,
            ),
        )
        val custom = WatchProfile(
            CUSTOM,
            "Custom",
            "The numbers you typed. Choosing another profile fills them in. Every temperature stays editable after that.",
            null,
        )

        val all: List<WatchProfile> = listOf(bench, charging, printer, electrical, general, custom)

        fun fromId(id: String?): WatchProfile = all.firstOrNull { it.id == id } ?: custom

        /** Which named profile these numbers match. Edited numbers are custom. */
        fun matching(settings: WatchSettings): WatchProfile =
            all.firstOrNull { it.settings == settings } ?: custom
    }
}
