package com.benchfirewatch

import com.benchfirewatch.model.PaletteChoice
import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.model.ViewMode
import com.benchfirewatch.model.pickPaletteName
import com.benchfirewatch.render.ContentRect
import com.benchfirewatch.render.FrameGeometry
import com.benchfirewatch.render.HotSpotFinder
import com.benchfirewatch.render.PreviewFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTest {
    @Test
    fun flushWidthMatchesThePictureAspect() {
        val height = FrameGeometry.flushHeight(400f, 160, 120)
        assertEquals(300f, height, 0.01f)
        val placed = FrameGeometry.place(400f, height, 160, 120)
        assertEquals(0f, placed!!.left, 0.01f)
        assertEquals(400f, placed.width, 0.01f)
        assertEquals(300f, placed.height, 0.01f)
    }

    @Test
    fun landscapeLetterboxKeepsZonesOnThePicture() {
        val placed = FrameGeometry.place(800f, 400f, 160, 120)
        assertEquals(133.33f, placed!!.left, 0.1f)
        assertEquals(0f, placed.top, 0.1f)
        assertEquals(533.33f, placed.width, 0.1f)
        assertEquals(400f, placed.height, 0.1f)
        val content = ContentRect.FULL
        val originX = placed.left + content.mapX(0f) * placed.width
        val originY = placed.top + content.mapY(0f) * placed.height
        assertEquals(placed.left, originX, 0.01f)
        assertEquals(placed.top, originY, 0.01f)
        val inset = ContentRect(0.25f, 0.25f, 0.75f, 0.75f)
        val centerX = placed.left + inset.mapX(0.5f) * placed.width
        val centerY = placed.top + inset.mapY(0.5f) * placed.height
        assertEquals(placed.left + placed.width / 2f, centerX, 0.01f)
        assertEquals(placed.top + placed.height / 2f, centerY, 0.01f)
    }

    @Test
    fun portraitBarsCenterThePicture() {
        val placed = FrameGeometry.place(400f, 800f, 160, 120)
        assertEquals(0f, placed!!.left, 0.01f)
        assertEquals(400f, placed.width, 0.01f)
        assertEquals(300f, placed.height, 0.01f)
        assertEquals(250f, placed.top, 0.01f)
    }

    @Test
    fun thermalPointMapsThroughTheInset() {
        val content = ContentRect(0.25f, 0.25f, 0.75f, 0.75f)
        assertEquals(0.25f, content.mapX(0f), 0.001f)
        assertEquals(0.5f, content.mapX(0.5f), 0.001f)
        assertEquals(0.75f, content.mapY(1f), 0.001f)
        assertEquals(0f, content.thermalX(0.25f), 0.001f)
        assertEquals(1f, content.thermalX(0.75f), 0.001f)
        assertEquals(0.5f, content.thermalY(0.5f), 0.001f)
    }

    @Test
    fun fullFrameModeDoesNotShiftTheThermalImage() {
        assertEquals(0.2f, ContentRect.FULL.mapX(0.2f), 0.001f)
        assertEquals(0.8f, ContentRect.FULL.thermalY(0.8f), 0.001f)
    }

    @Test
    fun hottestPixelIsTheCrosshair() {
        val point = HotSpotFinder.find(floatArrayOf(10f, 20f, 40f, 15f), 2, 2)
        assertEquals(0.25f, point!!.x, 0.001f)
        assertEquals(0.75f, point.y, 0.001f)
        assertEquals(40f, point.celsius, 0.001f)
    }

    @Test
    fun demoMsxKeepsTheThermalSizeAndDrawsAnEdge() {
        val temps = floatArrayOf(
            20f, 20f, 20f, 20f,
            20f, 80f, 80f, 20f,
            20f, 80f, 80f, 20f,
            20f, 20f, 20f, 20f,
        )
        val frame = ThermalFrame(0, 4, 4, temps, "demo")
        val thermal = PreviewFrames.forDemo(frame, ViewMode.THERMAL, PaletteChoice.IRON)
        val msx = PreviewFrames.forDemo(frame, ViewMode.MSX, PaletteChoice.IRON)
        assertEquals(4, msx.width)
        assertEquals(4, msx.height)
        assertEquals(0f, msx.contentLeft, 0.001f)
        assertEquals(1f, msx.contentRight, 0.001f)
        assertTrue(msx.argb.indices.any { msx.argb[it] != thermal.argb[it] })
    }

    @Test
    fun demoPictureInPictureInsetsTheThermalImage() {
        val frame = ThermalFrame(0, 4, 4, FloatArray(16) { 25f }, "demo")
        val pip = PreviewFrames.forDemo(frame, ViewMode.PIP, PaletteChoice.WHITE_HOT)
        assertEquals(8, pip.width)
        assertEquals(8, pip.height)
        assertEquals(0.25f, pip.contentLeft, 0.001f)
        assertEquals(0.75f, pip.contentRight, 0.001f)
        val thermal = PreviewFrames.forDemo(frame, ViewMode.THERMAL, PaletteChoice.WHITE_HOT)
        assertNotEquals(thermal.argb[0], pip.argb[0])
    }

    @Test
    fun coverFillsPortraitAndLandscapeWithoutBars() {
        val portrait = FrameGeometry.cover(400f, 800f, 160, 120)
        assertEquals(0f, portrait!!.top, 0.01f)
        assertEquals(800f, portrait.height, 0.01f)
        assertTrue(portrait.width > 400f)
        assertTrue(portrait.left < 0f)
        assertEquals(200f, portrait.left + portrait.width / 2f, 0.1f)
        val landscape = FrameGeometry.cover(800f, 400f, 160, 120)
        assertEquals(0f, landscape!!.left, 0.01f)
        assertEquals(800f, landscape.width, 0.01f)
        assertTrue(landscape.height > 400f)
        assertTrue(landscape.top < 0f)
        assertEquals(200f, landscape.top + landscape.height / 2f, 0.1f)
    }

    @Test
    fun standInPalettesAreReadableAndDifferent() {
        val temps = FloatArray(16) { index -> 30f + index }
        val frame = ThermalFrame(0, 4, 4, temps, "usb")
        val iron = PreviewFrames.standIn(frame, PaletteChoice.IRON)
        val rainbow = PreviewFrames.standIn(frame, PaletteChoice.RAINBOW)
        val white = PreviewFrames.standIn(frame, PaletteChoice.WHITE_HOT)
        assertNotEquals(iron.argb.toList(), rainbow.argb.toList())
        assertNotEquals(iron.argb.toList(), white.argb.toList())
        assertNotEquals(rainbow.argb.toList(), white.argb.toList())
        assertNotEquals(iron.argb.first(), iron.argb.last())
        assertNotEquals(rainbow.argb.first(), rainbow.argb.last())
        assertNotEquals(white.argb.first(), white.argb.last())
    }

    @Test
    fun paletteNamesPreferIronThenRainbowThenGray() {
        val names = listOf("Arctic", "Iron", "Rainbow", "Gray")
        assertEquals("Iron", pickPaletteName(names, PaletteChoice.IRON.matchKeys))
        assertEquals("Rainbow", pickPaletteName(names, PaletteChoice.RAINBOW.matchKeys))
        assertEquals("Gray", pickPaletteName(names, PaletteChoice.WHITE_HOT.matchKeys))
        assertEquals("White Hot", pickPaletteName(listOf("Black", "White Hot"), PaletteChoice.WHITE_HOT.matchKeys))
        assertEquals(ViewMode.MSX, ViewMode.fromId(null))
        assertEquals(ViewMode.PIP, ViewMode.fromId("pip"))
        assertEquals(PaletteChoice.IRON, PaletteChoice.fromId("nope"))
    }
}
