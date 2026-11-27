package com.autosmsforwarder.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WebQueuePolicyTest {
    private val now = 100L * WebQueuePolicy.MAX_AGE_MILLIS

    @Test fun keepsFreshEntriesUnderTheCap() {
        val entries = (1..10).map { QueueEntry("k$it", now - it) }
        assertEquals(emptyList<String>(), WebQueuePolicy.evictions(entries, now))
    }

    @Test fun dropsEntriesAtOrOverSevenDays() {
        val entries = listOf(
            QueueEntry("fresh", now - WebQueuePolicy.MAX_AGE_MILLIS + 1),
            QueueEntry("edge", now - WebQueuePolicy.MAX_AGE_MILLIS),
            QueueEntry("old", now - WebQueuePolicy.MAX_AGE_MILLIS - 5)
        )
        assertEquals(setOf("edge", "old"), WebQueuePolicy.evictions(entries, now).toSet())
    }

    @Test fun keepsOnlyTheNewestFifty() {
        val entries = (1..55).map { QueueEntry("k$it", now - 1000 + it) }
        assertEquals((1..5).map { "k$it" }.toSet(), WebQueuePolicy.evictions(entries, now).toSet())
    }

    @Test fun expiredEntriesDoNotCountTowardTheCap() {
        val fresh = (1..50).map { QueueEntry("f$it", now - it) }
        val expired = listOf(QueueEntry("old", now - WebQueuePolicy.MAX_AGE_MILLIS - 1))
        assertEquals(listOf("old"), WebQueuePolicy.evictions(fresh + expired, now))
    }

    @Test fun tiesBreakByKeyDeterministically() {
        val entries = (1..51).map { QueueEntry("k%02d".format(it), now) }
        assertEquals(listOf("k01"), WebQueuePolicy.evictions(entries, now))
    }
}
