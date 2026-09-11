package com.gridee.parking.ui.qr

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerFrameQualityTest {

    @Test
    fun estimator_reportsMeanAndNoDetailForUniformFrame() {
        val bytes = ByteArray(16 * 12) { 40 }

        val quality = ScannerFrameQualityEstimator.estimate(
            buffer = ByteBuffer.wrap(bytes),
            rowStride = 16,
            pixelStride = 1,
            left = 0,
            top = 0,
            right = 16,
            bottom = 12
        )

        assertNotNull(quality)
        assertEquals(40, quality?.meanLuma)
        assertEquals(0.0, quality?.detailScore ?: -1.0, 0.001)
    }

    @Test
    fun estimator_respectsAPlaneBufferStartingOffset() {
        val bytes = ByteArray(1 + (4 * 4)) { 40 }.also { it[0] = 120 }
        val buffer = ByteBuffer.wrap(bytes).apply { position(1) }

        val quality = ScannerFrameQualityEstimator.estimate(
            buffer = buffer,
            rowStride = 4,
            pixelStride = 1,
            left = 0,
            top = 0,
            right = 4,
            bottom = 4,
        )

        assertEquals(40, quality?.meanLuma)
    }

    @Test
    fun estimator_respectsCropAndPixelStride() {
        val rowStride = 20
        val bytes = ByteArray(rowStride * 8)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                bytes[(y * rowStride) + (x * 2)] = (20 + x * 10).toByte()
            }
        }

        val quality = ScannerFrameQualityEstimator.estimate(
            buffer = ByteBuffer.wrap(bytes),
            rowStride = rowStride,
            pixelStride = 2,
            left = 2,
            top = 1,
            right = 7,
            bottom = 7
        )

        assertNotNull(quality)
        assertTrue((quality?.meanLuma ?: 0) in 55..65)
        assertTrue((quality?.detailScore ?: 0.0) > 4.0)
    }

    @Test
    fun lightingMonitorRequiresSustainedDarknessAndRecovery() {
        val monitor = ScannerLightingMonitor(
            darkThreshold = 45,
            recoveryThreshold = 60,
            darkFramesRequired = 2,
            recoveryFramesRequired = 2
        )

        assertEquals(ScannerLightingMonitor.Update.NONE, monitor.observe(40))
        assertEquals(ScannerLightingMonitor.Update.BECAME_DARK, monitor.observe(42))
        assertEquals(ScannerLightingMonitor.Update.NONE, monitor.observe(62))
        assertEquals(ScannerLightingMonitor.Update.RECOVERED, monitor.observe(65))
    }

    @Test
    fun unusableFrameGateNeverStarvesOcr() {
        val gate = PlateFrameUsabilityGate(forcedAnalysisInterval = 3)
        val unusable = ScannerFrameQuality(meanLuma = 10, detailScore = 0.5)
        val usable = ScannerFrameQuality(meanLuma = 80, detailScore = 8.0)

        assertFalse(gate.shouldAnalyze(unusable))
        assertFalse(gate.shouldAnalyze(unusable))
        assertTrue(gate.shouldAnalyze(unusable))
        assertTrue(gate.shouldAnalyze(usable))
        assertFalse(gate.shouldAnalyze(unusable))
    }

    @Test
    fun reflectiveExposureAdvisorUsesHysteresis() {
        val advisor = ReflectivePlateExposureAdvisor(
            reflectiveThreshold = 205,
            recoveryThreshold = 178,
            reflectiveFramesRequired = 2,
            recoveryFramesRequired = 2,
        )

        assertEquals(ReflectivePlateExposureAdvisor.Update.NONE, advisor.observe(210))
        assertEquals(ReflectivePlateExposureAdvisor.Update.REDUCE_EXPOSURE, advisor.observe(220))
        assertEquals(ReflectivePlateExposureAdvisor.Update.NONE, advisor.observe(190))
        assertEquals(ReflectivePlateExposureAdvisor.Update.NONE, advisor.observe(170))
        assertEquals(ReflectivePlateExposureAdvisor.Update.RESTORE_EXPOSURE, advisor.observe(175))
    }
}
