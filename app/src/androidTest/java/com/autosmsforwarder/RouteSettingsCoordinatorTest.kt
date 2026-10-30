package com.autosmsforwarder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.domain.ForwardStatus
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.settings.RouteConfiguration
import com.autosmsforwarder.settings.RouteSettingsCoordinator
import com.autosmsforwarder.settings.SendDelay
import com.autosmsforwarder.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RouteSettingsCoordinatorTest {
    @Test fun savingForwardingOffCancelsOnlyUnsentForwardWork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, ForwardDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "route-settings-${System.nanoTime()}.preferences_pb")
        try {
            val forwardRepository = ForwardRepository(database)
            val settingsRepository = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            val base = IncomingMessage("sender", "private body", 1_800_000_000_000L)
            val pending = forwardRepository.recordIfNew(base, "+15551234567")!!.dedupeKey
            val sent = forwardRepository.recordIfNew(base.copy(receivedAtMillis = base.receivedAtMillis + 1), "+15551234567")!!.dedupeKey
            forwardRepository.beginOrResume(sent)
            forwardRepository.complete(sent, ForwardStatus.SENT)
            val cancelledWork = mutableListOf<String>()
            val coordinator = RouteSettingsCoordinator(
                settingsRepository, forwardRepository, WebDeliveryRepository(database),
                cancelForwardWork = { cancelledWork += it }, cancelWebWork = { }
            )

            coordinator.save(RouteConfiguration(
                forwardingEnabled = false,
                targetNumber = "+15551234567",
                sendDelay = SendDelay.FIFTEEN_SECONDS,
                forwardMatchMode = MatchMode.ALL,
                forwardKeywords = "",
                webEnabled = false,
                webMatchMode = MatchMode.ALL,
                webKeywords = "",
                archiveWriteToken = ""
            ))

            assertEquals(false, settingsRepository.settings.first().forwardingEnabled)
            assertEquals(listOf(pending), cancelledWork)
            assertEquals(ForwardStatus.CANCELLED, forwardRepository.findLog(pending)?.status)
            assertNull(database.pendingDao().find(pending))
            assertEquals(ForwardStatus.SENT, forwardRepository.findLog(sent)?.status)
        } finally {
            scope.cancel()
            database.close()
            file.delete()
        }
    }

    @Test fun savingWebOffDeletesTransientBodyAndCancelsWebWork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, ForwardDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "web-settings-${System.nanoTime()}.preferences_pb")
        try {
            val settingsRepository = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            val webRepository = WebDeliveryRepository(database)
            val key = webRepository.recordIfNew(IncomingMessage("sender", "private web body", 1_800_000_000_000L))!!
            val cancelledWebWork = mutableListOf<String>()
            val coordinator = RouteSettingsCoordinator(
                settingsRepository, ForwardRepository(database), webRepository,
                cancelForwardWork = { }, cancelWebWork = { cancelledWebWork += it }
            )

            coordinator.save(RouteConfiguration(
                forwardingEnabled = false,
                targetNumber = "",
                sendDelay = SendDelay.INSTANT,
                forwardMatchMode = MatchMode.ALL,
                forwardKeywords = "",
                webEnabled = false,
                webMatchMode = MatchMode.ALL,
                webKeywords = "",
                archiveWriteToken = "token"
            ))

            assertEquals(false, settingsRepository.settings.first().webEnabled)
            assertEquals(listOf(key), cancelledWebWork)
            assertNull(database.webDeliveryDao().find(key))
        } finally {
            scope.cancel()
            database.close()
            file.delete()
        }
    }
}
