package com.gridee.parking.ui.qr

internal data class ScannerCameraRebindDecision(
    val rebindNow: Boolean,
    val keepPending: Boolean,
)

/** Keeps an active/terminal operator workflow intact while a new analysis size is staged. */
internal object ScannerCameraConfigUpdatePolicy {
    fun decide(
        analysisSizeChanged: Boolean,
        protectedOperatorState: Boolean,
    ): ScannerCameraRebindDecision = when {
        !analysisSizeChanged -> ScannerCameraRebindDecision(
            rebindNow = false,
            keepPending = false,
        )
        protectedOperatorState -> ScannerCameraRebindDecision(
            rebindNow = false,
            keepPending = true,
        )
        else -> ScannerCameraRebindDecision(
            rebindNow = true,
            keepPending = false,
        )
    }
}
