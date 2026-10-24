package com.autosmsforwarder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autosmsforwarder.settings.SendDelay
import com.autosmsforwarder.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @Test fun settingsSurviveRepositoryAndDataStoreRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "settings-${System.nanoTime()}.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = SettingsRepository(PreferenceDataStoreFactory.create(scope = firstScope) { file })
            assertEquals(false, first.settings.first().onboardingCompleted)
            first.saveTargetAndDelay("9876543210", SendDelay.THIRTY_SECONDS)
            first.setForwardingEnabled(false)
            first.markOnboardingCompleted()
            firstScope.cancel()
            firstScope.coroutineContext[Job]?.join()

            val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val second = SettingsRepository(PreferenceDataStoreFactory.create(scope = secondScope) { file })
                assertEquals("+919876543210", second.settings.first().targetNumber)
                assertEquals(SendDelay.THIRTY_SECONDS, second.settings.first().sendDelay)
                assertEquals(false, second.settings.first().forwardingEnabled)
                assertEquals(true, second.settings.first().onboardingCompleted)
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { second.saveTargetAndDelay("123", SendDelay.INSTANT) }
                }
                assertEquals("+919876543210", second.settings.first().targetNumber)
            } finally {
                secondScope.cancel()
            }
        } finally {
            firstScope.cancel()
            file.delete()
        }
    }
}
