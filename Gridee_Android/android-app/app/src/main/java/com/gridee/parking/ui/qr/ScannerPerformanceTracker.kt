package com.gridee.parking.ui.qr

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.gridee.parking.utils.AppLog
import com.google.firebase.analytics.FirebaseAnalytics
import com.gridee.parking.BuildConfig
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

internal data class ScannerMetricsEvent(
    val name: String,
    val parameters: Map<String, Any>
)

internal fun interface ScannerMetricsSink {
    fun emit(event: ScannerMetricsEvent)
}

/**
 * Identifies the attempt that owned a frame when CameraX dispatched it.
 *
 * Preprocessing happens off the main thread, so the active attempt may change before crop,
 * quality, or detector work is recorded. Every frame-scoped metric must carry this token rather
 * than looking up whichever attempt happens to be current at callback time.
 */
internal class ScannerFrameToken internal constructor(
    internal val attemptIndex: Int,
    internal val mode: String,
    internal val operation: String,
)

/**
 * Identifies the attempt that owned an asynchronous detector invocation.
 *
 * Keeping this token opaque to callers prevents a completion from being charged to a newer
 * attempt when the operator changes mode while ML Kit is still finishing the previous frame.
 */
internal class ScannerAnalysisToken internal constructor(
    internal val attemptIndex: Int,
    internal val startedAtMs: Long,
    internal val mode: String,
    internal val operation: String,
)

/** Identifies the attempt and start time of an asynchronous CameraX bind request. */
internal class ScannerCameraBindingToken internal constructor(
    internal val attemptIndex: Int,
    internal val startedAtMs: Long,
    internal val mode: String,
    internal val operation: String,
)

/** Opaque correlation and monotonic start time that can survive Activity recreation. */
internal class ScannerApiTimingToken internal constructor(
    internal val startedAtMs: Long,
    internal val traceId: String,
)

internal enum class ScannerDetectorMetric {
    QR,
    PLATE,
}

/**
 * Aggregates scanner timings without recording booking IDs, QR contents, or vehicle numbers.
 * Normal frame callbacks only update counters; compact events are emitted for attempt summaries
 * and exceptional detector/camera failures.
 */
