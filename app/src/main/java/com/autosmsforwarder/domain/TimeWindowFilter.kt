package com.autosmsforwarder.domain

enum class TimeWindow(val durationMillis: Long) {
    FIVE_MINUTES(5 * 60_000L),
    ONE_HOUR(60 * 60_000L),
    TWENTY_FOUR_HOURS(24 * 60 * 60_000L)
}

object TimeWindowFilter {
    fun includes(receivedAtMillis: Long, nowMillis: Long, window: TimeWindow): Boolean =
        receivedAtMillis >= nowMillis - window.durationMillis
}
