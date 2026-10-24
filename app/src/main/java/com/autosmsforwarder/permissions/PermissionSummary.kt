package com.autosmsforwarder.permissions

data class PermissionSummary(val missing: List<String>) {
    val allGranted: Boolean get() = missing.isEmpty()
    val status: String get() = if (allGranted) {
        "Permissions ready"
    } else {
        "Permissions missing or restricted: ${missing.joinToString()}"
    }

    companion object {
        fun fromGrants(
            receiveSms: Boolean,
            sendSms: Boolean,
            notifications: Boolean,
            notificationsRuntimeRequired: Boolean
        ): PermissionSummary = PermissionSummary(buildList {
            if (!receiveSms) add("Receive SMS")
            if (!sendSms) add("Send SMS")
            if (notificationsRuntimeRequired && !notifications) add("Notifications")
        })
    }
}
