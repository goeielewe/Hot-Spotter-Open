package com.benchfirewatch.selftest

enum class CheckMark { PASS, FAIL, SKIP }

data class SelfTestStep(val name: String, val mark: CheckMark, val detail: String)

data class SelfTestReport(val steps: List<SelfTestStep>) {
    val failed: Boolean get() = steps.any { it.mark == CheckMark.FAIL }

    fun summary(): String {
        val head = if (failed) "Self-test failed" else "Self-test passed"
        return buildString {
            append(head)
            steps.forEach { step ->
                append('\n')
                append(step.name)
                append(": ")
                append(step.mark.name.lowercase())
                if (step.detail.isNotBlank()) {
                    append(" — ")
                    append(step.detail)
                }
            }
        }
    }
}

data class SelfTestInput(
    val watchRunning: Boolean,
    val sawHeat: Boolean,
    val flamePathOk: Boolean,
    val sirenEnabled: Boolean,
    val sirenOk: Boolean,
    val alarmAlreadySounding: Boolean,
    val remoteConfigured: Boolean,
    val remoteOk: Boolean,
    val plugConfigured: Boolean,
    val plugOffOk: Boolean,
    val plugOnOk: Boolean,
    val leftOffForAlarm: Boolean,
)

object SelfTestScore {
    fun score(input: SelfTestInput): SelfTestReport {
        val steps = ArrayList<SelfTestStep>()
        steps += when {
            !input.watchRunning -> SelfTestStep("Thermal Pickup", CheckMark.FAIL, "Start the watch and show a hot object.")
            input.sawHeat -> SelfTestStep("Thermal Pickup", CheckMark.PASS, "A hot object is in view.")
            else -> SelfTestStep("Thermal Pickup", CheckMark.FAIL, "No hot object in view.")
        }
        steps += if (input.flamePathOk) {
            SelfTestStep("Flame Path", CheckMark.PASS, "The canned flame check passed.")
        } else {
            SelfTestStep("Flame Path", CheckMark.FAIL, "The canned flame check failed.")
        }
        steps += when {
            !input.sirenEnabled -> SelfTestStep("Siren", CheckMark.SKIP, "Siren is off.")
            input.alarmAlreadySounding -> SelfTestStep("Siren", CheckMark.PASS, "An alarm is already sounding.")
            !input.watchRunning -> SelfTestStep("Siren", CheckMark.SKIP, "Start the watch first.")
            input.sirenOk -> SelfTestStep("Siren", CheckMark.PASS, "Played a short test.")
            else -> SelfTestStep("Siren", CheckMark.FAIL, "The siren did not play.")
        }
        steps += when {
            !input.remoteConfigured -> SelfTestStep("Remote Alert", CheckMark.SKIP, "Remote alerts are off.")
            input.remoteOk -> SelfTestStep("Remote Alert", CheckMark.PASS, "The test push was accepted.")
            else -> SelfTestStep("Remote Alert", CheckMark.FAIL, "The test push was not accepted.")
        }
        steps += when {
            !input.plugConfigured -> SelfTestStep("Plug", CheckMark.SKIP, "No plug is configured.")
            !input.plugOffOk -> SelfTestStep("Plug", CheckMark.FAIL, "The plug did not turn off.")
            input.leftOffForAlarm -> SelfTestStep("Plug", CheckMark.PASS, "Turned off and left off because an alarm is active.")
            input.plugOnOk -> SelfTestStep("Plug", CheckMark.PASS, "Turned off, then back on.")
            else -> SelfTestStep("Plug", CheckMark.FAIL, "Turned off, but did not turn back on.")
        }
        return SelfTestReport(steps)
    }
}
