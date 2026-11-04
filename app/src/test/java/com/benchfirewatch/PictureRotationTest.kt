package com.benchfirewatch

import com.benchfirewatch.render.ContentRect
import com.benchfirewatch.render.FrameGeometry
import com.benchfirewatch.render.PictureMapping
import com.benchfirewatch.render.PictureRotation
import com.benchfirewatch.render.PlacedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureRotationTest {
    @Test
    fun eachTapTurnsClockwiseAndReturnsToZero() {
        var rotation = PictureRotation.DEG_0
        val seen = mutableListOf<Int>()
        repeat(4) {
            rotation = rotation.next()
            seen += rotation.degrees
        }
        assertEquals(listOf(90, 180, 270, 0), seen)
        assertEquals(PictureRotation.DEG_0, PictureRotation.fromDegrees(0))
        assertEquals(PictureRotation.DEG_0, PictureRotation.fromDegrees(360))
        assertEquals(PictureRotation.DEG_90, PictureRotation.fromDegrees(450))
        assertEquals(PictureRotation.DEG_270, PictureRotation.fromDegrees(-90))
        assertEquals(PictureRotation.DEG_0, PictureRotation.fromDegrees(15))
    }

    @Test
    fun quarterTurnsSwapThePictureSides() {
        assertEquals(160 to 120, PictureRotation.DEG_0.displayedSize(160, 120))
        assertEquals(120 to 160, PictureRotation.DEG_90.displayedSize(160, 120))
        assertEquals(160 to 120, PictureRotation.DEG_180.displayedSize(160, 120))
        assertEquals(120 to 160, PictureRotation.DEG_270.displayedSize(160, 120))
    }

    @Test
    fun imagePointsLandOnTheClockwisePixel() {
        assertEquals(0.2f to 0.3f, PictureRotation.DEG_0.mapImage(0.2f, 0.3f))
        assertEquals(0.7f to 0.2f, PictureRotation.DEG_90.mapImage(0.2f, 0.3f))
        assertEquals(0.8f to 0.7f, PictureRotation.DEG_180.mapImage(0.2f, 0.3f))
        assertEquals(0.3f to 0.8f, PictureRotation.DEG_270.mapImage(0.2f, 0.3f))
        val samples = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f, 0.2f to 0.3f, 0.5f to 0.5f)
        for (rotation in PictureRotation.entries) {
            for ((x, y) in samples) {
                val (mx, my) = rotation.mapImage(x, y)
                val (ux, uy) = rotation.unmapImage(mx, my)
                assertEquals("$rotation $x", x, ux, 1e-5f)
                assertEquals("$rotation $y", y, uy, 1e-5f)
            }
        }
    }

    @Test
    fun cornersRoundTripThroughTheViewAtEveryAngle() {
        val content = ContentRect.FULL
        val thermal = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f, 0.25f to 0.8f)
        val views = listOf(400f to 800f, 800f to 400f)
        for (rotation in PictureRotation.entries) {
            val (imageW, imageH) = rotation.displayedSize(160, 120)
            for ((viewW, viewH) in views) {
                for (dest in listOf(
                    FrameGeometry.place(viewW, viewH, imageW, imageH),
                    FrameGeometry.cover(viewW, viewH, imageW, imageH),
                )) {
                    val placed = dest!!
                    assertAspect(placed, imageW, imageH)
                    for ((x, y) in thermal) {
                        val view = PictureMapping.thermalToView(x, y, placed, content, rotation)
                        val back = PictureMapping.viewToThermal(view.x, view.y, placed, content, rotation)
                        assertEquals("$rotation $viewW x,$y", x, back.first, 1e-4f)
                        assertEquals("$rotation $viewH y,$y", y, back.second, 1e-4f)
                    }
                }
            }
        }
    }

    @Test
    fun zoneStillCoversTheSameThermalAreaAfterEachTurn() {
        val left = 0.1f
        val top = 0.2f
        val right = 0.4f
        val bottom = 0.55f
        val inside = 0.25f to 0.35f
        val outside = 0.9f to 0.9f
        val content = ContentRect.FULL
        val dest = PlacedImage(10f, 20f, 300f, 400f)
        for (rotation in PictureRotation.entries) {
            val box = PictureMapping.thermalRectToView(left, top, right, bottom, dest, content, rotation)
            val hit = PictureMapping.thermalToView(inside.first, inside.second, dest, content, rotation)
            val miss = PictureMapping.thermalToView(outside.first, outside.second, dest, content, rotation)
            assertTrue("$rotation inside", box.contains(hit.x, hit.y))
            assertFalse("$rotation outside", box.contains(miss.x, miss.y))
            val back = PictureMapping.viewToThermal(hit.x, hit.y, dest, content, rotation)
            assertEquals(inside.first, back.first, 1e-4f)
            assertEquals(inside.second, back.second, 1e-4f)
            val drawnCorner = PictureMapping.viewToThermal(box.left, box.top, dest, content, rotation)
            assertTrue(
                "$rotation corner x ${drawnCorner.first}",
                near(drawnCorner.first, left) || near(drawnCorner.first, right),
            )
            assertTrue(
                "$rotation corner y ${drawnCorner.second}",
                near(drawnCorner.second, top) || near(drawnCorner.second, bottom),
            )
        }
    }

    @Test
    fun pictureInPictureTouchMapsBackThroughTheInset() {
        val content = ContentRect(0.25f, 0.25f, 0.75f, 0.75f)
        val dest = PlacedImage(0f, 0f, 100f, 100f)
        for (rotation in PictureRotation.entries) {
            val view = PictureMapping.thermalToView(0f, 0f, dest, content, rotation)
            val back = PictureMapping.viewToThermal(view.x, view.y, dest, content, rotation)
            assertEquals("$rotation x", 0f, back.first, 1e-4f)
            assertEquals("$rotation y", 0f, back.second, 1e-4f)
        }
        val turned = PictureMapping.thermalToView(0f, 0f, dest, content, PictureRotation.DEG_90)
        assertEquals(75f, turned.x, 0.01f)
        assertEquals(25f, turned.y, 0.01f)
    }

    @Test
    fun quarterTurnFitsPortraitAndLandscapeWithoutStretching() {
        for (rotation in listOf(PictureRotation.DEG_90, PictureRotation.DEG_270)) {
            val (imageW, imageH) = rotation.displayedSize(160, 120)
            val portrait = FrameGeometry.place(400f, 800f, imageW, imageH)!!
            val landscape = FrameGeometry.place(800f, 400f, imageW, imageH)!!
            val portraitFill = FrameGeometry.cover(400f, 800f, imageW, imageH)!!
            val landscapeFill = FrameGeometry.cover(800f, 400f, imageW, imageH)!!
            assertAspect(portrait, imageW, imageH)
            assertAspect(landscape, imageW, imageH)
            assertAspect(portraitFill, imageW, imageH)
            assertAspect(landscapeFill, imageW, imageH)
            assertEquals(533.33f, FrameGeometry.flushHeight(400f, imageW, imageH), 0.1f)
        }
    }

    private fun near(a: Float, b: Float) = kotlin.math.abs(a - b) < 1e-3f

    private fun assertAspect(placed: PlacedImage, imageW: Int, imageH: Int) {
        val expected = imageW.toFloat() / imageH
        assertEquals(expected, placed.width / placed.height, 0.01f)
    }
}
