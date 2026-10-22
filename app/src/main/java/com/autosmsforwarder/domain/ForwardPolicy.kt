package com.autosmsforwarder.domain

data class IncomingMessage(val sender: String, val body: String, val receivedAtMillis: Long)

data class ForwardSettings(val targetNumber: String)

enum class ForwardStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED_LOOP
}

sealed interface ForwardDecision {
    data object Forward : ForwardDecision
    data class Skip(val reason: ForwardStatus) : ForwardDecision
}

interface ForwardPolicy {
    fun decide(message: IncomingMessage, settings: ForwardSettings): ForwardDecision
}

class DefaultForwardPolicy : ForwardPolicy {
    override fun decide(message: IncomingMessage, settings: ForwardSettings): ForwardDecision =
        if (NumberNormalizer.sameForLoop(message.sender, settings.targetNumber)) {
            ForwardDecision.Skip(ForwardStatus.SKIPPED_LOOP)
        } else {
            ForwardDecision.Forward
        }
}
