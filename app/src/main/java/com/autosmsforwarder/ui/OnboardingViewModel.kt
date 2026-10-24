package com.autosmsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosmsforwarder.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingState { LOADING, REQUIRED, COMPLETE }

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val mutableState = MutableStateFlow(OnboardingState.LOADING)
    val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                mutableState.value = if (settings.onboardingCompleted) {
                    OnboardingState.COMPLETE
                } else {
                    OnboardingState.REQUIRED
                }
            }
        }
    }

    fun finishAfterPermissionRequest() {
        viewModelScope.launch { repository.markOnboardingCompleted() }
    }
}
