package com.benchfirewatch.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benchfirewatch.engine.WatchDisplay
import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.model.CameraChoice
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.PaletteChoice
import com.benchfirewatch.model.ViewMode
import com.benchfirewatch.model.Zone
import com.benchfirewatch.model.ZoneKind
import com.benchfirewatch.model.cameraChoice
import com.benchfirewatch.render.ContentRect
import com.benchfirewatch.render.FrameGeometry
import com.benchfirewatch.render.PictureMapping
import com.benchfirewatch.render.PictureRotation
import com.benchfirewatch.service.MonitorRuntime
import com.benchfirewatch.service.MonitorUiState
import com.benchfirewatch.source.SimulatedThermalSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    modifier: Modifier,
    state: MonitorUiState,
    prefs: AppPrefs,
    onPrefs: (AppPrefs) -> Unit,
    onUpdate: ((AppPrefs) -> AppPrefs) -> Unit,
    onFullScreen: () -> Unit,
    onEditing: (Boolean) -> Unit,
    onSheet: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var zoneName by remember { mutableStateOf("") }
    var zonesOpen by remember { mutableStateOf(false) }
    var seenZones by remember { mutableIntStateOf(prefs.zones.size) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    LaunchedEffect(editing) { onEditing(editing) }
    LaunchedEffect(sheet) { onSheet(sheet) }
    DisposableEffect(Unit) {
        onDispose {
            onEditing(false)
            onSheet(false)
        }
    }
    LaunchedEffect(prefs.zones.size) {
        if (prefs.zones.size > seenZones) zonesOpen = true
        seenZones = prefs.zones.size
    }
    if (sheet) HoldPagerWhileShown()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            StatusBanner(state, prefs)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = Space16, vertical = Space8),
                verticalArrangement = Arrangement.spacedBy(Space8),
            ) {
                CompactStrip(state, prefs, onPrefs)
                FullScreenIntentBanner()
                HotWorkPill(state)
                ThermalViewport(
                    state = state,
                    rotation = PictureRotation.fromDegrees(prefs.rotationDegrees),
                    editing = editing,
                    draft = draft,
                    onDraft = { draft = it },
                    onFullScreen = onFullScreen,
                    onRotate = {
                        draft = null
                        val next = PictureRotation.fromDegrees(prefs.rotationDegrees).next()
                        onPrefs(prefs.copy(rotationDegrees = next.degrees))
                    },
                    onGear = { sheet = true },
                )
                Text(
                    "${prefs.viewMode.shortLabel} · ${prefs.palette.shortLabel}",
                    color = chromePaper(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { sheet = true }
                        .padding(vertical = 4.dp),
                )
                if (prefs.showStats) {
                    Text(
                        state.diagnostics.ifBlank { "No blobs yet" },
                        color = chromePaper(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
                Button(
                    onClick = {
                        if (state.running) MonitorRuntime.stop(context) else MonitorRuntime.start(context)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.running) AlarmRed else if (LocalChrome.current.night) chromePanelHigh() else OkGreen,
                        contentColor = if (state.running) Color.White else if (LocalChrome.current.night) chromePaper() else Ink,
                    ),
                ) {
                    Text(if (state.running) "Stop Watch" else "Start Watch")
                }
                val alarming = state.status == MonitorStatus.ALARM ||
                    state.status == MonitorStatus.FLAME ||
                    state.status == MonitorStatus.FAULT
                if (alarming) {
                    Button(
                        onClick = { MonitorRuntime.acknowledge(context) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Acknowledge") }
                }
                OutlinedButton(
                    onClick = {
                        editing = !editing
                        if (!editing) draft = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (editing) "Done Drawing" else "Draw Zones")
                }
                if (editing && draft != null && !draft!!.live) {
                    OutlinedTextField(
                        value = zoneName,
                        onValueChange = { zoneName = it },
                        modifier = Modifier.fillMaxWidth().keepSwipeOffPager(),
                        label = { Text("Name (Optional)") },
                        singleLine = true,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space8),
                        modifier = Modifier.keepSwipeOffPager().horizontalScroll(rememberScrollState()),
                    ) {
                        ZoneKind.entries.forEach { kind ->
                            OutlinedButton(onClick = {
                                val box = draft ?: return@OutlinedButton
                                val name = zoneName.trim().ifBlank { kind.defaultName() }
                                val zone = Zone(
                                    id = UUID.randomUUID().toString().take(8),
                                    name = name,
                                    kind = kind,
                                    left = box.left,
                                    top = box.top,
                                    right = box.right,
                                    bottom = box.bottom,
                                ).normalized()
                                if (zone.right - zone.left > 0.02f && zone.bottom - zone.top > 0.02f) {
                                    onPrefs(prefs.copy(zones = prefs.zones + zone))
                                }
                                draft = null
                                zoneName = ""
                            }) { Text(kind.label()) }
                        }
                        TextButton(onClick = {
                            draft = null
                            zoneName = ""
                        }) { Text("Cancel") }
                    }
                }
                HotWorkPresets()
                ZonesCard(
                    zones = prefs.zones,
                    open = zonesOpen,
                    onOpen = { zonesOpen = it },
                    onRemove = { zone ->
                        onPrefs(prefs.copy(zones = prefs.zones.filterNot { it.id == zone.id }))
                        scope.launch {
                            val result = snackbar.showSnackbar("Removed ${zone.name}", actionLabel = "Undo")
                            if (result == SnackbarResult.ActionPerformed) {
                                onUpdate { current ->
                                    if (current.zones.any { it.id == zone.id }) current
                                    else current.copy(zones = current.zones + zone)
                                }
                            }
                        }
                    },
                )
                LogControls(state)
                Spacer(Modifier.height(Space8))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Space8))
        if (sheet) {
            ModalBottomSheet(
                onDismissRequest = { sheet = false },
                containerColor = chromePanel(),
            ) {
                PictureOptions(state, prefs, onPrefs)
            }
        }
    }
}

@Composable
private fun PictureOptions(state: MonitorUiState, prefs: AppPrefs, onPrefs: (AppPrefs) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space16).padding(bottom = Space24),
        verticalArrangement = Arrangement.spacedBy(Space16),
    ) {
        Text("Picture", color = chromePaper(), style = MaterialTheme.typography.titleMedium)
        SegmentChoice(
            ViewMode.entries.map { it.id to it.shortLabel },
            prefs.viewMode.id,
        ) { id -> onPrefs(prefs.copy(viewMode = ViewMode.fromId(id))) }
        Text("Palette", color = chromeMuted(), style = MaterialTheme.typography.labelLarge)
        LazyRow(
            Modifier.keepSwipeOffPager(),
            horizontalArrangement = Arrangement.spacedBy(Space8),
        ) {
            items(PaletteChoice.entries) { choice ->
                FilterChip(
                    selected = prefs.palette == choice,
                    onClick = { onPrefs(prefs.copy(palette = choice)) },
                    label = { Text(choice.shortLabel) },
                )
            }
        }
        ToggleRow(
            "Show Stats",
            defaultOn = false,
            detail = "The number feed under the picture. Off by default. It does not change detection.",
            checked = prefs.showStats,
        ) { onPrefs(prefs.copy(showStats = it)) }
        ToggleRow(
            "Night View",
            defaultOn = false,
            detail = "Dims the app to red on black for a dark bench. Alarms stay full brightness.",
            checked = prefs.nightView,
        ) { onPrefs(prefs.copy(nightView = it)) }
        if (prefs.cameraChoice() == CameraChoice.DEMO) {
            Text("Demo", color = chromePaper(), style = MaterialTheme.typography.titleMedium)
            LazyRow(
                Modifier.keepSwipeOffPager(),
                horizontalArrangement = Arrangement.spacedBy(Space8),
            ) {
                items(SimulatedThermalSource.catalog()) { scene ->
                    FilterChip(
                        selected = prefs.scenarioId == scene.id,
                        onClick = { onPrefs(prefs.copy(scenarioId = scene.id)) },
                        label = { Text(scene.title, maxLines = 1) },
                    )
                }
            }
            val blurb = SimulatedThermalSource.catalog().firstOrNull { it.id == prefs.scenarioId }?.blurb
            if (blurb != null) Text(blurb, color = chromeMuted(), style = MaterialTheme.typography.bodySmall)
            Text("Demo Speed", color = chromeMuted(), style = MaterialTheme.typography.labelLarge)
            SegmentChoice(
                listOf("1" to "1×", "4" to "4×", "12" to "12×"),
                prefs.demoSpeed.toInt().toString(),
            ) { id -> onPrefs(prefs.copy(demoSpeed = id.toFloat())) }
        }
        if (state.sourceDetail.isNotBlank()) {
            Text(state.sourceDetail, color = chromeMuted(), style = MaterialTheme.typography.bodySmall)
        }
        if (state.displayNote.isNotBlank()) {
            Text(state.displayNote, color = chromeMuted(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusBanner(state: MonitorUiState, prefs: AppPrefs) {
    val style = statusStyle(state.status.name)
    val quiet = state.status != MonitorStatus.FLAME &&
        state.status != MonitorStatus.ALARM &&
        state.status != MonitorStatus.FAULT &&
        state.status != MonitorStatus.WARNING
    val fill = if (quiet) chromePanel() else style.color
    val title = if (quiet && LocalChrome.current.night) chromePaper() else if (quiet) style.color else style.onColor
    val body = if (quiet) chromePaper() else style.onColor
    Column(
        Modifier
            .fillMaxWidth()
            .background(fill)
            .padding(horizontal = Space16, vertical = Space16)
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space8)) {
            Icon(style.icon, contentDescription = style.wording, tint = title, modifier = Modifier.size(28.dp))
            Text(state.status.name, color = title, style = StatusType.copy(fontSize = 32.sp))
        }
        Text(style.wording, color = title, style = MaterialTheme.typography.labelLarge)
        Text(state.headline, color = body, style = MaterialTheme.typography.bodyLarge)
        if (state.flameNote.isNotBlank()) {
            Text(state.flameNote, color = if (quiet) chromeMuted() else body, style = MaterialTheme.typography.bodySmall)
        }
        if (!prefs.sirenEnabled) {
            Text("Siren Muted", color = if (quiet) WarnAmber else body, fontWeight = FontWeight.Black)
        } else if (state.silenced) {
            Text(
                "Siren silenced. It sounds again only if this is still true.",
                color = if (quiet) WarnAmber else body,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CompactStrip(state: MonitorUiState, prefs: AppPrefs, onPrefs: (AppPrefs) -> Unit) {
    val phone = rememberPhoneBattery()
    Column(verticalArrangement = Arrangement.spacedBy(Space8)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.running) {
                val cameraColor = when (state.batteryWarnLevel) {
                    10 -> AlarmRed
                    20 -> WarnAmber
                    else -> chromeMuted()
                }
                Text(state.cameraBattery.line(), color = cameraColor, modifier = Modifier.weight(1f))
            }
            Text(phone.line(), color = chromeMuted())
        }
        HeartbeatLine(state, prefs)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .toggleable(
                        value = prefs.sirenEnabled,
                        role = Role.Switch,
                        onValueChange = { onPrefs(prefs.copy(sirenEnabled = it)) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (prefs.sirenEnabled) "Siren" else "Siren muted",
                    color = if (prefs.sirenEnabled) chromePaper() else WarnAmber,
                    fontWeight = FontWeight.Black,
                )
                Switch(checked = prefs.sirenEnabled, onCheckedChange = null)
            }
            InfoTip("On plays the bundled siren at full alarm-stream volume. Off keeps the alarm on screen, in the notification, and in the log.")
        }
        state.healthNotes.forEach { note ->
            Text(note, color = WarnAmber, style = MaterialTheme.typography.bodySmall)
        }
        if (state.shellyWarning.isNotBlank()) {
            Text(state.shellyWarning, color = WarnAmber, style = MaterialTheme.typography.bodySmall)
        }
        val warn = when {
            !state.running -> null
            state.batteryWarnLevel == 10 -> "Camera battery is under 10%. The watch goes blind when the camera dies."
            state.batteryWarnLevel == 20 -> "Camera battery is under 20%. Charge it before the watch goes blind."
            else -> null
        }
        if (warn != null) Text(warn, color = WarnAmber, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HeartbeatLine(state: MonitorUiState, prefs: AppPrefs) {
    if (!state.running) return
    val age = WatchDisplay.heartbeatAgeMs(state.frameTimeMs, state.lastFreshFrameMs) ?: return
    val color = when {
        WatchDisplay.heartbeatBlind(age, prefs.settings.frozenFrameSec) -> AlarmRed
        WatchDisplay.heartbeatAmber(age) -> WarnAmber
        else -> OkGreen
    }
    Text(WatchDisplay.heartbeatLabel(age), color = color, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HotWorkPill(state: MonitorUiState) {
    val until = state.hotWorkUntilMs ?: return
    if (!state.running) return
    val remain = (until - state.frameTimeMs).coerceAtLeast(0L)
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(WarnAmber, RoundedCornerShape(12.dp))
            .padding(horizontal = Space16, vertical = Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            WatchDisplay.hotWorkLabel(remain),
            color = Ink,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { MonitorRuntime.setHotWork(context, null) }) {
            Text("Cancel", color = Ink, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun HotWorkPresets() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Space8)) {
        Text("Hot Work", color = chromeMuted(), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Space8), modifier = Modifier.fillMaxWidth()) {
            listOf(5, 15, 30).forEach { minutes ->
                OutlinedButton(
                    onClick = { MonitorRuntime.setHotWork(context, minutes) },
                    modifier = Modifier.weight(1f),
                ) { Text("$minutes Min") }
            }
        }
    }
}

@Composable
private fun ZonesCard(
    zones: List<Zone>,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    onRemove: (Zone) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(chromePanel(), RoundedCornerShape(12.dp)).padding(Space8),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(!open) }.padding(Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Zones (${zones.size})", color = chromePaper(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Collapse zones" else "Expand zones",
                tint = chromePaper(),
            )
        }
        if (open) {
            if (zones.isEmpty()) {
                Text(
                    "Draw an iron holder, a solder area, or a charge rack. Details are in Help.",
                    color = chromeMuted(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(Space8),
                )
            }
            zones.forEach { zone ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space8, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(14.dp).background(zone.kind.color(), RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(Space8))
                    Text("${zone.name} · ${zone.kind.label()}", modifier = Modifier.weight(1f), color = chromePaper())
                    TextButton(onClick = { onRemove(zone) }) { Text("Remove") }
                }
            }
            InfoTip("Iron holder is ignored by the cell tracker. Solder area is more tolerant. Charge rack is stricter. An open flame still alarms. Shiny nickel, bare cans, and iron tips read cold.")
        }
    }
}

@Composable
private fun LogControls(state: MonitorUiState) {
    val context = LocalContext.current
    var reference by remember { mutableStateOf("") }
    var marker by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(chromePanel(), RoundedCornerShape(12.dp)).padding(Space8),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .weight(1f)
                    .toggleable(
                        value = state.logging,
                        role = Role.Switch,
                        onValueChange = { MonitorRuntime.setLogging(context, it) },
                    )
                    .padding(Space8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Logging", color = chromePaper(), style = MaterialTheme.typography.titleMedium)
                Switch(checked = state.logging, onCheckedChange = null)
            }
            IconButton(onClick = { open = !open }) {
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse logging" else "Expand logging",
                    tint = chromePaper(),
                )
            }
        }
        if (open) {
            Column(Modifier.padding(Space8), verticalArrangement = Arrangement.spacedBy(Space8)) {
                Text(
                    "CSV of camera readings, thermocouple notes, and marks. Works in demo mode.",
                    color = chromeMuted(),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.logging) {
                    val minutes = state.logElapsedSec / 60
                    val seconds = state.logElapsedSec % 60
                    Text(
                        "REC  %d:%02d  ·  %d samples".format(minutes, seconds, state.logSamples),
                        color = AlarmRed,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (state.logFileName.isNotBlank()) {
                        Text(state.logFileName, color = chromeMuted(), style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.logError.isNotBlank()) {
                        Text(state.logError, color = FaultOrange, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { reference = it },
                        modifier = Modifier.fillMaxWidth().keepSwipeOffPager(),
                        label = { Text(if (state.prefs.useFahrenheit) "Reference temp °F" else "Reference temp °C") },
                        placeholder = { Text("Thermocouple on the Kapton") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Button(
                        onClick = {
                            val typed = com.benchfirewatch.model.TemperatureScale.acceptedTemperature(reference) ?: return@Button
                            val celsius = com.benchfirewatch.model.TemperatureScale.fromDisplay(
                                typed,
                                state.prefs.useFahrenheit,
                                absolute = true,
                            )
                            MonitorRuntime.logReference(context, celsius)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Log Reference") }
                    OutlinedTextField(
                        value = marker,
                        onValueChange = { marker = it },
                        modifier = Modifier.fillMaxWidth().keepSwipeOffPager(),
                        label = { Text("Marker") },
                        placeholder = { Text("heater on 20W") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val label = marker.trim()
                            if (label.isEmpty()) return@Button
                            MonitorRuntime.logMark(context, label)
                            marker = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Mark") }
                }
            }
        }
    }
}

private data class PhoneBattery(val percent: Int?, val charging: Boolean) {
    fun line(): String {
        val pct = percent ?: return "Phone —"
        return if (charging) "Phone $pct% · charging" else "Phone $pct%"
    }
}

@Composable
private fun rememberPhoneBattery(): PhoneBattery {
    val context = LocalContext.current
    var percent by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val manager = context.getSystemService(BatteryManager::class.java)
        while (true) {
            val capacity = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            percent = capacity
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            delay(5_000)
        }
    }
    val known = percent.takeIf { it in 0..100 }
    return PhoneBattery(known, charging)
}

@Composable
private fun ThermalViewport(
    state: MonitorUiState,
    rotation: PictureRotation,
    editing: Boolean,
    draft: Draft?,
    onDraft: (Draft) -> Unit,
    onFullScreen: () -> Unit,
    onRotate: () -> Unit,
    onGear: () -> Unit,
) {
    val content = ContentRect(state.contentLeft, state.contentTop, state.contentRight, state.contentBottom)
    val (shownW, shownH) = rotation.displayedSize(state.width, state.height)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pictureHeight = if (shownW > 0 && shownH > 0) {
            maxWidth * shownH.toFloat() / shownW
        } else {
            240.dp
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(pictureHeight)
                .keepSwipeOffPager()
                .background(Color(0xFF070806), RoundedCornerShape(12.dp)),
        ) {
            ThermalCanvas(
                state = state,
                rotation = rotation,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(editing, state.width, state.height, content, rotation) {
                        if (!editing) return@pointerInput
                        var origin = Offset.Zero
                        var end = Offset.Zero
                        detectDragGestures(
                            onDragStart = { start ->
                                origin = start
                                end = start
                                onDraft(Draft(live = true, start = origin, end = end, viewW = size.width.toFloat(), viewH = size.height.toFloat()))
                            },
                            onDrag = { change, _ ->
                                end = change.position
                                onDraft(Draft(live = true, start = origin, end = end, viewW = size.width.toFloat(), viewH = size.height.toFloat()))
                            },
                            onDragEnd = {
                                val (imageW, imageH) = rotation.displayedSize(state.width, state.height)
                                val dest = FrameGeometry.place(size.width.toFloat(), size.height.toFloat(), imageW, imageH)
                                    ?: return@detectDragGestures
                                val a = PictureMapping.viewToThermal(origin.x, origin.y, dest, content, rotation)
                                val b = PictureMapping.viewToThermal(end.x, end.y, dest, content, rotation)
                                onDraft(Draft(left = a.first, top = a.second, right = b.first, bottom = b.second))
                            },
                        )
                    },
            ) { frameContent, dest ->
                val box = draft
                if (box != null && dest != null && state.width > 0) {
                    val norm = if (box.live) box.toThermal(dest, frameContent, rotation) else box
                    val drawn = PictureMapping.thermalRectToView(
                        norm.left,
                        norm.top,
                        norm.right,
                        norm.bottom,
                        dest,
                        frameContent,
                        rotation,
                    )
                    drawRect(
                        Paper.copy(alpha = 0.9f),
                        topLeft = Offset(drawn.left, drawn.top),
                        size = Size(
                            (drawn.right - drawn.left).coerceAtLeast(1f),
                            (drawn.bottom - drawn.top).coerceAtLeast(1f),
                        ),
                        style = Stroke(width = 3f),
                    )
                }
            }
            if (!editing) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onFullScreen,
                        ),
                )
            }
            IconButton(
                onClick = onRotate,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Ink.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                    .semantics { contentDescription = "Rotate picture clockwise, now ${rotation.degrees} degrees" },
            ) {
                Icon(BenchIcons.Rotate, contentDescription = null, tint = Paper)
            }
            Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                if (!editing) {
                    IconButton(
                        onClick = onFullScreen,
                        modifier = Modifier.background(Ink.copy(alpha = 0.72f), RoundedCornerShape(8.dp)),
                    ) {
                        Icon(BenchIcons.Fullscreen, contentDescription = "Full Screen", tint = Paper)
                    }
                }
                IconButton(
                    onClick = onGear,
                    modifier = Modifier.background(Ink.copy(alpha = 0.72f), RoundedCornerShape(8.dp)),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Picture Options", tint = Paper)
                }
            }
        }
    }
}

private data class Draft(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val live: Boolean = false,
    val start: Offset = Offset.Zero,
    val end: Offset = Offset.Zero,
    val viewW: Float = 0f,
    val viewH: Float = 0f,
) {
    fun toThermal(dest: com.benchfirewatch.render.PlacedImage, content: ContentRect, rotation: PictureRotation): Draft {
        val a = PictureMapping.viewToThermal(start.x, start.y, dest, content, rotation)
        val b = PictureMapping.viewToThermal(end.x, end.y, dest, content, rotation)
        return Draft(left = a.first, top = a.second, right = b.first, bottom = b.second)
    }
}

private fun ZoneKind.label(): String = when (this) {
    ZoneKind.IGNORE -> "Iron Holder"
    ZoneKind.SOLDER -> "Solder Area"
    ZoneKind.CHARGE -> "Charge Rack"
}

private fun ZoneKind.defaultName(): String = label()

internal fun ZoneKind.color() = when (this) {
    ZoneKind.IGNORE -> IgnoreGray
    ZoneKind.SOLDER -> SolderBlue
    ZoneKind.CHARGE -> ChargeGreen
}

@Composable
fun AlarmOverlay(
    status: MonitorStatus,
    headline: String,
    silent: Boolean,
    onAck: () -> Unit,
    onStop: () -> Unit,
) {
    BackHandler(onBack = onAck)
    val style = statusStyle(status.name)
    val borderAlpha by rememberInfiniteTransition(label = "border").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "border-alpha",
    )
    val view = LocalView.current
    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(style.color.copy(alpha = 0.92f))
            .border(8.dp, style.onColor.copy(alpha = borderAlpha))
            .padding(Space24),
    ) {
        Column(
            Modifier.weight(2f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(style.icon, contentDescription = style.wording, tint = style.onColor, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(Space16))
            Text(status.name, color = style.onColor, style = StatusType)
            Text(style.wording, color = style.onColor, style = MaterialTheme.typography.titleMedium)
            Text(headline, color = style.onColor, style = MaterialTheme.typography.headlineSmall)
            if (silent) {
                Text("Siren Muted", color = style.onColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Button(
                onClick = onAck,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = ButtonDefaults.buttonColors(containerColor = style.onColor, contentColor = style.color),
            ) {
                Text("Acknowledge", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(Space8))
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = style.onColor),
                border = BorderStroke(2.dp, style.onColor),
            ) {
                Text("Stop Watch", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
