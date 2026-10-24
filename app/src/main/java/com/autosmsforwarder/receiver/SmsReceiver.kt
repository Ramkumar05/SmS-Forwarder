package com.autosmsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.domain.MultipartStitcher
import com.autosmsforwarder.domain.SmsPart
import com.autosmsforwarder.settings.SettingsRepository
import com.autosmsforwarder.worker.ForwardWorkRequestFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                val settings = SettingsRepository(appContext).settings.first()
                if (!settings.forwardingEnabled || settings.targetNumber.isBlank()) return@launch
                val repository = ForwardRepository(ForwardDatabase.get(appContext))
                messages.forEach { message ->
                    val recorded = repository.recordIfNew(message, settings.targetNumber) ?: return@forEach
                    if (!recorded.shouldEnqueue) return@forEach
                    val request = ForwardWorkRequestFactory.create(recorded.dedupeKey, settings.sendDelay)
                    WorkManager.getInstance(appContext).enqueueUniqueWork(
                        "fwd-${recorded.dedupeKey}",
                        ExistingWorkPolicy.KEEP,
                        request
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
