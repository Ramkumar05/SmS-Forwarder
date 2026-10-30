package com.autosmsforwarder.settings

import com.autosmsforwarder.data.ForwardRepository
import com.autosmsforwarder.data.WebDeliveryRepository
import kotlinx.coroutines.sync.withLock

class RouteSettingsCoordinator(
    private val settingsRepository: SettingsRepository,
    private val forwardRepository: ForwardRepository,
    private val webRepository: WebDeliveryRepository,
    private val cancelForwardWork: suspend (String) -> Unit,
    private val cancelWebWork: suspend (String) -> Unit
) {
    suspend fun save(config: RouteConfiguration) = ForwardRouteLock.mutex.withLock {
        settingsRepository.saveRoutes(config)
        if (!config.forwardingEnabled) {
            forwardRepository.cancelUnsent().forEach { key -> cancelForwardWork(key) }
        }
        if (!config.webEnabled) {
            webRepository.discardAll().forEach { key -> cancelWebWork(key) }
        }
    }
}
