package com.autosmsforwarder.ui

import com.autosmsforwarder.domain.TimeWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainLogObserverTest {
    @Test fun rowLeavesFiveMinuteWindowWhenClockTicksWithoutDatabaseChange() = runTest {
        val receivedAt = 1_000_000L
        val clock = MutableStateFlow(receivedAt + 299_999L)
        val window = MutableStateFlow(TimeWindow.FIVE_MINUTES)
        val observer = MainLogObserver(
            recent = { cutoff -> flowOf(if (receivedAt >= cutoff) listOf("row") else emptyList()) },
            clock = clock
        )
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.observe(window).take(2).toList(emissions)
        }

        assertEquals(listOf(listOf("row")), emissions)
        clock.value = receivedAt + 300_001L
        runCurrent()
        assertEquals(listOf(listOf("row"), emptyList<String>()), emissions)
    }

    @Test fun changingWindowRequeriesWithNewCutoff() = runTest {
        val now = 1_000_000L
        val window = MutableStateFlow(TimeWindow.FIVE_MINUTES)
        val cutoffs = mutableListOf<Long>()
        val observer = MainLogObserver<String>(
            recent = { cutoff -> cutoffs += cutoff; flowOf(emptyList()) },
            clock = flowOf(now)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.observe(window).collect { }
        }
        window.value = TimeWindow.ONE_HOUR
        runCurrent()
        assertEquals(listOf(now - 300_000L, now - 3_600_000L), cutoffs)
    }
}
