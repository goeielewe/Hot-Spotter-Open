package com.benchfirewatch.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF12140F)
val Panel = Color(0xFF1C1F17)
val PanelHigh = Color(0xFF2A2E23)
val Paper = Color(0xFFF3F0E4)
val Muted = Color(0xFFA7A894)
val OkGreen = Color(0xFFC6F25C)
val WarnAmber = Color(0xFFFFB020)
val AlarmRed = Color(0xFFFF3B30)
val FaultOrange = Color(0xFFFF7A18)
val SolderBlue = Color(0xFF8EBEFF)
val ChargeGreen = Color(0xFF7DDC9A)
val IgnoreGray = Color(0xFFD5D5D0)
val FlameRed = Color(0xFFB00020)

val Space8 = 8.dp
val Space16 = 16.dp
val Space24 = 24.dp

private val dayScheme = darkColorScheme(
    primary = OkGreen,
    onPrimary = Color(0xFF1A2208),
    secondary = WarnAmber,
    onSecondary = Color(0xFF2A1C04),
    tertiary = SolderBlue,
    background = Ink,
    onBackground = Paper,
    surface = Panel,
    onSurface = Paper,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Muted,
    error = AlarmRed,
    onError = Color.White,
)

private val nightScheme = darkColorScheme(
    primary = Color(0xFF8C3030),
    onPrimary = Color(0xFF1A0000),
    secondary = Color(0xFF6A2828),
    onSecondary = Color(0xFF1A0000),
    tertiary = Color(0xFF8C3030),
    background = Color.Black,
    onBackground = Color(0xFFC45A5A),
    surface = Color(0xFF140606),
    onSurface = Color(0xFFC45A5A),
    surfaceVariant = Color(0xFF241010),
    onSurfaceVariant = Color(0xFF7A3333),
    error = AlarmRed,
    onError = Color.White,
)

data class BenchChrome(
    val night: Boolean,
    val ink: Color,
    val panel: Color,
    val panelHigh: Color,
    val paper: Color,
    val muted: Color,
)

private val dayChrome = BenchChrome(false, Ink, Panel, PanelHigh, Paper, Muted)
private val nightChrome = BenchChrome(
    night = true,
    ink = Color.Black,
    panel = Color(0xFF140606),
    panelHigh = Color(0xFF241010),
    paper = Color(0xFFC45A5A),
    muted = Color(0xFF7A3333),
)

val LocalChrome = staticCompositionLocalOf { dayChrome }

@Composable fun chromeInk(): Color = LocalChrome.current.ink
@Composable fun chromePanel(): Color = LocalChrome.current.panel
@Composable fun chromePanelHigh(): Color = LocalChrome.current.panelHigh
@Composable fun chromePaper(): Color = LocalChrome.current.paper
@Composable fun chromeMuted(): Color = LocalChrome.current.muted

@Composable
fun BenchTheme(night: Boolean = false, content: @Composable () -> Unit) {
    val chrome = if (night) nightChrome else dayChrome
    CompositionLocalProvider(LocalChrome provides chrome) {
        MaterialTheme(colorScheme = if (night) nightScheme else dayScheme, content = content)
    }
}

data class StatusStyle(
    val color: Color,
    val onColor: Color,
    val icon: ImageVector,
    val wording: String,
)

fun statusStyle(name: String): StatusStyle = when (name) {
    "FLAME" -> StatusStyle(FlameRed, Color.White, BenchIcons.Flame, "Open flame")
    "ALARM" -> StatusStyle(AlarmRed, Ink, Icons.Filled.Warning, "Hot cell alarm")
    "FAULT" -> StatusStyle(FaultOrange, Ink, BenchIcons.CameraOff, "Camera fault")
    "WARNING" -> StatusStyle(WarnAmber, Ink, Icons.Outlined.Info, "Warning")
    "OK" -> StatusStyle(OkGreen, Ink, Icons.Filled.CheckCircle, "All clear")
    "SEARCHING" -> StatusStyle(SolderBlue, Ink, Icons.Filled.Search, "Looking for the camera")
    else -> StatusStyle(Muted, Paper, BenchIcons.Pause, "Watch off")
}

fun statusColor(name: String): Color = statusStyle(name).color

val StatusType = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 42.sp,
    letterSpacing = 1.sp,
)
