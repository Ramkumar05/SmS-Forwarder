package com.autosmsforwarder.settings

import kotlinx.coroutines.sync.Mutex

/** Serializes receive-time forwarding decisions with saving a disabled route. */
object ForwardRouteLock {
    val mutex = Mutex()
}
