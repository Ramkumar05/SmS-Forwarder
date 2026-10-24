package com.autosmsforwarder.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository

class RetentionWorker(
    context: Context,
    parameters: WorkerParameters,
    private val repository: ForwardRepository
) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(
        context, parameters, ForwardRepository(ForwardDatabase.get(context))
    )

    override suspend fun doWork(): Result {
        repository.purgeExpired(System.currentTimeMillis())
        return Result.success()
    }
}
