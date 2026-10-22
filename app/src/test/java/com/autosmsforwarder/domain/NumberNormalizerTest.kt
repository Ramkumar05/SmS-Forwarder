package com.autosmsforwarder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberNormalizerTest {
    @Test
    fun localIndianNumberGetsCountryCode() {
        assertEquals("+919876543210", NumberNormalizer.normalize("98765 43210"))
    }

    @Test
    fun internationalNumberKeepsItsCountryCodeAndDropsPunctuation() {
        assertEquals("+919876543210", NumberNormalizer.normalize("+91 (98765) 43210"))
    }

    @Test
    fun loopComparisonUsesLastTenDigits() {
        assertTrue(NumberNormalizer.sameForLoop("98765-43210", "+91 98765 43210"))
        assertFalse(NumberNormalizer.sameForLoop("98765-43211", "+91 98765 43210"))
        assertFalse(NumberNormalizer.sameForLoop("12345", "12345"))
    }
}
