package com.autosmsforwarder

import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autosmsforwarder.health.HealthChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthCheckerTest {
    @Test fun batteryStatusMatchesSystemExemption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exempt = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
        val health = HealthChecker(context).check()
        assertEquals(exempt, health.batteryExempt)
        assertEquals(!exempt, health.batteryPromptNeeded)
        assertTrue(health.statusLine.contains(if (exempt) "Battery exempt" else "Battery optimized"))
    }
}
