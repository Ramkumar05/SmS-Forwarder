package com.autosmsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.settings.AppSettings
import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.settings.RouteConfiguration
import com.autosmsforwarder.settings.RouteSettingsErrors
import com.autosmsforwarder.settings.RouteSettingsCoordinator
import com.autosmsforwarder.settings.RouteSettingsValidator
import com.autosmsforwarder.settings.SendDelay
import com.autosmsforwarder.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val loaded: Boolean = false,
    val forwardingEnabled: Boolean = true,
    val targetNumber: String = "",
    val sendDelay: SendDelay = SendDelay.INSTANT,
    val forwardMatchMode: MatchMode = MatchMode.ALL,
    val forwardKeywords: String = "",
    val webEnabled: Boolean = false,
    val webMatchMode: MatchMode = MatchMode.ALL,
    val webKeywords: String = "",
    val archiveWriteToken: String = "",
    val appSourcePhoneNumber: String = "",
    val appSourceName: String = "",
    val errors: RouteSettingsErrors = RouteSettingsErrors(),
    val saved: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null
) {
    fun routeConfiguration() = RouteConfiguration(
        forwardingEnabled, targetNumber, sendDelay, forwardMatchMode, forwardKeywords,
        webEnabled, webMatchMode, webKeywords, archiveWriteToken, appSourcePhoneNumber, appSourceName
    )
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val forwardRepository = ForwardRepository(ForwardDatabase.get(application))
    private val routeSettingsCoordinator = RouteSettingsCoordinator(
        repository,
        forwardRepository,
        WebDeliveryRepository(ForwardDatabase.get(application)),
        cancelForwardWork = { key ->
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(getApplication()).cancelUniqueWork("fwd-$key").result.get()
            }
        },
        cancelWebWork = { key ->
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(getApplication()).cancelUniqueWork("web-$key").result.get()
                WorkManager.getInstance(getApplication()).cancelUniqueWork("web-expire-$key").result.get()
            }
        }
    )
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()
    private var hasDraftChanges = false
    private var draftVersion = 0

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                if (!hasDraftChanges) mutableState.value = settings.toUiState(mutableState.value.saved)
            }
        }
    }

    private fun edit(change: (SettingsUiState) -> SettingsUiState) {
        if (!mutableState.value.loaded) return
        hasDraftChanges = true
        draftVersion++
        mutableState.value = change(mutableState.value).copy(
            errors = RouteSettingsErrors(), saved = false, saveError = null
        )
    }

    fun setForwardingEnabled(value: Boolean) = edit { it.copy(forwardingEnabled = value) }

    fun setTargetNumber(value: String) {
        edit { it.copy(targetNumber = value) }
    }

    fun setSendDelay(value: SendDelay) {
        edit { it.copy(sendDelay = value) }
    }

    fun setForwardMatchMode(value: MatchMode) = edit { it.copy(forwardMatchMode = value) }
    fun setForwardKeywords(value: String) = edit { it.copy(forwardKeywords = value) }
    fun setWebEnabled(value: Boolean) = edit { it.copy(webEnabled = value) }
    fun setWebMatchMode(value: MatchMode) = edit { it.copy(webMatchMode = value) }
    fun setWebKeywords(value: String) = edit { it.copy(webKeywords = value) }
    fun setAppSourcePhoneNumber(value: String) = edit { it.copy(appSourcePhoneNumber = value) }
    fun setAppSourceName(value: String) = edit { it.copy(appSourceName = value) }

    fun setArchiveWriteToken(value: String) {
        edit { it.copy(archiveWriteToken = value) }
    }

    fun save() {
        val current = mutableState.value
        if (!current.loaded || current.saving) return
        val config = current.routeConfiguration()
        val errors = RouteSettingsValidator.validate(config)
        if (!errors.isValid) {
            mutableState.value = current.copy(errors = errors, saved = false)
            return
        }
        val submittedVersion = draftVersion
        mutableState.value = current.copy(saving = true, saved = false, saveError = null)
        viewModelScope.launch {
            try {
                routeSettingsCoordinator.save(config)
                if (draftVersion == submittedVersion) {
                    val savedSettings = repository.settings.first()
                    hasDraftChanges = false
                    mutableState.value = savedSettings.toUiState(saved = true)
                } else {
                    mutableState.value = mutableState.value.copy(saving = false)
                }
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(saving = false, saveError = "Could not save settings. Try again.")
            }
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            val pendingKeys = forwardRepository.clearLog()
            withContext(Dispatchers.IO) {
                pendingKeys.forEach { key ->
                    WorkManager.getInstance(getApplication()).cancelUniqueWork("fwd-$key").result.get()
                    WorkManager.getInstance(getApplication()).cancelUniqueWork("web-$key").result.get()
                    WorkManager.getInstance(getApplication()).cancelUniqueWork("web-expire-$key").result.get()
                    WorkManager.getInstance(getApplication()).cancelUniqueWork("archive-$key").result.get()
                }
            }
        }
    }
}

private fun AppSettings.toUiState(saved: Boolean) = SettingsUiState(
    loaded = true,
    forwardingEnabled = forwardingEnabled,
    targetNumber = targetNumber,
    sendDelay = sendDelay,
    forwardMatchMode = forwardMatchMode,
    forwardKeywords = forwardKeywords,
    webEnabled = webEnabled,
    webMatchMode = webMatchMode,
    webKeywords = webKeywords,
    archiveWriteToken = archiveWriteToken,
    appSourcePhoneNumber = appSourcePhoneNumber,
    appSourceName = appSourceName,
    saved = saved
)
