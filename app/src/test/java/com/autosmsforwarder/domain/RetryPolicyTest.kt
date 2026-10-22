package com.autosmsforwarder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetryPolicyTest {
    private val policy = RetryPolicy()

    @Test
    fun firstTwoFailuresRemainPendingWithExponentialDelays() {
        assertEquals(
            SendTransition(ForwardStatus.PENDING, 1, 30_000L),
            policy.afterAttempt(1, succeeded = false)
        )
        assertEquals(
            SendTransition(ForwardStatus.PENDING, 2, 60_000L),
            policy.afterAttempt(2, succeeded = false)
        )
    }

    @Test
    fun thirdFailureIsFinalAndSuccessIsSent() {
        assertEquals(
            SendTransition(ForwardStatus.FAILED, 3, null),
            policy.afterAttempt(3, succeeded = false)
        )
        assertEquals(
            SendTransition(ForwardStatus.SENT, 2, null),
            policy.afterAttempt(2, succeeded = true)
        )
    }

    @Test
    fun invalidAttemptCountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            policy.afterAttempt(0, succeeded = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.afterAttempt(4, succeeded = false)
        }
    }
}
