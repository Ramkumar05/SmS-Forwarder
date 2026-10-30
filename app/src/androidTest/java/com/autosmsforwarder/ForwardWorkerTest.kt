package com.autosmsforwarder

import androidx.room.Room
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.domain.ForwardStatus
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.sms.SmsSender
import com.autosmsforwarder.settings.SendDelay
import com.autosmsforwarder.settings.AppSettings
import com.autosmsforwarder.worker.ForwardWorkRequestFactory
import com.autosmsforwarder.worker.ForwardWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForwardWorkerTest {
    private lateinit var database: ForwardDatabase
    private lateinit var repository: ForwardRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ForwardDatabase::class.java
        ).build()
        repository = ForwardRepository(database)
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun receivedMessageIsFormattedAndSentOnceWithOnlyKeyInWorkInput() = runBlocking {
        val body = "Hello 😀 from a long message"
        val received = repository.recordIfNew(
            IncomingMessage("+919876543210", body, 1_700_000_000_000L),
            "+15551234567"
        )!!
        assertTrue(received.shouldEnqueue)
        assertNotNull(database.pendingDao().find(received.dedupeKey))

        val input = Data.Builder().putString(ForwardWorker.KEY_DEDUPE, received.dedupeKey).build()
        assertEquals(setOf(ForwardWorker.KEY_DEDUPE), input.keyValueMap.keys)
        assertFalse(input.toString().contains(body))
        val sender = RecordingSender()
        val worker = TestListenableWorkerBuilder<ForwardWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(input)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = ForwardWorker(
                    appContext, workerParameters, repository, sender, "+15551234567"
                )
            })
            .build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf("+15551234567" to "FWD from +919876543210: $body"), sender.sent)
        assertEquals(ForwardStatus.SENT, repository.findLog(received.dedupeKey)?.status)
        assertNull(database.pendingDao().find(received.dedupeKey))
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(1, sender.sent.size)
    }

    @Test fun scheduledWorkerUsesChosenDelayAndOnlyDedupeKey() {
        val instant = ForwardWorkRequestFactory.create("key", SendDelay.INSTANT)
        val fifteen = ForwardWorkRequestFactory.create("key", SendDelay.FIFTEEN_SECONDS)
        val thirty = ForwardWorkRequestFactory.create("key", SendDelay.THIRTY_SECONDS)

        assertTrue(instant.workSpec.expedited)
        assertFalse(fifteen.workSpec.expedited)
        assertFalse(thirty.workSpec.expedited)
        assertEquals(15_000L, fifteen.workSpec.initialDelay)
        assertEquals(30_000L, thirty.workSpec.initialDelay)
        assertEquals(setOf(ForwardWorker.KEY_DEDUPE), thirty.workSpec.input.keyValueMap.keys)
    }

    @Test fun disabledForwardingPreventsQueuedWorkerFromSending() = runBlocking {
        val key = repository.recordIfNew(
            IncomingMessage("sender", "private body", 1_800_000_000_000L), "+15551234567"
        )!!.dedupeKey
        val sender = RecordingSender()
        val worker = TestListenableWorkerBuilder<ForwardWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(Data.Builder().putString(ForwardWorker.KEY_DEDUPE, key).build())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    ForwardWorker(appContext, workerParameters, repository, sender, "+15551234567") {
                        AppSettings(forwardingEnabled = false)
                    }
            }).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertTrue(sender.sent.isEmpty())
        assertEquals(ForwardStatus.CANCELLED, repository.findLog(key)?.status)
        assertNull(database.pendingDao().find(key))
    }

    @Test fun cancelledInFlightFailureDoesNotRetry() = runBlocking {
        val key = repository.recordIfNew(
            IncomingMessage("sender", "private body", 1_800_000_000_001L), "+15551234567"
        )!!.dedupeKey
        val sender = object : SmsSender {
            override suspend fun send(targetNumber: String, text: String): Boolean {
                repository.cancelPending(key)
                return false
            }
        }
        val worker = TestListenableWorkerBuilder<ForwardWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(Data.Builder().putString(ForwardWorker.KEY_DEDUPE, key).build())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    ForwardWorker(appContext, workerParameters, repository, sender, "+15551234567") {
                        AppSettings(forwardingEnabled = true)
                    }
            }).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(ForwardStatus.CANCELLED, repository.findLog(key)?.status)
        assertNull(database.pendingDao().find(key))
    }

    private class RecordingSender : SmsSender {
        val sent = mutableListOf<Pair<String, String>>()
        override suspend fun send(targetNumber: String, text: String): Boolean {
            sent += targetNumber to text
            return true
        }
    }
}
