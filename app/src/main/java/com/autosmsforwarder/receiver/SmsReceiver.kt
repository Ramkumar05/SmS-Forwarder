package com.autosmsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.domain.MultipartStitcher
import com.autosmsforwarder.domain.SmsPart
import com.autosmsforwarder.settings.ForwardRouteLock
import com.autosmsforwarder.settings.SettingsRepository
import com.autosmsforwarder.worker.ForwardWorkRequestFactory
import com.autosmsforwarder.worker.AndroidWebConnectivityChecker
import com.autosmsforwarder.worker.WebDeliveryScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            .map { SmsPart(it.originatingAddress.orEmpty(), it.messageBody.orEmpty(), it.timestampMillis) }
        val messages = MultipartStitcher.stitch(parts)
        if (messages.isEmpty()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ForwardRouteLock.mutex.withLock {
                    val settings = SettingsRepository(appContext).settings.first()
                    if (!settings.forwardingEnabled && !settings.webEnabled) return@withLock
                    val config = settings.routeConfiguration()
                    val processor = ForwardRouteProcessor(ForwardRepository(ForwardDatabase.get(appContext)))
                    val webProcessor = WebRouteProcessor(
                        WebDeliveryRepository(ForwardDatabase.get(appContext)),
                        AndroidWebConnectivityChecker(appContext)
                    )
                    messages.forEach { message ->
                        try {
                            webProcessor.recordIfMatched(message, config)?.let { key ->
                                WebDeliveryScheduler.enqueue(appContext, key)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // A web-route failure cannot prevent an eligible SMS forward.
                        }
                        try {
                            val recorded = processor.recordIfMatched(message, config) ?: return@forEach
                            if (!recorded.shouldEnqueue) return@forEach
                            val request = ForwardWorkRequestFactory.create(recorded.dedupeKey, settings.sendDelay)
                            WorkManager.getInstance(appContext).enqueueUniqueWork(
                                "fwd-${recorded.dedupeKey}", ExistingWorkPolicy.KEEP, request
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // An SMS-route failure cannot retract the independent web attempt.
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
