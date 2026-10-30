package com.autosmsforwarder.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordMatcherTest {
    @Test fun commaInputTrimsEntriesAndIgnoresBlanks() {
        assertEquals(
            listOf("payment received", "OTP", "हिंदी"),
            KeywordMatcher.parse(" , payment received, , OTP ,, हिंदी , ")
        )
        assertEquals(emptyList<String>(), KeywordMatcher.parse(" , , "))
    }

    @Test fun anyKeywordMatchesBodyAsCaseInsensitiveSubstring() {
        assertTrue(KeywordMatcher.matchesBody("Your PAYMENT Received today", "otp, payment received"))
        assertFalse(KeywordMatcher.matchesBody("Your payment is pending", "otp, payment received"))
    }

    @Test fun emptyBodyAndEmptyKeywordsNeverMatch() {
        assertFalse(KeywordMatcher.matchesBody("", "otp"))
        assertFalse(KeywordMatcher.matchesBody("an OTP arrived", " , , "))
    }

    @Test fun nonLatinKeywordMatchesBody() {
        assertTrue(KeywordMatcher.matchesBody("कोड हिंदी में है", "हिंदी"))
        assertTrue(KeywordMatcher.matchesBody("ПРИВЕТ мир", "привет"))
    }
}