internal class ScannerPerformanceTracker(
    private val sink: ScannerMetricsSink,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
    private val sessionId: String = UUID.randomUUID().toString().take(12)
) {
    private data class Attempt(
        val index: Int,
        val startedAtMs: Long,
        var mode: String,
        var operation: String,
        var firstFrameAtMs: Long? = null,
        var firstAnalysisAtMs: Long? = null,
        var firstCandidateAtMs: Long? = null,
        var confirmedAtMs: Long? = null,
        var apiStartedAtMs: Long? = null,
        var apiCompletedAtMs: Long? = null,
        var apiTraceId: String? = null,
        var frames: Long = 0,
        var analyses: Long = 0,
        var completedAnalyses: Long = 0,
        var skippedFrames: Long = 0,
        var preparedInputs: Long = 0,
        var inputPixelTotal: Long = 0,
        var inputPreparationTotalMs: Long = 0,
        var inputPreparationMaxMs: Long = 0,
        var inputMaxWidth: Int = 0,
        var inputMaxHeight: Int = 0,
        var analysisTotalMs: Long = 0,
        var analysisMaxMs: Long = 0,
        var lowLightFrames: Long = 0,
        var autoZoomCount: Long = 0,
        var detectorProcessingFailures: Long = 0,
        var cameraBindFailures: Long = 0,
        var cameraRuntimeFailures: Long = 0,
        var cameraBindCount: Long = 0,
        var cameraReuseCount: Long = 0,
        var cameraBindTotalMs: Long = 0,
        var cameraBindMaxMs: Long = 0,
        var cameraReuseTotalMs: Long = 0,
        var cameraReuseMaxMs: Long = 0,
        var uiFrames: Long = 0,
        var jankyUiFrames: Long = 0,
        var maxUiFrameMs: Long = 0,
        var apiSlow: Boolean = false
    )

    private data class FinishedAttempt(
        val index: Int,
        val mode: String,
        val operation: String,
        val finishedAtMs: Long,
    )

    private val sessionStartedAtMs = clockMs()
    private var sessionMode = "unknown"
    private var sessionOperation = "unknown"
    private var attempt: Attempt? = null
    private var nextAttemptIndex = 1
    private var firstCameraBoundAtMs: Long? = null
    private var firstPreviewAtMs: Long? = null
    private var cameraBindCount = 0L
    private var cameraReuseCount = 0L
    private var cameraBindTotalMs = 0L
    private var cameraBindMaxMs = 0L
    private var cameraReuseTotalMs = 0L
    private var cameraReuseMaxMs = 0L
    private var totalCameraBindFailures = 0L
    private var totalCameraRuntimeFailures = 0L
    private var totalFrames = 0L
    private var totalAnalyses = 0L
    private var totalCompletedAnalyses = 0L
    private var totalSkippedFrames = 0L
    private var totalModeSwitches = 0L
    private var totalLowLightFrames = 0L
    private var totalAutoZoomCount = 0L
    private var totalFocusRequests = 0L
    private var totalFocusSuccesses = 0L
    private var totalFocusFailures = 0L
    private var totalDetectorInitializationFailures = 0L
    private var totalDetectorProcessingFailures = 0L
    private var totalUiFrames = 0L
    private var totalJankyUiFrames = 0L
    private var maxUiFrameMs = 0L
    private var finishedAttempts = 0L
    private var successfulAttempts = 0L
    private var lastFinishedAttempt: FinishedAttempt? = null
    private val emittedDetectorFailureEvents = mutableSetOf<Pair<Int, ScannerDetectorMetric>>()
    private var closed = false

    @Synchronized
    fun start(mode: String, operation: String) {
        sessionMode = mode
        sessionOperation = operation
        beginAttemptLocked(mode, operation, clockMs())
    }

    @Synchronized
    fun beginAttempt(mode: String, operation: String) {
        if (closed) return
        sessionMode = mode
        sessionOperation = operation
        val current = attempt
        if (current == null) {
            beginAttemptLocked(mode, operation, clockMs())
        } else if (current.mode != mode || current.operation != operation) {
            val now = clockMs()
            finishAttemptLocked(
                result = "context_replaced",
                now = now,
                trackForNextReady = false,
            )
            beginAttemptLocked(mode, operation, now)
        }
    }

    @Synchronized
    fun modeSwitched(mode: String, operation: String) {
        if (closed) return
        val now = clockMs()
        totalModeSwitches++
        sessionMode = mode
        sessionOperation = operation
        finishAttemptLocked(
            result = "mode_switched",
            now = now,
            trackForNextReady = false,
        )
        beginAttemptLocked(mode, operation, now)
    }

    @Synchronized
    fun operationChanged(operation: String) {
        if (closed) return
        sessionOperation = operation
        val current = attempt ?: return
        if (current.operation == operation) return
        val now = clockMs()
        val mode = current.mode
        finishAttemptLocked(
            result = "operation_switched",
            now = now,
            trackForNextReady = false,
        )
        beginAttemptLocked(mode, operation, now)
    }

    @Synchronized
    fun cameraBindingStarted(): ScannerCameraBindingToken {
        val now = clockMs()
        if (closed) {
            return ScannerCameraBindingToken(
                attemptIndex = -1,
                startedAtMs = now,
                mode = sessionMode,
                operation = sessionOperation,
            )
        }
        val current = ensureAttemptLocked(sessionMode, sessionOperation, now)
        return ScannerCameraBindingToken(
            attemptIndex = current.index,
            startedAtMs = now,
            mode = current.mode,
            operation = current.operation,
        )
    }

    @Synchronized
    fun cameraReady(reused: Boolean, token: ScannerCameraBindingToken? = null) {
        if (closed) return
        val now = clockMs()
        if (firstCameraBoundAtMs == null) firstCameraBoundAtMs = now
        val durationMs = token?.let { elapsed(it.startedAtMs, now) }?.coerceAtLeast(0L)
        val owningAttempt = attempt?.takeIf { current ->
            token == null || current.index == token.attemptIndex
        }
        if (reused) {
            cameraReuseCount++
            owningAttempt?.let { it.cameraReuseCount++ }
            if (durationMs != null) {
                cameraReuseTotalMs += durationMs
                cameraReuseMaxMs = maxOf(cameraReuseMaxMs, durationMs)
                owningAttempt?.let {
                    it.cameraReuseTotalMs += durationMs
                    it.cameraReuseMaxMs = maxOf(it.cameraReuseMaxMs, durationMs)
                }
            }
        } else {
            cameraBindCount++
            owningAttempt?.let { it.cameraBindCount++ }
            if (durationMs != null) {
                cameraBindTotalMs += durationMs
                cameraBindMaxMs = maxOf(cameraBindMaxMs, durationMs)
                owningAttempt?.let {
                    it.cameraBindTotalMs += durationMs
                    it.cameraBindMaxMs = maxOf(it.cameraBindMaxMs, durationMs)
                }
            }
        }
    }

    @Synchronized
    fun cameraBindFailed(failure: Throwable, token: ScannerCameraBindingToken) {
        if (closed) return
        val now = clockMs()
        totalCameraBindFailures++
        attempt
            ?.takeIf { it.index == token.attemptIndex }
            ?.let { it.cameraBindFailures++ }
        emitOperationalEventLocked(
            name = "scanner_camera_status",
            values = linkedMapOf(
                "status" to "bind_failed",
                "error_type" to throwableMetricType(failure),
                "duration_ms" to elapsed(token.startedAtMs, now),
            ),
            attemptIndex = token.attemptIndex,
            mode = token.mode,
            operation = token.operation,
        )
    }

    @Synchronized
    fun cameraRuntimeFailed(
        reason: ScannerCameraRuntimeFailureReason,
        failure: Throwable,
    ) {
        if (closed) return
        totalCameraRuntimeFailures++
        attempt?.cameraRuntimeFailures = (attempt?.cameraRuntimeFailures ?: 0L) + 1L
        emitOperationalEventLocked(
            name = "scanner_camera_status",
            values = linkedMapOf(
                "status" to "runtime_failed",
                // This enum is a fixed app-owned value; no camera/provider message is emitted.
                "reason" to reason.metricValue,
                "error_type" to throwableMetricType(failure),
            ),
        )
    }

    @Synchronized
    fun previewStreaming() {
        if (firstPreviewAtMs == null) firstPreviewAtMs = clockMs()
    }

    @Synchronized
    fun frameReceived(mode: String): ScannerFrameToken {
        val now = clockMs()
        if (closed) {
            return ScannerFrameToken(
                attemptIndex = -1,
                mode = mode,
                operation = sessionOperation,
            )
        }
        val current = ensureAttemptLocked(mode, sessionOperation, now)
        if (current.firstFrameAtMs == null) current.firstFrameAtMs = now
        current.frames++
        totalFrames++
        return ScannerFrameToken(
            attemptIndex = current.index,
            mode = current.mode,
            operation = current.operation,
        )
    }

    @Synchronized
    fun frameSkipped(token: ScannerFrameToken) {
        if (closed) return
        attemptForFrameLocked(token)?.let { it.skippedFrames++ }
        totalSkippedFrames++
    }

    @Synchronized
    fun inputPrepared(
        token: ScannerFrameToken,
        width: Int,
        height: Int,
        preparationMs: Long,
    ) {
        if (closed || width <= 0 || height <= 0) return
        attemptForFrameLocked(token)?.let {
            it.preparedInputs++
            it.inputPixelTotal += width.toLong() * height.toLong()
            it.inputPreparationTotalMs += preparationMs.coerceAtLeast(0L)
            it.inputPreparationMaxMs = maxOf(
                it.inputPreparationMaxMs,
                preparationMs.coerceAtLeast(0L),
            )
            it.inputMaxWidth = maxOf(it.inputMaxWidth, width)
            it.inputMaxHeight = maxOf(it.inputMaxHeight, height)
        }
    }

    @Synchronized
    fun analysisStarted(token: ScannerFrameToken): ScannerAnalysisToken {
        val now = clockMs()
        if (closed) {
            return ScannerAnalysisToken(
                attemptIndex = -1,
                startedAtMs = now,
                mode = token.mode,
                operation = token.operation,
            )
        }
        attemptForFrameLocked(token)?.let { current ->
            if (current.firstAnalysisAtMs == null) current.firstAnalysisAtMs = now
            current.analyses++
        }
        totalAnalyses++
        return ScannerAnalysisToken(
            attemptIndex = token.attemptIndex,
            startedAtMs = now,
            mode = token.mode,
            operation = token.operation,
        )
    }

    @Synchronized
    fun analysisCompleted(token: ScannerAnalysisToken) {
        if (closed) return
        val duration = (clockMs() - token.startedAtMs).coerceAtLeast(0L)
        attempt?.takeIf { it.index == token.attemptIndex }?.let {
            it.completedAnalyses++
            it.analysisTotalMs += duration
            it.analysisMaxMs = maxOf(it.analysisMaxMs, duration)
        }
        totalCompletedAnalyses++
    }

    @Synchronized
    fun candidateDetected(token: ScannerFrameToken) {
        if (closed) return
        val current = attemptForFrameLocked(token) ?: return
        if (current.firstCandidateAtMs == null) current.firstCandidateAtMs = clockMs()
    }

    @Synchronized
    fun recognitionConfirmed(token: ScannerFrameToken) {
        if (closed) return
        val current = attemptForFrameLocked(token) ?: return
        if (current.confirmedAtMs == null) current.confirmedAtMs = clockMs()
    }

    @Synchronized
    fun reserveApiTrace(): String? {
        if (closed) return null
        val current = attempt ?: return null
        if (current.apiTraceId == null) {
            current.apiTraceId = UUID.randomUUID().toString().take(16)
        }
        return current.apiTraceId
    }

    @Synchronized
    fun apiStarted(traceId: String?): String? {
        if (closed) return null
        val current = attempt ?: return null
        if (current.apiTraceId == null) {
            current.apiTraceId = traceId
                ?.takeIf(String::isNotBlank)
                ?.take(64)
                ?: UUID.randomUUID().toString().take(16)
        }
        if (current.apiStartedAtMs == null) current.apiStartedAtMs = clockMs()
        return current.apiTraceId
    }

    @Synchronized
    fun apiStarted(): String? = apiStarted(reserveApiTrace())

    @Synchronized
    fun apiTimingSnapshot(): ScannerApiTimingToken? {
        val current = attempt ?: return null
        val startedAtMs = current.apiStartedAtMs ?: return null
        val traceId = current.apiTraceId ?: return null
        return ScannerApiTimingToken(startedAtMs = startedAtMs, traceId = traceId)
    }

    /**
     * Reattaches a retained API operation after Activity recreation. The monotonic clock and opaque
     * trace are process-local, so callers should retain this token in a ViewModel, not persist it.
     */
    @Synchronized
    fun reattachApiTiming(token: ScannerApiTimingToken): String? {
        return reattachApiLocked(token.startedAtMs, token.traceId)
    }

    @Synchronized
    fun reattachApi(startedAtElapsedMs: Long, traceId: String?): String? {
        return reattachApiLocked(startedAtElapsedMs, traceId)
    }

    private fun reattachApiLocked(startedAtElapsedMs: Long, traceId: String?): String? {
        if (closed) return null
        val current = attempt ?: return null
        if (current.apiStartedAtMs == null && startedAtElapsedMs in 0L..clockMs() &&
            !traceId.isNullOrBlank()
        ) {
            current.apiStartedAtMs = startedAtElapsedMs
            current.apiTraceId = traceId.take(64)
        }
        return current.apiTraceId
    }

    @Synchronized
    fun apiCompleted() {
        val current = attempt ?: return
        if (current.apiCompletedAtMs == null) current.apiCompletedAtMs = clockMs()
    }

    @Synchronized
    fun apiSlow() {
        attempt?.apiSlow = true
    }

    @Synchronized
    fun lowLightFrame(token: ScannerFrameToken) {
        if (closed) return
        attemptForFrameLocked(token)?.let { it.lowLightFrames++ }
        totalLowLightFrames++
    }

    @Synchronized
    fun autoZoomApplied(token: ScannerFrameToken) {
        if (closed) return
        attemptForFrameLocked(token)?.let { it.autoZoomCount++ }
        totalAutoZoomCount++
    }

    @Synchronized
    fun focusStarted() {
        totalFocusRequests++
    }

    @Synchronized
    fun focusCompleted(successful: Boolean) {
        if (successful) totalFocusSuccesses++ else totalFocusFailures++
    }

    @Synchronized
    fun networkPreflight(readiness: String, transport: String) {
        emitOperationalEventLocked(
            name = "scanner_network_preflight",
            values = linkedMapOf(
                "readiness" to readiness.take(20),
                "transport" to transport.take(20),
            ),
        )
    }

    @Synchronized
    fun lifecycleState(state: String) {
        emitOperationalEventLocked(
            name = "scanner_lifecycle_state",
            values = linkedMapOf("state" to state.take(24)),
        )
    }

    @Synchronized
    fun uiFrame(frameDurationUiNanos: Long, isJank: Boolean) {
        val durationMs = (frameDurationUiNanos.coerceAtLeast(0L) / 1_000_000L)
        totalUiFrames++
        if (isJank) totalJankyUiFrames++
        maxUiFrameMs = maxOf(maxUiFrameMs, durationMs)
        attempt?.let {
            it.uiFrames++
            if (isJank) it.jankyUiFrames++
            it.maxUiFrameMs = maxOf(it.maxUiFrameMs, durationMs)
        }
    }

    @Synchronized
    fun readinessState(state: String, reason: String? = null) {
        emitOperationalEventLocked(
            name = "scanner_readiness",
            values = linkedMapOf(
                "state" to state,
                "reason" to reason.orEmpty().take(40),
            ),
        )
    }

    @Synchronized
    fun automaticRecognitionAvailability(enabled: Boolean) {
        emitOperationalEventLocked(
            name = "scanner_recognition_control",
            values = linkedMapOf(
                "automatic_recognition" to if (enabled) "enabled" else "disabled",
            ),
        )
    }

    @Synchronized
    fun detectorInitializationFailed(detector: String, reason: String) {
        totalDetectorInitializationFailures++
        emitOperationalEventLocked(
            name = "scanner_detector_status",
            values = linkedMapOf(
                "detector" to detector.take(20),
                "status" to "initialization_failed",
                "reason" to reason.take(40),
            ),
        )
    }

    @Synchronized
    fun detectorProcessingFailed(
        detector: ScannerDetectorMetric,
        failure: Throwable,
        token: ScannerAnalysisToken,
    ) {
        if (closed) return
        totalDetectorProcessingFailures++
        attempt
            ?.takeIf { it.index == token.attemptIndex }
            ?.let { it.detectorProcessingFailures++ }
        // A persistent ML failure can occur on every analyzed frame. Count every failure, but emit
        // only the first status event per detector/attempt to keep telemetry off the hot path.
        if (!emittedDetectorFailureEvents.add(token.attemptIndex to detector)) return
        emitOperationalEventLocked(
            name = "scanner_detector_status",
            values = linkedMapOf(
                "detector" to detector.name.lowercase(Locale.ROOT),
                "status" to "processing_failed",
                // Only the exception class is retained. Throwable messages can include provider
                // payloads, so they are deliberately excluded from scanner analytics.
                "error_type" to throwableMetricType(failure),
            ),
            attemptIndex = token.attemptIndex,
            mode = token.mode,
            operation = token.operation,
        )
    }

    @Synchronized
    fun manualFallbackOpened(source: String) {
        emitOperationalEventLocked(
            name = "scanner_manual_fallback",
            values = linkedMapOf("source" to source.take(20)),
        )
    }

    @Synchronized
    fun uncertainPlateConfirmation(outcome: String, correctedCharacters: Int) {
        emitOperationalEventLocked(
            name = "scanner_plate_confirmation",
            values = linkedMapOf(
                "outcome" to outcome.take(20),
                "corrected_characters" to correctedCharacters.coerceAtLeast(0).toLong(),
            ),
        )
    }

    @Synchronized
    fun successRendered() {
        val current = attempt ?: return
        val now = clockMs()
        emitOperationalEventLocked(
            name = "scanner_ui_stage",
            values = linkedMapOf(
                "stage" to "success_rendered",
                "attempt_ms" to elapsed(current.startedAtMs, now),
                "api_to_stage_ms" to elapsed(current.apiCompletedAtMs, now),
                "api_start_to_stage_ms" to elapsed(current.apiStartedAtMs, now),
            ),
        )
    }

    @Synchronized
    fun errorAwaitingOperatorAction() {
        val current = attempt ?: return
        val now = clockMs()
        emitOperationalEventLocked(
            name = "scanner_ui_stage",
            values = linkedMapOf(
                "stage" to "error_awaiting_action",
                "attempt_ms" to elapsed(current.startedAtMs, now),
                "api_to_stage_ms" to elapsed(current.apiCompletedAtMs, now),
            ),
        )
    }

    @Synchronized
    fun nextScanReady() {
        if (closed) return
        val finished = lastFinishedAttempt ?: return
        val now = clockMs()
        sink.emit(
            ScannerMetricsEvent(
                name = "scanner_ui_stage",
                parameters = linkedMapOf(
                    "session_id" to sessionId,
                    "attempt" to finished.index.toLong(),
                    "mode" to finished.mode,
                    "operation" to finished.operation,
                    "stage" to "next_scan_ready",
                    "result_to_ready_ms" to elapsed(finished.finishedAtMs, now),
                ),
            ),
        )
        lastFinishedAttempt = null
    }

    @Synchronized
    fun finishAttempt(result: String) {
        if (closed) return
        finishAttemptLocked(
            result = result,
            now = clockMs(),
            trackForNextReady = true,
        )
    }

    private fun finishAttemptLocked(
        result: String,
        now: Long,
        trackForNextReady: Boolean,
    ) {
        val current = attempt ?: return
        val averageAnalysisMs = if (current.completedAnalyses > 0) {
            current.analysisTotalMs.toDouble() / current.completedAnalyses.toDouble()
        } else {
            0.0
        }
        val abandonedAnalyses = (current.analyses - current.completedAnalyses).coerceAtLeast(0L)
        val averageInputPixels = if (current.preparedInputs > 0) {
            current.inputPixelTotal / current.preparedInputs
        } else {
            0L
        }
        val averageInputPreparationMs = if (current.preparedInputs > 0) {
            current.inputPreparationTotalMs / current.preparedInputs
        } else {
            0L
        }
        val averageCameraBindMs = averageDuration(current.cameraBindTotalMs, current.cameraBindCount)
        val averageCameraReuseMs = averageDuration(current.cameraReuseTotalMs, current.cameraReuseCount)
        val correlation = attemptCorrelation(current)
        sink.emit(
            ScannerMetricsEvent(
                name = "scanner_attempt",
                parameters = LinkedHashMap(correlation).apply {
                    put("result", result)
                    put("total_ms", elapsed(current.startedAtMs, now))
                    put("first_frame_ms", elapsed(current.startedAtMs, current.firstFrameAtMs))
                    put("first_analysis_ms", elapsed(current.startedAtMs, current.firstAnalysisAtMs))
                    put("first_candidate_ms", elapsed(current.startedAtMs, current.firstCandidateAtMs))
                    put("recognition_ms", elapsed(current.startedAtMs, current.confirmedAtMs))
                    put("api_ms", elapsed(current.apiStartedAtMs, current.apiCompletedAtMs))
                    put("frames", current.frames)
                    put("analyses", current.analyses)
                    put("completed_analyses", current.completedAnalyses)
                    put("abandoned_analyses", abandonedAnalyses)
                    put(
                        "api_in_flight",
                        if (current.apiStartedAtMs != null && current.apiCompletedAtMs == null) 1L else 0L,
                    )
                    put("api_slow", if (current.apiSlow) 1L else 0L)
                },
            ),
        )
        sink.emit(
            ScannerMetricsEvent(
                name = "scanner_attempt_analysis",
                parameters = LinkedHashMap(correlation).apply {
                    put("skipped_frames", current.skippedFrames)
                    put("prepared_inputs", current.preparedInputs)
                    put("input_avg_pixels", averageInputPixels)
                    put("input_max_width", current.inputMaxWidth.toLong())
                    put("input_max_height", current.inputMaxHeight.toLong())
                    put("input_copy_avg_ms", averageInputPreparationMs)
                    put("input_copy_max_ms", current.inputPreparationMaxMs)
                    put("analysis_avg_ms", averageAnalysisMs.roundToLong())
                    put("analysis_max_ms", current.analysisMaxMs)
                    put("low_light_frames", current.lowLightFrames)
                    put("auto_zoom_count", current.autoZoomCount)
                    put("detector_processing_failures", current.detectorProcessingFailures)
                },
            ),
        )
        sink.emit(
            ScannerMetricsEvent(
                name = "scanner_attempt_runtime",
                parameters = LinkedHashMap(correlation).apply {
                    put("camera_bind_failures", current.cameraBindFailures)
                    put("camera_runtime_failures", current.cameraRuntimeFailures)
                    put("camera_bind_count", current.cameraBindCount)
                    put("camera_reuse_count", current.cameraReuseCount)
                    put("camera_bind_avg_ms", averageCameraBindMs)
                    put("camera_bind_max_ms", current.cameraBindMaxMs)
                    put("camera_reuse_avg_ms", averageCameraReuseMs)
                    put("camera_reuse_max_ms", current.cameraReuseMaxMs)
                    put("ui_frames", current.uiFrames)
                    put("janky_ui_frames", current.jankyUiFrames)
                    put("max_ui_frame_ms", current.maxUiFrameMs)
                },
            ),
        )
        finishedAttempts++
        if (result == "success") successfulAttempts++
        if (trackForNextReady) {
            lastFinishedAttempt = FinishedAttempt(
                index = current.index,
                mode = current.mode,
                operation = current.operation,
                finishedAtMs = now,
            )
        }
        attempt = null
    }

    @Synchronized
    fun close() {
        if (closed) return
        val now = clockMs()
        // Do not silently drop an attempt when the Activity closes during scanning. This result is
        // intentionally ineligible for nextScanReady() because no next scan can become ready.
        finishAttemptLocked(
            result = "session_closed",
            now = now,
            trackForNextReady = false,
        )
        lastFinishedAttempt = null
        closed = true
        val averageCameraBindMs = averageDuration(cameraBindTotalMs, cameraBindCount)
        val averageCameraReuseMs = averageDuration(cameraReuseTotalMs, cameraReuseCount)
        val correlation = sessionCorrelation()
        sink.emit(
            ScannerMetricsEvent(
                name = "scanner_session",
                parameters = LinkedHashMap(correlation).apply {
                    put("duration_ms", elapsed(sessionStartedAtMs, now))
                    put("camera_ready_ms", elapsed(sessionStartedAtMs, firstCameraBoundAtMs))
                    put("preview_ready_ms", elapsed(sessionStartedAtMs, firstPreviewAtMs))
                    put("camera_bind_count", cameraBindCount)
                    put("camera_reuse_count", cameraReuseCount)
                    put("camera_bind_failures", totalCameraBindFailures)
                    put("camera_runtime_failures", totalCameraRuntimeFailures)
                    put("camera_bind_avg_ms", averageCameraBindMs)
                    put("camera_bind_max_ms", cameraBindMaxMs)
                    put("camera_reuse_avg_ms", averageCameraReuseMs)
                    put("camera_reuse_max_ms", cameraReuseMaxMs)
                    put("mode_switches", totalModeSwitches)
                    put("attempts", finishedAttempts)
                    put("successes", successfulAttempts)
                },
            ),
        )
        sink.emit(
            ScannerMetricsEvent(
                name = "scanner_session_analysis",
                parameters = LinkedHashMap(correlation).apply {
                    put("frames", totalFrames)
                    put("analyses", totalAnalyses)
                    put("completed_analyses", totalCompletedAnalyses)
                    put(
                        "in_flight_analyses",
                        (totalAnalyses - totalCompletedAnalyses).coerceAtLeast(0L),
                    )
                    put("skipped_frames", totalSkippedFrames)
                    put("low_light_frames", totalLowLightFrames)
                    put("auto_zoom_count", totalAutoZoomCount)
                    put("detector_init_failures", totalDetectorInitializationFailures)
                    put("detector_processing_failures", totalDetectorProcessingFailures)
                },
            ),
        )
        sink.emit(
            ScannerMetricsEvent(
                name = "scanner_session_ui",
                parameters = LinkedHashMap(correlation).apply {
                    put("focus_requests", totalFocusRequests)
                    put("focus_successes", totalFocusSuccesses)
                    put("focus_failures", totalFocusFailures)
                    put("ui_frames", totalUiFrames)
                    put("janky_ui_frames", totalJankyUiFrames)
                    put("max_ui_frame_ms", maxUiFrameMs)
                },
            ),
        )
    }

    private fun ensureAttemptLocked(mode: String, operation: String, now: Long): Attempt {
        return attempt ?: beginAttemptLocked(mode, operation, now)
    }

    private fun attemptForFrameLocked(token: ScannerFrameToken): Attempt? {
        return attempt?.takeIf {
            it.index == token.attemptIndex &&
                it.mode == token.mode &&
                it.operation == token.operation
        }
    }

    private fun beginAttemptLocked(mode: String, operation: String, now: Long): Attempt {
        return Attempt(
            index = nextAttemptIndex++,
            startedAtMs = now,
            mode = mode,
            operation = operation
        ).also { attempt = it }
    }

    private fun attemptCorrelation(current: Attempt): LinkedHashMap<String, Any> {
        return linkedMapOf(
            "session_id" to sessionId,
            "attempt" to current.index.toLong(),
            "mode" to current.mode,
            "operation" to current.operation,
            "trace_id" to current.apiTraceId.orEmpty(),
        )
    }

    private fun sessionCorrelation(): LinkedHashMap<String, Any> {
        return linkedMapOf(
            "session_id" to sessionId,
            "mode" to sessionMode,
            "operation" to sessionOperation,
        )
    }

    private fun emitOperationalEventLocked(
        name: String,
        values: Map<String, Any>,
        attemptIndex: Int? = null,
        mode: String? = null,
        operation: String? = null,
    ) {
        val current = attempt
        sink.emit(
            ScannerMetricsEvent(
                name = name,
                parameters = linkedMapOf<String, Any>(
                    "session_id" to sessionId,
                    "attempt" to (attemptIndex?.toLong() ?: current?.index?.toLong() ?: -1L),
                    "mode" to (mode ?: current?.mode ?: sessionMode),
                    "operation" to (operation ?: current?.operation ?: sessionOperation),
                ).apply { putAll(values) },
            ),
        )
    }

    private fun averageDuration(totalMs: Long, count: Long): Long {
        return if (count > 0L) totalMs / count else -1L
    }

    private fun throwableMetricType(failure: Throwable): String {
        return failure.javaClass.simpleName
            .takeIf(String::isNotBlank)
            ?.take(40)
            ?: "unknown"
    }

    private fun elapsed(startMs: Long?, endMs: Long?): Long {
        if (startMs == null || endMs == null) return -1L
        return (endMs - startMs).coerceAtLeast(0L)
    }

    companion object {
        fun create(context: Context): ScannerPerformanceTracker {
            return ScannerPerformanceTracker(FirebaseScannerMetricsSink(context.applicationContext))
        }
    }
}

