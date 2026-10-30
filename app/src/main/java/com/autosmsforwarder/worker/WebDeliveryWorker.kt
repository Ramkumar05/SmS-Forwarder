package com.autosmsforwarder.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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

class WebDeliveryWorker(
    context: Context,
    parameters: WorkerParameters,
    private val repository: WebDeliveryRepository,
    private val uploader: WebArchiveUploader,
    private val connectivity: WebConnectivityChecker,
    private val loadSettings: suspend () -> AppSettings
) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(
        context,
        parameters,
        WebDeliveryRepository(ForwardDatabase.get(context)),
        HttpWebArchiveUploader(),
        AndroidWebConnectivityChecker(context),
        { SettingsRepository(context).settings.first() }
    )

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_DEDUPE) ?: return Result.success()
        val settings = loadSettings()
        if (!settings.webEnabled || settings.archiveWriteToken.isBlank() || !connectivity.isOnline()) {
            repository.discard(key)
            return Result.success()
        }
        val message = repository.claim(key) ?: return Result.success()
        try {
            val response = uploader.upload(message, settings.archiveWriteToken)
            repository.recordOutcome(key, if (response in 200..299) WebStatus.COMPLETED else WebStatus.FAILED)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { repository.recordOutcome(key, WebStatus.UNCONFIRMED) }
            throw cancelled
        } catch (_: Exception) {
            repository.recordOutcome(key, WebStatus.FAILED)
        }
        return Result.success()
    }

    companion object {
        const val KEY_DEDUPE = "dedupeKey"
    }
}

object WebDeliveryScheduler {
    fun createExpiryRequest(key: String): OneTimeWorkRequest = OneTimeWorkRequestBuilder<WebExpiryWorker>()
            .setInputData(workDataOf(WebExpiryWorker.KEY_DEDUPE to key))
            .setInitialDelay(WebDeliveryRepository.MAX_AGE_MILLIS, TimeUnit.MILLISECONDS)
            .build()

    fun createUploadRequest(key: String): OneTimeWorkRequest = OneTimeWorkRequestBuilder<WebDeliveryWorker>()
            .setInputData(workDataOf(WebDeliveryWorker.KEY_DEDUPE to key))
            .build()

    fun enqueue(context: Context, key: String) {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork("web-expire-$key", ExistingWorkPolicy.REPLACE, createExpiryRequest(key))
        workManager.enqueueUniqueWork("web-$key", ExistingWorkPolicy.KEEP, createUploadRequest(key))
    }
}

class WebExpiryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        inputData.getString(KEY_DEDUPE)?.let { key ->
            WebDeliveryRepository(ForwardDatabase.get(applicationContext)).discardIfExpired(key)
        }
        return Result.success()
    }

    companion object {
        const val KEY_DEDUPE = "dedupeKey"
    }
}
