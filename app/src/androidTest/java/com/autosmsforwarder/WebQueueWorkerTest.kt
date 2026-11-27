package com.autosmsforwarder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.WebDeliveryEntity
import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.data.WebStatus
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.domain.WebQueuePolicy
import com.autosmsforwarder.settings.AppSettings
import com.autosmsforwarder.worker.WebArchiveUploader
import com.autosmsforwarder.worker.WebDeliveryScheduler
import com.autosmsforwarder.worker.WebDeliveryWorker
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebQueueWorkerTest {
    private lateinit var database: ForwardDatabase
    private lateinit var repository: WebDeliveryRepository
    private val now = 1_800_000_000_000L

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ForwardDatabase::class.java
        ).build()
        repository = WebDeliveryRepository(database, { now })
    }

    @After fun tearDown() = database.close()

    private suspend fun queue(body: String, at: Long) =
        repository.recordIfNew(IncomingMessage("sender", body, at))!!

    @Test fun drainRequestNeedsNetworkAndCarriesNoMessageData() {
        val request = WebDeliveryScheduler.createDrainRequest()
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.workSpec.input.keyValueMap.isEmpty())
    }

    @Test fun tenQueuedMessagesAreSentOldestFirstOnce() = runBlocking {
        val keys = (0 until 10).map { queue("body $it", now + it) }
        val uploader = RecordingUploader { 201 }
        assertEquals(ListenableWorker.Result.success(), worker(uploader).doWork())
        assertEquals((0 until 10).map { "body $it" }, uploader.uploads.map { it.body })
        keys.forEach {
            assertNull(database.webDeliveryDao().find(it))
            assertEquals(WebStatus.COMPLETED, database.webLogDao().find(it)?.status)
        }
        assertEquals(ListenableWorker.Result.success(), worker(uploader).doWork())
        assertEquals(10, uploader.uploads.size)
    }

    @Test fun networkFailureKeepsEverythingQueuedAndRetriesLater() = runBlocking {
        val first = queue("first", now)
        val second = queue("second", now + 1)
        val offline = RecordingUploader { throw IOException("offline") }
        assertEquals(ListenableWorker.Result.retry(), worker(offline).doWork())
        assertEquals(1, offline.uploads.size)
        assertEquals("first", database.webDeliveryDao().find(first)?.body)
        assertEquals("second", database.webDeliveryDao().find(second)?.body)
        assertEquals(WebStatus.QUEUED, database.webLogDao().find(first)?.status)

        val online = RecordingUploader { 201 }
        assertEquals(ListenableWorker.Result.success(), worker(online).doWork())
        assertEquals(listOf("first", "second"), online.uploads.map { it.body })
        assertTrue(database.webDeliveryDao().allKeys().isEmpty())
    }

    @Test fun serverRejectionIsNotRetriedAndDoesNotBlockLaterMessages() = runBlocking {
        val rejected = queue("rejected", now)
        val accepted = queue("accepted", now + 1)
        val uploader = RecordingUploader { if (it.body == "rejected") 503 else 201 }
        assertEquals(ListenableWorker.Result.success(), worker(uploader).doWork())
        assertEquals(listOf("rejected", "accepted"), uploader.uploads.map { it.body })
        assertNull(database.webDeliveryDao().find(rejected))
        assertEquals(WebStatus.FAILED, database.webLogDao().find(rejected)?.status)
        assertEquals(WebStatus.COMPLETED, database.webLogDao().find(accepted)?.status)
    }

    @Test fun webOffDeletesTheQueueWithoutUploading() = runBlocking {
        val key = queue("body", now)
        val uploader = RecordingUploader { 201 }
        assertEquals(ListenableWorker.Result.success(), worker(uploader, enabled = false).doWork())
        assertTrue(uploader.uploads.isEmpty())
        assertNull(database.webDeliveryDao().find(key))
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(key)?.status)
    }

    @Test fun blankTokenLeavesTheQueueUntouched() = runBlocking {
        val key = queue("body", now)
        val uploader = RecordingUploader { 201 }
        assertEquals(ListenableWorker.Result.success(), worker(uploader, token = "").doWork())
        assertTrue(uploader.uploads.isEmpty())
        assertEquals("body", database.webDeliveryDao().find(key)?.body)
    }

    @Test fun messagesOlderThanSevenDaysAreNotUploaded() = runBlocking {
        val old = queue("old", now)
        val later = WebDeliveryRepository(database, { now + WebQueuePolicy.MAX_AGE_MILLIS })
        val uploader = RecordingUploader { 201 }
        assertEquals(ListenableWorker.Result.success(), worker(uploader, repository = later).doWork())
        assertTrue(uploader.uploads.isEmpty())
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(old)?.status)
    }

    @Test fun uploadsUseTheAppSourceCapturedWhenTheMessageArrived() = runBlocking {
        repository.recordIfNew(IncomingMessage("sender", "body", now), "+919876543210", "Home phone")
        val uploader = RecordingUploader { 201 }
        worker(uploader).doWork()
        assertEquals("Home phone", uploader.uploads.single().appSourceName)
        assertEquals("+919876543210", uploader.uploads.single().appSourcePhoneNumber)
    }

    private fun worker(
        uploader: WebArchiveUploader,
        enabled: Boolean = true,
        token: String = "token",
        repository: WebDeliveryRepository = this.repository
    ): WebDeliveryWorker = TestListenableWorkerBuilder<WebDeliveryWorker>(ApplicationProvider.getApplicationContext())
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                WebDeliveryWorker(appContext, workerParameters, repository, uploader,
                    { AppSettings(webEnabled = enabled, archiveWriteToken = token) })
        }).build()

    private class RecordingUploader(private val respond: (WebDeliveryEntity) -> Int) : WebArchiveUploader {
        val uploads = mutableListOf<WebDeliveryEntity>()
        override suspend fun upload(message: WebDeliveryEntity, token: String): Int {
            assertEquals("token", token)
            uploads += message
            return respond(message)
        }
    }
}
