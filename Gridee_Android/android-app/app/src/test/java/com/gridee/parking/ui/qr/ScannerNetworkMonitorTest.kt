package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerNetworkMonitorTest {

    @Test
    fun `unvalidated network cannot submit a mutation`() {
        val snapshot = ScannerNetworkClassifier.classify(
            hasActiveNetwork = true,
            hasInternetCapability = true,
            isValidated = false,
            downstreamKbps = 10_000,
            transport = "wifi",
        )

        assertEquals(ScannerNetworkReadiness.OFFLINE, snapshot.readiness)
        assertFalse(snapshot.canSubmitMutation)
    }

    @Test
    fun `validated low bandwidth is degraded but remains submittable`() {
        val snapshot = ScannerNetworkClassifier.classify(
            hasActiveNetwork = true,
            hasInternetCapability = true,
            isValidated = true,
            downstreamKbps = 128,
            transport = "cellular",
        )

        assertEquals(ScannerNetworkReadiness.DEGRADED, snapshot.readiness)
        assertTrue(snapshot.canSubmitMutation)
    }

    @Test
    fun `validated normal network is ready`() {
        val snapshot = ScannerNetworkClassifier.classify(
            hasActiveNetwork = true,
            hasInternetCapability = true,
            isValidated = true,
            downstreamKbps = 20_000,
            transport = "wifi",
        )

        assertEquals(ScannerNetworkReadiness.READY, snapshot.readiness)
        assertTrue(snapshot.canSubmitMutation)
    }
}
