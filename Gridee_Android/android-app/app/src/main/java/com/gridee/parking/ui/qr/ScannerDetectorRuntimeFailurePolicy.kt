package com.gridee.parking.ui.qr

internal enum class ScannerDetectorRuntimeFailureDecision {
    KEEP_SCANNING,
    SHOW_FALLBACK,
}

/**
 * Opens the blocking detector fallback only after a bounded run of consecutive ML task failures.
 * A successful detector task proves that the runtime is healthy again and clears the streak.
 */
internal class ScannerDetectorRuntimeFailurePolicy(
    private val fallbackThreshold: Int = DEFAULT_FALLBACK_THRESHOLD,
) {
    private var consecutiveFailures = 0

    init {
        require(fallbackThreshold > 0)
    }

    @Synchronized
    fun recordFailure(): ScannerDetectorRuntimeFailureDecision {
        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(fallbackThreshold)
        return if (consecutiveFailures >= fallbackThreshold) {
            ScannerDetectorRuntimeFailureDecision.SHOW_FALLBACK
        } else {
            ScannerDetectorRuntimeFailureDecision.KEEP_SCANNING
        }
    }

    @Synchronized
    fun recordSuccess() {
        consecutiveFailures = 0
    }

    @Synchronized
    fun reset() {
        consecutiveFailures = 0
    }

    private companion object {
        const val DEFAULT_FALLBACK_THRESHOLD = 3
    }
}
