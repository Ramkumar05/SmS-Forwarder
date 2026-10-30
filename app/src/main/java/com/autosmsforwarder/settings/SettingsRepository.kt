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
    val archiveWriteToken: String = "",
    val forwardMatchMode: MatchMode = MatchMode.ALL,
    val forwardKeywords: String = "",
    val webEnabled: Boolean = false,
    val webMatchMode: MatchMode = MatchMode.ALL,
    val webKeywords: String = "",
    val appSourcePhoneNumber: String = "",
    val appSourceName: String = ""
) {
    fun routeConfiguration() = RouteConfiguration(
        forwardingEnabled, targetNumber, sendDelay, forwardMatchMode, forwardKeywords,
        webEnabled, webMatchMode, webKeywords, archiveWriteToken, appSourcePhoneNumber, appSourceName
    )
}

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<AppSettings> = store.data.map { preferences ->
        AppSettings(
            targetNumber = preferences[TARGET_NUMBER].orEmpty(),
            forwardingEnabled = preferences[ENABLED] ?: true,
            sendDelay = SendDelay.fromSeconds(preferences[DELAY_SECONDS] ?: 0),
            onboardingCompleted = preferences[ONBOARDING_COMPLETED] ?: false,
            archiveWriteToken = preferences[ARCHIVE_WRITE_TOKEN].orEmpty(),
            forwardMatchMode = MatchMode.fromStored(preferences[FORWARD_MATCH_MODE]),
            forwardKeywords = preferences[FORWARD_KEYWORDS].orEmpty(),
            webEnabled = preferences[WEB_ENABLED] ?: false,
            webMatchMode = MatchMode.fromStored(preferences[WEB_MATCH_MODE]),
            webKeywords = preferences[WEB_KEYWORDS].orEmpty(),
            appSourcePhoneNumber = preferences[APP_SOURCE_PHONE_NUMBER].orEmpty(),
            appSourceName = preferences[APP_SOURCE_NAME].orEmpty()
        )
    }

    suspend fun saveRoutes(config: RouteConfiguration) {
        require(RouteSettingsValidator.validate(config).isValid)
        store.edit { preferences ->
            preferences[TARGET_NUMBER] = if (TargetNumberValidator.isValid(config.targetNumber))
                TargetNumberValidator.canonical(config.targetNumber) else config.targetNumber
            preferences[ENABLED] = config.forwardingEnabled
            preferences[DELAY_SECONDS] = config.sendDelay.seconds
            preferences[FORWARD_MATCH_MODE] = config.forwardMatchMode.name
            preferences[FORWARD_KEYWORDS] = config.forwardKeywords
            preferences[WEB_ENABLED] = config.webEnabled
            preferences[WEB_MATCH_MODE] = config.webMatchMode.name
            preferences[WEB_KEYWORDS] = config.webKeywords
            preferences[ARCHIVE_WRITE_TOKEN] = config.archiveWriteToken.trim()
            preferences[APP_SOURCE_PHONE_NUMBER] = config.appSourcePhoneNumber.trim()
            preferences[APP_SOURCE_NAME] = config.appSourceName.trim()
        }
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
        private val FORWARD_MATCH_MODE = stringPreferencesKey("forward_match_mode")
        private val FORWARD_KEYWORDS = stringPreferencesKey("forward_keywords")
        private val WEB_ENABLED = booleanPreferencesKey("web_enabled")
        private val WEB_MATCH_MODE = stringPreferencesKey("web_match_mode")
        private val WEB_KEYWORDS = stringPreferencesKey("web_keywords")
        private val APP_SOURCE_PHONE_NUMBER = stringPreferencesKey("app_source_phone_number")
        private val APP_SOURCE_NAME = stringPreferencesKey("app_source_name")
    }
}
