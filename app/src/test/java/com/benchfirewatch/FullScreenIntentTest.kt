package com.benchfirewatch

import com.benchfirewatch.alert.AlertNotifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenIntentTest {
    @Test
    fun android14NeedsTheGrantAndOlderReleasesDoNot() {
        assertTrue(AlertNotifier.fullScreenIntentAllowed(33, granted = false))
        assertTrue(AlertNotifier.fullScreenIntentAllowed(34, granted = true))
        assertFalse(AlertNotifier.fullScreenIntentAllowed(34, granted = false))
        assertFalse(AlertNotifier.fullScreenIntentAllowed(36, granted = false))
    }
}
