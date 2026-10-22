package com.autosmsforwarder.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWindowFilterTest {
    private val now = 1_000_000_000L

    @Test
    fun fiveMinuteBoundaryIsInclusive() {
        assertTrue(TimeWindowFilter.includes(now - 300_000L, now, TimeWindow.FIVE_MINUTES))
        assertFalse(TimeWindowFilter.includes(now - 300_001L, now, TimeWindow.FIVE_MINUTES))
    }

    @Test
    fun hourAndDayWindowsUseTheirOwnCutoffs() {
        assertTrue(TimeWindowFilter.includes(now - 3_600_000L, now, TimeWindow.ONE_HOUR))
        assertFalse(TimeWindowFilter.includes(now - 3_600_001L, now, TimeWindow.ONE_HOUR))
        assertTrue(TimeWindowFilter.includes(now - 86_400_000L, now, TimeWindow.TWENTY_FOUR_HOURS))
        assertFalse(TimeWindowFilter.includes(now - 86_400_001L, now, TimeWindow.TWENTY_FOUR_HOURS))
    }

    @Test
    fun recentRowRemainsVisibleAsClockMovesUntilCutoff() {
        assertTrue(TimeWindowFilter.includes(now, now + 299_999L, TimeWindow.FIVE_MINUTES))
        assertFalse(TimeWindowFilter.includes(now, now + 300_001L, TimeWindow.FIVE_MINUTES))
    }
}
