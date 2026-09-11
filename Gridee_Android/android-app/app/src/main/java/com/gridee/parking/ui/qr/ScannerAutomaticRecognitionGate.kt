package com.gridee.parking.ui.qr

/** Automatic inputs covered by the shared production kill switch. */
internal enum class ScannerAutomaticRecognitionSource {
    PLATE,
    QR,
}

/** Why a recognition result may or may not advance to an operator mutation. */
internal enum class ScannerRecognitionDecision {
    ALLOW,
    AUTOMATIC_RECOGNITION_DISABLED,
    STALE_AUTOMATIC_RESULT,
    MUTATION_IN_FLIGHT,
}

internal data class ScannerAutomaticRecognitionGateUpdate(
    val previousEnabled: Boolean,
    val currentEnabled: Boolean,
    val generation: Long,
    /** True when pending analyzer callbacks and plate consensus must be discarded. */
    val shouldInvalidateAutomaticWork: Boolean,
    /** A config update must never clear, cancel, or replace the retained operator mutation. */
    val preserveInFlightMutation: Boolean,
) {
    val changed: Boolean get() = previousEnabled != currentEnabled
    val automaticPipelineMayRun: Boolean
        get() = currentEnabled && !preserveInFlightMutation
}

/** Android-free final-commit snapshot so lifecycle/session race behavior is unit-testable. */
internal data class ScannerAutomaticCommitContext(
    val expectedSessionId: Long,
    val currentSessionId: Long,
    val scannerRunning: Boolean,
    val sourceModeActive: Boolean,
    val lifecycleResumed: Boolean,
    val viewportReady: Boolean,
    val detectorReady: Boolean,
    val blockingInteraction: Boolean,
    val activityClosing: Boolean,
    val allowStoppedOperatorConfirmation: Boolean = false,
) {
    fun isValid(): Boolean {
        if (!sourceModeActive || !lifecycleResumed || !viewportReady || !detectorReady ||
            blockingInteraction || activityClosing
        ) {
            return false
        }
        return allowStoppedOperatorConfirmation ||
            (scannerRunning && expectedSessionId == currentSessionId)
    }
}

/**
 * Thread-safe snapshot used before a frame may change camera or shared quality state.
 *
 * Unlike the final UI commit guard, this contains only values safe to capture from an analyzer
 * callback. A mode switch, scanner stop, generation change, or started mutation invalidates every
 * side effect belonging to the older frame.
 */
internal data class ScannerFrameRuntimeGuard(
    val expectedSessionId: Long,
    val currentSessionId: Long,
    val scannerRunning: Boolean,
    val sourceModeActive: Boolean,
    val operationActive: Boolean,
    val scanCompleted: Boolean,
    val mutationInFlight: Boolean,
    val activityClosing: Boolean,
    val recognitionDecision: ScannerRecognitionDecision,
) {
    fun isCurrent(): Boolean =
        expectedSessionId == currentSessionId &&
            scannerRunning &&
            sourceModeActive &&
            operationActive &&
            !scanCompleted &&
            !mutationInFlight &&
            !activityClosing &&
            recognitionDecision == ScannerRecognitionDecision.ALLOW
}

/**
 * Thread-safe, Android-free gate for the asynchronous Plate and QR recognition pipelines.
 *
 * A frame obtains a generation before ML work begins and checks it again immediately before its
 * result is accepted on the main thread. Every enabled/disabled transition advances the
 * generation, so an old callback cannot slip through a quick off -> on config change. Manual
 * plate entry intentionally bypasses the feature flag, but still respects the active-mutation
 * lock.
 *
 * This class only decides whether new work may advance. It never owns or cancels the operator
 * mutation; that remains the retained ViewModel/coordinator's responsibility.
 */
internal class ScannerAutomaticRecognitionGate(
    initialEnabled: Boolean = true,
) {
    private var enabled = initialEnabled
    private var generation = INITIAL_GENERATION

    @Synchronized
    fun updateEnabled(
        newEnabled: Boolean,
        mutationInFlight: Boolean,
        invalidateExistingWork: Boolean = false,
    ): ScannerAutomaticRecognitionGateUpdate {
        val previousEnabled = enabled
        val changed = previousEnabled != newEnabled
        val shouldInvalidateAutomaticWork = changed || invalidateExistingWork
        if (shouldInvalidateAutomaticWork) generation = nextGeneration(generation)
        enabled = newEnabled
        return ScannerAutomaticRecognitionGateUpdate(
            previousEnabled = previousEnabled,
            currentEnabled = enabled,
            generation = generation,
            shouldInvalidateAutomaticWork = shouldInvalidateAutomaticWork,
            preserveInFlightMutation = mutationInFlight,
        )
    }

    /**
     * Returns a non-negative generation when a new automatic analysis may start, otherwise
     * [REJECTED_GENERATION]. [source] is explicit so Plate and QR call sites cannot accidentally
     * route a manual value through this API.
     */
    @Synchronized
    fun beginAutomaticRecognition(
        source: ScannerAutomaticRecognitionSource,
        mutationInFlight: Boolean,
    ): Long = when (source) {
        ScannerAutomaticRecognitionSource.PLATE,
        ScannerAutomaticRecognitionSource.QR ->
            if (enabled && !mutationInFlight) generation else REJECTED_GENERATION
    }

    /** Re-check immediately before an automatic result is allowed to start a mutation. */
    @Synchronized
    fun automaticCommitDecision(
        source: ScannerAutomaticRecognitionSource,
        startedAtGeneration: Long,
        mutationInFlight: Boolean,
    ): ScannerRecognitionDecision = when (source) {
        ScannerAutomaticRecognitionSource.PLATE,
        ScannerAutomaticRecognitionSource.QR -> when {
            mutationInFlight -> ScannerRecognitionDecision.MUTATION_IN_FLIGHT
            !enabled || startedAtGeneration == REJECTED_GENERATION ->
                ScannerRecognitionDecision.AUTOMATIC_RECOGNITION_DISABLED
            startedAtGeneration != generation -> ScannerRecognitionDecision.STALE_AUTOMATIC_RESULT
            else -> ScannerRecognitionDecision.ALLOW
        }
    }

    /** Manual entry stays available when automatic recognition is disabled. */
    @Synchronized
    fun manualPlateCommitDecision(mutationInFlight: Boolean): ScannerRecognitionDecision =
        if (mutationInFlight) {
            ScannerRecognitionDecision.MUTATION_IN_FLIGHT
        } else {
            ScannerRecognitionDecision.ALLOW
        }

    @Synchronized
    fun isAutomaticRecognitionEnabled(): Boolean = enabled

    private fun nextGeneration(current: Long): Long =
        if (current == Long.MAX_VALUE) INITIAL_GENERATION else current + 1L

    companion object {
        const val REJECTED_GENERATION = -1L
        private const val INITIAL_GENERATION = 0L
    }
}
