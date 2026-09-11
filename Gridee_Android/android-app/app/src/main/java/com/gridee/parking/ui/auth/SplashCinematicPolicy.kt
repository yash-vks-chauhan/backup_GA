package com.gridee.parking.ui.auth

/** Keeps the process-level splash decision deterministic and preserves its warm-start shortcut. */
internal object SplashCinematicPolicy {
    fun shouldSkip(
        hasShownCinematic: Boolean,
        areSystemAnimatorsEnabled: () -> Boolean,
    ): Boolean = hasShownCinematic || !areSystemAnimatorsEnabled()
}
