package com.autosmsforwarder.worker

import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import com.autosmsforwarder.settings.SendDelay
import java.util.concurrent.TimeUnit

object ForwardWorkRequestFactory {
    fun create(dedupeKey: String, delay: SendDelay): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(Data.Builder().putString(ForwardWorker.KEY_DEDUPE, dedupeKey).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        if (delay == SendDelay.INSTANT) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        } else {
            builder.setInitialDelay(delay.seconds, TimeUnit.SECONDS)
        }
        return builder.build()
    }
}
