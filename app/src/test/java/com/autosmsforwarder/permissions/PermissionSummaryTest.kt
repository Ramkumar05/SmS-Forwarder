package com.autosmsforwarder.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionSummaryTest {
    @Test fun grantedPermissionsNeedNoSettingsAction() {
        val summary = PermissionSummary.fromGrants(
            receiveSms = true, sendSms = true, notifications = true, notificationsRuntimeRequired = true
        )
        assertEquals("Permissions ready", summary.status)
        assertTrue(summary.allGranted)
    }

    @Test fun deniedOrRevokedPermissionsAreNamed() {
        val summary = PermissionSummary.fromGrants(
            receiveSms = false, sendSms = true, notifications = false, notificationsRuntimeRequired = true
        )
        assertEquals("Permissions missing or restricted: Receive SMS, Notifications", summary.status)
        assertFalse(summary.allGranted)
    }

    @Test fun notificationsDoNotNeedRuntimeGrantBeforeAndroidThirteen() {
        val summary = PermissionSummary.fromGrants(
            receiveSms = true, sendSms = true, notifications = false, notificationsRuntimeRequired = false
        )
        assertTrue(summary.allGranted)
    }
}
