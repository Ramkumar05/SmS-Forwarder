package com.autosmsforwarder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.domain.ForwardStatus
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.receiver.ForwardRouteProcessor
import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.settings.RouteConfiguration
import com.autosmsforwarder.settings.SendDelay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForwardRouteProcessorTest {
    private lateinit var database: ForwardDatabase
    private lateinit var processor: ForwardRouteProcessor
    private val config = RouteConfiguration(
        forwardingEnabled = true,
        targetNumber = "+15551234567",
        sendDelay = SendDelay.FIFTEEN_SECONDS,
        forwardMatchMode = MatchMode.KEYWORDS,
        forwardKeywords = "payment received",
        webEnabled = true,
        webMatchMode = MatchMode.ALL,
        webKeywords = "",
        archiveWriteToken = "token"
    )
    private val message = IncomingMessage("+919876543210", "Payment Received today", 1_800_000_000_000L)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ForwardDatabase::class.java
        ).build()
        processor = ForwardRouteProcessor(ForwardRepository(database))
    }

    @After fun tearDown() = database.close()

    @Test fun matchingBodyCreatesOnePendingForwardWithOriginalBody() = runBlocking {
        val first = processor.recordIfMatched(message, config)
        assertTrue(first?.shouldEnqueue == true)
        assertEquals(ForwardStatus.PENDING, database.logDao().find(first!!.dedupeKey)?.status)
        assertEquals(message.body, database.pendingDao().find(first.dedupeKey)?.body)
        assertNull(processor.recordIfMatched(message, config))
        assertEquals(1, database.logDao().count())
    }

    @Test fun webOnlyAndDisabledMessagesCreateNoForwardingData() = runBlocking {
        assertNull(processor.recordIfMatched(message.copy(body = "unrelated"), config))
        assertNull(processor.recordIfMatched(message, config.copy(forwardingEnabled = false)))
        assertNull(processor.recordIfMatched(message, config.copy(forwardingEnabled = false, webEnabled = false)))
        assertEquals(0, database.logDao().count())
        assertEquals(0, database.pendingDao().count())
    }

    @Test fun loopCheckStillAppliesOnlyAfterForwardingMatch() = runBlocking {
        val fromTarget = message.copy(sender = config.targetNumber)
        assertNull(processor.recordIfMatched(fromTarget.copy(body = "unrelated"), config))
        val recorded = processor.recordIfMatched(fromTarget, config)
        assertFalse(recorded!!.shouldEnqueue)
        assertEquals(ForwardStatus.SKIPPED_LOOP, database.logDao().find(recorded.dedupeKey)?.status)
        assertNull(database.pendingDao().find(recorded.dedupeKey))
        assertEquals(1, database.logDao().count())
    }
}