internal object ScannerMetricsSchema {
    const val FIREBASE_CUSTOM_PARAMETER_LIMIT = 25
    const val SINK_CONTEXT_PARAMETER_COUNT = 4
    const val TRACKER_PARAMETER_LIMIT =
        FIREBASE_CUSTOM_PARAMETER_LIMIT - SINK_CONTEXT_PARAMETER_COUNT

    private val correlationKeys = listOf(
        "session_id",
        "trace_id",
        "attempt",
        "mode",
        "operation",
        "result",
        "status",
    )

    /**
     * Last-resort schema guard for future metrics. Current event families are intentionally split
     * below this limit, but correlation keys remain first if a later change exceeds Firebase's cap.
     */
    fun boundTrackerParameters(
        parameters: Map<String, Any>,
        reservedContextParameters: Int = SINK_CONTEXT_PARAMETER_COUNT,
    ): LinkedHashMap<String, Any> {
        val trackerLimit = (FIREBASE_CUSTOM_PARAMETER_LIMIT - reservedContextParameters)
            .coerceAtLeast(0)
        if (parameters.size <= trackerLimit) return LinkedHashMap(parameters)
        return linkedMapOf<String, Any>().apply {
            correlationKeys.forEach { key ->
                parameters[key]?.let { value ->
                    if (size < trackerLimit) put(key, value)
                }
            }
            parameters.forEach { (key, value) ->
                if (size < trackerLimit && key !in this) put(key, value)
            }
        }
    }

