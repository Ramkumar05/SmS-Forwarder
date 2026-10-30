package com.autosmsforwarder.settings

data class RouteConfiguration(
    val forwardingEnabled: Boolean,
    val targetNumber: String,
    val sendDelay: SendDelay,
    val forwardMatchMode: MatchMode,
    val forwardKeywords: String,
    val webEnabled: Boolean,
    val webMatchMode: MatchMode,
    val webKeywords: String,
    val archiveWriteToken: String,
    val appSourcePhoneNumber: String = "",
    val appSourceName: String = ""
)

data class RouteSettingsErrors(
    val targetNumber: String? = null,
    val forwardKeywords: String? = null,
    val webKeywords: String? = null,
    val archiveWriteToken: String? = null,
    val appSourcePhoneNumber: String? = null,
    val appSourceName: String? = null
) {
    val isValid: Boolean get() = targetNumber == null && forwardKeywords == null &&
        webKeywords == null && archiveWriteToken == null && appSourcePhoneNumber == null && appSourceName == null
}

object RouteSettingsValidator {
    fun validate(config: RouteConfiguration): RouteSettingsErrors = RouteSettingsErrors(
        targetNumber = if (config.forwardingEnabled && !TargetNumberValidator.isValid(config.targetNumber))
            "Enter 10–15 digits, with an optional leading +" else null,
        forwardKeywords = if (config.forwardingEnabled && config.forwardMatchMode == MatchMode.KEYWORDS &&
            KeywordMatcher.parse(config.forwardKeywords).isEmpty()) "Enter at least one keyword" else null,
        webKeywords = if (config.webEnabled && config.webMatchMode == MatchMode.KEYWORDS &&
            KeywordMatcher.parse(config.webKeywords).isEmpty()) "Enter at least one keyword" else null,
        archiveWriteToken = if (config.webEnabled && config.archiveWriteToken.isBlank())
            "Enter the archive write token" else null,
        appSourcePhoneNumber = if (config.webEnabled) AppSourceMetadata.phoneError(config.appSourcePhoneNumber) else null,
        appSourceName = if (config.webEnabled) AppSourceMetadata.nameError(config.appSourceName) else null
    )
}
