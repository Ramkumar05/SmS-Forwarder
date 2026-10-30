package com.autosmsforwarder.ui

import com.autosmsforwarder.data.ForwardLogEntity
import com.autosmsforwarder.data.WebLogEntity
import com.autosmsforwarder.data.WebStatus
import com.autosmsforwarder.domain.ForwardStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageActivityRowTest {
    @Test fun combinesBothRoutesAndIncludesWebOnlyInTimeOrder() {
        val forward = listOf(
            ForwardLogEntity(1, "both", 20, "sender", "both body", ForwardStatus.SENT, 1, 20),
            ForwardLogEntity(2, "sms", 10, "sender", "sms body", ForwardStatus.PENDING, 0, 10)
        )
        val web = listOf(
            WebLogEntity("web", 30, "sender", "web body", WebStatus.COMPLETED, 30),
            WebLogEntity("both", 20, "sender", "both body", WebStatus.FAILED, 20)
        )

        val rows = mergeMessageActivity(forward, web)

        assertEquals(listOf("web", "both", "sms"), rows.map { it.dedupeKey })
        assertNull(rows[0].smsStatus)
        assertEquals(WebStatus.COMPLETED, rows[0].webStatus)
        assertEquals(ForwardStatus.SENT, rows[1].smsStatus)
        assertEquals(WebStatus.FAILED, rows[1].webStatus)
        assertNull(rows[2].webStatus)
    }
}
