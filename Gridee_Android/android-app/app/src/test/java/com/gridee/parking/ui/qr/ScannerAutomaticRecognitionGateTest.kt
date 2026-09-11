package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerAutomaticRecognitionGateTest {

    @Test
    fun `default gate allows new Plate and QR automatic recognition`() {
        val gate = ScannerAutomaticRecognitionGate()

        ScannerAutomaticRecognitionSource.values().forEach { source ->
            val generation = gate.beginAutomaticRecognition(source, mutationInFlight = false)

            assertTrue(generation >= 0L)
            assertEquals(
                ScannerRecognitionDecision.ALLOW,
                gate.automaticCommitDecision(source, generation, mutationInFlight = false),
            )
        }
    }

    @Test
    fun `disabled gate blocks both automatic sources but leaves manual plate entry available`() {
        val gate = ScannerAutomaticRecognitionGate(initialEnabled = false)

        ScannerAutomaticRecognitionSource.values().forEach { source ->
            val generation = gate.beginAutomaticRecognition(source, mutationInFlight = false)

            assertEquals(ScannerAutomaticRecognitionGate.REJECTED_GENERATION, generation)
            assertEquals(
                ScannerRecognitionDecision.AUTOMATIC_RECOGNITION_DISABLED,
                gate.automaticCommitDecision(source, generation, mutationInFlight = false),
            )
        }
        assertEquals(
            ScannerRecognitionDecision.ALLOW,
            gate.manualPlateCommitDecision(mutationInFlight = false),
        )
    }

    @Test
    fun `disabling invalidates an analyzer result that started while enabled`() {
        val gate = ScannerAutomaticRecognitionGate()
        val generation = gate.beginAutomaticRecognition(
            ScannerAutomaticRecognitionSource.QR,
            mutationInFlight = false,
        )

        val update = gate.updateEnabled(newEnabled = false, mutationInFlight = false)

        assertTrue(update.changed)
        assertTrue(update.shouldInvalidateAutomaticWork)
        assertFalse(update.automaticPipelineMayRun)
        assertEquals(
            ScannerRecognitionDecision.AUTOMATIC_RECOGNITION_DISABLED,
            gate.automaticCommitDecision(
                ScannerAutomaticRecognitionSource.QR,
                generation,
                mutationInFlight = false,
            ),
        )
    }

    @Test
    fun `quick disable and re-enable still rejects callbacks from the old generation`() {
        val gate = ScannerAutomaticRecognitionGate()
        val oldGeneration = gate.beginAutomaticRecognition(
            ScannerAutomaticRecognitionSource.PLATE,
            mutationInFlight = false,
        )

        gate.updateEnabled(newEnabled = false, mutationInFlight = false)
        gate.updateEnabled(newEnabled = true, mutationInFlight = false)

        assertEquals(
            ScannerRecognitionDecision.STALE_AUTOMATIC_RESULT,
            gate.automaticCommitDecision(
                ScannerAutomaticRecognitionSource.PLATE,
                oldGeneration,
                mutationInFlight = false,
            ),
        )
        val newGeneration = gate.beginAutomaticRecognition(
            ScannerAutomaticRecognitionSource.PLATE,
            mutationInFlight = false,
        )
        assertEquals(
            ScannerRecognitionDecision.ALLOW,
            gate.automaticCommitDecision(
                ScannerAutomaticRecognitionSource.PLATE,
                newGeneration,
                mutationInFlight = false,
            ),
        )
    }

    @Test
    fun `config disable preserves an in-flight mutation and blocks every new input`() {
        val gate = ScannerAutomaticRecognitionGate()

        val update = gate.updateEnabled(newEnabled = false, mutationInFlight = true)

        assertTrue(update.preserveInFlightMutation)
        assertFalse(update.automaticPipelineMayRun)
        ScannerAutomaticRecognitionSource.values().forEach { source ->
            assertEquals(
                ScannerAutomaticRecognitionGate.REJECTED_GENERATION,
                gate.beginAutomaticRecognition(source, mutationInFlight = true),
            )
        }
        assertEquals(
            ScannerRecognitionDecision.MUTATION_IN_FLIGHT,
            gate.manualPlateCommitDecision(mutationInFlight = true),
        )
    }

    @Test
    fun `reapplying the same value does not invalidate current automatic work`() {
        val gate = ScannerAutomaticRecognitionGate()
        val generation = gate.beginAutomaticRecognition(
            ScannerAutomaticRecognitionSource.QR,
            mutationInFlight = false,
        )

        val update = gate.updateEnabled(newEnabled = true, mutationInFlight = false)

        assertFalse(update.changed)
        assertFalse(update.shouldInvalidateAutomaticWork)
        assertEquals(generation, update.generation)
        assertEquals(
            ScannerRecognitionDecision.ALLOW,
            gate.automaticCommitDecision(
                ScannerAutomaticRecognitionSource.QR,
                generation,
                mutationInFlight = false,
            ),
        )
    }

    @Test
    fun `recognition policy update invalidates work even when availability stays enabled`() {
        val gate = ScannerAutomaticRecognitionGate()
        val oldGeneration = gate.beginAutomaticRecognition(
            ScannerAutomaticRecognitionSource.PLATE,
            mutationInFlight = false,
        )

        val update = gate.updateEnabled(
            newEnabled = true,
            mutationInFlight = false,
            invalidateExistingWork = true,
        )

        assertFalse(update.changed)
        assertTrue(update.shouldInvalidateAutomaticWork)
        assertTrue(update.automaticPipelineMayRun)
        assertEquals(
            ScannerRecognitionDecision.STALE_AUTOMATIC_RESULT,
            gate.automaticCommitDecision(
                ScannerAutomaticRecognitionSource.PLATE,
                oldGeneration,
                mutationInFlight = false,
            ),
        )
    }

    @Test
    fun `live automatic commit context requires the same running scanner session`() {
        val valid = validCommitContext()

        assertTrue(valid.isValid())
        assertFalse(valid.copy(scannerRunning = false).isValid())
        assertFalse(valid.copy(currentSessionId = 18L).isValid())
    }

    @Test
    fun `automatic commit context rejects mode lifecycle and blocking UI races`() {
        val valid = validCommitContext()

        assertFalse(valid.copy(sourceModeActive = false).isValid())
        assertFalse(valid.copy(lifecycleResumed = false).isValid())
        assertFalse(valid.copy(blockingInteraction = true).isValid())
        assertFalse(valid.copy(activityClosing = true).isValid())
        assertFalse(valid.copy(viewportReady = false).isValid())
        assertFalse(valid.copy(detectorReady = false).isValid())
    }

    @Test
    fun `explicit plate confirmation may commit after analyzer stop but not after unsafe UI state`() {
        val stoppedConfirmation = validCommitContext().copy(
            scannerRunning = false,
            currentSessionId = 18L,
            allowStoppedOperatorConfirmation = true,
        )

        assertTrue(stoppedConfirmation.isValid())
        assertFalse(stoppedConfirmation.copy(lifecycleResumed = false).isValid())
        assertFalse(stoppedConfirmation.copy(blockingInteraction = true).isValid())
        assertFalse(stoppedConfirmation.copy(sourceModeActive = false).isValid())
    }

    @Test
    fun `frame runtime guard rejects stale session mode and recognition generation`() {
        val current = validFrameRuntimeGuard()

        assertTrue(current.isCurrent())
        assertFalse(current.copy(currentSessionId = 18L).isCurrent())
        assertFalse(current.copy(scannerRunning = false).isCurrent())
        assertFalse(current.copy(sourceModeActive = false).isCurrent())
        assertFalse(current.copy(operationActive = false).isCurrent())
        assertFalse(current.copy(scanCompleted = true).isCurrent())
        assertFalse(current.copy(mutationInFlight = true).isCurrent())
        assertFalse(current.copy(activityClosing = true).isCurrent())
        assertFalse(
            current.copy(
                recognitionDecision = ScannerRecognitionDecision.STALE_AUTOMATIC_RESULT,
            ).isCurrent(),
        )
    }

    private fun validCommitContext() = ScannerAutomaticCommitContext(
        expectedSessionId = 17L,
        currentSessionId = 17L,
        scannerRunning = true,
        sourceModeActive = true,
        lifecycleResumed = true,
        viewportReady = true,
        detectorReady = true,
        blockingInteraction = false,
        activityClosing = false,
    )

    private fun validFrameRuntimeGuard() = ScannerFrameRuntimeGuard(
        expectedSessionId = 17L,
        currentSessionId = 17L,
        scannerRunning = true,
        sourceModeActive = true,
        operationActive = true,
        scanCompleted = false,
        mutationInFlight = false,
        activityClosing = false,
        recognitionDecision = ScannerRecognitionDecision.ALLOW,
    )
}
