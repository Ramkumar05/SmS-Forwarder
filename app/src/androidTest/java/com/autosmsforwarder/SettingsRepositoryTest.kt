package com.autosmsforwarder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.settings.RouteConfiguration
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
            assertEquals(false, first.settings.first().webEnabled)
            assertEquals(MatchMode.ALL, first.settings.first().forwardMatchMode)
            first.saveRoutes(RouteConfiguration(
                forwardingEnabled = false,
                targetNumber = "9876543210",
                sendDelay = SendDelay.THIRTY_SECONDS,
                forwardMatchMode = MatchMode.KEYWORDS,
                forwardKeywords = "bank, otp",
                webEnabled = true,
                webMatchMode = MatchMode.KEYWORDS,
                webKeywords = "payment received, receipt",
                archiveWriteToken = "  test-write-token  ",
                appSourcePhoneNumber = " +919876543210 ",
                appSourceName = " Home phone தமிழ் "
            ))
            first.markOnboardingCompleted()
            firstScope.cancel()
            firstScope.coroutineContext[Job]?.join()

            val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val second = SettingsRepository(PreferenceDataStoreFactory.create(scope = secondScope) { file })
                assertEquals("+919876543210", second.settings.first().targetNumber)
                assertEquals(SendDelay.THIRTY_SECONDS, second.settings.first().sendDelay)
                assertEquals(false, second.settings.first().forwardingEnabled)
                assertEquals(MatchMode.KEYWORDS, second.settings.first().forwardMatchMode)
                assertEquals("bank, otp", second.settings.first().forwardKeywords)
                assertEquals(true, second.settings.first().webEnabled)
                assertEquals(MatchMode.KEYWORDS, second.settings.first().webMatchMode)
                assertEquals("payment received, receipt", second.settings.first().webKeywords)
                assertEquals("test-write-token", second.settings.first().archiveWriteToken)
                assertEquals(true, second.settings.first().onboardingCompleted)
                assertEquals("+919876543210", second.settings.first().appSourcePhoneNumber)
                assertEquals("Home phone தமிழ்", second.settings.first().appSourceName)
                second.saveRoutes(second.settings.first().routeConfiguration().copy(appSourcePhoneNumber = "", appSourceName = ""))
                assertEquals("", second.settings.first().appSourcePhoneNumber)
                assertEquals("", second.settings.first().appSourceName)
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { second.saveRoutes(second.settings.first().routeConfiguration().copy(
                        forwardingEnabled = true,
                        targetNumber = "123"
                    )) }
                }
                assertEquals("+919876543210", second.settings.first().targetNumber)
                assertEquals(true, second.settings.first().webEnabled)
            } finally {
                secondScope.cancel()
            }
        } finally {
            firstScope.cancel()
            file.delete()
        }
    }
}
