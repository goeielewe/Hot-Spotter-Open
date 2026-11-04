package com.benchfirewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.benchfirewatch.BuildConfig
import com.benchfirewatch.release.ReleaseNotes

private data class HelpBlock(val title: String, val body: String)

private val blocks = listOf(
    HelpBlock(
        "A First Watch",
        "Settings has Watch profile. A lithium rebuild bench, charging packs, a 3D printer, an electrical panel, or a general watch fills in the temperatures. Custom keeps the numbers you typed, and every temperature stays editable. Flame detection stays on. Show the tour, at the bottom of Settings, walks through the camera, the USB Always permission, Demo, a profile, starting a watch, alarms, ntfy, and charger shutoff. You can skip it.",
    ),
    HelpBlock(
        "Moving Between Screens",
        "Swipe between Watch, Log, Settings, and Help. The bottom bar moves with the swipe, and tapping a tab slides to that screen. The dots above the bar show which screen you are on. Back returns to Watch before leaving the app. A swipe that starts on the picture, a zone you are drawing, a number field, a chip row, a sheet, or a dialog stays on that control. A swipe that starts at the screen edge stays the system Back gesture. Full screen is not one of these pages. Back there leaves the picture.",
    ),
    HelpBlock(
        "What It Is Watching",
        "The phone sits above the bench with a USB-C thermal camera. Demo mode needs no camera. The live picture defaults to MSX: visible-camera edges over the thermal image, with the Iron palette, when the camera provides them. Picture mode, palette, show stats, and night view sit behind the gear on the picture. The caption under the picture opens that sheet. The crosshair is the hottest pixel. Tap the picture, or Full screen, for an edge-to-edge view that fills the screen in portrait or landscape. System bars hide, the screen stays on, and a compact see-through bar keeps the alarm, Silence or Acknowledge, the max temperature, the camera battery, Mode, Palette, and Rotate. The bar hides after a few seconds. Tap the picture to bring it back. During an alarm or a flame it stays. Back leaves. Rotate, also on the watch picture, turns the view 90 degrees clockwise and is remembered. Zones, boxes, and the crosshair turn with the picture. Detection keeps running in the foreground service, including with the screen off or the app in the background, and it still uses the temperature of every pixel, not the colors. The watch tracks hot blobs: where they are, how big they are, how hot the body of the blob is, and how fast that is changing. A soldering iron and a spot welder are ignored by that tracker. A cell that heats up on its own is not. An open flame is a separate watch and does not wait for a big blob.\n\nWhile the watch is running, a line under the batteries says how old the last real frame is. It turns amber after about two seconds and red at the camera-blind threshold. A frozen frame does not refresh that line. It does not change the fault.",
    ),
    HelpBlock(
        "Open Flame",
        "FLAME is the status for an open flame. A lighter at about two feet only covers a few pixels and reads near 100°C, because the camera looks through the flame instead of at a surface. The watch compares each pixel with the bench behind it. It alarms on a hot jump that flickers, a very large jump, a fast rise on a few pixels, or a pixel pinned at the camera's range ceiling. Two frames are enough. Sensitivity defaults to 4 of 5. 1 is the least sensitive and still looks for that lighter. The whole frame is watched, including inside an iron-holder mask, unless you turn off Flame on the whole frame. Then only the zones you drew count. Picture mode, rotation, and a muted siren do not change it. The siren still sounds when it is on. The picture, the notification, and the log say FLAME either way.\n\nA steady soldering iron, hot and not flickering, is not called a flame. It can still become a parked-iron warning if you leave it in view. The camera's temperature range is read and not changed. If that range clips the tip, clipped pixels alarm, because a clipped tip and a clipped flame look the same.\n\nThe shutter can freeze the picture for a moment. Duplicate frames are skipped so they do not wipe the flame, and a frozen picture that still shows the flame still alarms. If frames stay frozen, the status is a camera-blind fault. The same fault is raised when the camera disconnects. A frozen picture from before the flame appeared cannot show a flame the camera is no longer sending; the fault is the alarm in that case.\n\nAt startup, and about once a minute, a canned 3-pixel spot is run through a separate copy of the detector. The status line says Flame watch on when that check passes. If it fails, the watch faults. It cannot see a flame hidden behind a cell or the bench lip, or anything outside the camera's view.",
    ),
    HelpBlock(
        "Shiny Metal Reads Cold",
        "Nickel strip, bare cell cans, and the iron tip have low emissivity. The camera under-reads them, sometimes by a huge amount, so a 350°C tip may not look like 350°C. The colored PVC wrap on a cell is close to 0.95 and is what you can trust. The logic uses blob size, motion, and the warm area around a hot core. Do not tune this by chasing the temperature of the nickel tab. Set emissivity to about 0.95 for the wrap, and draw a mask over the iron holder so a parked iron is not a hot spot.",
    ),
    HelpBlock(
        "What Each Status Means",
        "OK — frames are coming in and nothing looks like a bad cell or an open flame.\n\nWARNING — a cell-sized area is warming with no iron nearby, or it is sitting above the warning temperature. The siren is not on yet.\n\nFLAME — an open flame is in view. This is the one that must not be missed. The siren runs until you acknowledge, unless you muted the siren. The screen, the notification, and the log still say FLAME when the siren is muted. The status card fills deep red with white text. If the flame is still there, the siren comes back.\n\nALARM — runaway (several °C per second, or very hot, on a cell-sized area), a soldered spot that stopped cooling when the grace ended, or a warning that held. The siren runs until you acknowledge. If it is still happening, the siren comes back.\n\nFAULT — the camera dropped, frames froze (camera blind), the flame-watch self-check failed, the process was killed, or the phone rebooted mid-watch. A monitor that dies quietly is worse than none, so this is as loud as a fire.\n\nA flame, a hot-cell alarm, or a camera fault fills the screen even when the siren is muted. The alarm screen stays solid, with a large icon, Acknowledge, and Stop watch. Acknowledge and Back close that screen at once. The status card keeps the condition. The screen comes back only for a new alarm, or when the re-arm time says the same one is still there. Stop watch turns the watch off and clears it. On Android 14 and newer it can also show over the lock screen, like an incoming call, after you allow full-screen alerts. Settings says when that permission is missing, and the alarm then falls back to a heads-up notification. Acknowledging closes that notification's full-screen page too. Night view does not dim this screen. Looking for the USB camera, including the USB permission prompt, stays on the status card. It fills the screen only if the camera was already streaming and then dropped, or the search has gone on for about 30 seconds, and acknowledging it keeps it closed until that changes.",
    ),
    HelpBlock(
        "Soldering and Spot Welding",
        "A small, sharp, moving blob is the iron. A tiny flash shorter than the tool grace is a spot weld. Both are ignored. When the iron leaves, a cell-sized warm spot where it was gets the cool-down grace (default 90 seconds on the bench and in a solder zone, 8 seconds on a charge rack). During the grace, only a real runaway alarms. At the end of the grace, a spot that is still hot and not falling alarms. A spot that is still falling is left alone.\n\nHot-work mode, on the watch screen, is for a soldering or spot-weld session. Tap 5, 15, or 30 minutes. A countdown says how long is left and that the flame watch is still on. You can cancel it. It ends by itself and it does not stay on after the watch restarts. While it is on, the slow hot-blob alarm is quiet only inside an iron-holder zone or a solder zone. Flames, camera faults, runaway heat, and hot alarms outside those zones, including a charge rack, stay fully active. On a sped-up demo the timer follows the demo clock, so it finishes sooner than the wall clock.",
    ),
    HelpBlock(
        "Zones",
        "Draw on the live image. A rotated picture still saves the box on the camera's own pixels, so it covers the same part of the bench.\n\nIron holder — ignored by the cell tracker. Put this on the stand, not on the cells. An open flame inside it still alarms unless you turn off Flame on the whole frame.\n\nSolder area — more tolerant temperatures and the long grace.\n\nCharge rack — stricter. Packs that only sit and charge should live here. A cell at 45°C warns. Do not solder in this zone unless you want the short grace.\n\nAnywhere you do not paint uses the open-bench defaults, warning around 60°C. The flame watch uses the whole frame unless you limit it to drawn zones.",
    ),
    HelpBlock(
        "Mounting",
        "Clamp the phone so the lens looks straight down at the whole bench and cannot be bumped. Keep the camera lens clean. Shade the bench from sun and from a heater duct; those are real blobs. Give the camera a few seconds after plugging in for its shutter to settle. Start in demo mode and run Soldering pass, Soldered cell cooling, Self-heating cell, and Thermal runaway until the status matches the name. Then connect the camera, solder one scrap cell, and confirm it stays OK. Then look at a cell you warm on purpose, off to the side, and confirm it warns.",
    ),
    HelpBlock(
        "USB-C Hub and Charging",
        "The phone is the USB host. The thermal camera is the USB device. A hub works only if it keeps the phone in host mode while it charges: charger into the hub's PD port, camera into a data port, hub into the phone. Charge-only splitters will power the phone and hide the camera. Plug the camera in directly first. If it works direct and dies when you add the charger, that hub is dropping host mode. Accept the USB permission prompt and tick always. Some OnePlus and Oppo phones ship with OTG off. The watch reconnects after a dropout and faults if frames do not return. Switching between Demo and the USB camera stops the old source and starts the new one, with the first-connect grace.",
    ),
    HelpBlock(
        "Power and Alerts",
        "The watch is a foreground service with a wake lock. Leaving the app does not stop it. Swiping the app away does not stop the watch. Force Stop in system settings does, and Android will not let an app undo that. Turn off battery optimization, allow alarms through Do Not Disturb, and allow full-screen alerts, or the siren can lose to the ringer switch on some phones. On Android 14 and newer, full-screen alerts are a separate permission. Without it, a flame, alarm, or fault is still a heads-up notification, and it does not turn the lock screen into the alarm page. Siren sound, on the watch screen and in Settings, is on by default. It plays a bundled siren on the alarm stream at full alarm volume, looped, with vibration, from the foreground service. Test siren is in Settings and plays about three seconds. Turning the siren off leaves a Siren muted line; the picture, the notification, and the log still alarm. Acknowledge silences a live alarm, and the sound comes back only if the cell or the camera fault is still there when the rearm time is up.\n\nThe watch screen shows the camera battery, when the camera reports one, on the same strip as the phone battery and the frame heartbeat. Under 20% it warns and logs that once, and again under 10%, because a dead camera leaves the watch blind. Unplugging the camera is still a fault on its own. Demo mode says Camera — demo instead of a made-up percent.\n\nNight view, in the picture options and in Settings, dims the app chrome to red on black for a dark bench. Alarms keep their full-bright colours and the full-screen alarm stays bright. It is off until you turn it on.\n\nA one-shot ntfy URL and a webhook JSON POST are still in Settings. Repeating alerts, a Shelly plug, clips, the vent listener, the self-test, and phone-health warnings are described below.",
    ),
    HelpBlock(
        "Second Phone, Charger, and Checks",
        "Remote alerts are off until you turn them on. They use https://ntfy.sh, which is free and needs no account and no login. The app makes a long random topic name so it is hard to guess, and it keeps that name. On the other phone, install the ntfy app and subscribe to the same topic. Anyone who knows the name can read it, because this free server does not have private topics, so leave the name long. A flame, a hot-cell alarm, or a camera fault sends the alarm type, the max temperature, and the time. It repeats about every 15 minutes until you acknowledge in this app. That pace stays inside the free limit of 250 messages a day. A failed send is retried. Test remote alert sends one message. The phone does not sign in, and it does not use reserved topics, e-mail, or phone calls.\n\nCharger shutoff, in Settings, can turn off a Shelly plug, a TP-Link Kasa plug on its local protocol, a Tapo plug with its local login, a Tasmota plug, a Home Assistant switch, or a Wemo plug. The phone talks to the plug on your network. No vendor cloud account is required. An alarm sends off and never turns the charger back on. The off counts only when the plug confirms it. If that confirmation does not come back, the watch raises a fault and the siren sounds. Test plug turns it off and then on. Passwords and the Home Assistant token stay in the phone keystore. Tuya, Govee, and Amazon plugs are not listed because they need their own cloud. A Matter plug can be turned off by pointing this at the Home Assistant switch that already controls it.\n\nAlarm clips, off until you turn them on, keep about ten seconds of the thermal picture and, when the camera provides one, a small visible frame. On an alarm they save the snapshot you already get plus a short GIF from before and after. The log entry has a Clip link. The oldest clips are deleted after 50.\n\nThe vent listener, off until you turn it on, uses the microphone for the click and hiss of a cell vent. It only raises a warning, and for about a minute it makes the flame watch one step more sensitive. It is never the only reason for a full alarm. Audio is processed on the phone and is not saved or uploaded. Sensitivity is 1 to 5, default 3, and recent detections are listed in Settings.\n\nRun self-test asks you to flick a lighter or hold something hot in view. It checks that the camera sees heat, that the flame path answers its canned check, that the siren plays, and that the remote alert and the plug work if you turned them on. Pass or fail is written to the log. A daily reminder is optional and is only a notification.\n\nPhone health warnings are always on and are not a full alarm. The watch warns when the phone battery is low or not charging, when the phone is hot, and when a live camera picture stays flat long enough that the lens may be dirty or blocked. A flat demo scene does not count. Unplugging the camera is still a full fault.",
    ),
    HelpBlock(
        "Tuning on Your Bench",
        "Run the demo scenes at 1× before changing numbers. Lighter flame should go to FLAME quickly. Soldering pass should stay OK. If a normal solder pass alarms, lengthen the cool-down grace or raise the solder-zone temperatures, and make sure the iron holder is masked. If the iron itself is called FLAME, the camera's range ceiling is clipping the tip. If a warm cell is missed, lower the warning temperature or the minimum cell area — area is a fraction of the frame, so a higher camera needs a smaller fraction. If sunlight or your hands warn, those blobs are large and warm; shade the bench rather than raising the warning temperature so far that a cell at 70°C is quiet. Show stats, in the picture options behind the gear, turns the number feed on or off. It is off by default and does not change detection. Trust the body number, not the max, while an iron is in the blob. In Advanced detection you can type any temperature, rise rate, or temperature difference, in °C or °F. The slider is only a wide guide. A typed number is saved as written, and warning does not have to sit below alarm. Flame detection stays on.",
    ),
    HelpBlock(
        "Logging a Dummy Cell",
        "Turn on Logging on the watch screen. The switch is on the Logging card. Open the card for REC, the elapsed time, and the sample count while a CSV is open. Default is one row a second (0.5 to 10 Hz). Type the thermocouple reading and tap Log reference. Tap Mark for notes like \"heater on 20W\". Sessions, on the Log tab, can preview, share, and delete the file. Sharing needs no storage permission. An alarm during a log also saves a PNG next to the CSV. Timestamps are real wall-clock UTC, even when a demo scene is sped up. Compare frame_max_c and blob_max_c with reference_c on the Kapton wrap, not on bare metal.",
    ),
    HelpBlock(
        "Demo Mode",
        "Demo mode is the same detector as the camera, fed with synthetic 160×120 frames. 4× and 12× speed up the clock so a slow self-heat does not take minutes, and the thresholds still mean degrees per real second. Lighter flame is supposed to say FLAME. Frozen frames and Camera unplugged are supposed to fault. Soldering pass and a cooling cell are supposed to stay OK.",
    ),
)

