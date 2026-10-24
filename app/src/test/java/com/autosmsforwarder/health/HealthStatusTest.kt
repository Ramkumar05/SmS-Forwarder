package com.autosmsforwarder.health

import com.autosmsforwarder.permissions.PermissionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthStatusTest {
    @Test fun optimizedPhoneShowsPromptAndStatus() {
        val status = HealthStatus(PermissionSummary(emptyList()), batteryExempt = false)
        assertEquals("Permissions ready · Battery optimized", status.statusLine)
        assertTrue(status.batteryPromptNeeded)
        assertFalse(status.permissionsNeeded)
    }

    @Test fun exemptPhoneHidesPromptAndShowsStatus() {
        val status = HealthStatus(PermissionSummary(listOf("Send SMS")), batteryExempt = true)
        assertEquals("Permissions missing or restricted: Send SMS · Battery exempt", status.statusLine)
        assertFalse(status.batteryPromptNeeded)
        assertTrue(status.permissionsNeeded)
    }
}
