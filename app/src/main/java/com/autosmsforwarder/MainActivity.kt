package com.autosmsforwarder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autosmsforwarder.ui.MainScreen
import com.autosmsforwarder.ui.OnboardingScreen
import com.autosmsforwarder.ui.OnboardingState
import com.autosmsforwarder.ui.OnboardingViewModel
import com.autosmsforwarder.ui.QueueScreen
import com.autosmsforwarder.ui.SettingsScreen

private enum class AppTab(val label: String) { HOME("Home"), QUEUE("Queue"), SETTINGS("Settings") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent {
            val onboardingViewModel: OnboardingViewModel = viewModel()
            val onboardingState by onboardingViewModel.state.collectAsState()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { onboardingViewModel.finishAfterPermissionRequest() }
            var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
            BackHandler(tab != AppTab.HOME) { tab = AppTab.HOME }
            when (onboardingState) {
                OnboardingState.LOADING -> Unit
                OnboardingState.REQUIRED -> OnboardingScreen(
                    onRequestPermissions = {
                        val permissions = mutableListOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.SEND_SMS
                        )
                        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
                        permissionLauncher.launch(permissions.toTypedArray())
                    }
                )
                OnboardingState.COMPLETE -> MaterialTheme {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            NavigationBar {
                                AppTab.entries.forEach { entry ->
                                    NavigationBarItem(
                                        selected = tab == entry,
                                        onClick = { tab = entry },
                                        icon = {},
                                        label = { Text(entry.label) },
                                        alwaysShowLabel = true
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            when (tab) {
                                AppTab.HOME -> MainScreen(
                                    onBatteryExemptionClick = {
                                        startActivity(
                                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:$packageName")
                                            }
                                        )
                                    },
                                    onAppSettingsClick = {
                                        startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:$packageName")
                                            }
                                        )
                                    }
                                )
                                AppTab.QUEUE -> QueueScreen()
                                AppTab.SETTINGS -> SettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
