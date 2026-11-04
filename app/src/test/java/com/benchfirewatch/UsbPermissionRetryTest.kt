package com.benchfirewatch

import com.benchfirewatch.source.UsbPermissionRetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbPermissionRetryTest {
    @Test
    fun aDenialWaitsUnlessTheCameraIsUnplugged() {
        val now = 10_000L
        val until = UsbPermissionRetry.quietUntil(now)
        assertEquals(now + UsbPermissionRetry.QUIET_MS, until)
        assertTrue(UsbPermissionRetry.shouldWait(now + 1_000L, until, devicePresent = true))
        assertFalse(UsbPermissionRetry.shouldWait(now + 1_000L, until, devicePresent = false))
        assertFalse(UsbPermissionRetry.shouldWait(until, until, devicePresent = true))
        assertTrue(UsbPermissionRetry.DENIED_DETAIL.contains("Always"))
    }
}
