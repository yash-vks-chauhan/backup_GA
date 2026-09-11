package com.gridee.parking.ui.qr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerUiUpdateGateTest {

    @Test
    fun `deduplicates an unchanged status`() {
        val gate = ScannerUiUpdateGate()

        assertTrue(gate.shouldRenderStatus("scanning", highPriority = false, nowMs = 1_000L))
        assertFalse(gate.shouldRenderStatus("scanning", highPriority = false, nowMs = 2_000L))
    }

    @Test
    fun `throttles rapidly changing transient statuses`() {
        val gate = ScannerUiUpdateGate(transientUpdateIntervalMs = 250L)

        assertTrue(gate.shouldRenderStatus("guess-1", highPriority = false, nowMs = 1_000L))
        assertFalse(gate.shouldRenderStatus("guess-2", highPriority = false, nowMs = 1_100L))
        assertTrue(gate.shouldRenderStatus("guess-2", highPriority = false, nowMs = 1_250L))
    }

    @Test
    fun `never delays a high priority operation result`() {
        val gate = ScannerUiUpdateGate(transientUpdateIntervalMs = 250L)

        assertTrue(gate.shouldRenderStatus("scanning", highPriority = false, nowMs = 1_000L))
        assertTrue(gate.shouldRenderStatus("success", highPriority = true, nowMs = 1_010L))
    }

    @Test
    fun `reset allows the same status to render again`() {
        val gate = ScannerUiUpdateGate()

        assertTrue(gate.shouldRenderStatus("scanning", highPriority = false, nowMs = 1_000L))
        gate.reset()

        assertTrue(gate.shouldRenderStatus("scanning", highPriority = false, nowMs = 1_010L))
    }

    @Test
    fun `renders when any structured panel field changes`() {
        val gate = ScannerUiUpdateGate(transientUpdateIntervalMs = 0L)

        assertTrue(gate.shouldRenderStatus("scanning", highPriority = false, nowMs = 1_000L))
        assertTrue(
            gate.shouldRender(
                modeKey = 0,
                operationKey = 0,
                title = "scanning",
                subtitle = "move closer",
                meta = null,
                showProgress = false,
                highPriority = false,
                nowMs = 1_001L,
            )
        )
        assertTrue(
            gate.shouldRender(
                modeKey = 0,
                operationKey = 1,
                title = "scanning",
                subtitle = "move closer",
                meta = "Gate A",
                showProgress = true,
                highPriority = false,
                nowMs = 1_002L,
            )
        )
    }

    private fun ScannerUiUpdateGate.shouldRenderStatus(
        title: String,
        highPriority: Boolean,
        nowMs: Long,
    ): Boolean {
        return shouldRender(
            modeKey = 0,
            operationKey = 0,
            title = title,
            subtitle = "",
            meta = null,
            showProgress = false,
            highPriority = highPriority,
            nowMs = nowMs,
        )
    }
}
