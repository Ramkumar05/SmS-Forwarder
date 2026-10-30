package com.autosmsforwarder.domain

import com.autosmsforwarder.settings.KeywordMatcher
import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.settings.RouteConfiguration

data class RouteDecision(val forward: Boolean, val web: Boolean)

class IncomingRoutePolicy {
    fun decide(message: IncomingMessage, config: RouteConfiguration): RouteDecision = RouteDecision(
        forward = config.forwardingEnabled && matches(config.forwardMatchMode, config.forwardKeywords, message.body),
        web = config.webEnabled && matches(config.webMatchMode, config.webKeywords, message.body)
    )

    private fun matches(mode: MatchMode, keywords: String, body: String): Boolean = when (mode) {
        MatchMode.ALL -> true
        MatchMode.KEYWORDS -> KeywordMatcher.matchesBody(body, keywords)
    }
}
