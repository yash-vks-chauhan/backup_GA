package com.gridee.parking.ui.qr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerCameraConfigUpdatePolicyTest {

    @Test
    fun `analysis size change rebinds immediately only from an idle scanner`() {
        val idle = ScannerCameraConfigUpdatePolicy.decide(
            analysisSizeChanged = true,
            protectedOperatorState = false,
        )
        val protected = ScannerCameraConfigUpdatePolicy.decide(
            analysisSizeChanged = true,
            protectedOperatorState = true,
        )

        assertTrue(idle.rebindNow)
        assertFalse(idle.keepPending)
        assertFalse(protected.rebindNow)
        assertTrue(protected.keepPending)
    }

    @Test
    fun `unchanged analysis size never schedules a camera rebind`() {
        val decision = ScannerCameraConfigUpdatePolicy.decide(
            analysisSizeChanged = false,
            protectedOperatorState = true,
        )

        assertFalse(decision.rebindNow)
        assertFalse(decision.keepPending)
    }
}
