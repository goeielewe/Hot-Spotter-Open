package com.benchfirewatch

import com.benchfirewatch.ui.Tutorial
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTest {
    @Test
    fun tourCoversTheFirstWatchAndCanBeSkippedByTheFlag() {
        val text = Tutorial.steps.joinToString("\n") { it.title + "\n" + it.body }
        val needles = listOf(
            "USB-C",
            "P2 Pro",
            "Always",
            "Demo",
            "Watch Profile",
            "start the watch",
            "Acknowledge",
            "ntfy.sh",
            "Shelly",
            "Send Test Report",
        )
        needles.forEach { needle ->
            assertTrue("missing $needle", text.contains(needle))
        }
        assertFalse(text.contains("(build"))
        assertFalse(text.contains("1.6"))
        assertTrue(Tutorial.shouldOffer(tutorialSeen = false))
        assertFalse(Tutorial.shouldOffer(tutorialSeen = true))
        assertTrue(Tutorial.steps.size >= 8)
    }
}
