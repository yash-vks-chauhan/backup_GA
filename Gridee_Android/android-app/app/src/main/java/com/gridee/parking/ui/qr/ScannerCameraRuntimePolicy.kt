package com.gridee.parking.ui.qr

internal enum class ScannerCameraRuntimePhase {
    PENDING_OPEN,
    OPENING,
    OPEN,
    CLOSING,
    CLOSED,
}

internal enum class ScannerCameraRuntimeErrorSeverity {
    NONE,
    RECOVERABLE,
    CRITICAL,
}

internal enum class ScannerCameraRuntimeAction {
    HEALTHY,
    WAIT_FOR_RECOVERY,
    FAIL,
}

internal enum class ScannerCameraRuntimeFailureReason(val metricValue: String) {
    CRITICAL_STATE("critical_state"),
    FIRST_PREVIEW_TIMEOUT("first_preview_timeout"),
}

internal class ScannerCameraRuntimeException(
    val reason: ScannerCameraRuntimeFailureReason,
    cause: Throwable? = null,
) : IllegalStateException(
    when (reason) {
        ScannerCameraRuntimeFailureReason.CRITICAL_STATE ->
            "CameraX reported a critical runtime camera error"
        ScannerCameraRuntimeFailureReason.FIRST_PREVIEW_TIMEOUT ->
            "CameraX bound but PreviewView did not start streaming in time"
    },
    cause,
)

/**
 * Pure policy for CameraX runtime health and the first-preview watchdog.
 *
 * CameraX automatically retries recoverable errors while moving through pending/opening/closing
 * states. Those transitions must not eject the operator into manual entry. Critical errors fail
 * immediately; a separate bounded watchdog catches a session that binds but never renders a frame.
 */
internal object ScannerCameraRuntimePolicy {
    const val FIRST_PREVIEW_TIMEOUT_MS = 3_000L

    fun decide(
        phase: ScannerCameraRuntimePhase,
        errorSeverity: ScannerCameraRuntimeErrorSeverity,
    ): ScannerCameraRuntimeAction = when {
        errorSeverity == ScannerCameraRuntimeErrorSeverity.CRITICAL ->
            ScannerCameraRuntimeAction.FAIL
        errorSeverity == ScannerCameraRuntimeErrorSeverity.RECOVERABLE ->
            ScannerCameraRuntimeAction.WAIT_FOR_RECOVERY
        phase == ScannerCameraRuntimePhase.OPEN -> ScannerCameraRuntimeAction.HEALTHY
        else -> ScannerCameraRuntimeAction.WAIT_FOR_RECOVERY
    }

    fun shouldReportFirstPreviewTimeout(
        generationMatches: Boolean,
        cameraBound: Boolean,
        lifecycleActive: Boolean,
        shouldContinue: Boolean,
        firstPreviewObserved: Boolean,
    ): Boolean = generationMatches &&
        cameraBound &&
        lifecycleActive &&
        shouldContinue &&
        !firstPreviewObserved
}
