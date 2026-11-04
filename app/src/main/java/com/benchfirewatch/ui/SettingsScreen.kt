package com.benchfirewatch.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.benchfirewatch.alert.PlugKind
import com.benchfirewatch.alert.PlugSecrets
import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.model.WatchProfile
import com.benchfirewatch.model.WatchSettings
import com.benchfirewatch.model.cameraChoice
import com.benchfirewatch.model.temperatureOrderHint
import com.benchfirewatch.release.ReleaseNotes
import com.benchfirewatch.service.MonitorRuntime
import com.benchfirewatch.source.EditionCamera

private enum class SettingsPage {
    Home,
    Camera,
    Profile,
    Alarm,
    Remote,
    Charger,
    Clips,
    Safety,
    Startup,
    Advanced,
    About,
}

@Composable
fun SettingsScreen(
    modifier: Modifier,
    prefs: AppPrefs,
    logging: Boolean,
    onPrefs: (AppPrefs) -> Unit,
    onOpenEula: () -> Unit,
    onReplayTutorial: () -> Unit = {},
    onSubScreen: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val runtime by MonitorRuntime.state.collectAsState()
    var page by rememberSaveable { mutableStateOf(SettingsPage.Home) }
    var generation by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    var notices by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onPrefs(prefs.copy(ventListenerEnabled = granted))
    }
    LaunchedEffect(page) { onSubScreen(page != SettingsPage.Home) }
    BackHandler(enabled = page != SettingsPage.Home) { page = SettingsPage.Home }

    if (page == SettingsPage.Home) {
        SettingsHome(modifier, prefs, logging, onReplayTutorial) { page = it }
        return
    }

    val s = prefs.settings
    fun update(block: (WatchSettings) -> WatchSettings) =
        onPrefs(prefs.copy(settings = block(s), watchProfileId = WatchProfile.CUSTOM))

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { page = SettingsPage.Home }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = chromePaper())
            }
            Text(page.title(), color = chromePaper(), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space16, vertical = Space8),
            verticalArrangement = Arrangement.spacedBy(Space16),
        ) {
            when (page) {
                SettingsPage.Camera -> {
                    CameraPicker(prefs, compact = false, onPrefs = onPrefs)
                    NumberRow(
                        "Emissivity",
                        "",
                        "0.95",
                        "0.95 for PVC wrap. Sent when a P2 Pro-family camera accepts the command. Nickel and bare cans read cold.",
                        prefs.emissivity,
                        0.05f,
                        1f,
                        generation,
                    ) { onPrefs(prefs.copy(emissivity = it)) }
                    NumberRow(
                        "Camera Distance",
                        "m",
                        "0.45",
                        "Mounting height, for your notes. It is stored and not sent to the camera.",
                        prefs.distanceM,
                        0.05f,
                        5f,
                        generation,
                    ) { onPrefs(prefs.copy(distanceM = it)) }
                }
                SettingsPage.Profile -> ProfilePicker(prefs, onPrefs) { generation++ }
                SettingsPage.Alarm -> {
                    ToggleRow(
                        "Night View",
                        defaultOn = false,
                        detail = "Dims the app to red on black for a dark bench. Alarms stay full brightness.",
                        checked = prefs.nightView,
                    ) { onPrefs(prefs.copy(nightView = it)) }
                    ToggleRow(
                        "Siren Sound",
                        defaultOn = true,
                        detail = "On plays the bundled siren at full alarm-stream volume. Off keeps the red alarm, the notification, and the log, and the watch screen says Siren Muted.",
                        checked = prefs.sirenEnabled,
                    ) { onPrefs(prefs.copy(sirenEnabled = it)) }
                    if (!prefs.sirenEnabled) {
                        Text("Siren Muted", color = WarnAmber, style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = { MonitorRuntime.testSiren(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Test Siren")
                    }
                    ToggleRow(
                        "Keep Screen on",
                        defaultOn = true,
                        detail = "While the watch is running and the phone is on the charger.",
                        checked = prefs.keepScreenOn,
                    ) { onPrefs(prefs.copy(keepScreenOn = it)) }
                }
                SettingsPage.Remote -> {
                    Text(
                        "Free on https://ntfy.sh. No account and no login. A flame, a hot-cell alarm, or a camera fault repeats about every 15 minutes until you acknowledge. That stays inside the free limit of 250 messages a day.",
                        color = chromeMuted(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ToggleRow(
                        "Repeat to a Second Phone",
                        defaultOn = false,
                        detail = "Uses the topic below on https://ntfy.sh. A failed send stays queued. Off until you turn it on.",
                        checked = prefs.remoteAlertsEnabled,
                    ) { onPrefs(prefs.copy(remoteAlertsEnabled = it)) }
                    TextFieldRow(
                        "ntfy Topic",
                        "Leave the long name.",
                        "Generated so it is hard to guess. On the other phone, subscribe to this name in the ntfy app. Leave it long. The public server has no private topics.",
                        prefs.ntfyTopic,
                    ) { onPrefs(prefs.copy(ntfyTopic = it.trim())) }
                    if (prefs.ntfyTopic.length < 20) {
                        Text(
                            "This topic is short enough to guess. Leave the long generated name.",
                            color = WarnAmber,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = {
                        onPrefs(prefs)
                        MonitorRuntime.updatePrefs(context, prefs)
                        MonitorRuntime.testRemote(context)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Test Remote Alert") }
                    if (runtime.remoteNote.isNotBlank()) {
                        Text(runtime.remoteNote, color = chromePaper(), style = MaterialTheme.typography.bodySmall)
                    }
                    TextFieldRow(
                        "One-Shot ntfy URL",
                        "Optional.",
                        "Sent once to a full URL you paste. The repeating alert above does not use this.",
                        prefs.ntfyUrl,
                    ) { onPrefs(prefs.copy(ntfyUrl = it)) }
                    TextFieldRow(
                        "Webhook URL",
                        "Optional JSON POST.",
                        "POST JSON. Separate from the charger plug.",
                        prefs.webhookUrl,
                    ) { onPrefs(prefs.copy(webhookUrl = it)) }
                }
                SettingsPage.Charger -> ChargerSettings(prefs, onPrefs)
                SettingsPage.Clips -> {
                    ToggleRow(
                        "Save Alarm Clips",
                        defaultOn = false,
                        detail = "Keeps about ten seconds of the thermal picture, and a small visible frame when the camera has one. On an alarm it saves a snapshot and a short GIF from before and after. The oldest are deleted after 50. Off until you turn it on.",
                        checked = prefs.alarmClipsEnabled,
                    ) { onPrefs(prefs.copy(alarmClipsEnabled = it)) }
                    Text(
                        "Logging itself is the switch on the watch screen. Turning it on also starts the watch.",
                        color = chromeMuted(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    NumberRow(
                        "Log Rate",
                        "Hz",
                        "1",
                        "0.5 to 10. Default 1, about once a second. Timestamps are wall clock, in demo mode too.",
                        prefs.logHz,
                        0.5f,
                        10f,
                        generation,
                    ) { onPrefs(prefs.copy(logHz = it)) }
                    ToggleRow(
                        "Alarm Snapshots",
                        defaultOn = true,
                        detail = "While a log is open, save a PNG beside the CSV when an alarm fires.",
                        checked = prefs.logAlarmSnapshots,
                    ) { onPrefs(prefs.copy(logAlarmSnapshots = it)) }
                }
                SettingsPage.Safety -> {
                    Text(
                        "Start the watch. Flick a lighter, or hold something hot, in view. The test checks the picture, the flame path, the siren, and the remote alert and plug if you turned them on.",
                        color = chromeMuted(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = {
                        onPrefs(prefs)
                        MonitorRuntime.updatePrefs(context, prefs)
                        MonitorRuntime.runSelfTest(context)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Run Self-Test") }
                    if (runtime.selfTestNote.isNotBlank()) {
                        Text(runtime.selfTestNote, color = chromePaper(), style = MaterialTheme.typography.bodySmall)
                    }
                    ToggleRow(
                        "Daily Self-Test Reminder",
                        defaultOn = false,
                        detail = "A notification about once a day. It does not start an alarm.",
                        checked = prefs.selfTestReminder,
                    ) { onPrefs(prefs.copy(selfTestReminder = it)) }
                    Text("Vent Sound", color = chromePaper(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Listens on this phone for the click and hiss of a cell vent. It only raises a warning and, for about a minute, makes the flame watch one step more sensitive. Audio stays on the phone and is not saved or uploaded.",
                        color = chromeMuted(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ToggleRow(
                        "Listen for a Vent",
                        defaultOn = false,
                        detail = "Needs the microphone. Off until you turn it on. This is not a full alarm.",
                        checked = prefs.ventListenerEnabled,
                    ) { enabled ->
                        if (!enabled) {
                            onPrefs(prefs.copy(ventListenerEnabled = false))
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            onPrefs(prefs.copy(ventListenerEnabled = true))
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    NumberRow(
                        "Vent Sensitivity",
                        "",
                        "3",
                        "1 is the least sensitive and 5 is the most. Default 3.",
                        prefs.ventSensitivity.toFloat(),
                        1f,
                        5f,
                        generation,
                        whole = true,
                    ) { onPrefs(prefs.copy(ventSensitivity = it.toInt())) }
                    if (runtime.ventDetections.isEmpty()) {
                        Text("No vent sounds yet.", color = chromeMuted(), style = MaterialTheme.typography.bodySmall)
                    } else {
                        runtime.ventDetections.forEach { line ->
                            Text(line, color = chromePaper(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Phone Health", color = chromePaper(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The watch warns, without a full alarm, when the phone battery is low or not charging, when the phone is hot, and when a live camera picture stays flat long enough that the lens may be dirty or blocked. Unplugging the camera is still a full fault.",
                        color = chromeMuted(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SettingsPage.Startup -> {
                    ToggleRow(
                        "Start on Boot",
                        defaultOn = false,
                        detail = "Also starts when a known camera is plugged in. Leave this off until it is mounted, or a missing camera will fault.",
                        checked = prefs.startOnBoot,
                    ) { onPrefs(prefs.copy(startOnBoot = it)) }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }) { Text("Allow Alarms Through Do Not Disturb") }
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(
                            Uri.parse("package:${context.packageName}"),
                        )
                        context.startActivity(intent)
                    }) { Text("Don't Let the Phone Sleep This App") }
                    FullScreenIntentBanner()
                }
                SettingsPage.Advanced -> AdvancedThresholds(
                    s,
                    prefs.useFahrenheit,
                    generation,
                    ::update,
                    onFahrenheit = { useF ->
                        if (useF != prefs.useFahrenheit) {
                            generation++
                            onPrefs(prefs.copy(useFahrenheit = useF))
                        }
                    },
                ) { confirmReset = true }
                SettingsPage.About -> {
                    Text(
                        ReleaseNotes.label(),
                        color = chromePaper(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "Hot Spotter is a bench aid. It is not a certified fire alarm, smoke detector, or medical device.",
                        color = chromeMuted(),
                    )
                    Button(onClick = onOpenEula, modifier = Modifier.fillMaxWidth()) {
                        Text("End-User License")
                    }
                    Button(onClick = { notices = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open-Source Notices")
                    }
                    Button(
                        onClick = { EditionCamera.shareTestReport(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Send Test Report") }
                    Text(
                        "The report is one raw frame, the USB ids, the interface list, and a short log. It has no account and no serial number. Nothing is uploaded until you pick where to send it.",
                        color = chromeMuted(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SettingsPage.Home -> Unit
            }
        }
    }
    if (notices) {
        HoldPagerWhileShown()
        AlertDialog(
            onDismissRequest = { notices = false },
            containerColor = chromePanel(),
            titleContentColor = chromePaper(),
            textContentColor = chromePaper(),
            title = { Text("Open-Source Notices") },
            text = {
                Text(
                    EditionCamera.notices().trim(),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { notices = false }) { Text("Close") }
            },
        )
    }
    if (confirmReset) {
        HoldPagerWhileShown()
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = chromePanel(),
            titleContentColor = chromePaper(),
            textContentColor = chromePaper(),
            title = { Text("Reset Thresholds?") },
            text = { Text("Every detection number goes back to the bench defaults. Zones, the camera, and alerts stay as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    onPrefs(prefs.copy(settings = WatchSettings(), watchProfileId = WatchProfile.BENCH))
                    generation++
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsHome(
    modifier: Modifier,
    prefs: AppPrefs,
    logging: Boolean,
    onReplayTutorial: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
) {
    val items = listOf(
        SettingsPage.Camera to ("Camera" to cameraSummary(prefs)),
        SettingsPage.Profile to ("Watch Profile" to WatchProfile.fromId(prefs.watchProfileId).title),
        SettingsPage.Alarm to ("Alarm Sound & Screen" to alarmSummary(prefs)),
        SettingsPage.Remote to ("Remote Alerts" to if (prefs.remoteAlertsEnabled) "Repeating on ntfy.sh" else "Off"),
        SettingsPage.Charger to ("Charger Shutoff" to chargerSummary(prefs)),
        SettingsPage.Clips to ("Alarm Clips & Logging" to clipsSummary(prefs, logging)),
        SettingsPage.Safety to ("Safety Checks" to "Self-test, vent, phone health"),
        SettingsPage.Startup to ("Startup & Battery" to if (prefs.startOnBoot) "Starts on boot" else "Does not start on boot"),
        SettingsPage.Advanced to ("Advanced Detection" to "Thresholds"),
        SettingsPage.About to ("About" to "License and a test report"),
    )
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space8),
    ) {
        Text(
            "Settings",
            color = chromePaper(),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Space16, vertical = Space8),
        )
        items.forEach { (page, labels) ->
            ListItem(
                headlineContent = { Text(labels.first) },
                supportingContent = { Text(labels.second) },
                colors = ListItemDefaults.colors(
                    containerColor = chromeInk(),
                    headlineColor = chromePaper(),
                    supportingColor = chromeMuted(),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(page) },
            )
        }
        TextButton(
            onClick = onReplayTutorial,
            modifier = Modifier.padding(horizontal = Space8),
        ) { Text("Show the Tour") }
    }
}

private fun alarmSummary(prefs: AppPrefs): String {
    val siren = if (prefs.sirenEnabled) "Siren on" else "Siren Muted"
    return if (prefs.nightView) "$siren · Night View" else siren
}

private fun chargerSummary(prefs: AppPrefs): String {
    val kind = PlugKind.fromStored(prefs.plugKind, prefs.shellyEnabled)
    if (kind == PlugKind.NONE) return "Off"
    val where = prefs.plugHost.ifBlank { prefs.shellyHost }.ifBlank { "No address yet" }
    return "${kind.label} · $where"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChargerSettings(prefs: AppPrefs, onPrefs: (AppPrefs) -> Unit) {
    val context = LocalContext.current
    val runtime by MonitorRuntime.state.collectAsState()
    val kind = PlugKind.fromStored(prefs.plugKind, prefs.shellyEnabled)
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var passwordSaved by remember { mutableStateOf(false) }
    var tokenSaved by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        passwordSaved = PlugSecrets.hasPassword(context)
        tokenSaved = PlugSecrets.hasToken(context)
    }
    fun rememberPlug(next: AppPrefs) {
        if (password.isNotEmpty()) {
            PlugSecrets.setPassword(context, password)
            passwordSaved = true
            password = ""
        }
        if (token.isNotEmpty()) {
            PlugSecrets.setToken(context, token)
            tokenSaved = true
            token = ""
        }
        onPrefs(next)
        MonitorRuntime.updatePrefs(context, next)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space16)) {
    Text(
        "An alarm turns the plug off and leaves it off. Test plug turns it off, then on. If the off cannot be confirmed, the siren sounds. Tuya, Govee, and Amazon plugs need their cloud, so they are not listed. A Matter plug can be switched off through Home Assistant.",
        color = chromeMuted(),
        style = MaterialTheme.typography.bodySmall,
    )
    Text("Plug", color = chromeMuted(), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space8)) {
        PlugKind.entries.forEach { choice ->
            FilterChip(
                selected = kind == choice,
                onClick = {
                    rememberPlug(
                        prefs.copy(
                            plugKind = choice.id,
                            shellyEnabled = choice == PlugKind.SHELLY,
                        ),
                    )
                },
                label = { Text(choice.label) },
            )
        }
    }
    if (kind == PlugKind.NONE) return
    val hostLabel = if (kind == PlugKind.HOME_ASSISTANT) "Home Assistant Address" else "Plug Address"
    val hostHint = when (kind) {
        PlugKind.SHELLY -> "One address, or several separated by commas. Reserve each IP on the router."
        PlugKind.HOME_ASSISTANT -> "The local address, such as http://192.168.1.20:8123. The token is stored on this phone, not in this field."
        PlugKind.KASA -> "The plug's IP. This uses the local Kasa protocol on port 9999. No TP-Link account."
        PlugKind.TAPO -> "The plug's IP. The email and password are the local Tapo login, not a cloud session."
        PlugKind.TASMOTA -> "The plug's IP. Leave the password blank when the device has none."
        PlugKind.WEMO -> "The plug's IP. The phone talks to the Wemo on the local network. No Belkin account."
        PlugKind.NONE -> ""
    }
    TextFieldRow(hostLabel, "Reserved address.", hostHint, prefs.plugHost.ifBlank { prefs.shellyHost }) { value ->
        val host = value.trim()
        rememberPlug(prefs.copy(plugHost = host, shellyHost = host, shellyEnabled = kind == PlugKind.SHELLY, plugKind = kind.id))
    }
    if (kind == PlugKind.SHELLY) {
        Text("Plug Generation", color = chromeMuted(), style = MaterialTheme.typography.bodySmall)
        SegmentChoice(
            options = listOf("auto" to "Auto", "gen2" to "Gen2 / Plus", "gen1" to "Gen1"),
            selected = prefs.shellyGeneration,
        ) { rememberPlug(prefs.copy(shellyGeneration = it, plugKind = kind.id, shellyEnabled = true)) }
    }
    if (kind == PlugKind.TAPO || kind == PlugKind.TASMOTA) {
        TextFieldRow(
            if (kind == PlugKind.TAPO) "Email" else "Username",
            if (kind == PlugKind.TASMOTA) "admin if you leave it blank." else "The local account name.",
            "Stored with the other settings. The password is kept in the phone keystore.",
            prefs.plugUsername,
        ) { rememberPlug(prefs.copy(plugUsername = it.trim(), plugKind = kind.id)) }
        TextFieldRow(
            "Password",
            if (passwordSaved) "A password is saved on this phone. Type a new one to replace it." else "Saved in the phone keystore.",
            "Not written into the settings file.",
            password,
            hide = true,
        ) { password = it }
        if (passwordSaved) {
            TextButton(onClick = {
                PlugSecrets.setPassword(context, "")
                passwordSaved = false
                password = ""
            }) { Text("Forget Saved Password") }
        }
    }
    if (kind == PlugKind.HOME_ASSISTANT) {
        TextFieldRow(
            "Entity",
            "switch.bench_charger",
            "The entity id Home Assistant uses for this plug.",
            prefs.plugEntity,
        ) { rememberPlug(prefs.copy(plugEntity = it.trim(), plugKind = kind.id)) }
        TextFieldRow(
            "Long-Lived Token",
            if (tokenSaved) "A token is saved on this phone. Type a new one to replace it." else "From Home Assistant, profile, long-lived access tokens.",
            "Stored in the phone keystore. It is not sent anywhere except that Home Assistant address.",
            token,
            hide = true,
        ) { token = it }
        if (tokenSaved) {
            TextButton(onClick = {
                PlugSecrets.setToken(context, "")
                tokenSaved = false
                token = ""
            }) { Text("Forget Saved Token") }
        }
    }
    Button(onClick = {
        val next = prefs.copy(
            plugKind = kind.id,
            plugHost = prefs.plugHost.ifBlank { prefs.shellyHost },
            shellyHost = prefs.plugHost.ifBlank { prefs.shellyHost },
            shellyEnabled = kind == PlugKind.SHELLY,
        )
        rememberPlug(next)
        MonitorRuntime.testPlug(context)
    }, modifier = Modifier.fillMaxWidth()) { Text("Test Plug") }
    if (runtime.shellyNote.isNotBlank()) {
        Text(runtime.shellyNote, color = chromePaper(), style = MaterialTheme.typography.bodySmall)
    }
    if (runtime.shellyWarning.isNotBlank()) {
        Text(runtime.shellyWarning, color = WarnAmber, style = MaterialTheme.typography.bodySmall)
    }
    }
}

private fun cameraSummary(prefs: AppPrefs): String {
    val name = prefs.cameraChoice().displayTitle()
    return "$name · emissivity ${trimNumber(prefs.emissivity)}"
}

private fun clipsSummary(prefs: AppPrefs, logging: Boolean): String {
    val clips = if (prefs.alarmClipsEnabled) "Clips on" else "Clips off"
    val log = if (logging) "Logging" else "Not logging"
    return "$clips · $log · ${trimNumber(prefs.logHz)} Hz"
}

private fun SettingsPage.title(): String = when (this) {
    SettingsPage.Home -> "Settings"
    SettingsPage.Camera -> "Camera"
    SettingsPage.Profile -> "Watch Profile"
    SettingsPage.Alarm -> "Alarm Sound & Screen"
    SettingsPage.Remote -> "Remote Alerts"
    SettingsPage.Charger -> "Charger Shutoff"
    SettingsPage.Clips -> "Alarm Clips & Logging"
    SettingsPage.Safety -> "Safety Checks"
    SettingsPage.Startup -> "Startup & Battery"
    SettingsPage.Advanced -> "Advanced Detection"
    SettingsPage.About -> "About"
}

@Composable
private fun ProfilePicker(prefs: AppPrefs, onPrefs: (AppPrefs) -> Unit, onApplied: () -> Unit) {
    val selected = WatchProfile.fromId(prefs.watchProfileId).id
    Text(
        "Picking a profile fills in the temperatures. You can still type any number afterwards. Flame detection stays on.",
        color = chromeMuted(),
        style = MaterialTheme.typography.bodySmall,
    )
    WatchProfile.all.forEach { profile ->
        val inUse = profile.id == selected
        Column(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onPrefs(profile.applyTo(prefs))
                    onApplied()
                }
                .padding(vertical = Space8),
        ) {
            Text(
                if (inUse) "${profile.title} · in use" else profile.title,
                color = chromePaper(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(profile.detail, color = chromeMuted(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AdvancedThresholds(
    s: WatchSettings,
    fahrenheit: Boolean,
    generation: Int,
    update: ((WatchSettings) -> WatchSettings) -> Unit,
    onFahrenheit: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Text("Temperature Unit", color = chromePaper(), style = MaterialTheme.typography.titleMedium)
    Text(
        "Thresholds are stored in Celsius. This only changes how they are shown and typed. The watch uses the saved numbers.",
        color = chromeMuted(),
        style = MaterialTheme.typography.bodySmall,
    )
    SegmentChoice(
        listOf("c" to "°C", "f" to "°F"),
        selected = if (fahrenheit) "f" else "c",
        onSelect = { onFahrenheit(it == "f") },
    )
    val orderHint = temperatureOrderHint(s)
    if (orderHint != null) {
        Text(orderHint, color = WarnAmber, style = MaterialTheme.typography.bodySmall)
    }
    Text(
        "Used by the detector as saved. Type any number, including decimals. The slider is a wide range and does not limit what you type. Words are not saved. Reset puts the bench defaults back.",
        color = chromeMuted(),
        style = MaterialTheme.typography.bodySmall,
    )
    ThresholdGroup("Flame", startOpen = true) {
        NumberRow(
            "Flame Sensitivity",
            "",
            "4",
            "1 is the least sensitive and 5 is the most. Default 4. Even 1 still catches a lighter at about two feet.",
            s.flameSensitivity.toFloat(),
            1f,
            5f,
            generation,
            whole = true,
        ) { v -> update { it.copy(flameSensitivity = v.toInt()) } }
        ToggleRow(
            "Flame on the Whole Frame",
            defaultOn = true,
            detail = "On, an open flame counts anywhere, including inside an iron-holder mask. Off, it counts only inside the zones you drew. With no zones drawn, the whole frame stays watched.",
            checked = s.flameWholeFrame,
        ) { v -> update { it.copy(flameWholeFrame = v) } }
    }
    ThresholdGroup("Open Bench") {
        TempRow("Warning", "°C", 60f, "A cell-sized spot held here, with no iron nearby, warns.", s.warnTempC, fahrenheit, generation) { v -> update { it.copy(warnTempC = v) } }
        TempRow("Alarm", "°C", 75f, "Held this hot, a warning becomes an alarm.", s.alarmTempC, fahrenheit, generation) { v -> update { it.copy(alarmTempC = v) } }
        NumberRow("Warning Hold", "s", "10", "How long it must sit on the warning temperature.", s.warnSustainSec, 2f, 120f, generation) { v -> update { it.copy(warnSustainSec = v) } }
        NumberRow("Alarm Hold", "s", "12", "How long it must sit on the alarm temperature.", s.alarmSustainSec, 2f, 180f, generation) { v -> update { it.copy(alarmSustainSec = v) } }
        NumberRow("Escalate After", "s", "25", "A spot that stays at or above the warning temperature this long alarms even if it is only flat.", s.warningEscalateSec, 5f, 300f, generation) { v -> update { it.copy(warningEscalateSec = v) } }
        TempRow("Self-Heat Rate", "°C/s", 0.05f, "Warming this fast, with no iron nearby, is a warning.", s.selfHeatRateCPerSec, fahrenheit, generation, rate = true) { v -> update { it.copy(selfHeatRateCPerSec = v) } }
        NumberRow("Self-Heat Hold", "s", "12", "How long that rise has to continue.", s.selfHeatSustainSec, 3f, 120f, generation) { v -> update { it.copy(selfHeatSustainSec = v) } }
        TempRow("Self-Heat Minimum", "°C", 38f, "Ignore tiny rises below this.", s.selfHeatMinTempC, fahrenheit, generation) { v -> update { it.copy(selfHeatMinTempC = v) } }
        NumberRow("Self-Heat Window", "s", "5", "The rise is measured over this recent window.", s.selfHeatWindowSec, 2f, 20f, generation) { v -> update { it.copy(selfHeatWindowSec = v) } }
    }
    ThresholdGroup("Runaway") {
        TempRow("Runaway Rate", "°C/s", 2f, "A cell-sized rise this fast alarms with no grace.", s.runawayRateCPerSec, fahrenheit, generation, rate = true) { v -> update { it.copy(runawayRateCPerSec = v) } }
        TempRow("Runaway", "°C", 120f, "Immediate alarm on a cell-sized area.", s.runawayTempC, fahrenheit, generation) { v -> update { it.copy(runawayTempC = v) } }
        NumberRow("Runaway Window", "s", "0.8", "Slope is measured over this recent window.", s.runawayWindowSec, 0.4f, 3f, generation) { v -> update { it.copy(runawayWindowSec = v) } }
        NumberRow("Runaway Sustain", "s", "0.45", "The fast rise must hold this long, so a single noisy frame does not trip.", s.runawaySustainSec, 0.2f, 3f, generation) { v -> update { it.copy(runawaySustainSec = v) } }
    }
    ThresholdGroup("Soldering") {
        NumberRow("Cool-Down Grace", "s", "90", "After the iron leaves, alarm only if the spot is not cooling when this ends.", s.solderGraceSec, 2f, 600f, generation) { v -> update { it.copy(solderGraceSec = v) } }
        TempRow("Cooling Slope", "°C/s", -0.03f, "More negative means it must be falling faster.", s.minCoolingSlope, fahrenheit, generation, rate = true) { v -> update { it.copy(minCoolingSlope = v) } }
        TempRow("Solder-Zone Warning", "°C", 70f, "Higher than the open bench.", s.solderWarnC, fahrenheit, generation) { v -> update { it.copy(solderWarnC = v) } }
        TempRow("Solder-Zone Alarm", "°C", 90f, "Alarm temperature inside a solder zone.", s.solderAlarmC, fahrenheit, generation) { v -> update { it.copy(solderAlarmC = v) } }
        TempRow("Solder Self-Heat Rate", "°C/s", 0.12f, "Faster rise required inside a solder zone.", s.solderSelfHeatRate, fahrenheit, generation, rate = true) { v -> update { it.copy(solderSelfHeatRate = v) } }
        NumberRow("Cooling Watch", "s", "180", "How long a cooling spot is followed after the grace.", s.coolingWatchSec, 5f, 900f, generation) { v -> update { it.copy(coolingWatchSec = v) } }
    }
    ThresholdGroup("Charge Rack") {
        TempRow("Charge Warning", "°C", 45f, "Packs sitting here should stay cool.", s.chargeWarnC, fahrenheit, generation) { v -> update { it.copy(chargeWarnC = v) } }
        TempRow("Charge Alarm", "°C", 60f, "Alarm temperature on a charge rack.", s.chargeAlarmC, fahrenheit, generation) { v -> update { it.copy(chargeAlarmC = v) } }
        TempRow("Charge Runaway", "°C", 80f, "No grace.", s.chargeRunawayC, fahrenheit, generation) { v -> update { it.copy(chargeRunawayC = v) } }
        NumberRow("Charge Grace", "s", "8", "Short, for a spot weld that happens to land here.", s.chargeGraceSec, 0f, 120f, generation) { v -> update { it.copy(chargeGraceSec = v) } }
        NumberRow("Charge Hold", "s", "8", "How quickly a warm pack warns.", s.chargeSustainSec, 2f, 120f, generation) { v -> update { it.copy(chargeSustainSec = v) } }
        NumberRow("Charge Escalate", "s", "30", "How soon a held warning becomes an alarm.", s.chargeEscalateSec, 5f, 300f, generation) { v -> update { it.copy(chargeEscalateSec = v) } }
    }
    ThresholdGroup("Blobs and the Iron") {
        TempRow("Hot Above Background", "°C", 10f, "Pixels this far above the bench become a blob.", s.blobDeltaC, fahrenheit, generation, absolute = false) { v -> update { it.copy(blobDeltaC = v) } }
        NumberRow("Background Percentile", "", "0.35", "Which part of the frame is treated as the bench behind a blob.", s.backgroundPercentile, 0.05f, 0.6f, generation) { v -> update { it.copy(backgroundPercentile = v) } }
        NumberRow("Smallest Blob", "px", "8", "Blobs smaller than this are noise.", s.minBlobPixels.toFloat(), 3f, 80f, generation, whole = true) { v -> update { it.copy(minBlobPixels = v.toInt()) } }
        NumberRow("Smallest Cell Area", "", "0.0015", "Fraction of the frame. Raise it if the bench is far from the camera.", s.cellMinAreaFraction, 0.0004f, 0.05f, generation) { v -> update { it.copy(cellMinAreaFraction = v) } }
        NumberRow("Tool Max Area", "", "0.008", "A parked tool larger than this is not called an iron tip.", s.toolMaxAreaFraction, 0.0008f, 0.04f, generation) { v -> update { it.copy(toolMaxAreaFraction = v) } }
        NumberRow("Moving-Tool Max Area", "", "0.025", "An iron halo can be larger than the tip while it is moving.", s.movingToolMaxAreaFraction, 0.002f, 0.08f, generation) { v -> update { it.copy(movingToolMaxAreaFraction = v) } }
        TempRow("Tool Absolute", "°C", 180f, "A small blob at least this hot can be an iron. Low-emissivity tips may never reach it; motion still counts.", s.toolAbsC, fahrenheit, generation) { v -> update { it.copy(toolAbsC = v) } }
        TempRow("Tool Contrast", "°C", 28f, "How far above the body a tip must sit.", s.toolContrastC, fahrenheit, generation, absolute = false) { v -> update { it.copy(toolContrastC = v) } }
        TempRow("Tool Sharpness", "°C", 45f, "How peaked a tip has to be.", s.toolSharpnessC, fahrenheit, generation, absolute = false) { v -> update { it.copy(toolSharpnessC = v) } }
        NumberRow("Tool Speed", "", "0.04", "Normalized frame-widths per second. A moving compact hot blob is treated as the iron.", s.toolMinSpeed, 0.01f, 0.5f, generation) { v -> update { it.copy(toolMinSpeed = v) } }
        NumberRow("Tool Near a Cell", "", "0.18", "How close a hot tip has to be before the cell is treated as being soldered.", s.toolProximity, 0.04f, 0.4f, generation) { v -> update { it.copy(toolProximity = v) } }
        TempRow("Core Temperature", "°C", 200f, "A core at least this hot can mask the iron neighborhood.", s.coreTempC, fahrenheit, generation) { v -> update { it.copy(coreTempC = v) } }
        NumberRow("Core Halo", "px", "3", "Pixels around a hot core that count as the iron.", s.coreHaloPx, 1f, 12f, generation) { v -> update { it.copy(coreHaloPx = v) } }
        TempRow("Halo Cap", "°C", 95f, "Warm pixels under this, next to a core, stay in the halo.", s.haloCapC, fahrenheit, generation) { v -> update { it.copy(haloCapC = v) } }
        NumberRow("Match Distance", "", "0.12", "How far a blob can move and still be the same spot.", s.matchDistance, 0.03f, 0.3f, generation) { v -> update { it.copy(matchDistance = v) } }
        NumberRow("Coast", "s", "0.8", "How long a spot is remembered when it blinks out.", s.coastSec, 0.2f, 3f, generation) { v -> update { it.copy(coastSec = v) } }
        NumberRow("Tool Grace", "s", "4", "A brand new tiny hot flash, such as a spot weld, is ignored for this long.", s.toolTransientSec, 0.2f, 20f, generation) { v -> update { it.copy(toolTransientSec = v) } }
        NumberRow("Parked Iron Warn", "s", "25", "A very hot spot that stops moving, outside the holder mask.", s.restingHotWarnSec, 5f, 180f, generation) { v -> update { it.copy(restingHotWarnSec = v) } }
        NumberRow("Parked Iron Alarm", "s", "50", "Same spot, still there.", s.restingHotAlarmSec, s.restingHotWarnSec + 5f, 400f, generation) { v -> update { it.copy(restingHotAlarmSec = v) } }
    }
    ThresholdGroup("Camera Faults") {
        NumberRow("Frozen Frames", "s", "8", "Identical frames this long are a fault.", s.frozenFrameSec, 3f, 60f, generation) { v -> update { it.copy(frozenFrameSec = v) } }
        NumberRow("Reconnect Grace", "s", "12", "After the camera has been live, a dropout is tolerated this long.", s.reconnectGraceSec, 3f, 60f, generation) { v -> update { it.copy(reconnectGraceSec = v) } }
        NumberRow(
            "First Connect",
            "s",
            "30",
            "A first search, including the USB permission prompt, stays on the status banner. It faults once when this time is up, and never waits longer than 30 seconds. A camera that was already streaming uses the reconnect time.",
            s.firstConnectGraceSec,
            8f,
            120f,
            generation,
        ) { v -> update { it.copy(firstConnectGraceSec = v) } }
        NumberRow("Rearm After Ack", "s", "120", "A silenced alarm starts screaming again if the condition is still there.", s.rearmAfterAckSec, 1f, 600f, generation) { v -> update { it.copy(rearmAfterAckSec = v) } }
        NumberRow("Memory", "s", "300", "How long a finished spot is remembered.", s.memoryKeepSec, 10f, 900f, generation) { v -> update { it.copy(memoryKeepSec = v) } }
    }
    Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset Thresholds") }
}

@Composable
private fun ThresholdGroup(title: String, startOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    Column(verticalArrangement = Arrangement.spacedBy(Space8)) {
        TextButton(onClick = { open = !open }) {
            Text(if (open) "$title  ▾" else "$title  ▸", color = chromePaper())
        }
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(Space8)) { content() }
        }
    }
}

@Composable
private fun TempRow(
    label: String,
    unit: String,
    defaultCelsius: Float,
    detail: String,
    celsius: Float,
    fahrenheit: Boolean,
    generation: Int,
    absolute: Boolean = true,
    rate: Boolean = false,
    onValue: (Float) -> Unit,
) {
    TemperatureRow(
        label = label,
        unit = unit,
        defaultCelsius = defaultCelsius,
        detail = detail,
        celsius = celsius,
        fahrenheit = fahrenheit,
        generation = generation,
        absolute = absolute,
        rate = rate,
        onCelsius = onValue,
    )
}
