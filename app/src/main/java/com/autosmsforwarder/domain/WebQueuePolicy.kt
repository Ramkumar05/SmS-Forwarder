package com.autosmsforwarder.domain

data class QueueEntry(val key: String, val createdAt: Long)

/** Limits for the persistent web upload queue: newest 50 messages, at most 7 days old. */
object WebQueuePolicy {
    const val MAX_ENTRIES = 50
    const val MAX_AGE_MILLIS = 7 * 24 * 60 * 60 * 1000L

    /** Keys to drop: expired entries first, then the oldest entries beyond [MAX_ENTRIES]. */
    fun evictions(entries: List<QueueEntry>, nowMillis: Long): List<String> {
        val (fresh, expired) = entries.partition { nowMillis - it.createdAt < MAX_AGE_MILLIS }
        val overflow = fresh
            .sortedWith(compareByDescending<QueueEntry> { it.createdAt }.thenByDescending { it.key })
            .drop(MAX_ENTRIES)
        return (expired + overflow).map { it.key }
    }
}