    fun sinkContextParameters(
        network: String,
        appVersion: String,
        deviceModel: String,
        androidApi: Long,
    ): LinkedHashMap<String, Any> = linkedMapOf(
        "network" to network,
        "app_version" to appVersion,
        "device_model" to deviceModel,
        "android_api" to androidApi,
    )

    fun prepareForSink(
        trackerParameters: Map<String, Any>,
        contextParameters: Map<String, Any>,
    ): LinkedHashMap<String, Any> {
        val boundedTracker = boundTrackerParameters(
            parameters = trackerParameters,
            reservedContextParameters = contextParameters.size,
        )
        return boundedTracker.apply { putAll(contextParameters) }
    }
}

private class FirebaseScannerMetricsSink(context: Context) : ScannerMetricsSink {
    private val appContext = context.applicationContext
    private val analytics by lazy { FirebaseAnalytics.getInstance(appContext) }

    override fun emit(event: ScannerMetricsEvent) {
        val contextParameters = ScannerMetricsSchema.sinkContextParameters(
            network = networkType(),
            appVersion = BuildConfig.VERSION_NAME,
            deviceModel = Build.MODEL.take(40),
            androidApi = Build.VERSION.SDK_INT.toLong(),
        )
        val parameters = ScannerMetricsSchema.prepareForSink(
            trackerParameters = event.parameters,
            contextParameters = contextParameters,
        )
        if (event.parameters.size >
            ScannerMetricsSchema.FIREBASE_CUSTOM_PARAMETER_LIMIT - contextParameters.size
        ) {
            AppLog.w(TAG) {
                "${event.name} exceeded the Firebase parameter cap; low-priority fields dropped"
            }
        }

        if (BuildConfig.DEBUG || !analyticsEnabled()) return
        val bundle = Bundle().apply {
            parameters.forEach { (key, value) ->
                when (value) {
                    is Long -> putLong(key, value)
                    is Int -> putLong(key, value.toLong())
                    is Double -> putDouble(key, value)
                    is Float -> putDouble(key, value.toDouble())
                    else -> putString(key, value.toString().take(100))
                }
            }
        }
        analytics.logEvent(event.name, bundle)
    }

    private fun analyticsEnabled(): Boolean {
        return appContext
            .getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(ANALYTICS_ENABLED, true)
    }

    private fun networkType(): String {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val network = manager.activeNetwork ?: return "offline"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "offline"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    companion object {
        private const val TAG = "ScannerMetrics"
        private const val PRIVACY_PREFS = "gridee_privacy_prefs"
        private const val ANALYTICS_ENABLED = "analytics"
    }
}
