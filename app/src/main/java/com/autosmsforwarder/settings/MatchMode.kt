package com.autosmsforwarder.settings

enum class MatchMode(val label: String) {
    ALL("All SMS"),
    KEYWORDS("Matching keywords");

    companion object {
        fun fromStored(value: String?): MatchMode = entries.firstOrNull { it.name == value } ?: ALL
    }
}
