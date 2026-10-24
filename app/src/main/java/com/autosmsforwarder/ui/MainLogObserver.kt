package com.autosmsforwarder.ui

import com.autosmsforwarder.domain.TimeWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

class MainLogObserver<T>(
    private val recent: (Long) -> Flow<List<T>>,
    private val clock: Flow<Long>
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(window: Flow<TimeWindow>): Flow<List<T>> =
        combine(window, clock) { selected, now -> now - selected.durationMillis }
            .flatMapLatest(recent)
}
