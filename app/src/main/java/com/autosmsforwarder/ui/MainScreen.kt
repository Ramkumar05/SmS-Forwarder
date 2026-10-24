package com.autosmsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autosmsforwarder.data.ForwardLogEntity
import com.autosmsforwarder.domain.TimeWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    onSettingsClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onBatteryExemptionClick: () -> Unit,
    mainViewModel: MainViewModel = viewModel()
) {
    val state by mainViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) mainViewModel.refreshPhoneStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                Text("Auto SMS Forwarder", style = MaterialTheme.typography.headlineSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Forward to", style = MaterialTheme.typography.labelMedium)
                        Text(state.targetNumber.ifBlank { "Not configured" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(checked = state.forwardingEnabled, onCheckedChange = mainViewModel::setForwardingEnabled)
                }
                Text(state.statusLine, style = MaterialTheme.typography.bodySmall)
                if (state.batteryPromptNeeded) {
                    TextButton(onClick = onBatteryExemptionClick) { Text("Allow battery exemption") }
                }
                if (state.permissionsNeeded) {
                    TextButton(onClick = onAppSettingsClick) { Text("Open app settings") }
                    Text(
                        "If Android shows restricted settings, allow them in this app’s settings menu, then grant SMS access.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = onSettingsClick) { Text("Settings") }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        TimeWindow.FIVE_MINUTES to "5 min",
                        TimeWindow.ONE_HOUR to "1 hour",
                        TimeWindow.TWENTY_FOUR_HOURS to "24 h"
                    ).forEach { (window, label) ->
                        FilterChip(
                            selected = state.window == window,
                            onClick = { mainViewModel.selectWindow(window) },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                LogTableHeader()
                HorizontalDivider()
                if (state.rows.isEmpty()) {
                    Text(
                        "No forwarded messages in this time window",
                        modifier = Modifier.padding(top = 24.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.rows, key = { it.id }) { row ->
                            LogTableRow(row)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogTableHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Time", modifier = Modifier.weight(0.17f), style = MaterialTheme.typography.labelSmall)
        Text("From", modifier = Modifier.weight(0.22f).padding(end = 4.dp), style = MaterialTheme.typography.labelSmall)
        Text("Message", modifier = Modifier.weight(0.39f).padding(end = 4.dp), style = MaterialTheme.typography.labelSmall)
        Text("Status", modifier = Modifier.weight(0.22f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LogTableRow(row: ForwardLogEntity) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(row.receivedAt))
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(time, modifier = Modifier.weight(0.17f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        Text(row.sender, modifier = Modifier.weight(0.22f).padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        Text(row.snippet, modifier = Modifier.weight(0.39f).padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        Text(row.status.name, modifier = Modifier.weight(0.22f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}
