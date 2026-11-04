package com.benchfirewatch

import com.benchfirewatch.release.ReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseNotesTest {
    @Test
    fun labelIsTheAboutLineWithoutAVersion() {
        assertEquals("Hot Spotter", ReleaseNotes.label())
        assertFalse(ReleaseNotes.label().any { it.isDigit() })
        assertFalse(ReleaseNotes.label().contains("build"))
        assertEquals(16, ReleaseNotes.forCode(16).versionCode)
        assertEquals(ReleaseNotes.notes.first(), ReleaseNotes.forCode(1))
        ReleaseNotes.forCode(16).lines.forEach { line ->
            assertFalse(line.any { it.isDigit() && line.contains("Hot Spotter ") })
            assertFalse(line.contains("(build"))
        }
    }

    @Test
    fun notesShowOnceAfterAnUpdate() {
        assertTrue(ReleaseNotes.shouldShow(seenVersionCode = 1, currentVersionCode = 2, hadPriorData = true))
        assertTrue(ReleaseNotes.shouldShow(seenVersionCode = 0, currentVersionCode = 2, hadPriorData = true))
        assertFalse(ReleaseNotes.shouldShow(seenVersionCode = 0, currentVersionCode = 2, hadPriorData = false))
        assertFalse(ReleaseNotes.shouldShow(seenVersionCode = 2, currentVersionCode = 2, hadPriorData = true))
        assertTrue(ReleaseNotes.shouldShow(seenVersionCode = 2, currentVersionCode = 3, hadPriorData = false))
    }

    @Test
    fun changelogCarriesTheCurrentNotes() {
        val changelog = File("../CHANGELOG.md").takeIf { it.isFile } ?: File("CHANGELOG.md")
        val text = changelog.readText()
        ReleaseNotes.notes.forEach { note ->
            note.lines.forEach { line ->
                assertTrue("missing: $line", text.contains(line))
                assertFalse("still names a build: $line", line.contains("(build"))
            }
        }
    }
}
