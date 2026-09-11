package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerPerformanceTrackerTest {

    @Test
    fun attemptEventContainsRecognitionAnalysisAndApiTimings() {
        var now = 0L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session"
        )

        tracker.start("plate", "entry")
        now = 40
        tracker.cameraReady(reused = false)
        now = 60
        tracker.previewStreaming()
        now = 100
        val frameToken = tracker.frameReceived("plate")
        tracker.inputPrepared(
            token = frameToken,
            width = 640,
            height = 192,
            preparationMs = 3L,
        )
        now = 110
        val analysisStarted = tracker.analysisStarted(frameToken)
        now = 180
        tracker.candidateDetected(frameToken)
        now = 200
        tracker.analysisCompleted(analysisStarted)
        now = 220
        tracker.recognitionConfirmed(frameToken)
        now = 250
        val traceId = tracker.apiStarted()
        assertEquals(traceId, tracker.apiStarted())
        now = 400
        tracker.apiCompleted()
        tracker.finishAttempt("success")

        val event = events.single { it.name == "scanner_attempt" }
        val analysisEvent = events.single { it.name == "scanner_attempt_analysis" }
        assertEquals("scanner_attempt", event.name)
        assertEquals(100L, event.parameters["first_frame_ms"])
        assertEquals(180L, event.parameters["first_candidate_ms"])
        assertEquals(220L, event.parameters["recognition_ms"])
        assertEquals(150L, event.parameters["api_ms"])
        assertEquals(90L, analysisEvent.parameters["analysis_avg_ms"])
        assertEquals(122_880L, analysisEvent.parameters["input_avg_pixels"])
        assertEquals(3L, analysisEvent.parameters["input_copy_avg_ms"])
        assertEquals(1L, event.parameters["completed_analyses"])
        assertEquals("success", event.parameters["result"])
        assertTrue(traceId?.isNotBlank() == true)
        assertEquals(traceId, event.parameters["trace_id"])
    }

    @Test
    fun sessionEventAggregatesReuseSwitchAndPrivacySafeCounters() {
        var now = 10L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session"
        )

        tracker.start("plate", "entry")
        now = 20
        tracker.cameraReady(reused = false)
        now = 30
        tracker.modeSwitched("qr", "entry")
        tracker.cameraReady(reused = true)
        val frameToken = tracker.frameReceived("qr")
        tracker.lowLightFrame(frameToken)
        tracker.autoZoomApplied(frameToken)
        now = 70
        tracker.close()

        val event = events.single { it.name == "scanner_session" }
        val analysisEvent = events.single { it.name == "scanner_session_analysis" }
        assertEquals(1L, event.parameters["camera_bind_count"])
        assertEquals(1L, event.parameters["camera_reuse_count"])
        assertEquals(1L, event.parameters["mode_switches"])
        assertEquals(1L, analysisEvent.parameters["low_light_frames"])
        assertEquals(1L, analysisEvent.parameters["auto_zoom_count"])
        assertTrue(events.none { "vehicle_number" in it.parameters })
        assertTrue(events.none { "qr_value" in it.parameters })
    }

    @Test
    fun `records focus success success render and next scan readiness`() {
        var now = 0L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session",
        )

        tracker.start("plate", "entry")
        now = 50L
        tracker.focusStarted()
        tracker.focusCompleted(successful = true)
        tracker.apiStarted()
        now = 140L
        tracker.apiCompleted()
        now = 150L
        tracker.successRendered()
        tracker.finishAttempt("success")
        now = 1_000L
        tracker.nextScanReady()
        tracker.close()

        val successRender = events.single {
            it.name == "scanner_ui_stage" && it.parameters["stage"] == "success_rendered"
        }
        assertEquals(10L, successRender.parameters["api_to_stage_ms"])
        assertEquals(100L, successRender.parameters["api_start_to_stage_ms"])
        val nextReady = events.single {
            it.name == "scanner_ui_stage" && it.parameters["stage"] == "next_scan_ready"
        }
        assertEquals(850L, nextReady.parameters["result_to_ready_ms"])
        val session = events.single { it.name == "scanner_session_ui" }
        assertEquals(1L, session.parameters["focus_requests"])
        assertEquals(1L, session.parameters["focus_successes"])
        assertEquals(0L, session.parameters["focus_failures"])
    }

    @Test
    fun `readiness fallback and confirmation events contain no scanned value`() {
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { 10L },
            sessionId = "test-session",
        )

        tracker.start("plate", "exit")
        tracker.readinessState("unavailable", "network")
        tracker.detectorInitializationFailed("ocr_and_qr", "network")
        tracker.manualFallbackOpened("model_unavailable")
        tracker.uncertainPlateConfirmation("confirmed", correctedCharacters = 1)
        tracker.automaticRecognitionAvailability(enabled = false)

        assertTrue(events.none { "vehicle_number" in it.parameters || "qr_value" in it.parameters })
        assertTrue(events.any { it.name == "scanner_readiness" })
        assertTrue(events.any { it.name == "scanner_detector_status" })
        assertTrue(events.any { it.name == "scanner_manual_fallback" })
        assertTrue(events.any { it.name == "scanner_plate_confirmation" })
        val recognitionControl = events.single { it.name == "scanner_recognition_control" }
        assertEquals("disabled", recognitionControl.parameters["automatic_recognition"])
    }

    @Test
    fun `terminal mutation error records that operator action is required`() {
        var now = 0L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session",
        )

        tracker.start("qr", "exit")
        now = 40L
        tracker.apiStarted()
        now = 140L
        tracker.apiCompleted()
        now = 150L
        tracker.errorAwaitingOperatorAction()
        tracker.finishAttempt("error")

        val stage = events.single {
            it.name == "scanner_ui_stage" && it.parameters["stage"] == "error_awaiting_action"
        }
        assertEquals(10L, stage.parameters["api_to_stage_ms"])
    }

    @Test
    fun `aggregates JankStats frames without per-frame analytics events`() {
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { 100L },
            sessionId = "test-session",
        )

        tracker.start("plate", "entry")
        tracker.uiFrame(frameDurationUiNanos = 12_000_000L, isJank = false)
        tracker.uiFrame(frameDurationUiNanos = 48_000_000L, isJank = true)
        assertTrue(events.isEmpty())
        tracker.finishAttempt("cancelled")
        tracker.close()

        val attempt = events.single { it.name == "scanner_attempt_runtime" }
        assertEquals(2L, attempt.parameters["ui_frames"])
        assertEquals(1L, attempt.parameters["janky_ui_frames"])
        assertEquals(48L, attempt.parameters["max_ui_frame_ms"])
        val session = events.single { it.name == "scanner_session_ui" }
        assertEquals(2L, session.parameters["ui_frames"])
        assertEquals(1L, session.parameters["janky_ui_frames"])
    }

    @Test
    fun `late analysis completion remains attributed to its original attempt`() {
        var now = 0L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session",
        )

        tracker.start("plate", "entry")
        now = 10L
        val plateFrame = tracker.frameReceived("plate")
        val plateAnalysis = tracker.analysisStarted(plateFrame)
        now = 20L
        tracker.modeSwitched("qr", "entry")
        tracker.nextScanReady()
        tracker.detectorProcessingFailed(
            detector = ScannerDetectorMetric.PLATE,
            failure = IllegalStateException("late plate failure"),
            token = plateAnalysis,
        )
        now = 30L
        val qrFrame = tracker.frameReceived("qr")
        val qrAnalysis = tracker.analysisStarted(qrFrame)
        now = 35L
        tracker.analysisStarted(tracker.frameReceived("qr"))
        now = 50L
        tracker.analysisCompleted(plateAnalysis)
        now = 80L
        tracker.analysisCompleted(qrAnalysis)
        now = 90L
        tracker.finishAttempt("success")
        now = 100L
        tracker.close()

        val attempts = events.filter { it.name == "scanner_attempt" }
        val plateAttempt = attempts.single { it.parameters["mode"] == "plate" }
        assertEquals("mode_switched", plateAttempt.parameters["result"])
        assertEquals(1L, plateAttempt.parameters["analyses"])
        assertEquals(0L, plateAttempt.parameters["completed_analyses"])

        val qrAttempt = attempts.single { it.parameters["mode"] == "qr" }
        assertEquals(2L, qrAttempt.parameters["analyses"])
        assertEquals(1L, qrAttempt.parameters["completed_analyses"])
        assertEquals(1L, qrAttempt.parameters["abandoned_analyses"])
        val qrAnalysisAttempt = events.single {
            it.name == "scanner_attempt_analysis" && it.parameters["mode"] == "qr"
        }
        assertEquals(50L, qrAnalysisAttempt.parameters["analysis_avg_ms"])
        assertEquals(0L, qrAnalysisAttempt.parameters["detector_processing_failures"])

        val session = events.single { it.name == "scanner_session_analysis" }
        assertEquals(3L, session.parameters["analyses"])
        assertEquals(2L, session.parameters["completed_analyses"])
        assertEquals(1L, session.parameters["in_flight_analyses"])
        assertEquals(1L, session.parameters["detector_processing_failures"])
        val lateFailure = events.single {
            it.name == "scanner_detector_status" && it.parameters["status"] == "processing_failed"
        }
        assertEquals(1L, lateFailure.parameters["attempt"])
        assertEquals("plate", lateFailure.parameters["mode"])
        assertFalse(events.any {
            it.name == "scanner_ui_stage" && it.parameters["stage"] == "next_scan_ready"
        })
    }

    @Test
    fun `stale frame stages never contaminate the replacement mode attempt`() {
        var now = 0L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session",
        )

        tracker.start("plate", "entry")
        now = 10L
        val plateFrame = tracker.frameReceived("plate")
        now = 20L
        tracker.modeSwitched("qr", "entry")

        // The old analyzer resumes only after the QR attempt has become current.
        now = 25L
        tracker.frameSkipped(plateFrame)
        tracker.inputPrepared(
            token = plateFrame,
            width = 640,
            height = 192,
            preparationMs = 4L,
        )
        val plateAnalysis = tracker.analysisStarted(plateFrame)
        tracker.candidateDetected(plateFrame)
        tracker.recognitionConfirmed(plateFrame)
        tracker.lowLightFrame(plateFrame)
        tracker.autoZoomApplied(plateFrame)
        now = 40L
        tracker.analysisCompleted(plateAnalysis)
        tracker.finishAttempt("cancelled")
        tracker.close()

        val qrAttempt = events.single {
            it.name == "scanner_attempt" && it.parameters["mode"] == "qr"
        }
        assertEquals(0L, qrAttempt.parameters["frames"])
        assertEquals(0L, qrAttempt.parameters["analyses"])
        assertEquals(0L, qrAttempt.parameters["completed_analyses"])
        assertEquals(-1L, qrAttempt.parameters["first_candidate_ms"])
        assertEquals(-1L, qrAttempt.parameters["recognition_ms"])

        val qrAnalysis = events.single {
            it.name == "scanner_attempt_analysis" && it.parameters["mode"] == "qr"
        }
        assertEquals(0L, qrAnalysis.parameters["skipped_frames"])
        assertEquals(0L, qrAnalysis.parameters["prepared_inputs"])
        assertEquals(0L, qrAnalysis.parameters["low_light_frames"])
        assertEquals(0L, qrAnalysis.parameters["auto_zoom_count"])

        // Session totals still describe work that genuinely happened, even when its attempt has
        // already ended and therefore cannot be mutated retroactively.
        val session = events.single { it.name == "scanner_session_analysis" }
        assertEquals(1L, session.parameters["frames"])
        assertEquals(1L, session.parameters["analyses"])
        assertEquals(1L, session.parameters["completed_analyses"])
        assertEquals(1L, session.parameters["skipped_frames"])
        assertEquals(1L, session.parameters["low_light_frames"])
        assertEquals(1L, session.parameters["auto_zoom_count"])
    }

    @Test
    fun `operation switch starts a new attempt and rejects the previous frame token`() {
        var now = 0L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session",
        )

        tracker.start("qr", "entry")
        val entryFrame = tracker.frameReceived("qr")
        now = 10L
        tracker.operationChanged("exit")
        now = 15L
        val staleAnalysis = tracker.analysisStarted(entryFrame)
        tracker.candidateDetected(entryFrame)
        tracker.recognitionConfirmed(entryFrame)
        now = 20L
        tracker.analysisCompleted(staleAnalysis)
        tracker.finishAttempt("cancelled")
        tracker.close()

        val attempts = events.filter { it.name == "scanner_attempt" }
        assertEquals(2, attempts.size)
        assertEquals("entry", attempts[0].parameters["operation"])
        assertEquals("operation_switched", attempts[0].parameters["result"])
        assertEquals(1L, attempts[0].parameters["frames"])
        assertEquals("exit", attempts[1].parameters["operation"])
        assertEquals(0L, attempts[1].parameters["frames"])
        assertEquals(0L, attempts[1].parameters["analyses"])
        assertEquals(0L, attempts[1].parameters["completed_analyses"])
        assertEquals(-1L, attempts[1].parameters["first_candidate_ms"])
        assertEquals(-1L, attempts[1].parameters["recognition_ms"])

        val session = events.single { it.name == "scanner_session_analysis" }
        assertEquals(1L, session.parameters["analyses"])
        assertEquals(1L, session.parameters["completed_analyses"])
    }

    @Test
    fun `manual context replacement cannot relabel work from an in-flight QR frame`() {
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { 10L },
            sessionId = "test-session",
        )

        tracker.start("qr", "entry")
        val qrFrame = tracker.frameReceived("qr")
        tracker.beginAttempt("plate", "entry")
        tracker.inputPrepared(qrFrame, width = 320, height = 320, preparationMs = 2L)
        tracker.analysisStarted(qrFrame)
        tracker.finishAttempt("cancelled")
        tracker.close()

        val attempts = events.filter { it.name == "scanner_attempt" }
        assertEquals(2, attempts.size)
        assertEquals("qr", attempts[0].parameters["mode"])
        assertEquals("context_replaced", attempts[0].parameters["result"])
        assertEquals(1L, attempts[0].parameters["frames"])
        assertEquals("plate", attempts[1].parameters["mode"])
        assertEquals(0L, attempts[1].parameters["frames"])
        assertEquals(0L, attempts[1].parameters["analyses"])
        assertEquals(
            0L,
            events.single {
                it.name == "scanner_attempt_analysis" && it.parameters["mode"] == "plate"
            }.parameters["prepared_inputs"],
        )
    }

    @Test
    fun `close emits an unfinished attempt without publishing next ready timing`() {
        var now = 0L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session",
        )

        tracker.start("plate", "exit")
        now = 15L
        tracker.frameReceived("plate")
        tracker.apiStarted()
        now = 50L
        tracker.close()
        tracker.nextScanReady()

        val attempt = events.single { it.name == "scanner_attempt" }
        assertEquals("session_closed", attempt.parameters["result"])
        assertEquals(50L, attempt.parameters["total_ms"])
        assertEquals(-1L, attempt.parameters["api_ms"])
        assertEquals(1L, attempt.parameters["api_in_flight"])
        assertEquals(1L, events.single { it.name == "scanner_session" }.parameters["attempts"])
        assertFalse(events.any {
            it.name == "scanner_ui_stage" && it.parameters["stage"] == "next_scan_ready"
        })
    }

    @Test
    fun `close clears next ready state from an already finished attempt`() {
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { 25L },
            sessionId = "test-session",
        )

        tracker.start("qr", "entry")
        tracker.finishAttempt("success")
        tracker.close()
        tracker.nextScanReady()

        assertFalse(events.any {
            it.name == "scanner_ui_stage" && it.parameters["stage"] == "next_scan_ready"
        })
    }

    @Test
    fun `reserved trace does not mark API in flight when local cooldown rejects request`() {
        var now = 0L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session",
        )

        tracker.start("plate", "entry")
        val reservedTrace = tracker.reserveApiTrace()
        now = 5L
        tracker.finishAttempt("cooldown")

        val attempt = events.single { it.name == "scanner_attempt" }
        assertEquals(reservedTrace, attempt.parameters["trace_id"])
        assertEquals(-1L, attempt.parameters["api_ms"])
        assertEquals(0L, attempt.parameters["api_in_flight"])
    }

    @Test
    fun `manual retry after a terminal error owns a new correlated attempt`() {
        var now = 0L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session",
        )

        tracker.start("qr", "entry")
        now = 10L
        tracker.finishAttempt("error_network")

        now = 20L
        tracker.beginAttempt("plate", "entry")
        val manualTraceId = requireNotNull(tracker.reserveApiTrace())
        tracker.apiStarted(manualTraceId)
        now = 45L
        tracker.apiCompleted()
        tracker.finishAttempt("success")
        now = 60L
        tracker.nextScanReady()

        val attempts = events.filter { it.name == "scanner_attempt" }
        assertEquals(2, attempts.size)
        assertEquals(1L, attempts[0].parameters["attempt"])
        assertEquals("error_network", attempts[0].parameters["result"])
        assertEquals(2L, attempts[1].parameters["attempt"])
        assertEquals("plate", attempts[1].parameters["mode"])
        assertEquals(manualTraceId, attempts[1].parameters["trace_id"])
        assertEquals(25L, attempts[1].parameters["api_ms"])

        val nextReady = events.single {
            it.name == "scanner_ui_stage" && it.parameters["stage"] == "next_scan_ready"
        }
        assertEquals(2L, nextReady.parameters["attempt"])
        assertEquals(15L, nextReady.parameters["result_to_ready_ms"])
    }

    @Test
    fun `records camera timing and runtime failures without throwable messages`() {
        var now = 0L
        val events = mutableListOf<ScannerMetricsEvent>()
        val tracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(events::add),
            clockMs = { now },
            sessionId = "test-session",
        )

        tracker.start("plate", "entry")
        now = 10L
        val bindToken = tracker.cameraBindingStarted()
        now = 45L
        tracker.cameraReady(reused = false, token = bindToken)
        now = 50L
        val reuseToken = tracker.cameraBindingStarted()
        now = 53L
        tracker.cameraReady(reused = true, token = reuseToken)
        now = 60L
        val failedBindToken = tracker.cameraBindingStarted()
        now = 85L
        tracker.cameraBindFailed(
            failure = IllegalStateException("SECRET-PLATE-123"),
            token = failedBindToken,
        )
        now = 87L
        tracker.cameraRuntimeFailed(
            reason = ScannerCameraRuntimeFailureReason.FIRST_PREVIEW_TIMEOUT,
            failure = ScannerCameraRuntimeException(
                reason = ScannerCameraRuntimeFailureReason.FIRST_PREVIEW_TIMEOUT,
                cause = IllegalStateException("SECRET-CAMERA-PROVIDER"),
            ),
        )
        now = 90L
        val analysisToken = tracker.analysisStarted(tracker.frameReceived("plate"))
        now = 110L
        tracker.detectorProcessingFailed(
            detector = ScannerDetectorMetric.PLATE,
            failure = RuntimeException("SECRET-QR-PAYLOAD"),
            token = analysisToken,
        )
        now = 111L
        tracker.detectorProcessingFailed(
            detector = ScannerDetectorMetric.PLATE,
            failure = RuntimeException("SECOND-SECRET"),
            token = analysisToken,
        )
        now = 115L
        tracker.analysisCompleted(analysisToken)
        now = 120L
        tracker.finishAttempt("cancelled")
        now = 130L
        tracker.close()

        val attemptRuntime = events.single { it.name == "scanner_attempt_runtime" }
        assertEquals(1L, attemptRuntime.parameters["camera_bind_count"])
        assertEquals(35L, attemptRuntime.parameters["camera_bind_avg_ms"])
        assertEquals(1L, attemptRuntime.parameters["camera_reuse_count"])
        assertEquals(3L, attemptRuntime.parameters["camera_reuse_avg_ms"])
        assertEquals(1L, attemptRuntime.parameters["camera_bind_failures"])
        assertEquals(1L, attemptRuntime.parameters["camera_runtime_failures"])
        val attemptAnalysis = events.single { it.name == "scanner_attempt_analysis" }
        assertEquals(2L, attemptAnalysis.parameters["detector_processing_failures"])

        val cameraFailure = events.single {
            it.name == "scanner_camera_status" && it.parameters["status"] == "bind_failed"
        }
        assertEquals("IllegalStateException", cameraFailure.parameters["error_type"])
        assertEquals(25L, cameraFailure.parameters["duration_ms"])
        val runtimeCameraFailure = events.single {
            it.name == "scanner_camera_status" && it.parameters["status"] == "runtime_failed"
        }
        assertEquals(
            "first_preview_timeout",
            runtimeCameraFailure.parameters["reason"],
        )
        assertEquals(
            "ScannerCameraRuntimeException",
            runtimeCameraFailure.parameters["error_type"],
        )
        val detectorFailure = events.single {
            it.name == "scanner_detector_status" && it.parameters["status"] == "processing_failed"
        }
        assertEquals("plate", detectorFailure.parameters["detector"])
        assertEquals("RuntimeException", detectorFailure.parameters["error_type"])

        val session = events.single { it.name == "scanner_session" }
        assertEquals(1L, session.parameters["camera_bind_failures"])
        assertEquals(1L, session.parameters["camera_runtime_failures"])
        assertEquals(35L, session.parameters["camera_bind_avg_ms"])
        assertEquals(3L, session.parameters["camera_reuse_avg_ms"])
        val sessionAnalysis = events.single { it.name == "scanner_session_analysis" }
        assertEquals(2L, sessionAnalysis.parameters["detector_processing_failures"])
        assertTrue(events.flatMap { it.parameters.values }.none {
            it.toString().contains("SECRET")
        })
        assertTrue(events.all {
            it.parameters.size + ScannerMetricsSchema.SINK_CONTEXT_PARAMETER_COUNT <=
                ScannerMetricsSchema.FIREBASE_CUSTOM_PARAMETER_LIMIT
        })
    }

    @Test
    fun `api timing can reattach after Activity recreation without fake completion`() {
        var now = 0L
        val firstEvents = mutableListOf<ScannerMetricsEvent>()
        val firstTracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(firstEvents::add),
            clockMs = { now },
            sessionId = "first-session",
        )

        firstTracker.start("plate", "entry")
        now = 10L
        val traceId = firstTracker.apiStarted()
        val retainedTiming = requireNotNull(firstTracker.apiTimingSnapshot())
        now = 30L
        firstTracker.close()

        val closedAttempt = firstEvents.single { it.name == "scanner_attempt" }
        assertEquals(traceId, closedAttempt.parameters["trace_id"])
        assertEquals(-1L, closedAttempt.parameters["api_ms"])
        assertEquals(1L, closedAttempt.parameters["api_in_flight"])

        val recreatedEvents = mutableListOf<ScannerMetricsEvent>()
        val recreatedTracker = ScannerPerformanceTracker(
            sink = ScannerMetricsSink(recreatedEvents::add),
            clockMs = { now },
            sessionId = "second-session",
        )
        recreatedTracker.start("plate", "entry")
        assertEquals(traceId, recreatedTracker.reattachApiTiming(retainedTiming))
        now = 80L
        recreatedTracker.apiCompleted()
        recreatedTracker.finishAttempt("success")

        val completedAttempt = recreatedEvents.single { it.name == "scanner_attempt" }
        assertEquals(traceId, completedAttempt.parameters["trace_id"])
        assertEquals(70L, completedAttempt.parameters["api_ms"])
        assertEquals(0L, completedAttempt.parameters["api_in_flight"])
    }

    @Test
    fun `Firebase schema guard preserves correlation within total parameter cap`() {
        val oversized = linkedMapOf<String, Any>().apply {
            repeat(30) { index -> put("metric_$index", index.toLong()) }
            put("session_id", "session")
            put("trace_id", "trace")
            put("attempt", 4L)
            put("mode", "plate")
            put("operation", "entry")
        }

        val context = ScannerMetricsSchema.sinkContextParameters(
            network = "wifi",
            appVersion = "1.0",
            deviceModel = "test-device",
            androidApi = 34L,
        )
        val bounded = ScannerMetricsSchema.boundTrackerParameters(
            parameters = oversized,
            reservedContextParameters = context.size,
        )
        val sinkParameters = ScannerMetricsSchema.prepareForSink(oversized, context)

        assertEquals("session", bounded["session_id"])
        assertEquals("trace", bounded["trace_id"])
        assertEquals(4L, bounded["attempt"])
        assertEquals("plate", bounded["mode"])
        assertEquals("entry", bounded["operation"])
        assertEquals(ScannerMetricsSchema.SINK_CONTEXT_PARAMETER_COUNT, context.size)
        assertEquals("wifi", sinkParameters["network"])
        assertEquals("1.0", sinkParameters["app_version"])
        assertEquals("test-device", sinkParameters["device_model"])
        assertEquals(34L, sinkParameters["android_api"])
        assertTrue(sinkParameters.size <= ScannerMetricsSchema.FIREBASE_CUSTOM_PARAMETER_LIMIT)
    }
}
