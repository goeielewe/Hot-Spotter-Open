package com.benchfirewatch.health

/**
 * Warnings about the phone itself. None of these change the watch status and
 * none of them are a full alarm. A camera that is unplugged stays a fault,
 * and that path is elsewhere.
 */
class PhoneHealth {
    private var lowLatched = false
    private var chargeLatched = false
    private var hotLatched = false
    private var lensSince = -1L
    private var lensLatched = false

    fun poll(input: PhoneHealthInput): PhoneHealthSnapshot {
        val fresh = ArrayList<PhoneHealthNotice>()
        val percent = input.batteryPercent
        val low = percent != null && percent in 0..LOW_BATTERY
        if (low && !lowLatched) {
            lowLatched = true
            fresh += PhoneHealthNotice("battery", "Phone battery is low ($percent%).")
        }
        if (!low) lowLatched = false

        val unplugged = input.chargingKnown && !input.charging
        if (unplugged && !chargeLatched) {
            chargeLatched = true
            fresh += PhoneHealthNotice("charging", "Phone is not charging.")
        }
        if (!unplugged) chargeLatched = false

        val hot = input.thermalStatus >= THERMAL_MODERATE
        if (hot && !hotLatched) {
            hotLatched = true
            fresh += PhoneHealthNotice("thermal", "Phone is hot and may slow the watch.")
        }
        if (!hot) hotLatched = false

        val flat = input.hardwareCamera && input.framesChanging &&
            input.sceneRangeC != null && input.sceneRangeC < LENS_RANGE_C
        if (flat) {
            if (lensSince < 0L) lensSince = input.nowMs
            if (!lensLatched && input.nowMs - lensSince >= LENS_HOLD_MS) {
                lensLatched = true
                fresh += PhoneHealthNotice("lens", "The lens looks flat. It may be dirty or blocked.")
            }
        } else {
            lensSince = -1L
            lensLatched = false
        }

        val showing = ArrayList<String>()
        if (percent != null && percent <= LOW_BATTERY) showing += "Phone battery is low ($percent%)."
        if (unplugged) showing += "Phone is not charging."
        if (hot) showing += "Phone is hot and may slow the watch."
        if (lensLatched) showing += "The lens looks flat. It may be dirty or blocked."
        return PhoneHealthSnapshot(fresh, showing)
    }

    companion object {
        const val LOW_BATTERY = 20
        const val LENS_RANGE_C = 1.0f
        const val LENS_HOLD_MS = 15_000L

        /** Same value as PowerManager.THERMAL_STATUS_MODERATE. */
        const val THERMAL_MODERATE = 2

        fun isFullAlarm(): Boolean = false
    }
}

data class PhoneHealthInput(
    val batteryPercent: Int?,
    val charging: Boolean,
    val chargingKnown: Boolean,
    val thermalStatus: Int,
    val sceneRangeC: Float?,
    val framesChanging: Boolean,
    val hardwareCamera: Boolean,
    val nowMs: Long,
)

data class PhoneHealthNotice(val key: String, val message: String)

data class PhoneHealthSnapshot(val fresh: List<PhoneHealthNotice>, val showing: List<String>)
