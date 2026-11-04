package com.benchfirewatch

import com.benchfirewatch.ui.acceptedNumber
import com.benchfirewatch.ui.acceptedWholeNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldInputTest {
    @Test
    fun outOfRangeAndBlankAreNotSaved() {
        assertEquals(60f, acceptedNumber("60", 30f, 140f))
        assertEquals(30f, acceptedNumber("30", 30f, 140f))
        assertEquals(140f, acceptedNumber("140", 30f, 140f))
        assertNull(acceptedNumber("29.9", 30f, 140f))
        assertNull(acceptedNumber("141", 30f, 140f))
        assertNull(acceptedNumber("", 30f, 140f))
        assertNull(acceptedNumber("hot", 30f, 140f))
        assertNull(acceptedNumber("NaN", 30f, 140f))
        assertEquals(-0.03f, acceptedNumber("-0.03", -2f, -0.005f)!!, 0.0001f)
    }

    @Test
    fun wholeNumbersRejectAFraction() {
        assertEquals(4, acceptedWholeNumber("4", 1, 5))
        assertEquals(1, acceptedWholeNumber("1", 1, 5))
        assertNull(acceptedWholeNumber("1.5", 1, 5))
        assertNull(acceptedWholeNumber("0", 1, 5))
        assertNull(acceptedWholeNumber("6", 1, 5))
    }
}
