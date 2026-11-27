package com.autosmsforwarder.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.WebDeliveryEntity
import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.data.WebStatus
import com.autosmsforwarder.settings.AppSettings
import com.autosmsforwarder.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit

fun interface WebArchiveUploader {
    suspend fun upload(message: WebDeliveryEntity, token: String): Int
}

class HttpWebArchiveUploader(
    private val connectionFactory: () -> HttpURLConnection = { URL(ENDPOINT).openConnection() as HttpURLConnection }
) : WebArchiveUploader {
    override suspend fun upload(message: WebDeliveryEntity, token: String): Int = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("idempotencyKey", com.autosmsforwarder.domain.WebIdempotencyKey.create(
                message.dedupeKey, message.appSourcePhoneNumber, message.appSourceName))
            .put("sender", message.sender)
            .put("body", message.body)
            .put("receivedAt", Instant.ofEpochMilli(message.smsTimestamp).toString())
            .apply {
                if (message.appSourcePhoneNumber.isNotBlank()) put("appSourcePhoneNumber", message.appSourcePhoneNumber)
                if (message.appSourceName.isNotBlank()) put("appSourceName", message.appSourceName)
            }
            .toString()
        val connection = connectionFactory().apply {
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

/**
 * Drains the persistent web queue, oldest first. Input data is intentionally empty: message
 * bodies stay in Room and never enter WorkManager.
 */
class WebDeliveryWorker(
    context: Context,
    parameters: WorkerParameters,
    private val repository: WebDeliveryRepository,
    private val uploader: WebArchiveUploader,
    private val loadSettings: suspend () -> AppSettings
) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(
        context,
        parameters,
        WebDeliveryRepository(ForwardDatabase.get(context)),
        HttpWebArchiveUploader(),
        { SettingsRepository(context).settings.first() }
    )

    override suspend fun doWork(): Result {
        repository.enforceLimits()
        while (true) {
            val settings = loadSettings()
            if (!settings.webEnabled) {
                repository.discardAll()
                return Result.success()
            }
            if (settings.archiveWriteToken.isBlank()) return Result.success()
            val message = repository.nextQueued() ?: return Result.success()
            val key = message.dedupeKey
            repository.markUploading(key)
            try {
                val response = uploader.upload(message, settings.archiveWriteToken)
                repository.finish(key, if (response in 200..299) WebStatus.COMPLETED else WebStatus.FAILED)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { repository.requeue(key) }
                throw cancelled
            } catch (_: IOException) {
                repository.requeue(key)
                return Result.retry()
            } catch (_: Exception) {
                repository.finish(key, WebStatus.FAILED)
            }
        }
    }
}

object WebDeliveryScheduler {
    const val DRAIN_WORK = "web-drain"

    fun createDrainRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<WebDeliveryWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    fun enqueue(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DRAIN_WORK, ExistingWorkPolicy.KEEP, createDrainRequest())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DRAIN_WORK).result.get()
    }
}
