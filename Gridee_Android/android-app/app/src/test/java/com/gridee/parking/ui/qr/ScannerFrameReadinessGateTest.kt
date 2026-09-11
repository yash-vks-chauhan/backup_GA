package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerFrameReadinessGateTest {

    @Test
    fun `readiness requires both preview streaming and a routed analyzer frame`() {
        val gate = ScannerFrameReadinessGate(ScannerFrameReadinessMode.PLATE)

        assertFalse(gate.updatePreviewStreaming(true).isReady)
        assertTrue(gate.analyzerFrameRouted(ScannerFrameReadinessMode.PLATE).isReady)
    }

    @Test
    fun `mode switch invalidates the previous mode frame`() {
        val gate = ScannerFrameReadinessGate(ScannerFrameReadinessMode.PLATE)
        gate.updatePreviewStreaming(true)
        gate.analyzerFrameRouted(ScannerFrameReadinessMode.PLATE)

        val switched = gate.selectMode(ScannerFrameReadinessMode.QR)

        assertFalse(switched.isReady)
        assertFalse(gate.analyzerFrameRouted(ScannerFrameReadinessMode.PLATE).isReady)
        assertTrue(gate.analyzerFrameRouted(ScannerFrameReadinessMode.QR).isReady)
    }

    @Test
    fun `stream interruption requires a fresh analyzer frame`() {
        val gate = ScannerFrameReadinessGate(ScannerFrameReadinessMode.QR)
        gate.updatePreviewStreaming(true)
        gate.analyzerFrameRouted(ScannerFrameReadinessMode.QR)

        assertFalse(gate.updatePreviewStreaming(false).isReady)
        assertFalse(gate.updatePreviewStreaming(true).isReady)
        assertTrue(gate.analyzerFrameRouted(ScannerFrameReadinessMode.QR).isReady)
    }

    @Test
    fun `same mode selection preserves readiness`() {
        val gate = ScannerFrameReadinessGate(ScannerFrameReadinessMode.PLATE)
        gate.updatePreviewStreaming(true)
        gate.analyzerFrameRouted(ScannerFrameReadinessMode.PLATE)

        val state = gate.selectMode(ScannerFrameReadinessMode.PLATE)

        assertTrue(state.isReady)
        assertEquals(ScannerFrameReadinessMode.PLATE, state.selectedMode)
    }
}
