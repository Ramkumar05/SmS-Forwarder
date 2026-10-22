package com.autosmsforwarder.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardPolicyTest {
    private val policy: ForwardPolicy = DefaultForwardPolicy()

    @Test
    fun matchingTargetIsSkipped() {
        val message = IncomingMessage("98765 43210", "hello", 123L)

        assertEquals(
            ForwardDecision.Skip(ForwardStatus.SKIPPED_LOOP),
            policy.decide(message, ForwardSettings("+91 98765 43210"))
        )
    }

    @Test
    fun otherSenderIsForwardedEvenWhenBodyIsEmpty() {
        val message = IncomingMessage("+919876543211", "", 123L)

        assertEquals(
            ForwardDecision.Forward,
            policy.decide(message, ForwardSettings("+919876543210"))
        )
    }
}
