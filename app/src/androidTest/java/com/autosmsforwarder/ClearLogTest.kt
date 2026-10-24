package com.autosmsforwarder

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.settings.SendDelay
import com.autosmsforwarder.ui.SettingsViewModel
import com.autosmsforwarder.worker.ForwardWorkRequestFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClearLogTest {
    @Test fun clearLogCancelsQueuedForwardAndDeletesStoredBody() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val database = ForwardDatabase.get(application)
        val repository = ForwardRepository(database)
        val key = repository.recordIfNew(
            IncomingMessage("+919876543210", "pending clear check", System.currentTimeMillis()),
            "+15551234567"
        )!!.dedupeKey
        val workName = "fwd-$key"
        val workManager = WorkManager.getInstance(application)
        workManager.enqueueUniqueWork(
            workName, ExistingWorkPolicy.KEEP,
            ForwardWorkRequestFactory.create(key, SendDelay.THIRTY_SECONDS)
        ).result.get()

        SettingsViewModel(application).clearLog()

        withTimeout(10_000) {
            while (database.logDao().count() != 0 || database.pendingDao().count() != 0 ||
                workManager.getWorkInfosForUniqueWork(workName).get().single().state != WorkInfo.State.CANCELLED
            ) delay(100)
        }
        assertEquals(0, database.logDao().count())
        assertEquals(0, database.pendingDao().count())
        assertEquals(WorkInfo.State.CANCELLED, workManager.getWorkInfosForUniqueWork(workName).get().single().state)
    }
}
