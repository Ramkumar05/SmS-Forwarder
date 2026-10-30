package com.autosmsforwarder.domain

import org.junit.Assert.*
import org.junit.Test

class WebIdempotencyKeyTest {
    private val localKey = IdempotencyKey.create("BANK", 1234, "message")

    @Test fun blankMetadataPreservesLegacyIdentity() {
        assertEquals(localKey, WebIdempotencyKey.create(localKey, "", ""))
        assertEquals(localKey, WebIdempotencyKey.create(localKey, "  ", " "))
    }

    @Test fun sourcePhoneAndNameBothDistinguishUploads() {
        val original = WebIdempotencyKey.create(localKey, "+919876543210", "Home")
        assertNotEquals(original, WebIdempotencyKey.create(localKey, "+919876543211", "Home"))
        assertNotEquals(original, WebIdempotencyKey.create(localKey, "+919876543210", "Work"))
        assertEquals(original, WebIdempotencyKey.create(localKey, " +919876543210 ", " Home "))
        assertTrue(original.matches(Regex("[a-f0-9]{64}")))
    }

    @Test fun eitherOptionalFieldCanIdentifyAnUpload() {
        assertNotEquals(localKey, WebIdempotencyKey.create(localKey, "", "Home"))
        assertNotEquals(localKey, WebIdempotencyKey.create(localKey, "+919876543210", ""))
    }

    @Test fun lengthPrefixPreventsDelimiterAmbiguity() {
        assertNotEquals(WebIdempotencyKey.create(localKey, "a|1:b", "c"),
            WebIdempotencyKey.create(localKey, "a", "b|1:c"))
    }
}
