package com.benchfirewatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** First-launch tour. Skip at any step. Settings can show it again. */
object Tutorial {
    data class Step(val title: String, val body: String)

    val steps: List<Step> = listOf(
        Step(
            "USB-C Thermal Camera",
            "Plug a P2 Pro, TC001, T7, or Thermal Master into the phone's USB-C port, with the lens aimed at what you are watching. The phone is the USB host. A charge-only cable will not carry the picture. Allow the camera permission when Android asks. It is required before the USB prompt.",
        ),
        Step(
            "USB Permission",
            "Android asks the first time the camera is plugged in. Choose Always. If you only choose this once, the camera drops every time the watch starts again and you have to accept the prompt again.",
        ),
        Step(
            "Camera or Demo",
            "Settings, then Camera. Pick USB-C thermal when the camera is plugged in. Pick Demo to rehearse a solder pass, a hot cell, or a flame with nothing attached. Detection is the same either way. About has Send Test Report. It shares one raw frame and the USB ids, and nothing is uploaded until you choose where to send it.",
        ),
        Step(
            "Watch Profile",
            "Settings, then Watch Profile. A lithium rebuild bench, charging packs, a 3D printer, an electrical panel, or a general watch fills in the temperatures. Custom keeps what you typed. Every number stays editable after you pick one. Flame detection stays on.",
        ),
        Step(
            "Start a Watch",
            "On Watch, start the watch. It keeps running if you leave the app or swipe it away. Stop watch turns it off. A reboot starts it only if you turned that on in Settings.",
        ),
        Step(
            "Alarms",
            "A flame, a hot-cell alarm, or a camera fault fills the screen. Acknowledge, or Back, closes that screen. The status card keeps the condition. Stop watch clears it and turns the watch off. The siren can be muted and the screen still alarms.",
        ),
        Step(
            "A Second Phone",
            "Remote alerts use the free ntfy.sh server. No account and no login. Turn them on in Settings, leave the long topic name, and subscribe to that name in the ntfy app on the other phone. A flame, a hot-cell alarm, or a camera fault repeats about every 15 minutes until you acknowledge.",
        ),
        Step(
            "Charger Shutoff",
            "An optional plug on your local network can switch a charger off when an alarm fires. Shelly, Kasa, Tapo, Tasmota, Home Assistant, or Wemo. It never turns the charger back on by itself. If the off cannot be confirmed, the siren sounds. Test plug, in Settings, turns it off and then on. Watch profiles do not change the plug.",
        ),
    )

    fun shouldOffer(tutorialSeen: Boolean): Boolean = !tutorialSeen
}

@Composable
fun TutorialScreen(onFinished: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = Tutorial.steps[index.coerceIn(0, Tutorial.steps.lastIndex)]
    val last = index >= Tutorial.steps.lastIndex
    BackHandler {
        if (index == 0) onFinished() else index -= 1
    }
    Scaffold(
        containerColor = chromeInk(),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "How to Watch",
                color = chromePaper(),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "${index + 1} of ${Tutorial.steps.size}",
                color = chromeMuted(),
                style = MaterialTheme.typography.labelLarge,
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(step.title, color = chromePaper(), style = MaterialTheme.typography.titleLarge)
                Text(step.body, color = chromePaper(), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onFinished) { Text("Skip") }
                Button(onClick = { if (last) onFinished() else index += 1 }) {
                    Text(if (last) "Done" else "Next")
                }
            }
        }
    }
}
