package com.gridee.parking.ui.qr

/** Keeps the pre-layout UNKNOWN state distinct from a measured unsupported viewport. */
internal object ScannerViewportStatePolicy {
    fun isUnsupported(layoutKnown: Boolean, liveScanningSupported: Boolean): Boolean =
        layoutKnown && !liveScanningSupported

    fun canStartLiveScanning(layoutKnown: Boolean, liveScanningSupported: Boolean): Boolean =
        layoutKnown && liveScanningSupported
}
