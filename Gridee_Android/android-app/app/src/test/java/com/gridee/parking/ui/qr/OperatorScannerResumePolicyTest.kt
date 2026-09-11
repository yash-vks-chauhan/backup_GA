package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class OperatorScannerResumePolicyTest {

    @Test
    fun `active mutation always keeps scanner locked`() {
        assertEquals(
            OperatorScannerResumeDecision.KEEP_MUTATION_LOCKED,
            OperatorScannerResumePolicy.decide(
                hasActiveMutation = true,
                isResumed = true,
                hasUnacknowledgedTerminalResult = false,
            ),
        )
    }

    @Test
    fun `completed result does not expire while app is backgrounded`() {
        assertEquals(
            OperatorScannerResumeDecision.RETAIN_TERMINAL_RESULT,
            OperatorScannerResumePolicy.decide(
                hasActiveMutation = false,
                isResumed = false,
                hasUnacknowledgedTerminalResult = true,
            ),
        )
    }

    @Test
    fun `foreground result resumes only after its hold interval`() {
        assertEquals(
            OperatorScannerResumeDecision.RESUME_SCANNER,
            OperatorScannerResumePolicy.decide(
                hasActiveMutation = false,
                isResumed = true,
                hasUnacknowledgedTerminalResult = true,
            ),
        )
    }

    @Test
    fun `backgrounded transient message cannot leave a loading lock`() {
        assertEquals(
            OperatorScannerResumeDecision.CLEAR_TRANSIENT_STATE,
            OperatorScannerResumePolicy.decide(
                hasActiveMutation = false,
                isResumed = false,
                hasUnacknowledgedTerminalResult = false,
            ),
        )
    }
}
