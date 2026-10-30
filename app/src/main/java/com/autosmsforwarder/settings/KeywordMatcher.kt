package com.autosmsforwarder.settings

object KeywordMatcher {
    fun parse(commaSeparated: String): List<String> = commaSeparated.split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)

    fun matchesBody(body: String, commaSeparated: String): Boolean =
        parse(commaSeparated).any { keyword -> body.contains(keyword, ignoreCase = true) }
}
