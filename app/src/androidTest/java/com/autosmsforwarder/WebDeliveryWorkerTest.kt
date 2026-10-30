package com.autosmsforwarder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
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
import com.autosmsforwarder.settings.AppSettings
import com.autosmsforwarder.worker.WebArchiveUploader
import com.autosmsforwarder.worker.WebConnectivityChecker
import com.autosmsforwarder.worker.WebDeliveryWorker
import com.autosmsforwarder.worker.WebDeliveryScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebDeliveryWorkerTest {
    private lateinit var database: ForwardDatabase
    private lateinit var repository: WebDeliveryRepository
    private val now = 1_800_000_000_000L

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ForwardDatabase::class.java
        ).build()
        repository = WebDeliveryRepository(database, "current", { now })
    }

    @After fun tearDown() = database.close()

    @Test fun successfulAttemptUsesOriginalSmsAndClaimPreventsASecondPost() = runBlocking {
        val key = repository.recordIfNew(IncomingMessage("sender", "original body", now))!!
        val uploader = RecordingUploader(201)
        val worker = worker(key, uploader)
        assertEquals(setOf(WebDeliveryWorker.KEY_DEDUPE), worker.inputData.keyValueMap.keys)

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(1, uploader.uploads.size)
        assertEquals("original body", uploader.uploads.single().body)
        assertEquals("sender", uploader.uploads.single().sender)
        assertNull(database.webDeliveryDao().find(key))
        assertEquals(WebStatus.COMPLETED, database.webLogDao().find(key)?.status)
    }

    @Test fun scheduledUploadHasOnlyTheKeyAndDoesNotWaitForNetwork() {
        val request = WebDeliveryScheduler.createUploadRequest("key")
        assertEquals(setOf(WebDeliveryWorker.KEY_DEDUPE), request.workSpec.input.keyValueMap.keys)
        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(0L, request.workSpec.initialDelay)
        assertEquals(WebDeliveryRepository.MAX_AGE_MILLIS,
            WebDeliveryScheduler.createExpiryRequest("key").workSpec.initialDelay)
    }

    @Test fun serverAndTransportFailuresDiscardTheBodyWithoutRetry() = runBlocking {
        val serverKey = repository.recordIfNew(IncomingMessage("sender", "server failure", now))!!
        val serverUploader = RecordingUploader(503)
        assertEquals(ListenableWorker.Result.success(), worker(serverKey, serverUploader).doWork())
        assertEquals(1, serverUploader.uploads.size)
        assertNull(database.webDeliveryDao().find(serverKey))
        assertEquals(WebStatus.FAILED, database.webLogDao().find(serverKey)?.status)

        val transportKey = repository.recordIfNew(IncomingMessage("sender", "transport failure", now + 1))!!
        val transportUploader = RecordingUploader(null)
        assertEquals(ListenableWorker.Result.success(), worker(transportKey, transportUploader).doWork())
        assertEquals(1, transportUploader.uploads.size)
        assertNull(database.webDeliveryDao().find(transportKey))
        assertEquals(WebStatus.FAILED, database.webLogDao().find(transportKey)?.status)
    }

    @Test fun disabledOfflineOrMissingTokenDiscardsBeforeUpload() = runBlocking {
        val cases = listOf(
            Triple(false, true, "token"), Triple(true, false, "token"), Triple(true, true, "")
        )
        cases.forEachIndexed { index, (enabled, online, token) ->
            val key = repository.recordIfNew(IncomingMessage("sender", "body $index", now + index))!!
            val uploader = RecordingUploader(201)
            assertEquals(ListenableWorker.Result.success(), worker(key, uploader, enabled, online, token).doWork())
            assertTrue(uploader.uploads.isEmpty())
            assertNull(database.webDeliveryDao().find(key))
            assertEquals(WebStatus.SKIPPED, database.webLogDao().find(key)?.status)
        }
    }

    @Test fun expiredOrPreviousProcessDeliveryCannotPost() = runBlocking {
        val expiredKey = repository.recordIfNew(IncomingMessage("sender", "expired", now))!!
        val expiredRepository = WebDeliveryRepository(database, "current", { now + WebDeliveryRepository.MAX_AGE_MILLIS })
        val expiredUploader = RecordingUploader(201)
        assertEquals(ListenableWorker.Result.success(), worker(expiredKey, expiredUploader, repository = expiredRepository).doWork())
        assertTrue(expiredUploader.uploads.isEmpty())
        assertNull(database.webDeliveryDao().find(expiredKey))
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(expiredKey)?.status)

        val oldKey = repository.recordIfNew(IncomingMessage("sender", "old process", now + 1))!!
        val newProcessRepository = WebDeliveryRepository(database, "next-process", { now })
        val oldUploader = RecordingUploader(201)
        assertEquals(ListenableWorker.Result.success(), worker(oldKey, oldUploader, repository = newProcessRepository).doWork())
        assertTrue(oldUploader.uploads.isEmpty())
        assertNull(database.webDeliveryDao().find(oldKey))
        assertEquals(WebStatus.SKIPPED, database.webLogDao().find(oldKey)?.status)
    }

    private fun worker(
        key: String,
        uploader: WebArchiveUploader,
        enabled: Boolean = true,
        online: Boolean = true,
        token: String = "token",
        repository: WebDeliveryRepository = this.repository
    ): WebDeliveryWorker = TestListenableWorkerBuilder<WebDeliveryWorker>(ApplicationProvider.getApplicationContext())
        .setInputData(Data.Builder().putString(WebDeliveryWorker.KEY_DEDUPE, key).build())
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                WebDeliveryWorker(appContext, workerParameters, repository, uploader,
                    WebConnectivityChecker { online }, { AppSettings(webEnabled = enabled, archiveWriteToken = token) })
        }).build()

    private class RecordingUploader(private val response: Int?) : WebArchiveUploader {
        val uploads = mutableListOf<WebDeliveryEntity>()
        override suspend fun upload(message: WebDeliveryEntity, token: String): Int {
            assertEquals("token", token)
            uploads += message
            return response ?: throw IllegalStateException("transport failed")
        }
    }
}
