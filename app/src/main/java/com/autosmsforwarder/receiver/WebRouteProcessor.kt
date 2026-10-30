package com.autosmsforwarder.receiver

import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.domain.IncomingRoutePolicy
import com.autosmsforwarder.settings.RouteConfiguration
import com.autosmsforwarder.worker.WebConnectivityChecker

class WebRouteProcessor(
    private val repository: WebDeliveryRepository,
    private val connectivity: WebConnectivityChecker,
    private val routePolicy: IncomingRoutePolicy = IncomingRoutePolicy()
) {
    suspend fun recordIfMatched(message: IncomingMessage, config: RouteConfiguration): String? {
        if (!routePolicy.decide(message, config).web) return null
        if (!connectivity.isOnline()) {
            repository.recordSkipped(message)
            return null
        }
        return repository.recordIfNew(message, config.appSourcePhoneNumber, config.appSourceName)
    }
}
