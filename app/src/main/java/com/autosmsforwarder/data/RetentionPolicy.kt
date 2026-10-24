package com.autosmsforwarder.data

import java.util.concurrent.TimeUnit

object RetentionPolicy {
    fun logCutoff(nowMillis: Long): Long = nowMillis - TimeUnit.DAYS.toMillis(7)
    fun pendingCutoff(nowMillis: Long): Long = nowMillis - TimeUnit.DAYS.toMillis(1)
}
