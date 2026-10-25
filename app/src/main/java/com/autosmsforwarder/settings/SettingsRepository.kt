package com.autosmsforwarder.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "forward_settings")

data class AppSettings(
    val targetNumber: String = "",
    val forwardingEnabled: Boolean = true,
    val sendDelay: SendDelay = SendDelay.INSTANT,
    val onboardingCompleted: Boolean = false,
    val archiveWriteToken: String = ""
)

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<AppSettings> = store.data.map { preferences ->
        AppSettings(
            targetNumber = preferences[TARGET_NUMBER].orEmpty(),
            forwardingEnabled = preferences[ENABLED] ?: true,
            sendDelay = SendDelay.fromSeconds(preferences[DELAY_SECONDS] ?: 0),
            onboardingCompleted = preferences[ONBOARDING_COMPLETED] ?: false,
            archiveWriteToken = preferences[ARCHIVE_WRITE_TOKEN].orEmpty()
        )
    }

    suspend fun saveTargetAndDelay(targetNumber: String, delay: SendDelay) {
        val canonical = TargetNumberValidator.canonical(targetNumber)
        store.edit { preferences ->
            preferences[TARGET_NUMBER] = canonical
            preferences[DELAY_SECONDS] = delay.seconds
        }
    }

    suspend fun saveArchiveWriteToken(token: String) {
        store.edit { preferences -> preferences[ARCHIVE_WRITE_TOKEN] = token.trim() }
    }

    suspend fun setForwardingEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    suspend fun markOnboardingCompleted() {
        store.edit { it[ONBOARDING_COMPLETED] = true }
    }

    companion object {
        private val TARGET_NUMBER = stringPreferencesKey("target_number")
        private val ENABLED = booleanPreferencesKey("forwarding_enabled")
        private val DELAY_SECONDS = longPreferencesKey("send_delay_seconds")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val ARCHIVE_WRITE_TOKEN = stringPreferencesKey("archive_write_token")
    }
}
