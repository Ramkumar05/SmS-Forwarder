package com.autosmsforwarder.domain

data class SendTransition(
    val status: ForwardStatus,
    val attempts: Int,
    val retryDelayMillis: Long?
)

class RetryPolicy {
    private val maxAttempts = 3
    private val baseBackoffMillis = 30_000L

    fun afterAttempt(completedAttempts: Int, succeeded: Boolean): SendTransition {
        require(completedAttempts in 1..maxAttempts)
        return when {
            succeeded -> SendTransition(ForwardStatus.SENT, completedAttempts, null)
            completedAttempts == maxAttempts -> SendTransition(ForwardStatus.FAILED, completedAttempts, null)
            else -> SendTransition(
                ForwardStatus.PENDING,
                completedAttempts,
                baseBackoffMillis * (1L shl (completedAttempts - 1))
            )
        }
    }
}
