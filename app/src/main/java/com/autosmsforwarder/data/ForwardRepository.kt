package com.autosmsforwarder.data

import androidx.room.withTransaction
import com.autosmsforwarder.domain.DefaultForwardPolicy
import com.autosmsforwarder.domain.ForwardDecision
import com.autosmsforwarder.domain.ForwardSettings
import com.autosmsforwarder.domain.ForwardStatus
import com.autosmsforwarder.domain.IdempotencyKey
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.domain.RetryPolicy

data class ReceiveResult(val dedupeKey: String, val shouldEnqueue: Boolean)

class ForwardRepository(
    private val database: ForwardDatabase,
    private val policy: DefaultForwardPolicy = DefaultForwardPolicy(),
    private val retryPolicy: RetryPolicy = RetryPolicy()
) {
    suspend fun recordIfNew(message: IncomingMessage, targetNumber: String): ReceiveResult? {
        if (targetNumber.isBlank()) return null
        val key = IdempotencyKey.create(message.sender, message.receivedAtMillis, message.body)
        val decision = policy.decide(message, ForwardSettings(targetNumber))
        val status = if (decision is ForwardDecision.Skip) ForwardStatus.SKIPPED_LOOP else ForwardStatus.PENDING
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val inserted = database.logDao().insert(
                ForwardLogEntity(
                    dedupeKey = key,
                    receivedAt = message.receivedAtMillis,
                    sender = message.sender,
                    snippet = message.body.take(100),
                    status = status,
                    attempts = 0,
                    lastUpdatedAt = now
                )
            )
            if (inserted == -1L) return@withTransaction null
            if (status == ForwardStatus.PENDING) {
                database.pendingDao().insert(
                    PendingForwardEntity(key, message.sender, message.receivedAtMillis, message.body, now)
                )
            }
            ReceiveResult(key, status == ForwardStatus.PENDING)
        }
    }

    suspend fun beginOrResume(key: String): PendingForwardEntity? = database.withTransaction {
        val log = database.logDao().find(key) ?: return@withTransaction null
        if (retryPolicy.beginOrResumeSend(log.status) == null) return@withTransaction null
        val pending = database.pendingDao().find(key) ?: return@withTransaction null
        database.logDao().update(key, ForwardStatus.SENDING, log.attempts + 1, System.currentTimeMillis())
        pending
    }

    suspend fun complete(key: String, status: ForwardStatus, archiveAfterSend: Boolean = false): Boolean = database.withTransaction {
        require(status == ForwardStatus.SENT || status == ForwardStatus.FAILED)
        val log = database.logDao().find(key) ?: return@withTransaction false
        val pending = database.pendingDao().find(key)
        if (status == ForwardStatus.SENT && archiveAfterSend && pending != null) {
            database.uploadDao().insert(
                UploadOutboxEntity(key, pending.sender, pending.smsTimestamp, pending.body, System.currentTimeMillis())
            )
        }
        database.logDao().update(key, status, log.attempts, System.currentTimeMillis())
        database.pendingDao().delete(key)
        status == ForwardStatus.SENT && archiveAfterSend && pending != null
    }

    suspend fun resetForRetry(key: String) = database.withTransaction {
        val log = database.logDao().find(key) ?: return@withTransaction
        database.logDao().update(key, ForwardStatus.PENDING, log.attempts, System.currentTimeMillis())
    }

    suspend fun findLog(key: String): ForwardLogEntity? = database.logDao().find(key)

    suspend fun pendingUploads(): List<String> = database.uploadDao().allKeys()

    suspend fun findUpload(key: String): UploadOutboxEntity? = database.uploadDao().find(key)

    suspend fun completeUpload(key: String) = database.uploadDao().delete(key)

    suspend fun clearLog(): List<String> = database.withTransaction {
        val pendingKeys = database.pendingDao().allKeys()
        val uploadKeys = database.uploadDao().allKeys()
        database.pendingDao().deleteAll()
        database.uploadDao().deleteAll()
        database.logDao().deleteAll()
        pendingKeys + uploadKeys
    }

    suspend fun purgeExpired(nowMillis: Long) = database.withTransaction {
        val pendingCutoff = RetentionPolicy.pendingCutoff(nowMillis)
        database.logDao().failExpiredPending(pendingCutoff, nowMillis)
        database.pendingDao().deleteOlderThan(pendingCutoff)
        database.logDao().deleteOlderThan(RetentionPolicy.logCutoff(nowMillis))
    }
}
