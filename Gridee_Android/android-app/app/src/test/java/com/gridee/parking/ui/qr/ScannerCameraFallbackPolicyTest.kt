package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScannerCameraFallbackPolicyTest {

    @Test
    fun `granted permission clears only a permission-denied fallback`() {
        assertNull(
            ScannerCameraFallbackPolicy.afterPermissionCheck(
                currentReason = CameraManualFallbackReason.PERMISSION_DENIED,
                permissionGranted = true,
            )
        )
        assertEquals(
            CameraManualFallbackReason.UNAVAILABLE,
            ScannerCameraFallbackPolicy.afterPermissionCheck(
                currentReason = CameraManualFallbackReason.UNAVAILABLE,
                permissionGranted = true,
            ),
        )
    }

    @Test
    fun `missing permission never clears either fallback reason`() {
        CameraManualFallbackReason.entries.forEach { reason ->
            assertEquals(
                reason,
                ScannerCameraFallbackPolicy.afterPermissionCheck(
                    currentReason = reason,
                    permissionGranted = false,
                ),
            )
        }
    }

    @Test
    fun `no prior fallback remains empty after a permission check`() {
        assertNull(
            ScannerCameraFallbackPolicy.afterPermissionCheck(
                currentReason = null,
                permissionGranted = true,
            )
        )
    }
}
