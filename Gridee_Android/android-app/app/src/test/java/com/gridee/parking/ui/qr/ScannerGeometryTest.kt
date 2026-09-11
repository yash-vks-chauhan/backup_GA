package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerGeometryTest {

    private val scanRegion = ScannerBounds(100f, 100f, 300f, 300f)

    @Test
    fun `QR policy accepts a code substantially inside the centre region`() {
        val policy = OperatorQrAcceptancePolicy(minimumContainmentRatio = 0.70f)

        assertTrue(policy.accepts(ScannerBounds(120f, 120f, 280f, 280f), scanRegion))
    }

    @Test
    fun `QR policy rejects missing and mostly outside bounds`() {
        val policy = OperatorQrAcceptancePolicy(minimumContainmentRatio = 0.70f)

        assertFalse(policy.accepts(null, scanRegion))
        assertFalse(policy.accepts(ScannerBounds(250f, 250f, 410f, 410f), scanRegion))
    }

    @Test
    fun `containment and centre distance are normalized`() {
        val centred = ScannerBounds(150f, 150f, 250f, 250f)

        assertEquals(1f, ScannerGeometry.containmentRatio(centred, scanRegion), 0.001f)
        assertEquals(0f, ScannerGeometry.normalizedCenterDistance(centred, scanRegion), 0.001f)
    }
}
