package com.autosmsforwarder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardLogEntity
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.PendingForwardEntity
import com.autosmsforwarder.domain.ForwardStatus
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.worker.RetentionWorker
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForwardRepositoryTest {
    private lateinit var database: ForwardDatabase
    private lateinit var repository: ForwardRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ForwardDatabase::class.java
        ).build()
        repository = ForwardRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun transitionsDeleteFullBodyOnlyAtTerminalStatus() = runBlocking {
        val message = IncomingMessage("+919876543210", "body with private details", 1_800_000_000_000L)
        val key = repository.recordIfNew(message, "+15551234567")!!.dedupeKey
        assertEquals(ForwardStatus.PENDING, repository.findLog(key)?.status)
        assertEquals(message.body, database.pendingDao().find(key)?.body)

        repository.beginOrResume(key)
        assertEquals(ForwardStatus.SENDING, repository.findLog(key)?.status)
        assertEquals(1, repository.findLog(key)?.attempts)
        repository.complete(key, ForwardStatus.SENT)
        assertEquals(ForwardStatus.SENT, repository.findLog(key)?.status)
        assertNull(database.pendingDao().find(key))
        assertNull(repository.beginOrResume(key))

        val failedKey = repository.recordIfNew(message.copy(receivedAtMillis = message.receivedAtMillis + 1), "+15551234567")!!.dedupeKey
        repository.beginOrResume(failedKey)
        repository.complete(failedKey, ForwardStatus.FAILED)
        assertEquals(ForwardStatus.FAILED, repository.findLog(failedKey)?.status)
        assertNull(database.pendingDao().find(failedKey))
    }

    @Test fun duplicateInsertIsAtomicAndSnippetIsLimited() = runBlocking {
        val body = "x".repeat(150)
        val message = IncomingMessage("+919876543210", body, 1_800_000_000_000L)
        val results = (1..8).map { async { repository.recordIfNew(message, "+15551234567") } }.awaitAll()
        assertEquals(1, results.count { it != null })
        val key = results.first { it != null }!!.dedupeKey
        assertEquals(body.take(100), repository.findLog(key)?.snippet)
        assertEquals(1, database.logDao().count())
        assertEquals(1, database.pendingDao().count())

        val second = repository.recordIfNew(message.copy(receivedAtMillis = message.receivedAtMillis + 1), "+15551234567")
        assertTrue(second != null)
        assertEquals(2, database.logDao().count())
    }

    @Test fun sentMessageMovesToUploadOutboxAndClearLogRemovesIt() = runBlocking {
        val message = IncomingMessage("+919876543210", "full private body", 1_800_000_000_000L)
        val key = repository.recordIfNew(message, "+15551234567")!!.dedupeKey
        repository.beginOrResume(key)

        assertTrue(repository.complete(key, ForwardStatus.SENT, archiveAfterSend = true))
        assertNull(database.pendingDao().find(key))
        assertEquals(message.body, repository.findUpload(key)?.body)
        assertEquals(ForwardStatus.SENT, repository.findLog(key)?.status)

        assertEquals(listOf(key), repository.clearLog())
        assertNull(repository.findUpload(key))
        assertNull(repository.findLog(key))
    }

    @Test fun clearLogDeletesEveryRowAndPendingBodyAndReturnsWorkToCancel() = runBlocking {
        val pending = repository.recordIfNew(
            IncomingMessage("+919876543210", "pending private body", 1_800_000_000_001L),
            "+15551234567"
        )!!.dedupeKey
        val sent = repository.recordIfNew(
            IncomingMessage("+919876543210", "sent private body", 1_800_000_000_002L),
            "+15551234567"
        )!!.dedupeKey
        repository.beginOrResume(sent)
        repository.complete(sent, ForwardStatus.SENT)

        assertEquals(listOf(pending), repository.clearLog())
        assertEquals(0, database.logDao().count())
        assertEquals(0, database.pendingDao().count())
        assertNull(repository.beginOrResume(pending))
        assertTrue(repository.recordIfNew(
            IncomingMessage("+919876543210", "pending private body", 1_800_000_000_001L),
            "+15551234567"
        )?.shouldEnqueue == true)
    }

    @Test fun retentionDeletesOldLogAndExpiredPendingBodyAtBoundaries() = runBlocking {
        val now = 1_800_000_000_000L
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        val day = 24L * 60 * 60 * 1000
        database.logDao().insert(ForwardLogEntity(dedupeKey = "old", receivedAt = now - sevenDays - 1, sender = "s", snippet = "", status = ForwardStatus.SENT, attempts = 1, lastUpdatedAt = now))
        database.logDao().insert(ForwardLogEntity(dedupeKey = "boundary", receivedAt = now - sevenDays, sender = "s", snippet = "", status = ForwardStatus.SENT, attempts = 1, lastUpdatedAt = now))
        database.logDao().insert(ForwardLogEntity(dedupeKey = "stale", receivedAt = now - day, sender = "s", snippet = "", status = ForwardStatus.PENDING, attempts = 0, lastUpdatedAt = now - day))
        database.pendingDao().insert(PendingForwardEntity("stale", "s", now - day, "private", now - day - 1))

        repository.purgeExpired(now)

        assertNull(repository.findLog("old"))
        assertEquals(ForwardStatus.SENT, repository.findLog("boundary")?.status)
        assertEquals(ForwardStatus.FAILED, repository.findLog("stale")?.status)
        assertNull(database.pendingDao().find("stale"))
        assertFalse(database.logDao().count() == 0)
    }

    @Test fun windowQueryShowsOnlyRecentRowsNewestFirst() = runBlocking {
        val now = 1_800_000_000_000L
        listOf("old" to now - 11, "newer" to now - 1, "newest" to now).forEach { (key, time) ->
            database.logDao().insert(ForwardLogEntity(dedupeKey = key, receivedAt = time, sender = "s", snippet = "", status = ForwardStatus.SENT, attempts = 1, lastUpdatedAt = now))
        }
        assertEquals(listOf("newest", "newer"), database.logDao().recent(now - 10).first().map { it.dedupeKey })
    }

    @Test fun recentFlowEmitsWhenNewLogRowArrives() = runBlocking {
        val now = System.currentTimeMillis()
        val observed = async {
            withTimeout(5_000) {
                database.logDao().recent(now - 300_000L).first { rows -> rows.any { it.dedupeKey == "live" } }
            }
        }
        database.logDao().insert(ForwardLogEntity(dedupeKey = "live", receivedAt = now, sender = "s", snippet = "sample", status = ForwardStatus.PENDING, attempts = 0, lastUpdatedAt = now))
        assertEquals("live", observed.await().single().dedupeKey)
    }

    @Test fun retentionWorkerRunsThePurge() = runBlocking {
        val now = System.currentTimeMillis()
        database.logDao().insert(ForwardLogEntity(dedupeKey = "expired", receivedAt = now - 8L * 24 * 60 * 60 * 1000, sender = "s", snippet = "", status = ForwardStatus.SENT, attempts = 1, lastUpdatedAt = now))
        val worker = TestListenableWorkerBuilder<RetentionWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = RetentionWorker(appContext, workerParameters, repository)
            })
            .build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertNull(repository.findLog("expired"))
    }
}