@Composable
fun HelpScreen(modifier: Modifier, onOpenEula: () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            ReleaseNotes.label(),
            color = chromePaper(),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "This edition watches a USB-C thermal camera: P2 Pro, TC001, T7, any camera that offers 256×384, Thermal Master P1 and P3, and a TC002C 512×484 frame. UTi120M is recognized and does not show temperatures yet. Send Test Report is in Settings, About. It shares one raw frame and the USB ids, and it does not upload until you choose where to send it.",
            color = chromePaper(),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onOpenEula, modifier = Modifier.fillMaxWidth()) {
            Text("End-User License")
        }
        WhatsNewBlock()
        blocks.forEach { block ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(block.title, color = OkGreen, style = MaterialTheme.typography.titleMedium)
                Text(block.body, color = chromePaper(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WhatsNewBlock() {
    val note = ReleaseNotes.forCode(BuildConfig.VERSION_CODE)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("What's New", color = OkGreen, style = MaterialTheme.typography.titleMedium)
        note.lines.forEach { line ->
            Text("• $line", color = chromePaper(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    HoldPagerWhileShown()
    val note = ReleaseNotes.forCode(BuildConfig.VERSION_CODE)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = chromePanel(),
        titleContentColor = chromePaper(),
        textContentColor = chromePaper(),
        title = {
            Text(ReleaseNotes.label())
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("What's New", color = OkGreen, style = MaterialTheme.typography.titleMedium)
                note.lines.forEach { line ->
                    Text("• $line", color = chromePaper(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Got It")
            }
        },
    )
}
