package com.autosmsforwarder.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.autosmsforwarder.permissions.PermissionSummary

data class HealthStatus(val permissions: PermissionSummary, val batteryExempt: Boolean) {
    val statusLine: String get() = "${permissions.status} · ${if (batteryExempt) "Battery exempt" else "Battery optimized"}"
    val permissionsNeeded: Boolean get() = !permissions.allGranted
    val batteryPromptNeeded: Boolean get() = !batteryExempt
}

class HealthChecker(private val context: Context) {
    fun check(): HealthStatus {
        val notificationsRuntimeRequired = Build.VERSION.SDK_INT >= 33
        val permissions = PermissionSummary.fromGrants(
            receiveSms = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED,
            sendSms = context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED,
            notifications = !notificationsRuntimeRequired ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            notificationsRuntimeRequired = notificationsRuntimeRequired
        )
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return HealthStatus(permissions, powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
}
