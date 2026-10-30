package com.autosmsforwarder.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSettingsValidatorTest {
    private val valid = RouteConfiguration(
        forwardingEnabled = true,
        targetNumber = "9876543210",
        sendDelay = SendDelay.INSTANT,
        forwardMatchMode = MatchMode.ALL,
        forwardKeywords = "",
        webEnabled = false,
        webMatchMode = MatchMode.ALL,
        webKeywords = "",
        archiveWriteToken = ""
    )

    @Test fun enabledRoutesRequireTheirOwnDetails() {
        val errors = RouteSettingsValidator.validate(valid.copy(
            targetNumber = "bad",
            forwardMatchMode = MatchMode.KEYWORDS,
            forwardKeywords = " , , ",
            webEnabled = true,
            webMatchMode = MatchMode.KEYWORDS,
            webKeywords = " , ",
            archiveWriteToken = " "
        ))
        assertFalse(errors.isValid)
        assertEquals("Enter at least one keyword", errors.forwardKeywords)
        assertEquals("Enter at least one keyword", errors.webKeywords)
        assertEquals("Enter the archive write token", errors.archiveWriteToken)
        assertTrue(errors.targetNumber != null)
    }

    @Test fun disabledRoutesKeepIncompleteConfiguration() {
        assertTrue(RouteSettingsValidator.validate(valid.copy(
            forwardingEnabled = false,
            targetNumber = "bad",
            forwardMatchMode = MatchMode.KEYWORDS,
            webMatchMode = MatchMode.KEYWORDS,
            webEnabled = false
        )).isValid)
    }

    @Test fun eitherCommaSeparatedListCanEnableItsRoute() {
        assertTrue(RouteSettingsValidator.validate(valid.copy(
            forwardMatchMode = MatchMode.KEYWORDS,
            forwardKeywords = " , payment received, ",
            webEnabled = true,
            webMatchMode = MatchMode.KEYWORDS,
            webKeywords = " otp, ",
            archiveWriteToken = "write-token"
        )).isValid)
    }
}
