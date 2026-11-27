package com.autosmsforwarder.receiver

import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.domain.IncomingRoutePolicy
import com.autosmsforwarder.settings.RouteConfiguration

class WebRouteProcessor(
    private val repository: WebDeliveryRepository,
    private val routePolicy: IncomingRoutePolicy = IncomingRoutePolicy()
) {
    /** Queues the message for upload (online or not). Returns its key when newly queued. */
    suspend fun recordIfMatched(message: IncomingMessage, config: RouteConfiguration): String? {
        if (!routePolicy.decide(message, config).web) return null
        return repository.recordIfNew(message, config.appSourcePhoneNumber, config.appSourceName)
    }
}
