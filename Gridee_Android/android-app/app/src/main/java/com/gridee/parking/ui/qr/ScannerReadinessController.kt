package com.gridee.parking.ui.qr

internal enum class ScannerReadinessState {
    PREPARING,
    READY,
    UNAVAILABLE,
}

/** Ignores stale Google Play module callbacks after a retry or Activity state change. */
internal class ScannerReadinessController {
    var state: ScannerReadinessState = ScannerReadinessState.PREPARING
        private set

    private var activeRequestId = 0L

    @Synchronized
    fun beginPreparation(): Long {
        state = ScannerReadinessState.PREPARING
        return ++activeRequestId
    }

    @Synchronized
    fun isActive(requestId: Long): Boolean = requestId == activeRequestId

    @Synchronized
    fun markReady(requestId: Long): Boolean {
        if (requestId != activeRequestId) return false
        state = ScannerReadinessState.READY
        return true
    }

    @Synchronized
    fun markUnavailable(requestId: Long): Boolean {
        if (requestId != activeRequestId) return false
        state = ScannerReadinessState.UNAVAILABLE
        return true
    }
}
