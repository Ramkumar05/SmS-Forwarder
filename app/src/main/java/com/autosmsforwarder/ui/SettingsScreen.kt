package com.autosmsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autosmsforwarder.settings.SendDelay

@Composable
fun SettingsScreen(onBack: () -> Unit = {}, settingsViewModel: SettingsViewModel = viewModel()) {
    val state by settingsViewModel.state.collectAsState()
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text("Forwarding settings", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = state.targetNumber,
                    onValueChange = settingsViewModel::setTargetNumber,
                    label = { Text("Target phone number") },
                    supportingText = { Text(state.error ?: "10–15 digits; a 10-digit number uses +91") },
                    isError = state.error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Send delay", style = MaterialTheme.typography.titleMedium)
                SendDelay.entries.forEach { delay ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = state.sendDelay == delay,
                            onClick = { settingsViewModel.setSendDelay(delay) },
                            modifier = Modifier.semantics { contentDescription = delay.label }
                        )
                        Text(delay.label, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
                    }
                }
                Text("Cloud archive", style = MaterialTheme.typography.titleMedium)
                Text("After an SMS is sent, save its full text to your private web archive. Leave the token blank to turn this off.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = state.archiveWriteToken,
                    onValueChange = settingsViewModel::setArchiveWriteToken,
                    label = { Text("Archive write token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Uploads waiting: ${state.pendingUploads}", style = MaterialTheme.typography.bodySmall)
                Button(onClick = settingsViewModel::save, modifier = Modifier.fillMaxWidth()) {
                    Text("Save settings")
                }
                if (state.saved) Text("Settings saved")
                TextButton(onClick = settingsViewModel::clearLog) { Text("Clear log and waiting uploads") }
                Text("Clear log removes local data only. Messages already in the web archive remain there.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
