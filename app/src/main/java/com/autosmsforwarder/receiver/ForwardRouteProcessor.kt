package com.autosmsforwarder.receiver

import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.ReceiveResult
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.domain.IncomingRoutePolicy
import com.autosmsforwarder.settings.RouteConfiguration

class ForwardRouteProcessor(
    private val repository: ForwardRepository,
    private val routePolicy: IncomingRoutePolicy = IncomingRoutePolicy()
) {
    suspend fun recordIfMatched(message: IncomingMessage, config: RouteConfiguration): ReceiveResult? {
        if (!routePolicy.decide(message, config).forward) return null
        if (config.targetNumber.isBlank()) return null
        return repository.recordIfNew(message, config.targetNumber)
    }
}
