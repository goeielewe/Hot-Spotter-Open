package com.benchfirewatch

import com.benchfirewatch.eula.Eula
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EulaTest {
    @Test
    fun freshInstallAndAChangedLicenseMustBeAccepted() {
        assertTrue(Eula.needsAcceptance(acceptedVersion = 0))
        assertFalse(Eula.needsAcceptance(acceptedVersion = Eula.VERSION))
        assertTrue(Eula.needsAcceptance(acceptedVersion = Eula.VERSION, currentVersion = Eula.VERSION + 1))
        assertTrue(Eula.needsAcceptance(acceptedVersion = 2, currentVersion = 1))
    }

    @Test
    fun licenseTextCoversTheRequiredTerms() {
        val file = File("src/main/assets/eula.txt").takeIf { it.isFile } ?: File("app/src/main/assets/eula.txt")
        val text = file.readText()
        assertTrue(text.contains("EULA version ${Eula.VERSION}"))
        assertTrue(text.contains("Licensor: goeielewe"))
        assertTrue(text.contains("only for legal purposes"))
        assertTrue(text.contains("hold those camera makers harmless"))
        assertTrue(text.contains("does not include a vendor camera SDK"))
        assertTrue(text.contains("may terminate this license"))
        assertTrue(text.contains("not a fire alarm"))
        assertTrue(text.contains("not a smoke detector"))
        assertTrue(text.contains("not a safety device"))
        assertTrue(text.contains("not a medical device"))
        assertTrue(text.contains("sole means of fire protection or thermal-runaway protection"))
        assertTrue(text.contains("provided as is"))
        assertTrue(text.contains("no warranty of any kind"))
        assertTrue(text.contains("do not endorse it"))
        assertFalse(text.contains("@"))
        assertFalse(text.contains("Tele" + "dyne"))
        assertFalse(text.contains("FL" + "IR"))
    }
}
