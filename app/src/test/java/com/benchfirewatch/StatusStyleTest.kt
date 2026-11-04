package com.benchfirewatch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.Color
import com.benchfirewatch.ui.BenchIcons
import com.benchfirewatch.ui.statusStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StatusStyleTest {
    @Test
    fun eachAlarmStateHasItsOwnIconAndColor() {
        val flame = statusStyle("FLAME")
        val alarm = statusStyle("ALARM")
        val fault = statusStyle("FAULT")
        val warning = statusStyle("WARNING")
        val ok = statusStyle("OK")
        assertEquals(Color(0xFFB00020), flame.color)
        assertEquals(Color.White, flame.onColor)
        assertEquals(BenchIcons.Flame, flame.icon)
        assertEquals("Open flame", flame.wording)
        assertEquals(Icons.Filled.Warning, alarm.icon)
        assertEquals("Hot cell alarm", alarm.wording)
        assertEquals(BenchIcons.CameraOff, fault.icon)
        assertEquals("Camera fault", fault.wording)
        assertEquals(Icons.Outlined.Info, warning.icon)
        assertEquals(Icons.Filled.CheckCircle, ok.icon)
        assertNotEquals(flame.color, alarm.color)
        assertNotEquals(flame.color, fault.color)
        assertNotEquals(alarm.color, fault.color)
    }
}
