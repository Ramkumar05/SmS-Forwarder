package com.autosmsforwarder.settings

object AppSourceMetadata {
    private val phonePattern = Regex("\\+?[0-9]{10,15}")

    fun phoneError(value: String): String? = if (value.trim().isNotEmpty() && !phonePattern.matches(value.trim()))
        "Enter 10–15 digits with an optional leading +, or leave blank" else null

    fun nameError(value: String): String? = when {
        value.trim().length > 100 -> "Use 100 characters or fewer, or leave blank"
        value.trim().any { it.code < 32 || it.code == 127 } -> "Use a single-line name without control characters"
        else -> null
    }
}
