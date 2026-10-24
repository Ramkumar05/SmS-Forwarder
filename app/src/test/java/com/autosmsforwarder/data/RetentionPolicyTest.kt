package com.autosmsforwarder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionPolicyTest {
    @Test fun cutoffsUseSevenDaysForLogAndTwentyFourHoursForPending() {
        val now = 1_800_000_000_000L
        assertEquals(now - 7L * 24 * 60 * 60 * 1000, RetentionPolicy.logCutoff(now))
        assertEquals(now - 24L * 60 * 60 * 1000, RetentionPolicy.pendingCutoff(now))
    }
}
