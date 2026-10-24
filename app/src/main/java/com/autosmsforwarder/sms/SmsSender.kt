package com.autosmsforwarder.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface SmsSender {
    suspend fun send(targetNumber: String, text: String): Boolean
}

internal object SentResultRegistry {
    private class PendingResult(val expectedParts: Int) {
        val outcome = CompletableDeferred<Boolean>()
        private var receivedParts = 0

        @Synchronized fun report(succeeded: Boolean) {
            if (outcome.isCompleted) return
            if (!succeeded) {
                outcome.complete(false)
            } else if (++receivedParts == expectedParts) {
                outcome.complete(true)
            }
        }
    }

    private val pending = ConcurrentHashMap<String, PendingResult>()

    fun register(token: String, partCount: Int): CompletableDeferred<Boolean> =
        PendingResult(partCount).also { pending[token] = it }.outcome

    fun report(token: String, succeeded: Boolean) {
        pending[token]?.report(succeeded)
    }

    fun remove(token: String) {
        pending.remove(token)
    }
}

class SentResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        SentResultRegistry.report(token, resultCode == Activity.RESULT_OK)
    }

    companion object {
        const val EXTRA_TOKEN = "sent_result_token"
    }
}

class AndroidSmsSender(private val context: Context) : SmsSender {
    override suspend fun send(targetNumber: String, text: String): Boolean {
        val manager = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return false
        val parts = manager.divideMessage(text)
        if (parts.isEmpty()) return false

        val token = UUID.randomUUID().toString()
        val result = SentResultRegistry.register(token, parts.size)
        try {
            val sentIntents = ArrayList(parts.indices.map { index ->
                val intent = Intent(context, SentResultReceiver::class.java).apply {
                    data = Uri.parse("autosmsforwarder://sent/$token/$index")
                    putExtra(SentResultReceiver.EXTRA_TOKEN, token)
                }
                PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            })
            if (parts.size == 1) {
                manager.sendTextMessage(targetNumber, null, parts.single(), sentIntents.single(), null)
            } else {
                manager.sendMultipartTextMessage(targetNumber, null, parts, sentIntents, null)
            }
            return withTimeoutOrNull(120_000L) { result.await() } ?: false
        } finally {
            SentResultRegistry.remove(token)
        }
    }
}
