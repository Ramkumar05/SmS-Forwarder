package com.autosmsforwarder.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.domain.ForwardStatus
import com.autosmsforwarder.domain.MessageFormatter
import com.autosmsforwarder.sms.AndroidSmsSender
import com.autosmsforwarder.sms.SmsSender
import com.autosmsforwarder.settings.AppSettings
import com.autosmsforwarder.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ForwardWorker(
    context: Context,
    parameters: WorkerParameters,
    private val repository: ForwardRepository,
    private val sender: SmsSender,
    private val targetNumber: String?,
    private val loadSettings: suspend () -> AppSettings = { SettingsRepository(context).settings.first() }
) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(
        context,
        parameters,
        ForwardRepository(ForwardDatabase.get(context)),
        AndroidSmsSender(context),
        null
    )

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_DEDUPE) ?: return Result.failure()
        val settings = loadSettings()
        if (!settings.forwardingEnabled) {
            repository.cancelPending(key)
            return Result.success()
        }
        val pending = repository.beginOrResume(key) ?: return Result.success()
        if (!loadSettings().forwardingEnabled) {
            repository.cancelPending(key)
            return Result.success()
        }
        val currentTarget = targetNumber ?: settings.targetNumber
        if (currentTarget.isBlank()) {
            repository.complete(key, ForwardStatus.FAILED)
            return Result.failure()
        }
        val text = MessageFormatter.format(pending.sender, pending.body)
        val sent = try {
            sender.send(currentTarget, text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (revoked: SecurityException) {
            repository.complete(key, ForwardStatus.FAILED)
            return Result.failure()
        } catch (failure: Exception) {
            false
        }
        if (sent) {
            repository.complete(key, ForwardStatus.SENT)
            return Result.success()
        }

        val log = repository.findLog(key) ?: return Result.failure()
        if (log.status == ForwardStatus.CANCELLED) return Result.success()
        val attempts = log.attempts
        if (attempts >= MAX_ATTEMPTS) {
            repository.complete(key, ForwardStatus.FAILED)
            return Result.failure()
        }
        repository.resetForRetry(key)
        return Result.retry()
    }

    companion object {
        const val KEY_DEDUPE = "dedupeKey"
        private const val MAX_ATTEMPTS = 3
    }
}
