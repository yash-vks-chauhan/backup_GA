package com.gridee.parking.ui.qr

internal enum class ScannerViewportBindAction {
    BIND,
    RETRY,
    CANCEL,
    FAIL,
}

internal class ScannerViewportUnavailableException(message: String) :
    IllegalStateException(message)

/**
 * Makes CameraX binding deterministic when [androidx.camera.view.PreviewView] has not completed
 * its first layout/transform pass yet. Binding without a ViewPort would let Preview and
 * ImageAnalysis use different crops, so scanner acceptance must fail closed until it exists.
 */
internal object ScannerViewportBindPolicy {
    const val MAX_RETRY_ATTEMPTS = 30
    const val RETRY_DELAY_MS = 32L

    fun decide(
        viewportAvailable: Boolean,
        shouldContinue: Boolean,
        retryAttempt: Int,
    ): ScannerViewportBindAction = when {
        !shouldContinue -> ScannerViewportBindAction.CANCEL
        viewportAvailable -> ScannerViewportBindAction.BIND
        retryAttempt < MAX_RETRY_ATTEMPTS -> ScannerViewportBindAction.RETRY
        else -> ScannerViewportBindAction.FAIL
    }
}
