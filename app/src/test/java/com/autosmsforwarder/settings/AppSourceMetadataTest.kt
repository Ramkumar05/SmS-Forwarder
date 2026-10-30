package com.autosmsforwarder.settings

import org.junit.Assert.*
import org.junit.Test

class AppSourceMetadataTest {
    @Test fun bothFieldsAreIndependentlyOptional() {
        val config = RouteConfiguration(false, "", SendDelay.INSTANT, MatchMode.ALL, "",
            true, MatchMode.ALL, "", "fixture-token")
        listOf(config, config.copy(appSourceName = "Home"), config.copy(appSourcePhoneNumber = "+919876543210"))
            .forEach { assertTrue(RouteSettingsValidator.validate(it).isValid) }
    }

    @Test fun phoneValidationAcceptsOnlyDocumentedBoundaries() {
        listOf("", " ", "1234567890", "+123456789012345", " +919876543210 ").forEach {
            assertNull(AppSourceMetadata.phoneError(it))
        }
        listOf("123", "abc", "+1234567890123456", "91987 6543210").forEach {
            assertNotNull(AppSourceMetadata.phoneError(it))
        }
    }

    @Test fun nameSupportsUnicodeAndRejectsOversizedOrControlText() {
        listOf("", " Home phone தமிழ் ", "x".repeat(100)).forEach { assertNull(AppSourceMetadata.nameError(it)) }
        listOf("x".repeat(101), "Home\nPhone", "Home\u007fPhone").forEach { assertNotNull(AppSourceMetadata.nameError(it)) }
    }

    @Test fun sourceValidationDoesNotChangeForwardDestinationCanonicalization() {
        assertEquals("+919876543210", TargetNumberValidator.canonical("9876543210"))
        assertNull(AppSourceMetadata.phoneError("9876543210"))
    }

    @Test fun disablingWebNeverRequiresFixingAnUnusedSourceDraft() {
        val disabled = RouteConfiguration(false, "", SendDelay.INSTANT, MatchMode.ALL, "", false, MatchMode.ALL, "", "",
            "incomplete number", "x".repeat(101))
        assertTrue(RouteSettingsValidator.validate(disabled).isValid)
        val enabled = RouteSettingsValidator.validate(disabled.copy(webEnabled = true, archiveWriteToken = "fixture"))
        assertFalse(enabled.isValid)
        assertNotNull(enabled.appSourcePhoneNumber)
        assertNotNull(enabled.appSourceName)
    }
}
