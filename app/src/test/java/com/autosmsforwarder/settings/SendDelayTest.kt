package com.autosmsforwarder.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SendDelayTest {
    @Test fun supportsOnlyTheThreeSpecifiedDelays() {
        assertEquals(listOf(0L, 15L, 30L), SendDelay.entries.map { it.seconds })
        assertEquals(SendDelay.INSTANT, SendDelay.fromSeconds(999))
    }
}
