package com.autosmsforwarder.data

import androidx.room.withTransaction
import com.autosmsforwarder.domain.IdempotencyKey
import com.autosmsforwarder.domain.IncomingMessage
import java.util.UUID

object WebDeliverySession {
    val id: String = UUID.randomUUID().toString()
}

data class WebCleanupKeys(val web: List<String>, val legacy: List<String>)

class WebDeliveryRepository(
    private val database: ForwardDatabase,
    private val sessionId: String = WebDeliverySession.id,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun recordIfNew(message: IncomingMessage, appSourcePhoneNumber: String = "", appSourceName: String = ""): String? {
        val key = IdempotencyKey.create(message.sender, message.receivedAtMillis, message.body)
        return database.withTransaction {
            if (database.webLogDao().insert(logRow(key, message, WebStatus.QUEUED)) == -1L) return@withTransaction null
            database.webDeliveryDao().insert(WebDeliveryEntity(
                key, message.sender, message.receivedAtMillis, message.body, nowMillis(), sessionId,
                appSourcePhoneNumber.trim(), appSourceName.trim()
            ))
            key
        }
    }

    suspend fun recordSkipped(message: IncomingMessage) {
        val key = IdempotencyKey.create(message.sender, message.receivedAtMillis, message.body)
        database.webLogDao().insert(logRow(key, message, WebStatus.SKIPPED))
    }

    private fun logRow(key: String, message: IncomingMessage, status: WebStatus) = WebLogEntity(
        key, message.receivedAtMillis, message.sender, message.body.take(100), status, nowMillis()
    )

    suspend fun claim(key: String): WebDeliveryEntity? = database.withTransaction {
        val row = database.webDeliveryDao().find(key) ?: return@withTransaction null
        database.webDeliveryDao().delete(key)
        if (row.sessionId != sessionId || nowMillis() - row.createdAt !in 0L until MAX_AGE_MILLIS) {
            database.webLogDao().update(key, WebStatus.SKIPPED, nowMillis())
            return@withTransaction null
        }
        database.webLogDao().update(key, WebStatus.UPLOADING, nowMillis())
        row
    }

    suspend fun recordOutcome(key: String, status: WebStatus) {
        require(status == WebStatus.COMPLETED || status == WebStatus.FAILED || status == WebStatus.UNCONFIRMED)
        database.webLogDao().update(key, status, nowMillis())
    }

    suspend fun discard(key: String) = database.withTransaction {
        if (database.webDeliveryDao().find(key) != null) {
            database.webDeliveryDao().delete(key)
            database.webLogDao().update(key, WebStatus.SKIPPED, nowMillis())
        }
    }

    suspend fun discardIfExpired(key: String) = database.withTransaction {
        val row = database.webDeliveryDao().find(key) ?: return@withTransaction
        if (nowMillis() - row.createdAt >= MAX_AGE_MILLIS) {
            database.webDeliveryDao().delete(key)
            database.webLogDao().update(key, WebStatus.SKIPPED, nowMillis())
        }
    }

    suspend fun discardAll(): List<String> = database.withTransaction {
        val keys = database.webDeliveryDao().allKeys()
        database.webDeliveryDao().deleteAll()
        keys.forEach { database.webLogDao().update(it, WebStatus.SKIPPED, nowMillis()) }
        keys
    }

    suspend fun cleanupAtStartup(): WebCleanupKeys = database.withTransaction {
        val keys = WebCleanupKeys(
            database.webDeliveryDao().otherSessionKeys(sessionId),
            database.legacyArchiveWorkDao().allKeys()
        )
        database.webDeliveryDao().deleteOtherSessions(sessionId)
        keys.web.forEach { database.webLogDao().update(it, WebStatus.SKIPPED, nowMillis()) }
        database.webLogDao().updateAll(WebStatus.UPLOADING, WebStatus.UNCONFIRMED, nowMillis())
        database.legacyArchiveWorkDao().deleteAll()
        keys
    }

    companion object {
        const val MAX_AGE_MILLIS = 5 * 60 * 1000L
    }
}
