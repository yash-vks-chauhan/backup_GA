package com.gridee.parking.ui.qr

internal enum class ScannerFrameReadinessMode {
    PLATE,
    QR,
}

internal data class ScannerFrameReadinessState(
    val selectedMode: ScannerFrameReadinessMode,
    val previewStreaming: Boolean,
    val analyzerFrameRouted: Boolean,
) {
    val isReady: Boolean
        get() = previewStreaming && analyzerFrameRouted
}

/** Thread-safe readiness gate shared by PreviewView's observer and the camera analyzer thread. */
internal class ScannerFrameReadinessGate(initialMode: ScannerFrameReadinessMode) {
    private var selectedMode = initialMode
    private var previewStreaming = false
    private var analyzerFrameRouted = false

    @Synchronized
    fun selectMode(mode: ScannerFrameReadinessMode): ScannerFrameReadinessState {
        if (mode != selectedMode) {
            selectedMode = mode
            analyzerFrameRouted = false
        }
        return snapshotLocked()
    }

    @Synchronized
    fun updatePreviewStreaming(streaming: Boolean): ScannerFrameReadinessState {
        previewStreaming = streaming
        if (!streaming) analyzerFrameRouted = false
        return snapshotLocked()
    }

    @Synchronized
    fun analyzerFrameRouted(mode: ScannerFrameReadinessMode): ScannerFrameReadinessState {
        if (mode == selectedMode) analyzerFrameRouted = true
        return snapshotLocked()
    }

    @Synchronized
    fun snapshot(): ScannerFrameReadinessState = snapshotLocked()

    private fun snapshotLocked() = ScannerFrameReadinessState(
        selectedMode = selectedMode,
        previewStreaming = previewStreaming,
        analyzerFrameRouted = analyzerFrameRouted,
    )
}
