package com.benchfirewatch

import com.benchfirewatch.source.SensorGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorGridTest {
    @Test
    fun firstFrameLocksTheMeasurementGrid() {
        val grid = SensorGrid()
        assertTrue(grid.accept(160, 120))
        assertEquals(160, grid.width)
        assertEquals(120, grid.height)
        assertTrue(grid.accept(160, 120))
    }

    @Test
    fun aLargerFrameIsNotTemperatures() {
        val grid = SensorGrid()
        assertTrue(grid.accept(160, 120))
        assertFalse(grid.accept(320, 240))
        assertFalse(grid.accept(640, 480))
        assertTrue(grid.accept(160, 120))
        assertEquals(160, grid.width)
    }

    @Test
    fun clearLetsTheNextCameraLockItsOwnSize() {
        val grid = SensorGrid()
        assertTrue(grid.accept(160, 120))
        grid.clear()
        assertTrue(grid.accept(80, 60))
        assertEquals(80, grid.width)
        assertFalse(grid.accept(160, 120))
    }

    @Test
    fun emptyFramesAreRefused() {
        val grid = SensorGrid()
        assertFalse(grid.accept(0, 120))
        assertFalse(grid.accept(160, 0))
        assertEquals(0, grid.width)
    }
}
