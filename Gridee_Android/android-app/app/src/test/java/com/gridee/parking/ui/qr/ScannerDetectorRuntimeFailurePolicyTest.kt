package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class ScannerDetectorRuntimeFailurePolicyTest {

    @Test
    fun `fallback opens only after bounded consecutive failures`() {
        val policy = ScannerDetectorRuntimeFailurePolicy(fallbackThreshold = 3)

        assertEquals(
            ScannerDetectorRuntimeFailureDecision.KEEP_SCANNING,
            policy.recordFailure(),
        )
        assertEquals(
            ScannerDetectorRuntimeFailureDecision.KEEP_SCANNING,
            policy.recordFailure(),
        )
        assertEquals(
            ScannerDetectorRuntimeFailureDecision.SHOW_FALLBACK,
            policy.recordFailure(),
        )
    }

    @Test
    fun `successful detector task breaks the failure streak`() {
        val policy = ScannerDetectorRuntimeFailurePolicy(fallbackThreshold = 2)

        assertEquals(
            ScannerDetectorRuntimeFailureDecision.KEEP_SCANNING,
            policy.recordFailure(),
        )
        policy.recordSuccess()

        assertEquals(
            ScannerDetectorRuntimeFailureDecision.KEEP_SCANNING,
            policy.recordFailure(),
        )
        assertEquals(
            ScannerDetectorRuntimeFailureDecision.SHOW_FALLBACK,
            policy.recordFailure(),
        )
    }

    @Test
    fun `explicit reset starts a fresh streak`() {
        val policy = ScannerDetectorRuntimeFailurePolicy(fallbackThreshold = 2)

        policy.recordFailure()
        policy.reset()

        assertEquals(
            ScannerDetectorRuntimeFailureDecision.KEEP_SCANNING,
            policy.recordFailure(),
        )
    }
}
