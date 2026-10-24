package com.autosmsforwarder.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetNumberValidatorTest {
    @Test fun acceptsTenToFifteenDigitsWithOptionalLeadingPlus() {
        assertTrue(TargetNumberValidator.isValid("9876543210"))
        assertTrue(TargetNumberValidator.isValid("+919876543210"))
        assertTrue(TargetNumberValidator.isValid("123456789012345"))
    }

    @Test fun rejectsInvalidNumbers() {
        listOf("", "123456789", "1234567890123456", "++919876543210", "+91 9876543210", "98765-43210", "abc9876543210").forEach {
            assertFalse(TargetNumberValidator.isValid(it))
        }
    }

    @Test fun addsIndiaCodeToTenDigitNumber() {
        assertEquals("+919876543210", TargetNumberValidator.canonical("9876543210"))
        assertEquals("+919876543210", TargetNumberValidator.canonical("+919876543210"))
    }
}
