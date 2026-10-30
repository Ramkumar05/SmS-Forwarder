package com.autosmsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.settings.SendDelay

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onForwardingEnabled: (Boolean) -> Unit = {},
    val onTargetNumber: (String) -> Unit = {},
    val onSendDelay: (SendDelay) -> Unit = {},
    val onForwardMatchMode: (MatchMode) -> Unit = {},
    val onForwardKeywords: (String) -> Unit = {},
    val onWebEnabled: (Boolean) -> Unit = {},
    val onWebMatchMode: (MatchMode) -> Unit = {},
    val onWebKeywords: (String) -> Unit = {},
    val onArchiveWriteToken: (String) -> Unit = {},
    val onAppSourcePhoneNumber: (String) -> Unit = {},
    val onAppSourceName: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onClearLog: () -> Unit = {}
)

@Composable
fun SettingsScreen(onBack: () -> Unit = {}, settingsViewModel: SettingsViewModel = viewModel()) {
    val state by settingsViewModel.state.collectAsState()
    SettingsContent(
        state,
        SettingsActions(
            onBack = onBack,
            onForwardingEnabled = settingsViewModel::setForwardingEnabled,
            onTargetNumber = settingsViewModel::setTargetNumber,
            onSendDelay = settingsViewModel::setSendDelay,
            onForwardMatchMode = settingsViewModel::setForwardMatchMode,
            onForwardKeywords = settingsViewModel::setForwardKeywords,
            onWebEnabled = settingsViewModel::setWebEnabled,
            onWebMatchMode = settingsViewModel::setWebMatchMode,
            onWebKeywords = settingsViewModel::setWebKeywords,
            onArchiveWriteToken = settingsViewModel::setArchiveWriteToken,
            onAppSourcePhoneNumber = settingsViewModel::setAppSourcePhoneNumber,
            onAppSourceName = settingsViewModel::setAppSourceName,
            onSave = settingsViewModel::save,
            onClearLog = settingsViewModel::clearLog
        )
    )
}

@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions) {
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.saved, state.saveError, state.errors) {
        when {
            state.saved -> snackbarHostState.showSnackbar("Settings saved")
            state.saveError != null -> snackbarHostState.showSnackbar(state.saveError)
            !state.errors.isValid -> snackbarHostState.showSnackbar("Please fix the highlighted settings")
        }
    }
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextButton(onClick = actions.onBack) { Text("Back") }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                if (!state.loaded) {
                    Text("Loading settings…")
                    return@Column
                }

                RouteSwitch("SMS forwarding", state.forwardingEnabled, actions.onForwardingEnabled)
                OutlinedTextField(
                    value = state.targetNumber,
                    onValueChange = actions.onTargetNumber,
                    label = { Text("Forward to") },
                    supportingText = { Text(state.errors.targetNumber ?: "10–15 digits; a 10-digit number uses +91") },
                    isError = state.errors.targetNumber != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MatchModeSelector(state.forwardMatchMode, actions.onForwardMatchMode)
                if (state.forwardMatchMode == MatchMode.KEYWORDS) {
                    KeywordField("Forwarding keywords", state.forwardKeywords, state.errors.forwardKeywords, actions.onForwardKeywords)
                }
                Text("Send delay", style = MaterialTheme.typography.titleMedium)
                SendDelay.entries.forEach { delay ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = state.sendDelay == delay,
                            onClick = { actions.onSendDelay(delay) },
                            modifier = Modifier.semantics { contentDescription = delay.label }
                        )
                        Text(delay.label, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
                    }
                }

                HorizontalDivider()
                RouteSwitch("Web archive", state.webEnabled, actions.onWebEnabled)
                MatchModeSelector(state.webMatchMode, actions.onWebMatchMode)
                if (state.webMatchMode == MatchMode.KEYWORDS) {
                    KeywordField("Web archive keywords", state.webKeywords, state.errors.webKeywords, actions.onWebKeywords)
                }
                OutlinedTextField(
                    value = state.archiveWriteToken,
                    onValueChange = actions.onArchiveWriteToken,
                    label = { Text("Archive write token") },
                    supportingText = { Text(state.errors.archiveWriteToken ?: "The WRITE_TOKEN for your private Cloudflare archive") },
                    isError = state.errors.archiveWriteToken != null,
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { tokenVisible = !tokenVisible }) {
                            Text(if (tokenVisible) "Hide" else "Show")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Web delivery makes one attempt. Offline or failed requests are dropped.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = state.appSourcePhoneNumber,
                    onValueChange = actions.onAppSourcePhoneNumber,
                    label = { Text("App source phone number (optional)") },
                    supportingText = { Text(state.errors.appSourcePhoneNumber ?: "This phone’s number, including country code; may be blank") },
                    isError = state.errors.appSourcePhoneNumber != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.appSourceName,
                    onValueChange = actions.onAppSourceName,
                    label = { Text("App source name (optional)") },
                    supportingText = { Text(state.errors.appSourceName ?: "For example Home phone; up to 100 characters; may be blank") },
                    isError = state.errors.appSourceName != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(onClick = actions.onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.saving) "Saving…" else "Save settings")
                }
                state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = actions.onClearLog) { Text("Clear local log and pending data") }
                Text("This removes local data only. Messages already in the web archive remain there.", style = MaterialTheme.typography.bodySmall)
            }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun RouteSwitch(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun MatchModeSelector(selected: MatchMode, onChange: (MatchMode) -> Unit) {
    Text("Which messages?", style = MaterialTheme.typography.titleSmall)
    MatchMode.entries.forEach { mode ->
        Row(modifier = Modifier.fillMaxWidth()) {
            RadioButton(
                selected = selected == mode,
                onClick = { onChange(mode) },
                modifier = Modifier.semantics { contentDescription = mode.label }
            )
            Text(mode.label, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
        }
    }
}

@Composable
private fun KeywordField(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(error ?: "Separate keywords with commas; any one match is enough") },
        isError = error != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
