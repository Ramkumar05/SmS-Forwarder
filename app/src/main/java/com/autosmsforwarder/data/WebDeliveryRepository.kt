package com.autosmsforwarder.data

import androidx.room.withTransaction
import com.autosmsforwarder.domain.IdempotencyKey
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.domain.QueueEntry
import com.autosmsforwarder.domain.WebQueuePolicy
import kotlinx.coroutines.flow.Flow

/** Persistent queue of web-route messages waiting to be uploaded (newest 50, at most 7 days). */
class WebDeliveryRepository(
    private val database: ForwardDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    /** Stores a matched message in the queue. Returns its key, or null if it was already recorded. */
    suspend fun recordIfNew(message: IncomingMessage, appSourcePhoneNumber: String = "", appSourceName: String = ""): String? {
        val key = IdempotencyKey.create(message.sender, message.receivedAtMillis, message.body)
        return database.withTransaction {
            if (database.webLogDao().insert(logRow(key, message, WebStatus.QUEUED)) == -1L) return@withTransaction null
            database.webDeliveryDao().insert(WebDeliveryEntity(
                key, message.sender, message.receivedAtMillis, message.body, nowMillis(), "",
                appSourcePhoneNumber.trim(), appSourceName.trim()
            ))
            enforceLimitsInTransaction()
            key
        }
    }

    private fun logRow(key: String, message: IncomingMessage, status: WebStatus) = WebLogEntity(
        key, message.receivedAtMillis, message.sender, message.body.take(100), status, nowMillis()
    )

    /** The next message to upload (oldest first), or null when the queue is empty. */
    suspend fun nextQueued(): WebDeliveryEntity? = database.webDeliveryDao().oldest()

    suspend fun hasQueued(): Boolean = database.webDeliveryDao().count() > 0

    fun observeQueue(): Flow<List<QueuedMessage>> = database.webDeliveryDao().observeQueue()

    suspend fun markUploading(key: String) {
        database.webLogDao().update(key, WebStatus.UPLOADING, nowMillis())
    }

    /** Puts an interrupted attempt back to waiting; the body stays queued. */
    suspend fun requeue(key: String) = database.withTransaction {
        // The row may have been deleted meanwhile (switched Off, expired); never resurrect its log.
        if (database.webDeliveryDao().find(key) != null) {
            database.webLogDao().update(key, WebStatus.QUEUED, nowMillis())
        }
    }

    /** Ends a message after the server answered: removes the body and records the outcome. */
    suspend fun finish(key: String, status: WebStatus) {
        require(status == WebStatus.COMPLETED || status == WebStatus.FAILED)
        database.withTransaction {
            database.webDeliveryDao().delete(key)
            database.webLogDao().update(key, status, nowMillis())
        }
    }

    /** Deletes the whole queue (Web archive switched Off). */
    suspend fun discardAll(): List<String> = database.withTransaction {
        val keys = database.webDeliveryDao().allKeys()
        database.webDeliveryDao().deleteAll()
        keys.forEach { database.webLogDao().update(it, WebStatus.SKIPPED, nowMillis()) }
        keys
    }

    /** Drops queued messages older than 7 days or beyond the newest 50. */
    suspend fun enforceLimits() = database.withTransaction { enforceLimitsInTransaction() }

    private suspend fun enforceLimitsInTransaction() {
        val rows = database.webDeliveryDao().queueRows().map { QueueEntry(it.dedupeKey, it.createdAt) }
        WebQueuePolicy.evictions(rows, nowMillis()).forEach { key ->
            database.webDeliveryDao().delete(key)
            database.webLogDao().update(key, WebStatus.SKIPPED, nowMillis())
        }
    }

    /**
     * Startup: attempts interrupted by a process death return to Queued (the queue itself is kept),
     * then limits are applied. Returns keys of obsolete V1.1 archive work to cancel.
     */
    suspend fun cleanupAtStartup(): List<String> = database.withTransaction {
        val legacy = database.legacyArchiveWorkDao().allKeys()
        database.webLogDao().updateAll(WebStatus.UPLOADING, WebStatus.QUEUED, nowMillis())
        database.legacyArchiveWorkDao().deleteAll()
        enforceLimitsInTransaction()
        legacy
    }
}
