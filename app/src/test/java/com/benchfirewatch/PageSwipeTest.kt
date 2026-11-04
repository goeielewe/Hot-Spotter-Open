package com.benchfirewatch

import com.benchfirewatch.ui.AppPages
import com.benchfirewatch.ui.PageSwipe
import com.benchfirewatch.ui.PagerTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSwipeTest {
    @Test
    fun pagesStayInWatchLogSettingsHelpOrder() {
        assertEquals(4, AppPages.COUNT)
        assertEquals(listOf("Watch", "Log", "Settings", "Help"), AppPages.titles)
        assertEquals(0, AppPages.WATCH)
        assertEquals(1, AppPages.LOG)
        assertEquals(2, AppPages.SETTINGS)
        assertEquals(3, AppPages.HELP)
    }

    @Test
    fun tabFollowsTheTargetWhileThePagerIsMoving() {
        assertEquals(1, PageSwipe.selectedTab(currentPage = 1, targetPage = 1, scrollInProgress = false))
        assertEquals(3, PageSwipe.selectedTab(currentPage = 0, targetPage = 3, scrollInProgress = true))
        assertEquals(0, PageSwipe.selectedTab(currentPage = -4, targetPage = 9, scrollInProgress = false))
        assertEquals(3, PageSwipe.selectedTab(currentPage = 1, targetPage = 8, scrollInProgress = true))
    }

    @Test
    fun backReturnsToWatchBeforeLeaving() {
        assertNull(PageSwipe.backPage(AppPages.WATCH))
        assertEquals(AppPages.WATCH, PageSwipe.backPage(AppPages.LOG))
        assertEquals(AppPages.WATCH, PageSwipe.backPage(AppPages.SETTINGS))
        assertEquals(AppPages.WATCH, PageSwipe.backPage(AppPages.HELP))
        assertNull(PageSwipe.backPage(-1))
    }

    @Test
    fun systemGestureEdgesAreNotPageDrags() {
        val width = 1080f
        val inset = 48f
        assertTrue(PageSwipe.inSystemGestureZone(0f, width, inset, inset))
        assertTrue(PageSwipe.inSystemGestureZone(47f, width, inset, inset))
        assertFalse(PageSwipe.inSystemGestureZone(48f, width, inset, inset))
        assertFalse(PageSwipe.inSystemGestureZone(1032f, width, inset, inset))
        assertTrue(PageSwipe.inSystemGestureZone(1033f, width, inset, inset))
        assertTrue(PageSwipe.inSystemGestureZone(1079f, width, inset, inset))
        assertFalse(PageSwipe.inSystemGestureZone(0f, width, 0f, 0f))
        assertFalse(PageSwipe.inSystemGestureZone(0f, 0f, inset, inset))
        assertFalse(PageSwipe.inSystemGestureZone(0f, width, -10f, -10f))
    }

    @Test
    fun onlyAClearlyHorizontalDragClaimsTheAxis() {
        assertFalse(PageSwipe.claimsHorizontal(8f, 0f, slop = 8f))
        assertTrue(PageSwipe.claimsHorizontal(9f, 0f, slop = 8f))
        assertFalse(PageSwipe.claimsHorizontal(20f, 20f, slop = 8f))
        assertFalse(PageSwipe.claimsHorizontal(4f, 30f, slop = 8f))
        assertTrue(PageSwipe.claimsHorizontal(-30f, 4f, slop = 8f))
        assertFalse(PageSwipe.claimsHorizontal(40f, 0f, slop = -1f))
    }

    @Test
    fun innerControlsDialogsEdgesAndFullScreenDoNotPage() {
        assertTrue(
            PageSwipe.allowsPaging(
                touch = PagerTouch.OPEN,
                inSystemGesture = false,
                fullScreen = false,
                modalVisible = false,
            ),
        )
        val blocked = listOf(
            PagerTouch.CAMERA,
            PagerTouch.ZONES,
            PagerTouch.SLIDER,
            PagerTouch.CHIP_ROW,
            PagerTouch.DIALOG,
        )
        blocked.forEach { touch ->
            assertFalse(
                PageSwipe.allowsPaging(
                    touch = touch,
                    inSystemGesture = false,
                    fullScreen = false,
                    modalVisible = false,
                ),
            )
        }
        assertFalse(
            PageSwipe.allowsPaging(
                touch = PagerTouch.OPEN,
                inSystemGesture = true,
                fullScreen = false,
                modalVisible = false,
            ),
        )
        assertFalse(
            PageSwipe.allowsPaging(
                touch = PagerTouch.OPEN,
                inSystemGesture = false,
                fullScreen = true,
                modalVisible = false,
            ),
        )
        assertFalse(
            PageSwipe.allowsPaging(
                touch = PagerTouch.OPEN,
                inSystemGesture = false,
                fullScreen = false,
                modalVisible = true,
            ),
        )
    }
}
