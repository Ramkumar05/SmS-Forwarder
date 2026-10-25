package com.autosmsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.settings.SendDelay
import com.autosmsforwarder.settings.SettingsRepository
import com.autosmsforwarder.settings.TargetNumberValidator
import com.autosmsforwarder.worker.ArchiveScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val targetNumber: String = "",
    val sendDelay: SendDelay = SendDelay.INSTANT,
    val archiveWriteToken: String = "",
    val pendingUploads: Int = 0,
    val error: String? = null,
    val saved: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val forwardRepository = ForwardRepository(ForwardDatabase.get(application))
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                mutableState.value = mutableState.value.copy(
                    targetNumber = settings.targetNumber,
                    sendDelay = settings.sendDelay,
                    archiveWriteToken = settings.archiveWriteToken
                )
            }
        }
        viewModelScope.launch {
            ForwardDatabase.get(application).uploadDao().pendingCount().collect { count ->
                mutableState.value = mutableState.value.copy(pendingUploads = count)
            }
        }
    }

    fun setTargetNumber(value: String) {
        mutableState.value = mutableState.value.copy(targetNumber = value, error = null, saved = false)
    }

    fun setSendDelay(value: SendDelay) {
        mutableState.value = mutableState.value.copy(sendDelay = value, saved = false)
    }

    fun setArchiveWriteToken(value: String) {
        mutableState.value = mutableState.value.copy(archiveWriteToken = value, saved = false)
    }

    fun save() {
        val current = mutableState.value
        if (!TargetNumberValidator.isValid(current.targetNumber)) {
            mutableState.value = current.copy(error = "Enter 10–15 digits, with an optional leading +", saved = false)
            return
        }
        viewModelScope.launch {
            repository.saveTargetAndDelay(current.targetNumber, current.sendDelay)
            repository.saveArchiveWriteToken(current.archiveWriteToken)
            if (current.archiveWriteToken.isNotBlank()) ArchiveScheduler.sweepNow(getApplication())
            mutableState.value = mutableState.value.copy(error = null, saved = true)
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            val pendingKeys = forwardRepository.clearLog()
            withContext(Dispatchers.IO) {
                pendingKeys.forEach { key ->
                    WorkManager.getInstance(getApplication()).cancelUniqueWork("fwd-$key").result.get()
                    WorkManager.getInstance(getApplication()).cancelUniqueWork("archive-$key").result.get()
                }
            }
        }
    }
}
