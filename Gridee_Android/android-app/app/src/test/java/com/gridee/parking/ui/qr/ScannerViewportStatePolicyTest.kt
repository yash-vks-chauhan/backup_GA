package com.gridee.parking.ui.qr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerViewportStatePolicyTest {

    @Test
    fun `unknown startup viewport is not treated as unsupported`() {
        assertFalse(ScannerViewportStatePolicy.isUnsupported(false, false))
        assertFalse(ScannerViewportStatePolicy.canStartLiveScanning(false, false))
    }

    @Test
    fun `measured viewport selects exactly one supported state`() {
        assertTrue(ScannerViewportStatePolicy.isUnsupported(true, false))
        assertFalse(ScannerViewportStatePolicy.canStartLiveScanning(true, false))
        assertFalse(ScannerViewportStatePolicy.isUnsupported(true, true))
        assertTrue(ScannerViewportStatePolicy.canStartLiveScanning(true, true))
    }
}
