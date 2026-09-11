package com.gridee.parking.ui.qr

internal sealed class OperatorOperationTimeoutDecision {
    object Completed : OperatorOperationTimeoutDecision()
    data class Wait(val remainingMs: Long) : OperatorOperationTimeoutDecision()
    object TimedOut : OperatorOperationTimeoutDecision()
}

/**
 * Reconstructs the presentation-only timeout from the retained mutation's monotonic start time.
 * The network mutation itself is never cancelled or unlocked by this policy.
 */
internal object OperatorOperationTimeoutPolicy {
    fun decide(
        startedAtElapsedMs: Long,
        nowElapsedMs: Long,
        timeoutMs: Long,
        isInFlight: Boolean,
    ): OperatorOperationTimeoutDecision {
        if (!isInFlight) return OperatorOperationTimeoutDecision.Completed
        val elapsedMs = (nowElapsedMs - startedAtElapsedMs).coerceAtLeast(0L)
        val remainingMs = timeoutMs.coerceAtLeast(0L) - elapsedMs
        return if (remainingMs > 0L) {
            OperatorOperationTimeoutDecision.Wait(remainingMs)
        } else {
            OperatorOperationTimeoutDecision.TimedOut
        }
    }
}
