package com.autosmsforwarder

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Configuration
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.autosmsforwarder.worker.RetentionWorker
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.WebDeliveryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        val workManager = WorkManager.getInstance(this)
        workManager.cancelUniqueWork("archive-sweep")
        workManager.cancelUniqueWork("archive-sweep-now")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val cleanup = WebDeliveryRepository(ForwardDatabase.get(this@ForwardApplication)).cleanupAtStartup()
            cleanup.web.forEach { key ->
                workManager.cancelUniqueWork("web-$key").result.get()
                workManager.cancelUniqueWork("web-expire-$key").result.get()
            }
            cleanup.legacy.forEach { key -> workManager.cancelUniqueWork("archive-$key").result.get() }
        }
    }
}
