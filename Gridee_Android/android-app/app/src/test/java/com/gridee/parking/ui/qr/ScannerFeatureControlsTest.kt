package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerFeatureControlsTest {

    @Test
    fun `recognition policy changes invalidate work but result hold timing does not`() {
        val defaults = ScannerFeatureControls()

        assertTrue(
            defaults.copy(forceManualPlateConfirmation = true)
                .invalidatesRecognitionWorkComparedTo(defaults)
        )
        assertTrue(
            defaults.copy(cleanPlateConsensusMatches = 3)
                .invalidatesRecognitionWorkComparedTo(defaults)
        )
        assertTrue(
            defaults.copy(analysisWidth = 960, analysisHeight = 540)
                .invalidatesRecognitionWorkComparedTo(defaults)
        )
        assertFalse(
            defaults.copy(successResultHoldMs = 1_000L, errorResultHoldMs = 2_000L)
                .invalidatesRecognitionWorkComparedTo(defaults)
        )
    }

    @Test
    fun `uses conservative local defaults when config is absent`() {
        val controls = ScannerFeatureControls.from()

        assertTrue(controls.automaticRecognitionEnabled)
        assertEquals(850L, controls.successResultHoldMs)
        assertEquals(1_800L, controls.errorResultHoldMs)
        assertEquals(2, controls.cleanPlateConsensusMatches)
        assertEquals(3, controls.correctedPlateConsensusMatches)
        assertEquals(1_280, controls.analysisWidth)
        assertEquals(1.12f, controls.plateInitialZoomRatio, 0.001f)
        assertTrue(controls.qrAutoZoomEnabled)
        assertTrue(controls.reflectivePlateExposureCompensationEnabled)
        assertFalse(controls.forceManualPlateConfirmation)
    }

    @Test
    fun `reads nested settings and clamps unsafe production values`() {
        val controls = ScannerFeatureControls.from(
            customSettings = mapOf(
                "operatorScanner" to mapOf(
                    "successResultHoldMs" to 100,
                    "errorResultHoldMs" to 20_000,
                    "cleanPlateConsensusMatches" to 1,
                    "correctedPlateConsensusMatches" to 9,
                    "plateConsensusMaxGapMs" to 100,
                    "analysisResolution" to "640x480",
                    "plateInitialZoomRatio" to 3.0,
                ),
            ),
        )

        assertEquals(600L, controls.successResultHoldMs)
        assertEquals(5_000L, controls.errorResultHoldMs)
        assertEquals(2, controls.cleanPlateConsensusMatches)
        assertEquals(5, controls.correctedPlateConsensusMatches)
        assertEquals(500L, controls.plateConsensusMaxGapMs)
        assertEquals(640, controls.analysisWidth)
        assertEquals(480, controls.analysisHeight)
        assertEquals(1.35f, controls.plateInitialZoomRatio, 0.001f)
    }

    @Test
    fun `non finite zoom values fall back to the safe local default`() {
        listOf(Float.NaN, Double.POSITIVE_INFINITY, "NaN", "-Infinity").forEach { value ->
            val controls = ScannerFeatureControls.from(
                customSettings = mapOf(
                    "operatorScanner" to mapOf("plateInitialZoomRatio" to value),
                ),
            )

            assertEquals(1.12f, controls.plateInitialZoomRatio, 0.001f)
        }
    }

    @Test
    fun `non finite numeric controls use defaults instead of unsafe clamps`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            val controls = ScannerFeatureControls.from(
                customSettings = mapOf(
                    "operatorScanner" to mapOf(
                        "successResultHoldMs" to value,
                        "errorResultHoldMs" to value,
                        "cleanPlateConsensusMatches" to value,
                        "correctedPlateConsensusMatches" to value,
                        "plateConsensusMaxGapMs" to value,
                        "qrAutoZoomEnabled" to value,
                        "reflectivePlateExposureCompensationEnabled" to value,
                        "forceManualPlateConfirmation" to value,
                    ),
                ),
            )

            assertEquals(850L, controls.successResultHoldMs)
            assertEquals(1_800L, controls.errorResultHoldMs)
            assertEquals(2, controls.cleanPlateConsensusMatches)
            assertEquals(3, controls.correctedPlateConsensusMatches)
            assertEquals(900L, controls.plateConsensusMaxGapMs)
            assertTrue(controls.qrAutoZoomEnabled)
            assertTrue(controls.reflectivePlateExposureCompensationEnabled)
            assertFalse(controls.forceManualPlateConfirmation)
        }
    }

    @Test
    fun `feature toggles provide emergency auto zoom and auto submit switches`() {
        val controls = ScannerFeatureControls.from(
            featureToggles = mapOf(
                "operator_scanner_automatic_recognition_enabled" to false,
                "operator_scanner_qr_auto_zoom_enabled" to false,
                "operator-scanner-plate-auto-submit" to false,
                "operator-scanner-reflective-plate-exposure-compensation" to false,
            ),
        )

        assertFalse(controls.automaticRecognitionEnabled)
        assertFalse(controls.qrAutoZoomEnabled)
        assertFalse(controls.reflectivePlateExposureCompensationEnabled)
        assertTrue(controls.forceManualPlateConfirmation)
    }

    @Test
    fun `nested automatic recognition setting is supported without a typed backend field`() {
        val controls = ScannerFeatureControls.from(
            customSettings = mapOf(
                "operatorScanner" to mapOf(
                    "automaticRecognitionEnabled" to "off",
                ),
            ),
        )

        assertFalse(controls.automaticRecognitionEnabled)
    }

    @Test
    fun `feature toggle wins over nested automatic recognition setting`() {
        val controls = ScannerFeatureControls.from(
            customSettings = mapOf(
                "operatorScanner" to mapOf(
                    "automaticRecognitionEnabled" to false,
                ),
            ),
            featureToggles = mapOf(
                "operatorScannerAutomaticRecognition" to true,
            ),
        )

        assertTrue(controls.automaticRecognitionEnabled)
    }

    @Test
    fun `invalid automatic recognition setting fails open to tested local default`() {
        val controls = ScannerFeatureControls.from(
            customSettings = mapOf(
                "operatorScannerAutomaticRecognitionEnabled" to "sometimes",
            ),
        )

        assertTrue(controls.automaticRecognitionEnabled)
    }
}
