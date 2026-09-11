package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerReadinessControllerTest {

    @Test
    fun `preparation can transition to ready`() {
        val controller = ScannerReadinessController()
        val requestId = controller.beginPreparation()

        assertEquals(ScannerReadinessState.PREPARING, controller.state)
        assertTrue(controller.markReady(requestId))
        assertEquals(ScannerReadinessState.READY, controller.state)
    }

    @Test
    fun `failed preparation can be retried`() {
        val controller = ScannerReadinessController()
        val failedRequest = controller.beginPreparation()
        assertTrue(controller.markUnavailable(failedRequest))

        val retryRequest = controller.beginPreparation()
        assertEquals(ScannerReadinessState.PREPARING, controller.state)
        assertTrue(controller.markReady(retryRequest))
        assertEquals(ScannerReadinessState.READY, controller.state)
    }

    @Test
    fun `stale callback cannot replace a newer request state`() {
        val controller = ScannerReadinessController()
        val staleRequest = controller.beginPreparation()
        val activeRequest = controller.beginPreparation()

        assertFalse(controller.markUnavailable(staleRequest))
        assertTrue(controller.markReady(activeRequest))
        assertEquals(ScannerReadinessState.READY, controller.state)
    }
}
