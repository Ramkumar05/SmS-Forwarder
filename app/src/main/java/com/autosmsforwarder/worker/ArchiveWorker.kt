package com.autosmsforwarder.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.UploadOutboxEntity
import com.autosmsforwarder.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit

interface ArchiveUploader {
    suspend fun upload(message: UploadOutboxEntity, token: String): Int
}

class HttpArchiveUploader : ArchiveUploader {
    override suspend fun upload(message: UploadOutboxEntity, token: String): Int = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("idempotencyKey", message.dedupeKey)
            .put("sender", message.sender)
            .put("body", message.body)
            .put("receivedAt", Instant.ofEpochMilli(message.smsTimestamp).toString())
            .toString()
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ENDPOINT = "https://sms-archive.arj-ramkumar.workers.dev/api/messages"
    }
}

class ArchiveWorker(
    context: Context,
    parameters: WorkerParameters,
    private val repository: ForwardRepository,
    private val uploader: ArchiveUploader,
    private val configuredToken: String?
) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(
        context, parameters, ForwardRepository(ForwardDatabase.get(context)), HttpArchiveUploader(), null
    )

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_DEDUPE) ?: return Result.failure()
        val message = repository.findUpload(key) ?: return Result.success()
        val token = configuredToken ?: SettingsRepository(applicationContext).settings.first().archiveWriteToken
        if (token.isBlank()) return Result.retry()
        val status = try {
            uploader.upload(message, token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.retry()
        }
        return when {
            status == 200 || status == 201 -> {
                repository.completeUpload(key)
                Result.success()
            }
            status == 408 || status == 429 || status >= 500 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_DEDUPE = "dedupeKey"
    }
}

object ArchiveScheduler {
    fun enqueue(context: Context, key: String, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val request = OneTimeWorkRequestBuilder<ArchiveWorker>()
            .setInputData(workDataOf(ArchiveWorker.KEY_DEDUPE to key))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("archive-$key", policy, request)
    }

    fun sweepNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "archive-sweep-now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ArchiveSweepWorker>().build()
        )
    }
}

class ArchiveSweepWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = ForwardRepository(ForwardDatabase.get(applicationContext))
        repository.pendingUploads().forEach { key ->
            ArchiveScheduler.enqueue(applicationContext, key, ExistingWorkPolicy.REPLACE)
        }
        return Result.success()
    }
}
