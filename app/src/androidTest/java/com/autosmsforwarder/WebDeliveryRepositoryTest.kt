package com.autosmsforwarder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.data.WebStatus
import com.autosmsforwarder.domain.IncomingMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebDeliveryRepositoryTest {
    private lateinit var database: ForwardDatabase
    private val now = 1_800_000_000_000L

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ForwardDatabase::class.java
        ).build()
    }

    @After fun tearDown() = database.close()

    @Test fun duplicateInsertAndConcurrentClaimsAllowOnlyOneAttempt() = runBlocking {
        val repository = WebDeliveryRepository(database, "current", { now })
        val message = IncomingMessage("sender", "p".repeat(150), now)
        val key = repository.recordIfNew(message)!!
        assertNull(repository.recordIfNew(message))

        val claims = (1..8).map { async { repository.claim(key) } }.awaitAll()
        assertEquals(1, claims.count { it != null })
        assertEquals(message.body, claims.single { it != null }?.body)
        assertNull(database.webDeliveryDao().find(key))
        assertEquals(WebStatus.UPLOADING, database.webLogDao().find(key)?.status)
        assertEquals(message.body.take(100), database.webLogDao().find(key)?.snippet)
    }

    @Test fun startupCleanupDeletesPreviousProcessBodiesWithoutTouchingCurrentWork() = runBlocking {
        val previous = WebDeliveryRepository(database, "previous", { now })
        val current = WebDeliveryRepository(database, "current", { now })
        val oldKey = previous.recordIfNew(IncomingMessage("sender", "old body", now))!!
        val newKey = current.recordIfNew(IncomingMessage("sender", "new body", now + 1))!!

        assertEquals(listOf(oldKey), current.cleanupAtStartup().web)
        assertNull(database.webDeliveryDao().find(oldKey))
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(oldKey)?.status)
        assertEquals("new body", database.webDeliveryDao().find(newKey)?.body)
    }

    @Test fun expiryRemovesOnlyRowsThatHaveWaitedFiveMinutes() = runBlocking {
        var clock = now
        val repository = WebDeliveryRepository(database, "current", { clock })
        val key = repository.recordIfNew(IncomingMessage("sender", "private body", now))!!
        clock += WebDeliveryRepository.MAX_AGE_MILLIS - 1
        repository.discardIfExpired(key)
        assertEquals("private body", database.webDeliveryDao().find(key)?.body)
        clock++
        repository.discardIfExpired(key)
        assertNull(database.webDeliveryDao().find(key))
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(key)?.status)
    }

    @Test fun startupMarksInterruptedAttemptUnconfirmedWithoutReplayingItsBody() = runBlocking {
        val previous = WebDeliveryRepository(database, "previous", { now })
        val key = previous.recordIfNew(IncomingMessage("sender", "private body", now))!!
        assertEquals("private body", previous.claim(key)?.body)

        val current = WebDeliveryRepository(database, "current", { now + 1 })
        assertEquals(emptyList<String>(), current.cleanupAtStartup().web)
        assertNull(database.webDeliveryDao().find(key))
        assertEquals(WebStatus.UNCONFIRMED, database.webLogDao().find(key)?.status)
    }

    @Test fun switchingOffDiscardsPendingBodyAndMarksItSkipped() = runBlocking {
        val repository = WebDeliveryRepository(database, "current", { now })
        val key = repository.recordIfNew(IncomingMessage("sender", "private body", now))!!

        assertEquals(listOf(key), repository.discardAll())
        assertNull(database.webDeliveryDao().find(key))
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(key)?.status)
    }
}
