package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerCameraRuntimePolicyTest {

    @Test
    fun `opening pending closing and closed states wait without a critical error`() {
        val transitionalPhases = listOf(
            ScannerCameraRuntimePhase.PENDING_OPEN,
            ScannerCameraRuntimePhase.OPENING,
            ScannerCameraRuntimePhase.CLOSING,
            ScannerCameraRuntimePhase.CLOSED,
        )

        transitionalPhases.forEach { phase ->
            assertEquals(
                ScannerCameraRuntimeAction.WAIT_FOR_RECOVERY,
                ScannerCameraRuntimePolicy.decide(
                    phase = phase,
                    errorSeverity = ScannerCameraRuntimeErrorSeverity.NONE,
                ),
            )
        }
    }

    @Test
    fun `open state without an error is healthy`() {
        assertEquals(
            ScannerCameraRuntimeAction.HEALTHY,
            ScannerCameraRuntimePolicy.decide(
                phase = ScannerCameraRuntimePhase.OPEN,
                errorSeverity = ScannerCameraRuntimeErrorSeverity.NONE,
            ),
        )
    }

    @Test
    fun `recoverable errors wait for CameraX recovery even when camera was open`() {
        ScannerCameraRuntimePhase.entries.forEach { phase ->
            assertEquals(
                ScannerCameraRuntimeAction.WAIT_FOR_RECOVERY,
                ScannerCameraRuntimePolicy.decide(
                    phase = phase,
                    errorSeverity = ScannerCameraRuntimeErrorSeverity.RECOVERABLE,
                ),
            )
        }
    }

    @Test
    fun `critical error fails immediately in every camera phase`() {
        ScannerCameraRuntimePhase.entries.forEach { phase ->
            assertEquals(
                ScannerCameraRuntimeAction.FAIL,
                ScannerCameraRuntimePolicy.decide(
                    phase = phase,
                    errorSeverity = ScannerCameraRuntimeErrorSeverity.CRITICAL,
                ),
            )
        }
    }

    @Test
    fun `first preview watchdog reports only the current active bound generation`() {
        assertTrue(
            ScannerCameraRuntimePolicy.shouldReportFirstPreviewTimeout(
                generationMatches = true,
                cameraBound = true,
                lifecycleActive = true,
                shouldContinue = true,
                firstPreviewObserved = false,
            ),
        )
        assertFalse(
            ScannerCameraRuntimePolicy.shouldReportFirstPreviewTimeout(
                generationMatches = false,
                cameraBound = true,
                lifecycleActive = true,
                shouldContinue = true,
                firstPreviewObserved = false,
            ),
        )
        assertFalse(
            ScannerCameraRuntimePolicy.shouldReportFirstPreviewTimeout(
                generationMatches = true,
                cameraBound = false,
                lifecycleActive = true,
                shouldContinue = true,
                firstPreviewObserved = false,
            ),
        )
        assertFalse(
            ScannerCameraRuntimePolicy.shouldReportFirstPreviewTimeout(
                generationMatches = true,
                cameraBound = true,
                lifecycleActive = false,
                shouldContinue = true,
                firstPreviewObserved = false,
            ),
        )
        assertFalse(
            ScannerCameraRuntimePolicy.shouldReportFirstPreviewTimeout(
                generationMatches = true,
                cameraBound = true,
                lifecycleActive = true,
                shouldContinue = false,
                firstPreviewObserved = false,
            ),
        )
        assertFalse(
            ScannerCameraRuntimePolicy.shouldReportFirstPreviewTimeout(
                generationMatches = true,
                cameraBound = true,
                lifecycleActive = true,
                shouldContinue = true,
                firstPreviewObserved = true,
            ),
        )
    }
}
