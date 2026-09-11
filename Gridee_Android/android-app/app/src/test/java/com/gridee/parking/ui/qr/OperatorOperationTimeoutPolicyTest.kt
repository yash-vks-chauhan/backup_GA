package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class OperatorOperationTimeoutPolicyTest {

    @Test
    fun `recreated screen arms only the remaining presentation timeout`() {
        assertEquals(
            OperatorOperationTimeoutDecision.Wait(7_000L),
            OperatorOperationTimeoutPolicy.decide(
                startedAtElapsedMs = 10_000L,
                nowElapsedMs = 15_000L,
                timeoutMs = 12_000L,
                isInFlight = true,
            ),
        )
    }

    @Test
    fun `backgrounded request that is still running restores timed out presentation`() {
        assertEquals(
            OperatorOperationTimeoutDecision.TimedOut,
            OperatorOperationTimeoutPolicy.decide(
                startedAtElapsedMs = 10_000L,
                nowElapsedMs = 25_000L,
                timeoutMs = 12_000L,
                isInFlight = true,
            ),
        )
    }

    @Test
    fun `completed request never emits a false slow signal`() {
        assertEquals(
            OperatorOperationTimeoutDecision.Completed,
            OperatorOperationTimeoutPolicy.decide(
                startedAtElapsedMs = 10_000L,
                nowElapsedMs = 30_000L,
                timeoutMs = 12_000L,
                isInFlight = false,
            ),
        )
    }

    @Test
    fun `future monotonic timestamp waits the full timeout defensively`() {
        assertEquals(
            OperatorOperationTimeoutDecision.Wait(12_000L),
            OperatorOperationTimeoutPolicy.decide(
                startedAtElapsedMs = 20_000L,
                nowElapsedMs = 10_000L,
                timeoutMs = 12_000L,
                isInFlight = true,
            ),
        )
    }
}
