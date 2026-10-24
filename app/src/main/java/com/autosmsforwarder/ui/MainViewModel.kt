package com.autosmsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardLogEntity
import com.autosmsforwarder.domain.TimeWindow
import com.autosmsforwarder.health.HealthChecker
import com.autosmsforwarder.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

data class MainUiState(
    val targetNumber: String = "",
    val forwardingEnabled: Boolean = true,
    val statusLine: String = "Checking phone status…",
    val permissionsNeeded: Boolean = false,
    val batteryPromptNeeded: Boolean = false,
    val window: TimeWindow = TimeWindow.FIVE_MINUTES,
    val rows: List<ForwardLogEntity> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val healthChecker = HealthChecker(application)
    private val database = ForwardDatabase.get(application)
    private val selectedWindow = MutableStateFlow(TimeWindow.FIVE_MINUTES)
    private val mutableState = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        refreshPhoneStatus()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableState.value = mutableState.value.copy(
                    targetNumber = settings.targetNumber,
                    forwardingEnabled = settings.forwardingEnabled
                )
            }
        }
        viewModelScope.launch {
            MainLogObserver(database.logDao()::recent, flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(15_000L)
                }
            }).observe(selectedWindow).collect { rows ->
                mutableState.value = mutableState.value.copy(rows = rows)
            }
        }
    }

    fun selectWindow(window: TimeWindow) {
        selectedWindow.value = window
        mutableState.value = mutableState.value.copy(window = window)
    }

    fun setForwardingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setForwardingEnabled(enabled) }
    }

    fun refreshPhoneStatus() {
        val health = healthChecker.check()
        mutableState.value = mutableState.value.copy(
            statusLine = health.statusLine,
            permissionsNeeded = health.permissionsNeeded,
            batteryPromptNeeded = health.batteryPromptNeeded
        )
    }
}
