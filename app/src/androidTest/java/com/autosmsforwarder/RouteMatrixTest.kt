package com.autosmsforwarder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.domain.IdempotencyKey
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.receiver.ForwardRouteProcessor
import com.autosmsforwarder.receiver.WebRouteProcessor
import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.settings.RouteConfiguration
import com.autosmsforwarder.settings.SendDelay
import com.autosmsforwarder.worker.WebConnectivityChecker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteMatrixTest {
    private lateinit var database: ForwardDatabase
    private lateinit var forward: ForwardRouteProcessor
    private lateinit var web: WebRouteProcessor
    private val now = 1_800_000_000_000L
    private val base = RouteConfiguration(
        forwardingEnabled = true,
        targetNumber = "+15551234567",
        sendDelay = SendDelay.FIFTEEN_SECONDS,
        forwardMatchMode = MatchMode.ALL,
        forwardKeywords = "",
        webEnabled = true,
        webMatchMode = MatchMode.ALL,
        webKeywords = "",
        archiveWriteToken = "test-token"
    )

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ForwardDatabase::class.java
        ).build()
        forward = ForwardRouteProcessor(ForwardRepository(database))
        web = WebRouteProcessor(WebDeliveryRepository(database, "test-session", { now }), WebConnectivityChecker { true })
    }

    @After fun tearDown() = database.close()

    @Test fun allFourSwitchCombinationsRouteTheSameMessageIndependently() = runBlocking {
        val message = IncomingMessage("sender", "private body", now)
        for (forwardOn in listOf(false, true)) {
            for (webOn in listOf(false, true)) {
                val incoming = message.copy(receivedAtMillis = now + (if (forwardOn) 2 else 0) + (if (webOn) 1 else 0))
                val key = IdempotencyKey.create(incoming.sender, incoming.receivedAtMillis, incoming.body)
                val config = base.copy(forwardingEnabled = forwardOn, webEnabled = webOn)
                assertEquals(forwardOn, forward.recordIfMatched(incoming, config) != null)
                assertEquals(webOn, web.recordIfMatched(incoming, config) != null)
                assertEquals(forwardOn, database.logDao().find(key) != null)
                assertEquals(forwardOn, database.pendingDao().find(key) != null)
                assertEquals(webOn, database.webDeliveryDao().find(key) != null)
            }
        }
        assertEquals(2, database.logDao().count())
        assertEquals(2, database.webDeliveryDao().allKeys().size)
    }

    @Test fun separateKeywordModesInspectOnlyTheBodyAndDoNotLogWebOnlyMessages() = runBlocking {
        val config = base.copy(
            forwardMatchMode = MatchMode.KEYWORDS,
            forwardKeywords = "otp, payment received",
            webMatchMode = MatchMode.KEYWORDS,
            webKeywords = "receipt, हिंदी"
        )
        val forwardOnly = IncomingMessage("sender", "PAYMENT Received today", now)
        val webOnly = IncomingMessage("OTP-SENDER", "Here is your receipt", now + 1)
        val both = IncomingMessage("sender", "हिंदी OTP", now + 2)
        val neither = IncomingMessage("OTP-SENDER", "unrelated", now + 3)

        assertTrue(forward.recordIfMatched(forwardOnly, config) != null)
        assertNull(web.recordIfMatched(forwardOnly, config))
        assertNull(forward.recordIfMatched(webOnly, config))
        assertTrue(web.recordIfMatched(webOnly, config) != null)
        assertTrue(forward.recordIfMatched(both, config) != null)
        assertTrue(web.recordIfMatched(both, config) != null)
        assertNull(forward.recordIfMatched(neither, config))
        assertNull(web.recordIfMatched(neither, config))

        assertEquals(2, database.logDao().count())
        assertEquals(2, database.webDeliveryDao().allKeys().size)
        assertNull(database.logDao().find(IdempotencyKey.create(webOnly.sender, webOnly.receivedAtMillis, webOnly.body)))
    }
}
