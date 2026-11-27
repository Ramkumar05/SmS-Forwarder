package com.autosmsforwarder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.data.WebStatus
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.domain.WebQueuePolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebQueueRepositoryTest {
    private lateinit var database: ForwardDatabase
    private var now = 1_800_000_000_000L
    private lateinit var repository: WebDeliveryRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ForwardDatabase::class.java
        ).build()
        repository = WebDeliveryRepository(database, { now })
    }

    @After fun tearDown() = database.close()

    private suspend fun queue(body: String, at: Long = now) =
        repository.recordIfNew(IncomingMessage("sender", body, at))!!

    @Test fun matchedMessagesAreQueuedAndDuplicatesAreIgnored() = runBlocking {
        val message = IncomingMessage("sender", "p".repeat(150), now)
        val key = repository.recordIfNew(message)!!
        assertNull(repository.recordIfNew(message))
        assertEquals(message.body, database.webDeliveryDao().find(key)?.body)
        assertEquals(WebStatus.QUEUED, database.webLogDao().find(key)?.status)
        assertEquals(message.body.take(100), database.webLogDao().find(key)?.snippet)
    }

    @Test fun nextQueuedReturnsOldestFirstAndFinishRemovesTheBody() = runBlocking {
        val first = queue("first"); now += 10
        val second = queue("second")
        assertEquals(first, repository.nextQueued()?.dedupeKey)
        repository.markUploading(first)
        assertEquals(WebStatus.UPLOADING, database.webLogDao().find(first)?.status)
        repository.finish(first, WebStatus.COMPLETED)
        assertNull(database.webDeliveryDao().find(first))
        assertEquals(WebStatus.COMPLETED, database.webLogDao().find(first)?.status)
        assertEquals(second, repository.nextQueued()?.dedupeKey)
    }

    @Test fun requeueKeepsTheBodyAndReturnsToQueued() = runBlocking {
        val key = queue("keep me")
        repository.markUploading(key)
        repository.requeue(key)
        assertEquals("keep me", database.webDeliveryDao().find(key)?.body)
        assertEquals(WebStatus.QUEUED, database.webLogDao().find(key)?.status)
    }

    @Test fun requeueDoesNotResurrectADiscardedMessage() = runBlocking {
        val key = queue("gone")
        repository.markUploading(key)
        repository.discardAll()
        repository.requeue(key)
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(key)?.status)
    }

    @Test fun onlyTheNewestFiftyAreKept() = runBlocking {
        val keys = (0 until 55).map { i -> queue("body $i", now + i).also { now += 1 } }
        val remaining = database.webDeliveryDao().allKeys().toSet()
        assertEquals(WebQueuePolicy.MAX_ENTRIES, remaining.size)
        keys.take(5).forEach {
            assertTrue(it !in remaining)
            assertEquals(WebStatus.SKIPPED, database.webLogDao().find(it)?.status)
        }
        assertEquals(keys[5], repository.nextQueued()?.dedupeKey)
    }

    @Test fun messagesOlderThanSevenDaysAreDropped() = runBlocking {
        val old = queue("old")
        now += WebQueuePolicy.MAX_AGE_MILLIS - 1
        val fresh = queue("fresh", now)
        repository.enforceLimits()
        assertEquals("old", database.webDeliveryDao().find(old)?.body)
        now += 1
        repository.enforceLimits()
        assertNull(database.webDeliveryDao().find(old))
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(old)?.status)
        assertEquals("fresh", database.webDeliveryDao().find(fresh)?.body)
    }

    @Test fun startupKeepsTheQueueAndReturnsInterruptedAttemptsToQueued() = runBlocking {
        val key = queue("survives restart")
        repository.markUploading(key)
        repository.cleanupAtStartup()
        assertEquals("survives restart", database.webDeliveryDao().find(key)?.body)
        assertEquals(WebStatus.QUEUED, database.webLogDao().find(key)?.status)
        assertTrue(repository.hasQueued())
    }

    @Test fun switchingOffDeletesTheWholeQueue() = runBlocking {
        val a = queue("a"); now += 1
        val b = queue("b")
        assertEquals(setOf(a, b), repository.discardAll().toSet())
        assertTrue(database.webDeliveryDao().allKeys().isEmpty())
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(a)?.status)
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(b)?.status)
    }

    @Test fun clearLogLeavesTheQueueAndItsActivityAlone() = runBlocking {
        val waiting = queue("waiting"); now += 1
        val done = queue("done")
        repository.finish(done, WebStatus.COMPLETED)
        ForwardRepository(database).clearLog()
        assertEquals("waiting", database.webDeliveryDao().find(waiting)?.body)
        assertEquals(WebStatus.QUEUED, database.webLogDao().find(waiting)?.status)
        assertNull(database.webLogDao().find(done))
    }

    @Test fun queueProjectionNeverExposesMoreThanOneHundredCharacters() = runBlocking {
        queue("x".repeat(500))
        val row = repository.observeQueue().first().single()
        assertEquals(100, row.snippet.length)
    }
}
