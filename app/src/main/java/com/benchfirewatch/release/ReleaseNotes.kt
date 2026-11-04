package com.benchfirewatch.release

data class ReleaseNote(
    val versionName: String,
    val versionCode: Int,
    val lines: List<String>,
)

/**
 * Notes shown in Help and in the one-time update dialog. The same sentences
 * are the current section of CHANGELOG.md. The screen does not show a version.
 */
object ReleaseNotes {
    val notes: List<ReleaseNote> = listOf(
        ReleaseNote(
            versionName = "open",
            versionCode = 16,
            lines = listOf(
                "USB-C thermal cameras are the watch camera. P2 Pro and TC001 use the 256×384 frame. T7 uses that same frame and needs testers. Thermal Master P1 and P3 use their own stream and need testers. UTi120M is recognized and does not invent temperatures.",
                "Send Test Report, in Settings under About, shares one raw frame and the USB ids. Nothing is uploaded until you choose where to send it.",
                "Charger shutoff can turn off a Shelly plug, a TP-Link Kasa plug on the local protocol, a Tapo plug with its local login, a Tasmota plug, a Home Assistant switch, or a Wemo plug. A Matter plug can be turned off through the Home Assistant switch that already controls it. Test plug turns it off and then on.",
                "If an alarm cannot confirm that the plug turned off, the watch raises a fault and the siren sounds. Acknowledging it does not clear the fault while the plug is still on. Stop watch does.",
                "Watch profile, in Settings, fills in temperatures for a lithium rebuild bench, charging batteries, a 3D printer, an electrical panel, or a general watch. Custom keeps the numbers you typed. Flame detection stays on.",
                "The first time you open Hot Spotter, a short tour walks through the USB-C camera, the USB Always permission, Demo, a watch profile, starting a watch, alarms, ntfy, and charger shutoff. Skip it any time. Settings can show it again.",
            ),
        ),
    )

    fun forCode(versionCode: Int): ReleaseNote =
        notes.firstOrNull { it.versionCode == versionCode } ?: notes.first()

    /** Title shown in Help and About. No version number. */
    fun label(): String = "Hot Spotter"

    /**
     * Once per upgrade. A seen code of 0 with older app data is an install
     * that did not record a version. A brand-new install has no older data
     * and stays quiet.
     */
    fun shouldShow(seenVersionCode: Int, currentVersionCode: Int, hadPriorData: Boolean): Boolean {
        if (seenVersionCode >= currentVersionCode) return false
        if (seenVersionCode > 0) return true
        return hadPriorData
    }
}
