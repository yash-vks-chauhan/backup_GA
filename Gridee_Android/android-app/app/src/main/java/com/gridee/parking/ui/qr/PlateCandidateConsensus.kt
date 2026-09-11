package com.gridee.parking.ui.qr

/**
 * Prevents a single noisy OCR frame from starting a production check-in/check-out.
 *
 * A value is accepted only when it appears in consecutive, recent frames. This keeps the
 * confirmation delay to one camera frame in the normal case while rejecting stale or alternating
 * guesses.
 */
internal class PlateCandidateConsensus(
    private val requiredConsecutiveMatches: Int = 2,
    private val maxGapMs: Long = 650L
) {
    private var lastCandidate: String? = null
    private var lastObservedAtMs: Long = Long.MIN_VALUE
    private var consecutiveMatches: Int = 0
    private var activeRequiredMatches: Int = requiredConsecutiveMatches

    init {
        require(requiredConsecutiveMatches > 0)
        require(maxGapMs >= 0L)
    }

    @Synchronized
    fun observe(
        candidate: String,
        observedAtMs: Long,
        requiredMatches: Int = requiredConsecutiveMatches,
        allowedGapMs: Long = maxGapMs,
    ): String? {
        require(requiredMatches > 0)
        require(allowedGapMs >= 0L)
        val continuesSequence = candidate == lastCandidate &&
            requiredMatches == activeRequiredMatches &&
            lastObservedAtMs != Long.MIN_VALUE &&
            observedAtMs >= lastObservedAtMs &&
            observedAtMs - lastObservedAtMs <= allowedGapMs

        consecutiveMatches = if (continuesSequence) consecutiveMatches + 1 else 1
        lastCandidate = candidate
        lastObservedAtMs = observedAtMs
        activeRequiredMatches = requiredMatches

        return candidate.takeIf { consecutiveMatches >= requiredMatches }
    }

    @Synchronized
    fun clear() {
        lastCandidate = null
        lastObservedAtMs = Long.MIN_VALUE
        consecutiveMatches = 0
        activeRequiredMatches = requiredConsecutiveMatches
    }
}
