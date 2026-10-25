package com.autosmsforwarder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.UploadOutboxEntity
import com.autosmsforwarder.domain.ForwardStatus
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.worker.ArchiveUploader
import com.autosmsforwarder.worker.ArchiveWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveWorkerTest {
    private lateinit var database: ForwardDatabase
    private lateinit var repository: ForwardRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ForwardDatabase::class.java
        ).build()
        repository = ForwardRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun successfulUploadDeletesOutboxWithoutChangingForwardStatus() = runBlocking {
        val key = queueMessage()
        val uploader = RecordingUploader(201)

        assertEquals(ListenableWorker.Result.success(), worker(key, uploader).doWork())
        assertEquals(1, uploader.uploads.size)
        assertEquals("full body", uploader.uploads.single().body)
        assertNull(repository.findUpload(key))
        assertEquals(ForwardStatus.SENT, repository.findLog(key)?.status)
    }

    @Test fun serverFailureKeepsBodyForRetry() = runBlocking {
        val key = queueMessage()
        val uploader = RecordingUploader(503)

        assertEquals(ListenableWorker.Result.retry(), worker(key, uploader).doWork())
        assertNotNull(repository.findUpload(key))
    }

    @Test fun authFailureKeepsBodyWithoutImmediateRetry() = runBlocking {
        val key = queueMessage()
        val uploader = RecordingUploader(401)

        assertEquals(ListenableWorker.Result.failure(), worker(key, uploader).doWork())
        assertNotNull(repository.findUpload(key))
    }

    private suspend fun queueMessage(): String {
        val key = repository.recordIfNew(
            IncomingMessage("+919876543210", "full body", 1_700_000_000_000L), "+15551234567"
        )!!.dedupeKey
        repository.beginOrResume(key)
        repository.complete(key, ForwardStatus.SENT, archiveAfterSend = true)
        return key
    }

    private fun worker(key: String, uploader: ArchiveUploader): ArchiveWorker {
        val input = Data.Builder().putString(ArchiveWorker.KEY_DEDUPE, key).build()
        return TestListenableWorkerBuilder<ArchiveWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(input)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = ArchiveWorker(
                    appContext, workerParameters, repository, uploader, "test-write-token"
                )
            }).build()
    }

    private class RecordingUploader(private val response: Int) : ArchiveUploader {
        val uploads = mutableListOf<UploadOutboxEntity>()
        override suspend fun upload(message: UploadOutboxEntity, token: String): Int {
            assertEquals("test-write-token", token)
            uploads += message
            return response
        }
    }
}
