package com.gridee.parking.ui.qr

internal enum class CameraManualFallbackReason {
    PERMISSION_DENIED,
    UNAVAILABLE,
}

/** Keeps a runtime camera failure sticky until the operator explicitly chooses Retry Camera. */
internal object ScannerCameraFallbackPolicy {
    fun afterPermissionCheck(
        currentReason: CameraManualFallbackReason?,
        permissionGranted: Boolean,
    ): CameraManualFallbackReason? {
        return if (
            permissionGranted &&
            currentReason == CameraManualFallbackReason.PERMISSION_DENIED
        ) {
            null
        } else {
            currentReason
        }
    }
}
