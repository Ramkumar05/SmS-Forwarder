package com.autosmsforwarder.settings

enum class SendDelay(val seconds: Long, val label: String) {
    INSTANT(0, "Instant"),
    FIFTEEN_SECONDS(15, "15 seconds"),
    THIRTY_SECONDS(30, "30 seconds");

    companion object {
        fun fromSeconds(seconds: Long): SendDelay = entries.firstOrNull { it.seconds == seconds } ?: INSTANT
    }
}
