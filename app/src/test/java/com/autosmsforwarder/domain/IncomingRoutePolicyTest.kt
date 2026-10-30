package com.autosmsforwarder.domain

import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.settings.RouteConfiguration
import com.autosmsforwarder.settings.SendDelay
import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingRoutePolicyTest {
    private val policy = IncomingRoutePolicy()
    private val message = IncomingMessage(sender = "OTP-SENDER", body = "Payment received", receivedAtMillis = 123L)
    private val config = RouteConfiguration(
        forwardingEnabled = false,
        targetNumber = "9876543210",
        sendDelay = SendDelay.INSTANT,
        forwardMatchMode = MatchMode.ALL,
        forwardKeywords = "",
        webEnabled = false,
        webMatchMode = MatchMode.ALL,
        webKeywords = "",
        archiveWriteToken = "token"
    )

    @Test fun eachSwitchCombinationProducesItsOwnDecision() {
        assertEquals(RouteDecision(false, false), policy.decide(message, config))
        assertEquals(RouteDecision(true, false), policy.decide(message, config.copy(forwardingEnabled = true)))
        assertEquals(RouteDecision(false, true), policy.decide(message, config.copy(webEnabled = true)))
        assertEquals(RouteDecision(true, true), policy.decide(message,
            config.copy(forwardingEnabled = true, webEnabled = true)))
    }

    @Test fun separateKeywordListsCanMatchEitherRouteOrBoth() {
        val routes = config.copy(
            forwardingEnabled = true,
            forwardMatchMode = MatchMode.KEYWORDS,
            forwardKeywords = "payment received",
            webEnabled = true,
            webMatchMode = MatchMode.KEYWORDS,
            webKeywords = "otp"
        )
        assertEquals(RouteDecision(true, false), policy.decide(message, routes))
        assertEquals(RouteDecision(false, true), policy.decide(message.copy(body = "Your OTP is ready"), routes))
        assertEquals(RouteDecision(true, true), policy.decide(message.copy(body = "Payment received; OTP ready"), routes))
        assertEquals(RouteDecision(false, false), policy.decide(message.copy(body = "No matching terms"), routes))
    }

    @Test fun senderKeywordAloneDoesNotMatchEitherKeywordRoute() {
        val routes = config.copy(
            forwardingEnabled = true,
            forwardMatchMode = MatchMode.KEYWORDS,
            forwardKeywords = "otp",
            webEnabled = true,
            webMatchMode = MatchMode.KEYWORDS,
            webKeywords = "otp"
        )
        assertEquals(RouteDecision(false, false), policy.decide(message, routes))
    }

    @Test fun allSmsMatchesEmptyBodyWhileKeywordModeDoesNot() {
        val routes = config.copy(
            forwardingEnabled = true,
            webEnabled = true,
            webMatchMode = MatchMode.KEYWORDS,
            webKeywords = "otp"
        )
        assertEquals(RouteDecision(true, false), policy.decide(message.copy(body = ""), routes))
    }

    @Test fun disabledRouteIgnoresMatchingKeywords() {
        val routes = config.copy(
            forwardMatchMode = MatchMode.KEYWORDS,
            forwardKeywords = "payment",
            webEnabled = true,
            webMatchMode = MatchMode.ALL
        )
        assertEquals(RouteDecision(false, true), policy.decide(message, routes))
    }
}
