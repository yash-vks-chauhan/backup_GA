package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerStateMachineTest {

    @Test
    fun `models the normal successful scan lifecycle`() {
        val machine = ScannerStateMachine()

        assertEquals(ScannerLifecycleState.SCANNING, machine.accept(ScannerLifecycleEvent.READY).current)
        assertEquals(ScannerLifecycleState.CANDIDATE, machine.accept(ScannerLifecycleEvent.CANDIDATE_FOUND).current)
        assertEquals(ScannerLifecycleState.PROCESSING, machine.accept(ScannerLifecycleEvent.SUBMIT).current)
        assertEquals(ScannerLifecycleState.SUCCESS, machine.accept(ScannerLifecycleEvent.SUCCEEDED).current)
        assertEquals(ScannerLifecycleState.SCANNING, machine.accept(ScannerLifecycleEvent.RESET).current)
    }

    @Test
    fun `ignores success without an active processing state`() {
        val machine = ScannerStateMachine(ScannerLifecycleState.SCANNING)

        val transition = machine.accept(ScannerLifecycleEvent.SUCCEEDED)

        assertFalse(transition.changed)
        assertEquals(ScannerLifecycleState.SCANNING, transition.current)
    }

    @Test
    fun `unavailable scanner rejects automatic submit until ready`() {
        val machine = ScannerStateMachine()
        assertTrue(machine.accept(ScannerLifecycleEvent.MODEL_UNAVAILABLE).changed)

        assertEquals(ScannerLifecycleState.UNAVAILABLE, machine.accept(ScannerLifecycleEvent.SUBMIT).current)
        assertEquals(ScannerLifecycleState.SCANNING, machine.accept(ScannerLifecycleEvent.READY).current)
        assertEquals(ScannerLifecycleState.PROCESSING, machine.accept(ScannerLifecycleEvent.SUBMIT).current)
    }

    @Test
    fun `manual fallback can submit while camera or models are unavailable`() {
        listOf(
            ScannerLifecycleState.PREPARING,
            ScannerLifecycleState.UNAVAILABLE,
            ScannerLifecycleState.ERROR,
            ScannerLifecycleState.SUCCESS,
        ).forEach { initialState ->
            val machine = ScannerStateMachine(initialState)

            assertEquals(
                ScannerLifecycleState.PROCESSING,
                machine.accept(ScannerLifecycleEvent.MANUAL_SUBMIT).current,
            )
            assertEquals(
                ScannerLifecycleState.SUCCESS,
                machine.accept(ScannerLifecycleEvent.SUCCEEDED).current,
            )
        }
    }

    @Test
    fun `manual submit cannot replace an existing timed out mutation`() {
        val processing = ScannerStateMachine(ScannerLifecycleState.PROCESSING)
        val timedOut = ScannerStateMachine(ScannerLifecycleState.TIMEOUT)

        assertFalse(processing.accept(ScannerLifecycleEvent.MANUAL_SUBMIT).changed)
        assertFalse(timedOut.accept(ScannerLifecycleEvent.MANUAL_SUBMIT).changed)
    }

    @Test
    fun `offline manual fallback becomes an error without unlocking a mutation`() {
        val unavailable = ScannerStateMachine(ScannerLifecycleState.UNAVAILABLE)
        val processing = ScannerStateMachine(ScannerLifecycleState.PROCESSING)

        assertEquals(
            ScannerLifecycleState.ERROR,
            unavailable.accept(ScannerLifecycleEvent.LOCAL_ERROR).current,
        )
        assertFalse(processing.accept(ScannerLifecycleEvent.LOCAL_ERROR).changed)
    }

    @Test
    fun `retained mutation restores processing without erasing a prior timeout`() {
        val recreated = ScannerStateMachine(ScannerLifecycleState.PREPARING)
        val timedOut = ScannerStateMachine(ScannerLifecycleState.TIMEOUT)

        assertEquals(
            ScannerLifecycleState.PROCESSING,
            recreated.accept(ScannerLifecycleEvent.RESTORE_PROCESSING).current,
        )
        assertFalse(timedOut.accept(ScannerLifecycleEvent.RESTORE_PROCESSING).changed)
    }

    @Test
    fun `late model readiness callbacks cannot overwrite a retained mutation`() {
        listOf(
            ScannerLifecycleEvent.PREPARE,
            ScannerLifecycleEvent.READY,
            ScannerLifecycleEvent.MODEL_UNAVAILABLE,
        ).forEach { event ->
            assertFalse(
                ScannerStateMachine(ScannerLifecycleState.PROCESSING).accept(event).changed
            )
            assertFalse(
                ScannerStateMachine(ScannerLifecycleState.TIMEOUT).accept(event).changed
            )
        }
    }

    @Test
    fun `cooldown rejection exits provisional processing state`() {
        val machine = ScannerStateMachine(ScannerLifecycleState.SCANNING)
        machine.accept(ScannerLifecycleEvent.SUBMIT)

        assertEquals(
            ScannerLifecycleState.ERROR,
            machine.accept(ScannerLifecycleEvent.REJECTED_BEFORE_START).current,
        )
        assertEquals(
            ScannerLifecycleState.SCANNING,
            machine.accept(ScannerLifecycleEvent.RESET).current,
        )
    }

    @Test
    fun `presentation timeout remains locked until terminal result`() {
        val machine = ScannerStateMachine(ScannerLifecycleState.SCANNING)
        machine.accept(ScannerLifecycleEvent.SUBMIT)

        assertEquals(ScannerLifecycleState.TIMEOUT, machine.accept(ScannerLifecycleEvent.TIMED_OUT).current)
        assertEquals(ScannerLifecycleState.TIMEOUT, machine.accept(ScannerLifecycleEvent.SUBMIT).current)
        assertEquals(ScannerLifecycleState.SUCCESS, machine.accept(ScannerLifecycleEvent.SUCCEEDED).current)
    }
}
