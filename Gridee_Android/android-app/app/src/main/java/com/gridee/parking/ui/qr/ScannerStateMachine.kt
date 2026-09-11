package com.gridee.parking.ui.qr

internal enum class ScannerLifecycleState {
    PREPARING,
    SCANNING,
    CANDIDATE,
    PROCESSING,
    SUCCESS,
    ERROR,
    TIMEOUT,
    UNAVAILABLE,
}

internal enum class ScannerLifecycleEvent {
    PREPARE,
    READY,
    CANDIDATE_FOUND,
    SUBMIT,
    MANUAL_SUBMIT,
    RESTORE_PROCESSING,
    SUCCEEDED,
    FAILED,
    LOCAL_ERROR,
    REJECTED_BEFORE_START,
    TIMED_OUT,
    RESET,
    MODEL_UNAVAILABLE,
}

internal data class ScannerStateTransition(
    val previous: ScannerLifecycleState,
    val current: ScannerLifecycleState,
) {
    val changed: Boolean get() = previous != current
}

/** Explicit, testable scanner lifecycle used for telemetry and illegal-transition suppression. */
internal class ScannerStateMachine(
    initialState: ScannerLifecycleState = ScannerLifecycleState.PREPARING,
) {
    var state: ScannerLifecycleState = initialState
        private set

    @Synchronized
    fun accept(event: ScannerLifecycleEvent): ScannerStateTransition {
        val previous = state
        state = when (event) {
            ScannerLifecycleEvent.PREPARE -> when (state) {
                ScannerLifecycleState.PROCESSING,
                ScannerLifecycleState.TIMEOUT -> state
                else -> ScannerLifecycleState.PREPARING
            }
            ScannerLifecycleEvent.READY -> when (state) {
                ScannerLifecycleState.PROCESSING,
                ScannerLifecycleState.TIMEOUT -> state
                else -> ScannerLifecycleState.SCANNING
            }
            ScannerLifecycleEvent.MODEL_UNAVAILABLE -> when (state) {
                ScannerLifecycleState.PROCESSING,
                ScannerLifecycleState.TIMEOUT -> state
                else -> ScannerLifecycleState.UNAVAILABLE
            }
            ScannerLifecycleEvent.CANDIDATE_FOUND -> when (state) {
                ScannerLifecycleState.SCANNING,
                ScannerLifecycleState.CANDIDATE -> ScannerLifecycleState.CANDIDATE
                else -> state
            }
            ScannerLifecycleEvent.SUBMIT -> when (state) {
                ScannerLifecycleState.SCANNING,
                ScannerLifecycleState.CANDIDATE -> ScannerLifecycleState.PROCESSING
                else -> state
            }
            ScannerLifecycleEvent.MANUAL_SUBMIT -> when (state) {
                ScannerLifecycleState.PROCESSING,
                ScannerLifecycleState.TIMEOUT -> state
                else -> ScannerLifecycleState.PROCESSING
            }
            ScannerLifecycleEvent.RESTORE_PROCESSING -> when (state) {
                ScannerLifecycleState.TIMEOUT -> ScannerLifecycleState.TIMEOUT
                else -> ScannerLifecycleState.PROCESSING
            }
            ScannerLifecycleEvent.SUCCEEDED -> if (
                state == ScannerLifecycleState.PROCESSING || state == ScannerLifecycleState.TIMEOUT
            ) {
                ScannerLifecycleState.SUCCESS
            } else {
                state
            }
            ScannerLifecycleEvent.FAILED -> if (
                state == ScannerLifecycleState.PROCESSING || state == ScannerLifecycleState.TIMEOUT
            ) {
                ScannerLifecycleState.ERROR
            } else {
                state
            }
            ScannerLifecycleEvent.TIMED_OUT -> if (state == ScannerLifecycleState.PROCESSING) {
                ScannerLifecycleState.TIMEOUT
            } else {
                state
            }
            ScannerLifecycleEvent.LOCAL_ERROR -> when (state) {
                ScannerLifecycleState.PROCESSING,
                ScannerLifecycleState.TIMEOUT -> state
                else -> ScannerLifecycleState.ERROR
            }
            ScannerLifecycleEvent.REJECTED_BEFORE_START -> ScannerLifecycleState.ERROR
            ScannerLifecycleEvent.RESET -> when (state) {
                ScannerLifecycleState.PREPARING,
                ScannerLifecycleState.UNAVAILABLE -> state
                else -> ScannerLifecycleState.SCANNING
            }
        }
        return ScannerStateTransition(previous, state)
    }
}
