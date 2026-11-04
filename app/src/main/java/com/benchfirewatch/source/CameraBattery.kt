package com.benchfirewatch.source

/**
 * Camera battery as the watch understands it. A camera that does not
 * report a percent stays on the unavailable line. Demo never invents one.
 */
enum class ChargeKind {
    CHARGING,
    NOT_CHARGING,
    POWERING_PHONE,
    FAULT,
    UNKNOWN,
}

data class CameraBattery(
    val percent: Int? = null,
    val charge: ChargeKind = ChargeKind.UNKNOWN,
    val placeholder: Boolean = false,
) {
    fun line(): String {
        if (placeholder) return "Camera — demo"
        val pct = percent?.takeIf { it in 0..100 } ?: return "Camera battery unavailable"
        val chargeText = when (charge) {
            ChargeKind.CHARGING -> " · charging"
            ChargeKind.NOT_CHARGING -> " · not charging"
            ChargeKind.POWERING_PHONE -> " · not charging, powering the phone"
            ChargeKind.FAULT -> " · charge fault"
            ChargeKind.UNKNOWN -> ""
        }
        return "Camera $pct%$chargeText"
    }
}

data class BatteryNotice(val level: Int, val message: String)

/**
 * Low-battery crossings. A dead camera blinds the watch, so 20% and 10% each
 * log once. Staying low is quiet. Charging back to 20% or above re-arms both.
 * A missing reading does not count as empty and does not re-arm.
 */
object CameraBatteryWatch {
    const val LOW = 20
    const val CRITICAL = 10

    fun warnLevel(percent: Int?): Int {
        val pct = percent?.takeIf { it in 0..100 } ?: return 0
        return when {
            pct < CRITICAL -> CRITICAL
            pct < LOW -> LOW
            else -> 0
        }
    }

    fun notices(previous: Int?, current: Int?): List<BatteryNotice> {
        val now = current?.takeIf { it in 0..100 } ?: return emptyList()
        val before = previous?.takeIf { it in 0..100 } ?: 101
        val out = ArrayList<BatteryNotice>(2)
        if (before >= LOW && now < LOW) {
            out += BatteryNotice(
                LOW,
                "Camera battery is $now%, under 20%. Charge it before the watch goes blind.",
            )
        }
        if (before >= CRITICAL && now < CRITICAL) {
            out += BatteryNotice(
                CRITICAL,
                "Camera battery is $now%, under 10%. The watch goes blind when the camera dies.",
            )
        }
        return out
    }

    /** Unavailable current keeps the last known percent. */
    fun remember(previous: Int?, current: Int?): Int? {
        val now = current?.takeIf { it in 0..100 } ?: return previous?.takeIf { it in 0..100 }
        return now
    }
}
