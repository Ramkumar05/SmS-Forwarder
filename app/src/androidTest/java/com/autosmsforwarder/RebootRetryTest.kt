package com.autosmsforwarder

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.domain.ForwardStatus
import com.autosmsforwarder.domain.IdempotencyKey
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.settings.SendDelay
import com.autosmsforwarder.settings.SettingsRepository
import com.autosmsforwarder.worker.ForwardWorkRequestFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run seedPendingRetry, reboot the emulator, then run verifySentAfterReboot. */
@RunWith(AndroidJUnit4::class)
class RebootRetryTest {
    private val message = IncomingMessage("+919876543210", "reboot retry check", 1_800_000_000_000L)
    private val key = IdempotencyKey.create(message.sender, message.receivedAtMillis, message.body)

    @Test fun seedPendingRetry() = runBlocking {
        if (!rebootTestEnabled()) return@runBlocking
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.SEND_SMS))
        SettingsRepository(context).saveTargetAndDelay("+15551234567", SendDelay.INSTANT)
        val repository = ForwardRepository(ForwardDatabase.get(context))
        val recorded = repository.recordIfNew(message, "+15551234567")
        assertNotNull(recorded)
        assertTrue(recorded!!.shouldEnqueue)
        assertNotNull(repository.beginOrResume(key))
        repository.resetForRetry(key)
        assertEquals(1, repository.findLog(key)?.attempts)
        assertEquals(ForwardStatus.PENDING, repository.findLog(key)?.status)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "fwd-$key", ExistingWorkPolicy.KEEP,
            ForwardWorkRequestFactory.create(key, SendDelay.THIRTY_SECONDS)
        ).result.get()
    }

    @Test fun verifySentAfterReboot() = runBlocking {
        if (!rebootTestEnabled()) return@runBlocking
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = ForwardRepository(ForwardDatabase.get(context))
        repeat(90) {
            if (repository.findLog(key)?.status == ForwardStatus.SENT) {
                assertEquals(2, repository.findLog(key)?.attempts)
                assertNull(ForwardDatabase.get(context).pendingDao().find(key))
                return@runBlocking
            }
            delay(2_000)
        }
        assertEquals(ForwardStatus.SENT, repository.findLog(key)?.status)
    }

    private fun rebootTestEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("rebootTest") == "true"
}
