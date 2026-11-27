package com.autosmsforwarder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autosmsforwarder.data.QueuedMessage
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun QueueScreen(queueViewModel: QueueViewModel = viewModel()) {
    val messages by queueViewModel.messages.collectAsState()
    QueueContent(messages)
}

@Composable
fun QueueContent(messages: List<QueuedMessage>) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                Text("Queue", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Web archive uploads waiting to be sent, oldest first. They send automatically when the network is available.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (messages.isEmpty()) {
                    Text("No messages waiting", modifier = Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(messages, key = { it.dedupeKey }) { message ->
                            QueueRow(message)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(message: QueuedMessage) {
    val time = SimpleDateFormat("dd MMM HH:mm", LocalConfiguration.current.locales[0]).format(Date(message.smsTimestamp))
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row {
            Text(time, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.labelMedium)
            Text(
                message.sender, modifier = Modifier.weight(0.6f), maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium
            )
        }
        Text(message.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}
