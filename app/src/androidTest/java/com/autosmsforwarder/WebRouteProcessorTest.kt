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
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.domain.ForwardStatus
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.receiver.ForwardRouteProcessor
import com.autosmsforwarder.receiver.WebRouteProcessor
import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.settings.AppSettings
import com.autosmsforwarder.settings.RouteConfiguration
import com.autosmsforwarder.settings.SendDelay
import com.autosmsforwarder.worker.WebArchiveUploader
import com.autosmsforwarder.worker.WebDeliveryWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebRouteProcessorTest {
    private lateinit var database: ForwardDatabase
    private lateinit var webRepository: WebDeliveryRepository
    private lateinit var forwardProcessor: ForwardRouteProcessor
    private val now = 1_800_000_000_000L
    private val message = IncomingMessage("sender", "Payment received", now)
    private val config = RouteConfiguration(
        forwardingEnabled = true,
        targetNumber = "+15551234567",
        sendDelay = SendDelay.THIRTY_SECONDS,
        forwardMatchMode = MatchMode.KEYWORDS,
        forwardKeywords = "otp",
        webEnabled = true,
        webMatchMode = MatchMode.KEYWORDS,
        webKeywords = "payment received",
        archiveWriteToken = "token"
    )

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ForwardDatabase::class.java
        ).build()
        webRepository = WebDeliveryRepository(database, { now })
        forwardProcessor = ForwardRouteProcessor(ForwardRepository(database))
    }

    @After fun tearDown() = database.close()

    @Test fun webOnlyMessageStoresOriginalBodyWithoutForwardLog() = runBlocking {
        val processor = WebRouteProcessor(webRepository)
        assertNull(forwardProcessor.recordIfMatched(message, config))
        val key = processor.recordIfMatched(message, config)!!
        assertEquals(message.body, database.webDeliveryDao().find(key)?.body)
        assertEquals(0, database.logDao().count())
        assertNull(processor.recordIfMatched(message, config))
    }

    @Test fun loopSkippedForwardStillQualifiesForWeb() = runBlocking {
        val fromTarget = message.copy(sender = config.targetNumber)
        val forwardConfig = config.copy(forwardMatchMode = MatchMode.ALL)
        val forward = forwardProcessor.recordIfMatched(fromTarget, forwardConfig)!!
        val web = WebRouteProcessor(webRepository)
            .recordIfMatched(fromTarget, forwardConfig)!!
        assertEquals(ForwardStatus.SKIPPED_LOOP, database.logDao().find(forward.dedupeKey)?.status)
        assertEquals(fromTarget.body, database.webDeliveryDao().find(web)?.body)
    }

    @Test fun disabledAndUnmatchedWebMessagesStoreNothing() = runBlocking {
        val processor = WebRouteProcessor(webRepository)
        assertNull(processor.recordIfMatched(message, config.copy(webEnabled = false)))
        assertNull(processor.recordIfMatched(message.copy(body = "unrelated"), config))
        assertTrue(database.webDeliveryDao().allKeys().isEmpty())
        assertEquals(0, database.logDao().count())
    }

    @Test fun pendingOrFailedForwardDoesNotDelayOrChangeWebAttempt() = runBlocking {
        val both = config.copy(forwardMatchMode = MatchMode.ALL, webMatchMode = MatchMode.ALL)
        val forwardRepository = ForwardRepository(database)
        val webProcessor = WebRouteProcessor(webRepository)
        val received = forwardProcessor.recordIfMatched(message, both)!!
        webProcessor.recordIfMatched(message, both)!!
        assertEquals(ForwardStatus.PENDING, forwardRepository.findLog(received.dedupeKey)?.status)
        val uploads = mutableListOf<String>()
        val worker = TestListenableWorkerBuilder<WebDeliveryWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    WebDeliveryWorker(appContext, workerParameters, webRepository,
                        WebArchiveUploader { stored, _ -> uploads += stored.body; 201 },
                        { AppSettings(webEnabled = true, archiveWriteToken = "token") })
            }).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf(message.body), uploads)
        assertEquals(ForwardStatus.PENDING, forwardRepository.findLog(received.dedupeKey)?.status)
        forwardRepository.beginOrResume(received.dedupeKey)
        forwardRepository.complete(received.dedupeKey, ForwardStatus.FAILED)
        assertEquals(ForwardStatus.FAILED, forwardRepository.findLog(received.dedupeKey)?.status)
        assertEquals(1, uploads.size)
    }
}
