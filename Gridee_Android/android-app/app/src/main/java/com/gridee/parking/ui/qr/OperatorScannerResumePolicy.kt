package com.gridee.parking.ui.qr

internal enum class OperatorScannerResumeDecision {
    KEEP_MUTATION_LOCKED,
    RETAIN_TERMINAL_RESULT,
    CLEAR_TRANSIENT_STATE,
    RESUME_SCANNER,
}

/** Lifecycle policy for a delayed scanner resume after a terminal or transient result. */
internal object OperatorScannerResumePolicy {
    fun decide(
        hasActiveMutation: Boolean,
        isResumed: Boolean,
        hasUnacknowledgedTerminalResult: Boolean,
    ): OperatorScannerResumeDecision {
        return when {
            hasActiveMutation -> OperatorScannerResumeDecision.KEEP_MUTATION_LOCKED
            !isResumed && hasUnacknowledgedTerminalResult ->
                OperatorScannerResumeDecision.RETAIN_TERMINAL_RESULT
            !isResumed -> OperatorScannerResumeDecision.CLEAR_TRANSIENT_STATE
            else -> OperatorScannerResumeDecision.RESUME_SCANNER
        }
    }
}
