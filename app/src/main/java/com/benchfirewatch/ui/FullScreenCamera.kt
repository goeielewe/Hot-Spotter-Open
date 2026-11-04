package com.benchfirewatch.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.PaletteChoice
import com.benchfirewatch.model.ViewMode
import com.benchfirewatch.render.PictureRotation
import com.benchfirewatch.service.MonitorRuntime
import com.benchfirewatch.service.MonitorUiState
import kotlinx.coroutines.delay
/**
 * Edge-to-edge picture. The watch service is not touched: detection, the siren,
 * and the camera-lost fault keep running. Leaving this view only changes the UI.
 */
@Composable
fun FullScreenCamera(
    state: MonitorUiState,
    prefs: AppPrefs,
    onPrefs: (AppPrefs) -> Unit,
    onExit: () -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        val window = activity.window
        val previousCutout = if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode
        } else {
            0
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
            if (Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = previousCutout
                }
            }
            activity.requestedOrientation = previousOrientation
        }
    }
    BackHandler(onBack = onExit)

    val alarm = state.status == MonitorStatus.ALARM || state.status == MonitorStatus.FLAME
    val fault = state.status == MonitorStatus.FAULT
    val urgent = alarm || fault
    var chrome by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    LaunchedEffect(chrome, poke, urgent) {
        if (!chrome || urgent) return@LaunchedEffect
        delay(CHROME_MS)
        chrome = false
    }
    val showBar = chrome || urgent
    val wash = urgent
    val style = statusStyle(state.status.name)
    val banner = if (wash) style.color.copy(alpha = 0.92f) else chromeInk().copy(alpha = 0.58f)
    val context = view.context
    val maxText = if (state.hotC.isNaN()) {
        "Max —"
    } else {
        "Max " + com.benchfirewatch.model.TemperatureScale.formatAbsolute(state.hotC, prefs.useFahrenheit)
    }
    val batteryColor = when {
        wash -> style.onColor
        state.batteryWarnLevel == 10 -> AlarmRed
        state.batteryWarnLevel == 20 -> WarnAmber
        else -> chromePaper()
    }
    val action = when {
        state.siren -> "Silence"
        alarm || fault -> "Acknowledge"
        else -> null
    }
    val overlayInsets = WindowInsets.safeDrawing.union(WindowInsets.displayCutout)
    val rotation = PictureRotation.fromDegrees(prefs.rotationDegrees)

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        ThermalCanvas(
            state,
            Modifier.fillMaxSize().clipToBounds(),
            cover = true,
            rotation = rotation,
        )
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (urgent) return@clickable
                        if (chrome) chrome = false else {
                            chrome = true
                            poke++
                        }
                    },
                ),
        )
        if (showBar) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(overlayInsets)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(banner)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { poke++ },
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        state.status.name,
                        color = if (wash) style.onColor else style.color,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        maxLines = 1,
                    )
                    if (!prefs.sirenEnabled) {
                        Text(
                            "Siren Muted",
                            color = if (wash) style.onColor else WarnAmber,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    if (state.status == MonitorStatus.WARNING || urgent) {
                        Text(
                            state.headline,
                            color = if (wash) style.onColor else chromePaper(),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(Modifier.weight(1f))
                    }
                    if (action != null) {
                        Button(
                            onClick = { MonitorRuntime.acknowledge(context) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (wash) style.onColor else AlarmRed,
                                contentColor = if (wash) style.color else chromeInk(),
                            ),
                        ) {
                            Text(action, maxLines = 1)
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        maxText,
                        color = if (wash) style.onColor else chromePaper(),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                    Text(
                        state.cameraBattery.line(),
                        color = batteryColor,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.silenced) {
                    Text(
                        "Siren silenced. It sounds again only if this is still true.",
                        color = if (wash) style.onColor else WarnAmber,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.running && state.batteryWarnLevel == 10) {
                    Text(
                        "Camera battery is under 10%.",
                        color = if (wash) style.onColor else AlarmRed,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                } else if (state.running && state.batteryWarnLevel == 20) {
                    Text(
                        "Camera battery is under 20%.",
                        color = if (wash) style.onColor else WarnAmber,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayChip(
                            label = "Mode",
                            value = prefs.viewMode.shortLabel,
                            onClick = {
                                val modes = ViewMode.entries
                                val next = modes[(modes.indexOf(prefs.viewMode) + 1) % modes.size]
                                onPrefs(prefs.copy(viewMode = next))
                                poke++
                            },
                            modifier = Modifier.weight(1f),
                            wash = wash,
                            washColor = style.onColor,
                        )
                        OverlayChip(
                            label = "Palette",
                            value = prefs.palette.shortLabel,
                            onClick = {
                                val choices = PaletteChoice.entries
                                val next = choices[(choices.indexOf(prefs.palette) + 1) % choices.size]
                                onPrefs(prefs.copy(palette = next))
                                poke++
                            },
                            modifier = Modifier.weight(1f),
                            wash = wash,
                            washColor = style.onColor,
                        )
                        OverlayChip(
                            label = "Rotate",
                            value = "${rotation.degrees}°",
                            onClick = {
                                onPrefs(prefs.copy(rotationDegrees = rotation.next().degrees))
                                poke++
                            },
                            modifier = Modifier.weight(1f),
                            wash = wash,
                            washColor = style.onColor,
                            contentDescription = "Rotate picture clockwise, now ${rotation.degrees} degrees",
                        )
                }
            }
        }
    }
}

@Composable
private fun OverlayChip(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier,
    wash: Boolean,
    washColor: Color = Ink,
    contentDescription: String = "",
) {
    val shape = RoundedCornerShape(12.dp)
    val ink = if (wash) washColor else chromePaper()
    val described = if (contentDescription.isEmpty()) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Row(
        described
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (wash) washColor.copy(alpha = 0.18f) else chromePaper().copy(alpha = 0.12f))
            .border(1.dp, ink.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = ink.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1)
        Text(
            "  $value",
            color = ink,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Context.findActivity(): Activity {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    error("Full screen camera has no activity")
}

private const val CHROME_MS = 4_000L
