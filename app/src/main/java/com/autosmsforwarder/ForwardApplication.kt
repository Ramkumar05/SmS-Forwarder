package com.autosmsforwarder

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Configuration
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.autosmsforwarder.worker.RetentionWorker
import com.autosmsforwarder.worker.ArchiveScheduler
import com.autosmsforwarder.worker.ArchiveSweepWorker
import java.util.concurrent.TimeUnit

class ForwardApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "forward-retention",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build()
        )
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "archive-sweep",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ArchiveSweepWorker>(15, TimeUnit.MINUTES).build()
        )
        ArchiveScheduler.sweepNow(this)
    }
}
