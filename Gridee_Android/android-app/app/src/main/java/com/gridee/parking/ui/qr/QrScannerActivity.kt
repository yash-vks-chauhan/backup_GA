package com.gridee.parking.ui.qr

import android.Manifest
import android.annotation.SuppressLint
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.util.TypedValue
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.gridee.parking.BuildConfig
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.databinding.BottomSheetOperatorSpotSelectionBinding
import com.gridee.parking.ui.operator.CheckInState
import com.gridee.parking.ui.operator.OperatorParkingSpotLoader
import com.gridee.parking.ui.operator.OperatorViewModel
import com.gridee.parking.ui.utils.BlurViewHelper
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.VehicleNumberValidator
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
class QrScannerActivity : AppCompatActivity() {

    private lateinit var rootView: FrameLayout
    private lateinit var scannerBenchmarkReadinessMarker: View
    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var vehiclePreview: PreviewView
    private lateinit var vehicleHint: TextView
    private lateinit var scannerContentContainer: View
    private lateinit var scanningFrameContainer: FrameLayout
    private lateinit var overlayView: View
    private lateinit var scanLine: View
    private lateinit var statusContainer: View
    private lateinit var statusBadge: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var statusMeta: TextView
    private lateinit var statusIconContainer: View
    private lateinit var statusIcon: ImageView
    private lateinit var statusProgress: ProgressBar
    private lateinit var resultActionsContainer: View
    private lateinit var resultScanAgainButton: MaterialButton
    private lateinit var resultManualEntryButton: MaterialButton
    private lateinit var manualFallbackButton: MaterialButton
    private lateinit var cornerTopLeft: ImageView
    private lateinit var cornerTopRight: ImageView
    private lateinit var cornerBottomLeft: ImageView
    private lateinit var cornerBottomRight: ImageView
    private lateinit var flashToggle: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var voiceEntryButton: ImageButton
    private lateinit var topControlsContainer: LinearLayout
    private lateinit var modeDockContainer: LinearLayout
    private lateinit var spotSelectorPill: LinearLayout
    private lateinit var spotSelectorLabel: TextView
    private lateinit var operationToggleContainer: LinearLayout
    private lateinit var checkInSegment: View
    private lateinit var checkOutSegment: View
    private lateinit var checkInLabel: TextView
    private lateinit var checkOutLabel: TextView
    private lateinit var scannerInputToggleContainer: LinearLayout
    private lateinit var plateInputSegment: View
    private lateinit var qrInputSegment: View
    private lateinit var plateInputLabel: TextView
    private lateinit var qrInputLabel: TextView
    private lateinit var manualEntryCard: View
    private lateinit var manualEntryModeChip: TextView
    private lateinit var manualEntryInputLayout: TextInputLayout
    private lateinit var manualEntryInput: TextInputEditText
    private lateinit var manualEntrySubmitButton: MaterialButton
    private var scannerSystemTopInsetPx = 0
    private var scannerSystemBottomInsetPx = 0
    private var scannerViewportSupportsLiveScanning = false
    private var scannerViewportLayoutKnown = false
    private var scannerUsesNarrowSideRails = false
    private var lastScannerRootWidthPx = 0
    private var lastScannerRootHeightPx = 0
    private val scannerTraceCookie = System.identityHashCode(this)
    private val scannerTraceLock = Any()
    private var scannerPreviewTraceOpen = false
    private var scannerModeSwitchTraceOpen = false
    private var scannerModeSwitchTraceCookie = scannerTraceCookie
    private var scannerModeSwitchTraceSequence = 0
    private var scannerModeSwitchTraceExpectedMode: ScannerInputMode? = null
    private var scannerModeSwitchTraceLayoutReady = false
    private var scannerModeSwitchTraceFrameReady = false
    private var cameraManualFallbackReason: CameraManualFallbackReason? = null
    private var scannerCameraRebindPending = false

    private var lastScanTimestamp: Long = 0L
    private var scanType: String = ""
    @Volatile
    private var selectedOperationType = OperationType.CHECK_IN
    @Volatile
    private var selectedScannerInputMode = ScannerInputMode.PLATE
    @Volatile
    private var vehicleScannerRunning = false
    @Volatile
    private var operatorQrScannerRunning = false
    @Volatile
    private var vehicleScanCompleted = false
    private var qrScannerStarted = false
    private lateinit var scannerPerformance: ScannerPerformanceTracker
    private lateinit var scannerFrameReadinessGate: ScannerFrameReadinessGate
    private lateinit var scannerStateMachine: ScannerStateMachine
    private lateinit var scannerUiRenderer: ScannerUiRenderer
    private var jankStats: JankStats? = null
    private var performanceMetricsStateHolder: PerformanceMetricsState.Holder? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scannerCameraController: ScannerCameraController
    private val scannerCameraAdjustmentLock = Any()
    private val scannerInputImageFactory = ScannerInputImageFactory()
    private val mlTaskExecutor = Executor { task ->
        val executor = if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
            cameraExecutor
        } else {
            null
        }
        if (executor == null) {
            task.run()
        } else {
            try {
                executor.execute(task)
            } catch (_: RejectedExecutionException) {
                // ML Kit may finish while Activity teardown is shutting down the analyzer. Run the
                // callback inline so ImageProxy completion still closes its frame instead of
                // crashing the process or leaking the camera buffer.
                task.run()
            }
        }
    }
    private var scanLineAnimator: ObjectAnimator? = null
    private var previewGlassAnimator: ValueAnimator? = null
    private var previewGlassProgress = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var autoCloseRunnable: Runnable? = null
    private val autoCloseDelayMs = 3000L
    private var scanResumeRunnable: Runnable? = null
    private val vehicleScanResumeDelayMs = 250L
    private val vehicleScanErrorResumeDelayMs = 450L
    private val vehicleScanTimeoutResumeDelayMs = 450L
    private var operationTimeoutRunnable: Runnable? = null
    private var operatorTimedOutRequestId: Long? = null
    private val operatorOperationTimeoutMs = 12000L
    private var scanTimeoutRunnable: Runnable? = null
    private val scanTimeoutMs = 7000L
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var vehicleResultSheet: BottomSheetDialog? = null
    private var manualEntrySheet: BottomSheetDialog? = null
    @Volatile
    private var vehicleScannerSessionId: Long = 0L
    @Volatile
    private var isTorchOn = false
    @Volatile
    private lateinit var scannerFeatureControls: ScannerFeatureControls
    private lateinit var automaticRecognitionGate: ScannerAutomaticRecognitionGate
    private val scannerReadinessController = ScannerReadinessController()
    private val moduleInstallClient by lazy { ModuleInstall.getClient(this) }
    private var scannerModelPreparationInFlight = false
    private val ocrCropHorizontalInsetRatio = 0.035f
    private val ocrCropVerticalInsetRatio = 0.12f
    private val plateWideRoiInterval = 4
    private var plateFrameSequence = 0L
    private val lightingMonitor = ScannerLightingMonitor()
    private val reflectivePlateExposureAdvisor = ReflectivePlateExposureAdvisor()
    private val plateFrameUsabilityGate = PlateFrameUsabilityGate()
    @Volatile
    private var lowLightActive = false
    private val focusMeteringCooldownMs = 1_200L
    @Volatile
    private var lastQrAutoZoomAtMs = 0L
    @Volatile
    private var activeQrAutoZoomContext: ScannerRecognitionFrameContext? = null
    private val qrAutoZoomCooldownMs = 350L
    private val qrAutoZoomMaxRatio = 3f
    private val vehiclePlateInterpreter = VehiclePlateInterpreter()
    private val vehiclePlateAnalyzer = VehiclePlateAnalyzer(vehiclePlateInterpreter)
    private val operatorQrAnalyzer = OperatorQrAnalyzer(
        autoZoomCallback = ::applyQrAutoZoomSuggestion,
        maximumAutoZoomRatio = qrAutoZoomMaxRatio,
    )
    private val detectorRuntimeFailurePolicy = ScannerDetectorRuntimeFailurePolicy()
    private var detectorRuntimeFallbackMode: ScannerInputMode? = null
    private val textRecognizer get() = vehiclePlateAnalyzer.detector
    private val qrBarcodeScanner get() = operatorQrAnalyzer.detector
    private val scannerUiUpdateGate = ScannerUiUpdateGate()
    private val scanRegionRefreshPending = AtomicBoolean(false)
    private val voiceRecognitionLocale: Locale = Locale.forLanguageTag("en-IN")
    private val voiceAutoStopMs = 7000L
    private val voiceProcessingTimeoutMs = 2200L
    private val operatorViewModel: OperatorViewModel by viewModels()
    private var vehicleSheetUi: VehicleSheetUi? = null
    @Volatile
    private var currentVehicleNumber: String? = null
    @Volatile
    private var currentQrCode: String? = null
    private var operatorParkingSpotId: String? = null
    private var operatorParkingSpotName: String? = null
    private var operatorParkingLotId: String? = null
    @Volatile
    private var operatorOperationInProgress = false
    private val parkingRepository = ParkingRepository()
    private val scannerNetworkMonitor by lazy { ScannerNetworkMonitor(applicationContext) }
    private var scannerSpotSelectionDialog: BottomSheetDialog? = null
    private var uncertainPlateDialog: androidx.appcompat.app.AlertDialog? = null
    private var voiceRecognitionInProgress = false
    private var voiceListeningInProgress = false
    private var pendingVoiceStartAfterPermission = false
    private var voiceStopRequested = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var voiceAutoStopRunnable: Runnable? = null
    private var voiceProcessingTimeoutRunnable: Runnable? = null
    private var pendingOperatorRequestId: Long? = null
    private var lastPresentedOperatorTerminalRequestId: Long? = null
    private var persistentOperatorErrorMessage: String? = null

    // Scan state for corner glow transitions
    private enum class ScanState {
        SCANNING, SUCCESS, ERROR, WARNING
    }

    private enum class ScannerPanelMode {
        SCANNING, PROCESSING, SUCCESS, ERROR, WARNING
    }

    private enum class FeedbackType {
        SUCCESS, ERROR, TIMEOUT
    }

    private enum class ScannerInputMode {
        PLATE, QR
    }

    private enum class OperationType {
        CHECK_IN, CHECK_OUT
    }

    private data class VehicleSheetUi(
        val plateLabel: TextView,
        val statusText: TextView,
        val progress: ProgressBar,
        val scanAgainButton: MaterialButton,
        val manualToggleButton: MaterialButton,
        val manualEntryContainer: View,
        val manualInputLayout: TextInputLayout,
        val manualInput: TextInputEditText,
        val manualSubmit: MaterialButton
    )

    private data class SpeechPlateCandidate(
        val plate: String,
        val score: Int,
        val confidence: Float?,
        val heardPhrase: String
    )

    /** Immutable identity shared by every asynchronous stage belonging to one camera frame. */
    private class ScannerRecognitionFrameContext(
        val source: ScannerAutomaticRecognitionSource,
        val operationType: OperationType,
        val scanSessionId: Long,
        val recognitionGeneration: Long,
        val performanceToken: ScannerFrameToken,
    )

    companion object {
        const val EXTRA_BOOKING_ID = "booking_id"
        const val EXTRA_SCAN_TYPE = "scan_type" // CHECK_IN, CHECK_OUT, VEHICLE_CHECK_IN, VEHICLE_CHECK_OUT
        const val EXTRA_QR_CODE = "qr_code"
        const val EXTRA_PARKING_SPOT_ID = "parking_spot_id"
        const val EXTRA_PARKING_SPOT_NAME = "parking_spot_name"
        const val EXTRA_PARKING_LOT_ID = "parking_lot_id"
        const val RESULT_QR_SCANNED = 100
        private const val CAMERA_PERMISSION_REQUEST = 101
        private const val AUDIO_PERMISSION_REQUEST = 102
        private const val STATE_SELECTED_OPERATION_TYPE = "selected_operation_type"
        private const val STATE_SELECTED_SCANNER_INPUT_MODE = "selected_scanner_input_mode"
        private const val STATE_PERSISTENT_OPERATOR_ERROR = "persistent_operator_error"
        private const val STATE_CAMERA_MANUAL_FALLBACK_REASON =
            "camera_manual_fallback_reason"
        private const val STATE_DETECTOR_RUNTIME_FALLBACK_MODE =
            "detector_runtime_fallback_mode"
        private const val STATE_LAST_PRESENTED_OPERATOR_TERMINAL_REQUEST =
            "last_presented_operator_terminal_request"
        private const val STATE_SCANNER_LIFECYCLE = "scanner_lifecycle"
        private const val STATE_OPERATOR_PARKING_SPOT_ID = "operator_parking_spot_id"
        private const val STATE_OPERATOR_PARKING_SPOT_NAME = "operator_parking_spot_name"
        private const val STATE_OPERATOR_PARKING_LOT_ID = "operator_parking_lot_id"
        private const val SCANNER_PREVIEW_READY_TRACE = "scanner_preview_ready"
        private const val SCANNER_MODE_SWITCH_TRACE = "scanner_mode_switch"
        private const val BENCHMARK_READY_PLATE = "scanner_benchmark_ready_plate"
        private const val BENCHMARK_READY_QR = "scanner_benchmark_ready_qr"
        private const val BENCHMARK_WAITING_PLATE = "scanner_benchmark_waiting_plate"
        private const val BENCHMARK_WAITING_QR = "scanner_benchmark_waiting_qr"
        private const val SCANNER_CONFIG_POLL_INTERVAL_MS = 60_000L

        private val fillerSpeechTokens = setOf(
            "vehicle", "number", "plate", "registration", "reg", "car", "bike", "scooter"
        )

        private val spokenDigitMap = mapOf(
            "zero" to "0",
            "oh" to "0",
            "one" to "1",
            "two" to "2",
            "to" to "2",
            "too" to "2",
            "three" to "3",
            "four" to "4",
            "for" to "4",
            "five" to "5",
            "six" to "6",
            "seven" to "7",
            "eight" to "8",
            "ate" to "8",
            "nine" to "9"
        )

        private val spokenWholeNumberMap = mapOf(
            "ten" to "10",
            "eleven" to "11",
            "twelve" to "12",
            "thirteen" to "13",
            "fourteen" to "14",
            "fifteen" to "15",
            "sixteen" to "16",
            "seventeen" to "17",
            "eighteen" to "18",
            "nineteen" to "19"
        )

        private val spokenTensMap = mapOf(
            "twenty" to "20",
            "thirty" to "30",
            "forty" to "40",
            "fourty" to "40",
            "fifty" to "50",
            "sixty" to "60",
            "seventy" to "70",
            "eighty" to "80",
            "ninety" to "90"
        )

        private val spokenLetterMap = mapOf(
            "a" to "A",
            "ay" to "A",
            "b" to "B",
            "be" to "B",
            "bee" to "B",
            "c" to "C",
            "cee" to "C",
            "see" to "C",
            "d" to "D",
            "dee" to "D",
            "e" to "E",
            "ee" to "E",
            "f" to "F",
            "eff" to "F",
            "g" to "G",
            "gee" to "G",
            "h" to "H",
            "aitch" to "H",
            "i" to "I",
            "eye" to "I",
            "j" to "J",
            "jay" to "J",
            "k" to "K",
            "kay" to "K",
            "l" to "L",
            "el" to "L",
            "m" to "M",
            "em" to "M",
            "n" to "N",
            "en" to "N",
            "o" to "O",
            "p" to "P",
            "pee" to "P",
            "q" to "Q",
            "queue" to "Q",
            "cue" to "Q",
            "r" to "R",
            "ar" to "R",
            "are" to "R",
            "s" to "S",
            "ess" to "S",
            "t" to "T",
            "tee" to "T",
            "u" to "U",
            "you" to "U",
            "v" to "V",
            "vee" to "V",
            "w" to "W",
            "doubleu" to "W",
            "x" to "X",
            "ex" to "X",
            "y" to "Y",
            "why" to "Y",
            "z" to "Z",
            "zee" to "Z",
            "zed" to "Z"
        )
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        beginScannerPreviewTrace()
        setContentView(R.layout.activity_qr_scanner)

        rootView = findViewById(R.id.scanner_root)
        scanType = intent?.getStringExtra(EXTRA_SCAN_TYPE) ?: ""
        selectedOperationType = savedInstanceState
            ?.getString(STATE_SELECTED_OPERATION_TYPE)
            ?.let { runCatching { OperationType.valueOf(it) }.getOrNull() }
            ?: resolveOperationType(scanType)
            ?: OperationType.CHECK_IN
        selectedScannerInputMode = savedInstanceState
            ?.getString(STATE_SELECTED_SCANNER_INPUT_MODE)
            ?.let { runCatching { ScannerInputMode.valueOf(it) }.getOrNull() }
            ?: ScannerInputMode.PLATE
        scannerFrameReadinessGate = ScannerFrameReadinessGate(scannerFrameReadinessMode())
        persistentOperatorErrorMessage = savedInstanceState
            ?.getString(STATE_PERSISTENT_OPERATOR_ERROR)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        cameraManualFallbackReason = savedInstanceState
            ?.getString(STATE_CAMERA_MANUAL_FALLBACK_REASON)
            ?.let { runCatching { CameraManualFallbackReason.valueOf(it) }.getOrNull() }
        detectorRuntimeFallbackMode = savedInstanceState
            ?.getString(STATE_DETECTOR_RUNTIME_FALLBACK_MODE)
            ?.let { runCatching { ScannerInputMode.valueOf(it) }.getOrNull() }
        lastPresentedOperatorTerminalRequestId = savedInstanceState
            ?.takeIf { it.containsKey(STATE_LAST_PRESENTED_OPERATOR_TERMINAL_REQUEST) }
            ?.getLong(STATE_LAST_PRESENTED_OPERATOR_TERMINAL_REQUEST)
        if (persistentOperatorErrorMessage != null || detectorRuntimeFallbackMode != null) {
            vehicleScanCompleted = true
        }
        val restoredLifecycleState = savedInstanceState
            ?.getString(STATE_SCANNER_LIFECYCLE)
            ?.let { runCatching { ScannerLifecycleState.valueOf(it) }.getOrNull() }
            ?: ScannerLifecycleState.PREPARING
        scannerStateMachine = ScannerStateMachine(restoredLifecycleState)
        scannerPerformance = ScannerPerformanceTracker.create(this).also {
            it.start(scannerMetricMode(), scannerMetricOperation())
            it.lifecycleState(scannerStateMachine.state.name.lowercase(Locale.ROOT))
        }
        jankStats = JankStats.createAndTrack(window) { frameData ->
            scannerPerformance.uiFrame(frameData.frameDurationUiNanos, frameData.isJank)
        }
        performanceMetricsStateHolder = PerformanceMetricsState.getHolderForHierarchy(rootView)
        updatePerformanceUiState()
        val cachedConfig = RemoteConfigManager.loadCached(this)
        scannerFeatureControls = ScannerFeatureControls.from(
            customSettings = cachedConfig.customSettings,
            featureToggles = cachedConfig.features.featureToggleMap,
        )
        automaticRecognitionGate = ScannerAutomaticRecognitionGate(
            initialEnabled = scannerFeatureControls.automaticRecognitionEnabled,
        )
        val restoredSpotState = savedInstanceState?.let {
            ScannerSpotState(
                spotId = it.getString(STATE_OPERATOR_PARKING_SPOT_ID),
                spotName = it.getString(STATE_OPERATOR_PARKING_SPOT_NAME),
                lotId = it.getString(STATE_OPERATOR_PARKING_LOT_ID),
            )
        }
        val resolvedSpotState = ScannerSpotStatePolicy.resolve(
            assignedLotId = AuthSession.getParkingLotId(this),
            restored = restoredSpotState,
            launched = ScannerSpotState(
                spotId = intent?.getStringExtra(EXTRA_PARKING_SPOT_ID),
                spotName = intent?.getStringExtra(EXTRA_PARKING_SPOT_NAME),
                lotId = intent?.getStringExtra(EXTRA_PARKING_LOT_ID),
            ),
        )
        operatorParkingSpotId = resolvedSpotState.spotId
        operatorParkingSpotName = resolvedSpotState.spotName
        operatorParkingLotId = resolvedSpotState.lotId
        cameraExecutor = Executors.newSingleThreadExecutor()

        barcodeView = findViewById(R.id.barcode_scanner)
        configureBarcodeViewChrome()
        vehiclePreview = findViewById(R.id.vehicle_preview)
        scannerBenchmarkReadinessMarker = findViewById(R.id.scanner_benchmark_readiness)
        scannerBenchmarkReadinessMarker.isVisible = BuildConfig.SCANNER_BENCHMARK_MARKERS_ENABLED
        publishScannerFrameReadiness()
        scannerCameraController = ScannerCameraController(
            context = this,
            lifecycleOwner = this,
            previewView = vehiclePreview,
            analysisExecutor = cameraExecutor,
        )
        vehiclePreview.previewStreamState.observe(this) { streamState ->
            val streaming = streamState == PreviewView.StreamState.STREAMING
            scannerCameraController.onPreviewStreamStateChanged(streaming)
            scannerFrameReadinessGate.updatePreviewStreaming(streaming)
            publishScannerFrameReadiness()
            if (streaming) {
                scannerPerformance.previewStreaming()
            }
        }
        vehicleHint = findViewById(R.id.tv_vehicle_hint)
        scannerContentContainer = findViewById(R.id.scanner_content_container)
        scanningFrameContainer = findViewById(R.id.scanning_frame_container)
        overlayView = findViewById(R.id.vehicle_overlay)
        scanLine = findViewById(R.id.vehicle_scan_line)
        statusContainer = findViewById(R.id.vehicle_status_container)
        statusBadge = findViewById(R.id.tv_vehicle_status_badge)
        statusTitle = findViewById(R.id.tv_vehicle_status_title)
        statusText = findViewById(R.id.tv_vehicle_status)
        statusMeta = findViewById(R.id.tv_scanner_status_meta)
        statusIconContainer = findViewById(R.id.scanner_status_icon_container)
        statusIcon = findViewById(R.id.iv_scanner_status_icon)
        statusProgress = findViewById(R.id.pb_vehicle_scanning)
        resultActionsContainer = findViewById(R.id.scanner_result_actions)
        resultScanAgainButton = findViewById(R.id.btn_scanner_result_scan_again)
        resultManualEntryButton = findViewById(R.id.btn_scanner_manual_entry)
        manualFallbackButton = findViewById(R.id.btn_scanner_manual_fallback)
        cornerTopLeft = findViewById(R.id.corner_top_left)
        cornerTopRight = findViewById(R.id.corner_top_right)
        cornerBottomLeft = findViewById(R.id.corner_bottom_left)
        cornerBottomRight = findViewById(R.id.corner_bottom_right)
        flashToggle = findViewById(R.id.btn_flash_toggle)
        closeButton = findViewById(R.id.btn_close_scanner)
        voiceEntryButton = findViewById(R.id.btn_voice_entry)
        topControlsContainer = findViewById(R.id.scanner_top_controls)
        modeDockContainer = findViewById(R.id.scanner_mode_dock)
        spotSelectorPill = findViewById(R.id.scanner_spot_selector)
        spotSelectorLabel = findViewById(R.id.tv_scanner_spot_name)
        operationToggleContainer = findViewById(R.id.scanner_operation_toggle)
        checkInSegment = findViewById(R.id.segment_scanner_checkin)
        checkOutSegment = findViewById(R.id.segment_scanner_checkout)
        checkInLabel = findViewById(R.id.tv_scanner_mode_checkin)
        checkOutLabel = findViewById(R.id.tv_scanner_mode_checkout)
        scannerInputToggleContainer = findViewById(R.id.scanner_input_toggle)
        plateInputSegment = findViewById(R.id.segment_scanner_plate)
        qrInputSegment = findViewById(R.id.segment_scanner_qr)
        plateInputLabel = findViewById(R.id.tv_scanner_input_plate)
        qrInputLabel = findViewById(R.id.tv_scanner_input_qr)
        configureSegmentAccessibility(checkInSegment, checkInLabel)
        configureSegmentAccessibility(checkOutSegment, checkOutLabel)
        configureSegmentAccessibility(plateInputSegment, plateInputLabel)
        configureSegmentAccessibility(qrInputSegment, qrInputLabel)
        manualEntryCard = findViewById(R.id.card_inline_manual_entry)
        manualEntryModeChip = findViewById(R.id.tv_manual_entry_mode_chip)
        manualEntryInputLayout = findViewById(R.id.layout_inline_manual_input)
        manualEntryInput = findViewById(R.id.input_inline_manual_plate)
        manualEntrySubmitButton = findViewById(R.id.btn_manual_submit_inline)
        scannerUiRenderer = ScannerUiRenderer(
            statusContainer = statusContainer,
            statusIconContainer = statusIconContainer,
            statusIcon = statusIcon,
            statusBadge = statusBadge,
            statusTitle = statusTitle,
            statusText = statusText,
            statusMeta = statusMeta,
            statusProgress = statusProgress,
            resultActionsContainer = resultActionsContainer,
            manualFallbackButton = manualFallbackButton,
            cornerTopLeft = cornerTopLeft,
            cornerTopRight = cornerTopRight,
            cornerBottomLeft = cornerBottomLeft,
            cornerBottomRight = cornerBottomRight,
        )

        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

        observeOperatorViewModel()
        restoreRetainedOperatorOperation()
        configureScannerUi()
        setupScannerLayout()
        flashToggle.setOnClickListener { toggleFlash() }
        closeButton.setOnClickListener { closeScanner() }
        scanningFrameContainer.setOnClickListener {
            if (!isAutomaticRecognitionEnabled()) {
                renderAutomaticRecognitionFallback()
                return@setOnClickListener
            }
            updateMeteringRegion(force = true)
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (!operatorOperationInProgress && !vehicleScanCompleted) {
                setStatus(getString(R.string.scanner_refocusing_hint), ScanState.SCANNING)
            }
        }
        voiceEntryButton.setOnClickListener { startVoiceVehicleInput() }
        manualEntryInput.setOnClickListener {
            manualEntryInputLayout.error = null
            pauseScannerForManualEntry()
        }
        manualEntryInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                manualEntryInputLayout.error = null
                pauseScannerForManualEntry()
            } else {
                manualEntryInput.post {
                    if (!manualEntryInput.hasFocus() && !manualEntrySubmitButton.isPressed) {
                        resumeScannerAfterManualEntry()
                    }
                }
            }
        }
        manualEntryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitInlineManualEntry()
                true
            } else {
                false
            }
        }
        manualEntrySubmitButton.setOnClickListener { submitInlineManualEntry() }
        manualFallbackButton.setOnClickListener { showManualEntrySheet() }
        resultManualEntryButton.setOnClickListener {
            acknowledgePresentedOperatorState()
            clearPersistentOperatorError()
            vehicleScanCompleted = false
            showManualEntrySheet()
        }
        resultScanAgainButton.setOnClickListener {
            cancelVehicleScanResume()
            if (operatorViewModel.hasActiveOperation()) {
                operatorOperationInProgress = true
                vehicleScanCompleted = true
                pendingOperatorRequestId = operatorViewModel.activeRequestId()
                updateOperatorInteractionState()
                return@setOnClickListener
            }
            val wasShowingTerminalResult = vehicleScanCompleted ||
                hasUnacknowledgedTerminalResult()
            acknowledgePresentedOperatorState()
            if (detectorRuntimeFallbackMode != null) {
                clearPersistentOperatorError()
                operatorOperationInProgress = false
                vehicleScanCompleted = false
                pendingOperatorRequestId = null
                currentVehicleNumber = null
                currentQrCode = null
                clearDetectorRuntimeFallback()
                resumeScannerAfterBlockingInteraction()
                return@setOnClickListener
            }
            if (cameraManualFallbackReason != null) {
                clearPersistentOperatorError()
                operatorOperationInProgress = false
                vehicleScanCompleted = false
                pendingOperatorRequestId = null
                currentVehicleNumber = null
                currentQrCode = null
                moveScannerLifecycle(ScannerLifecycleEvent.RESET)
                if (wasShowingTerminalResult) {
                    renderPendingCameraFallbackIfPossible()
                } else {
                    retryCameraAfterManualFallback()
                }
                return@setOnClickListener
            }
            clearPersistentOperatorError()
            if (isVehicleScan() && scannerReadinessController.state == ScannerReadinessState.UNAVAILABLE) {
                vehicleScanCompleted = false
                prepareOperatorScannerModels()
                return@setOnClickListener
            }
            if (isOperatorQrMode()) {
                operatorOperationInProgress = false
                vehicleScanCompleted = false
                currentVehicleNumber = null
                currentQrCode = null
                resumeOperatorQrScanning()
            } else {
                restartVehicleScan()
            }
        }
        spotSelectorPill.setOnClickListener { showScannerSpotSelectionSheet() }
        checkInSegment.setOnClickListener { switchOperationType(OperationType.CHECK_IN, it) }
        checkOutSegment.setOnClickListener { switchOperationType(OperationType.CHECK_OUT, it) }
        plateInputSegment.setOnClickListener { switchScannerInputMode(ScannerInputMode.PLATE, it) }
        qrInputSegment.setOnClickListener { switchScannerInputMode(ScannerInputMode.QR, it) }
        updateOperationToggleUi(animated = false)
        updateScannerInputToggleUi(animated = false)
        updateOperatorInteractionState()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isVehicleScan()) {
                    closeScanner()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        startScannerFeatureRefreshLoop()
        val cameraPermissionGranted = checkCameraPermission()
        cameraManualFallbackReason = ScannerCameraFallbackPolicy.afterPermissionCheck(
            currentReason = cameraManualFallbackReason,
            permissionGranted = cameraPermissionGranted,
        )
        if (isVehicleScan() && !isAutomaticRecognitionEnabled()) {
            renderAutomaticRecognitionFallback()
            return
        }
        if (isVehicleScan() && renderDetectorRuntimeFallbackIfPresent()) return
        if (isVehicleScan() && renderPendingCameraFallbackIfPossible()) return
        if (cameraPermissionGranted) prepareScannerOrStart() else requestCameraPermission()
    }

    private fun configureScannerUi() {
        if (isVehicleScan()) {
            scannerUiUpdateGate.reset()
            val plateMode = isOperatorPlateMode()
            val qrMode = isOperatorQrMode()
            barcodeView.visibility = View.GONE
            vehiclePreview.visibility = if (plateMode || qrMode) View.VISIBLE else View.GONE
            scannerContentContainer.visibility = View.VISIBLE
            scanningFrameContainer.visibility = View.VISIBLE
            overlayView.visibility = View.VISIBLE
            val scannerReady = scannerReadinessController.state == ScannerReadinessState.READY
            scanLine.visibility = if (plateMode && scannerReady) View.VISIBLE else View.GONE
            vehicleHint.visibility = if (plateMode) View.VISIBLE else View.GONE
            statusContainer.visibility = View.VISIBLE
            if (scannerReady) {
                setStatus(getIdleScanMessage(), ScanState.SCANNING, showProgress = plateMode)
            }
            if (plateMode && scannerReady) {
                startScanLineAnimation()
                animateHintFadeIn()
            } else {
                stopScanLineAnimation()
                vehicleHint.animate().cancel()
                updateFlashToggleVisibility(false)
            }
            updateFlashToggleVisibility(plateMode)
            topControlsContainer.visibility = View.VISIBLE
            voiceEntryButton.visibility = View.GONE
            manualEntryCard.visibility = View.GONE
            resetCornerTints()
            animateCornerGlow(ScanState.SCANNING)
            // Show spot selector pill with current spot name
            spotSelectorPill.visibility = View.VISIBLE
            operationToggleContainer.visibility = View.VISIBLE
            scannerInputToggleContainer.visibility = View.VISIBLE
            updateScannerSpotPill(operatorParkingSpotName ?: operatorParkingSpotId)
            updateOperationToggleUi(animated = false)
            updateScannerInputToggleUi(animated = false)
            resetInlineManualEntryForm(clearText = false)
            if (renderPersistentOperatorError()) {
                stopScanLineAnimation()
            } else if (renderDetectorRuntimeFallbackIfPresent()) {
                stopScanLineAnimation()
            } else if (!scannerReady) {
                renderScannerReadiness(scannerReadinessController.state)
            }
            if (!isAutomaticRecognitionEnabled()) {
                renderAutomaticRecognitionFallback()
            }
        } else {
            barcodeView.visibility = View.VISIBLE
            vehiclePreview.visibility = View.GONE
            scannerContentContainer.visibility = View.GONE
            scanningFrameContainer.visibility = View.GONE
            overlayView.visibility = View.GONE
            scanLine.visibility = View.GONE
            vehicleHint.visibility = View.GONE
            statusContainer.visibility = View.GONE
            topControlsContainer.visibility = View.GONE
            spotSelectorPill.visibility = View.GONE
            operationToggleContainer.visibility = View.GONE
            scannerInputToggleContainer.visibility = View.GONE
            voiceEntryButton.visibility = View.GONE
            manualEntryCard.visibility = View.GONE
            stopScanLineAnimation()
            updateFlashToggleVisibility(false)
        }
        updateOperatorInteractionState()
    }

    private fun configureBarcodeViewChrome() {
        if (isVehicleScan()) {
            barcodeView.setStatusText("")
            barcodeView.viewFinder?.visibility = View.GONE
            barcodeView.statusView?.visibility = View.GONE
        }
    }

    private fun setupScannerLayout() {
        rootView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width <= 0 || height <= 0 ||
                (width == lastScannerRootWidthPx && height == lastScannerRootHeightPx)
            ) {
                return@addOnLayoutChangeListener
            }
            lastScannerRootWidthPx = width
            lastScannerRootHeightPx = height
            rootView.post { updateScannerLayoutForScreen() }
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            scannerSystemTopInsetPx = systemBars.top
            scannerSystemBottomInsetPx = systemBars.bottom
            val logicalInsets = ScannerChromeLayoutCalculator.logicalSideInsets(
                physicalLeftPx = systemBars.left,
                physicalRightPx = systemBars.right,
                isRtl = rootView.layoutDirection == View.LAYOUT_DIRECTION_RTL,
            )
            updateTopButtonInsets(
                closeButton,
                systemBars.top,
                logicalInsets.startPx,
                alignStart = true,
            )
            updateTopButtonInsets(
                flashToggle,
                systemBars.top,
                logicalInsets.endPx,
                alignStart = false,
            )

            (voiceEntryButton.layoutParams as? FrameLayout.LayoutParams)?.let { voiceParams ->
                voiceParams.topMargin = dp(88) + systemBars.top
                voiceParams.marginStart = dp(20) + logicalInsets.startPx
                voiceEntryButton.layoutParams = voiceParams
            }

            scannerContentContainer.setPadding(
                dp(24) + systemBars.left,
                0,
                dp(24) + systemBars.right,
                dp(36) + systemBars.bottom
            )
            rootView.post { updateScannerLayoutForScreen() }
            insets
        }
        rootView.post { updateScannerLayoutForScreen() }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun updateTopButtonInsets(view: View, topInset: Int, sideInset: Int, alignStart: Boolean) {
        val layoutParams = view.layoutParams as? FrameLayout.LayoutParams ?: return
        layoutParams.topMargin = dp(20) + topInset
        if (alignStart) {
            layoutParams.marginStart = dp(20) + sideInset
        } else {
            layoutParams.marginEnd = dp(20) + sideInset
        }
        view.layoutParams = layoutParams
    }

    private fun updateScannerLayoutForScreen() {
        if (!::scanningFrameContainer.isInitialized) return
        val screenWidth = rootView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val screenHeight = rootView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val usesSideRails = ScannerChromeLayoutCalculator.usesSideRails(screenWidth, screenHeight)
        val frameLayout = ScannerLayoutCalculator.calculate(
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            contentPaddingLeftPx = scannerContentContainer.paddingLeft,
            contentPaddingRightPx = scannerContentContainer.paddingRight,
            contentPaddingBottomPx = scannerContentContainer.paddingBottom,
            systemTopInsetPx = scannerSystemTopInsetPx,
            systemBottomInsetPx = scannerSystemBottomInsetPx,
            density = resources.displayMetrics.density,
            fontScale = resources.configuration.fontScale,
            qrMode = isOperatorQrMode(),
            usesSideRails = usesSideRails,
        )
        val targetWidth = frameLayout.widthPx
        val targetHeight = frameLayout.heightPx

        scanningFrameContainer.layoutParams?.let { layoutParams ->
            if (layoutParams.width != targetWidth || layoutParams.height != targetHeight) {
                layoutParams.width = targetWidth
                layoutParams.height = targetHeight
            }
            scanningFrameContainer.layoutParams = layoutParams
        }
        // ConstraintLayout centres within its padded content. Offset asymmetric side and bottom
        // insets so the frame remains centred on the physical camera preview (cutouts included).
        scanningFrameContainer.translationX = frameLayout.translationXPx
        scanningFrameContainer.translationY = frameLayout.translationYPx
        vehicleHint.translationX = frameLayout.translationXPx
        vehicleHint.translationY = frameLayout.translationYPx
        val chromeLayout = ScannerChromeLayoutCalculator.calculate(
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            topInsetPx = scannerSystemTopInsetPx,
            bottomInsetPx = scannerSystemBottomInsetPx,
            contentPaddingLeftPx = scannerContentContainer.paddingLeft,
            contentPaddingRightPx = scannerContentContainer.paddingRight,
            frameWidthPx = targetWidth,
            frameHeightPx = targetHeight,
            density = resources.displayMetrics.density,
            fontScale = resources.configuration.fontScale,
        )
        val viewportWasSupported = scannerViewportLayoutKnown &&
            scannerViewportSupportsLiveScanning
        scannerViewportSupportsLiveScanning = chromeLayout.isLiveScanningSupported
        scannerViewportLayoutKnown = true
        scannerUsesNarrowSideRails = chromeLayout.usesNarrowSideRails
        applyScannerChromeLayout(
            chromeLayout = chromeLayout,
            frameTranslationXPx = frameLayout.translationXPx,
            frameTranslationYPx = frameLayout.translationYPx,
        )

        (scanLine.layoutParams as? FrameLayout.LayoutParams)?.let { layoutParams ->
            val lineWidth = (targetWidth * 0.78f).roundToInt()
            if (layoutParams.width != lineWidth) {
                layoutParams.width = lineWidth
                scanLine.layoutParams = layoutParams
            }
        }

        vehicleHint.maxWidth = targetWidth + dp(48)
        statusContainer.minimumWidth = if (chromeLayout.usesSideRails) {
            0
        } else {
            (targetWidth * 0.64f).roundToInt().coerceAtLeast(dp(180))
        }

        manualFallbackButton.isVisible = isOperatorPlateMode() &&
            !operatorOperationInProgress &&
            !vehicleScanCompleted &&
            scannerReadinessController.state != ScannerReadinessState.UNAVAILABLE
        applyScannerViewportSupport(viewportWasSupported)
        val expectedLayoutMode = selectedScannerInputMode
        scanningFrameContainer.doOnLayout { frame ->
            if (selectedScannerInputMode == expectedLayoutMode &&
                frame.width == targetWidth && frame.height == targetHeight
            ) {
                markScannerModeSwitchLayoutReady(expectedLayoutMode)
            }
        }
        if (!isAutomaticRecognitionEnabled()) {
            renderAutomaticRecognitionFallback()
        } else {
            cameraManualFallbackReason?.let(::renderCameraManualFallback)
        }
        if (scannerViewportSupportsLiveScanning) {
            scanningFrameContainer.post { refreshScanRegion() }
        } else {
            scannerCameraController.invalidateScanRegion()
        }
    }

    private fun applyScannerViewportSupport(wasSupported: Boolean) {
        if (ScannerViewportStatePolicy.isUnsupported(
                scannerViewportLayoutKnown,
                scannerViewportSupportsLiveScanning,
            )
        ) {
            stopVehicleScanner(releaseCamera = false)
            stopOperatorQrCameraScanner(releaseCamera = false)
            stopScanLineAnimation()
            cancelScanTimeout()
            if (scannerCameraController.isBound) releaseScannerCameraSession()
            scanningFrameContainer.visibility = View.INVISIBLE
            scanningFrameContainer.isEnabled = false
            vehicleHint.visibility = View.GONE
            applySmallViewportChrome()
            if (isAutomaticRecognitionEnabled()) {
                renderSmallViewportFallback()
            } else {
                renderAutomaticRecognitionFallback()
            }
            return
        }

        statusContainer.translationX = 0f
        topControlsContainer.visibility = View.VISIBLE
        modeDockContainer.visibility = View.VISIBLE
        spotSelectorPill.visibility = View.VISIBLE
        operationToggleContainer.visibility = View.VISIBLE
        scannerInputToggleContainer.visibility = View.VISIBLE
        scanningFrameContainer.visibility = View.VISIBLE
        scanningFrameContainer.isEnabled =
            scannerReadinessController.state == ScannerReadinessState.READY
        if (wasSupported) return

        if (!isAutomaticRecognitionEnabled()) {
            renderAutomaticRecognitionFallback()
            return
        }
        renderScannerReadiness(scannerReadinessController.state)
        if (scannerReadinessController.state != ScannerReadinessState.READY ||
            !checkCameraPermission() ||
            !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        ) {
            return
        }
        rootView.post {
            if (!scannerViewportSupportsLiveScanning || isFinishing || isDestroyed) return@post
            if (isOperatorQrMode()) {
                resumeOperatorQrScanning()
            } else {
                resumeLiveVehicleScanning()
            }
        }
    }

    private fun applySmallViewportChrome() {
        topControlsContainer.visibility = View.GONE
        modeDockContainer.visibility = View.GONE
        statusContainer.translationX = scanningFrameContainer.translationX
        statusContainer.minimumWidth = 0
        statusContainer.minimumHeight = 0

        (statusContainer.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            params.width = 0
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.startToStart = ConstraintSet.PARENT_ID
            params.startToEnd = ConstraintSet.UNSET
            params.endToStart = ConstraintSet.UNSET
            params.endToEnd = ConstraintSet.PARENT_ID
            params.topToTop = ConstraintSet.PARENT_ID
            params.topToBottom = ConstraintSet.UNSET
            params.bottomToTop = ConstraintSet.UNSET
            params.bottomToBottom = ConstraintSet.PARENT_ID
            params.topMargin = scannerSystemTopInsetPx + dp(72)
            params.bottomMargin = scannerSystemBottomInsetPx + dp(16)
            params.marginStart = 0
            params.marginEnd = 0
            params.constrainedHeight = true
            params.verticalBias = 0.5f
            statusContainer.layoutParams = params
        }
        applyCompactScannerStatus(
            compact = true,
            narrow = false,
            largeText = resources.configuration.fontScale > 1.2f,
        )
    }

    private fun renderSmallViewportFallback() {
        if (!isVehicleScan() || !::statusContainer.isInitialized ||
            operatorOperationInProgress || vehicleScanCompleted ||
            persistentOperatorErrorMessage != null
        ) {
            return
        }
        if (!isAutomaticRecognitionEnabled()) {
            renderAutomaticRecognitionFallback()
            return
        }
        cameraManualFallbackReason?.let {
            renderCameraManualFallback(it)
            return
        }
        // The compact layout hides the Plate/QR selector. Force the only input that can still
        // complete without a live camera so QR mode never becomes a close-only dead end.
        if (selectedScannerInputMode != ScannerInputMode.PLATE) {
            setScannerInputModeState(ScannerInputMode.PLATE)
            updateScannerInputToggleUi(animated = false)
        }
        renderScannerPanel(
            mode = ScannerPanelMode.WARNING,
            operationType = selectedOperationType,
            title = getString(R.string.scanner_viewport_too_small_title),
            subtitle = getString(R.string.scanner_viewport_too_small_message),
            meta = getSpotMetaText(),
            showProgress = false,
        )
        statusTitle.maxLines = 2
        statusText.maxLines = 3
        resultActionsContainer.isVisible = false
        manualFallbackButton.isVisible = true
        statusContainer.scrollTo(0, 0)
        updateOperatorInteractionState()
    }

    private fun renderAutomaticRecognitionFallback() {
        if (!isVehicleScan() || !::statusContainer.isInitialized ||
            isAutomaticRecognitionEnabled() ||
            hasActiveOperatorMutation() || vehicleScanCompleted ||
            persistentOperatorErrorMessage != null
        ) {
            return
        }
        if (selectedScannerInputMode != ScannerInputMode.PLATE) {
            setScannerInputModeState(ScannerInputMode.PLATE)
            updateScannerInputToggleUi(animated = false)
        }
        if (scannerCameraController.isBound) releaseScannerCameraSession()
        stopScanLineAnimation()
        scanningFrameContainer.visibility = View.INVISIBLE
        scanningFrameContainer.isEnabled = false
        vehicleHint.visibility = View.GONE
        scannerInputToggleContainer.visibility = View.GONE
        updateFlashToggleVisibility(false)
        renderScannerPanel(
            mode = ScannerPanelMode.WARNING,
            operationType = selectedOperationType,
            title = getString(R.string.scanner_automatic_recognition_disabled_title),
            subtitle = getString(R.string.scanner_automatic_recognition_disabled_message),
            meta = getSpotMetaText(),
            showProgress = false,
        )
        statusTitle.maxLines = 2
        statusText.maxLines = 4
        resultActionsContainer.isVisible = false
        manualFallbackButton.isVisible = true
        (statusContainer as? androidx.core.widget.NestedScrollView)?.scrollTo(0, 0)
        updateOperatorInteractionState()
    }

    private fun renderCameraManualFallback(reason: CameraManualFallbackReason) {
        if (!isVehicleScan() || !::statusContainer.isInitialized ||
            !isAutomaticRecognitionEnabled() || hasActiveOperatorMutation() ||
            vehicleScanCompleted || persistentOperatorErrorMessage != null
        ) {
            return
        }
        cameraManualFallbackReason = reason
        if (selectedScannerInputMode != ScannerInputMode.PLATE) {
            setScannerInputModeState(ScannerInputMode.PLATE)
            updateScannerInputToggleUi(animated = false)
        }
        stopVehicleScanner(releaseCamera = false)
        stopOperatorQrCameraScanner(releaseCamera = false)
        cancelScanTimeout()
        stopScanLineAnimation()
        if (scannerCameraController.isBound) releaseScannerCameraSession()
        scannerCameraController.invalidateScanRegion()
        scanningFrameContainer.visibility = View.INVISIBLE
        scanningFrameContainer.isEnabled = false
        vehicleHint.visibility = View.GONE
        scannerInputToggleContainer.visibility = View.GONE
        updateFlashToggleVisibility(false)
        renderScannerPanel(
            mode = ScannerPanelMode.WARNING,
            operationType = selectedOperationType,
            title = getString(
                when (reason) {
                    CameraManualFallbackReason.PERMISSION_DENIED ->
                        R.string.scanner_camera_access_needed_title
                    CameraManualFallbackReason.UNAVAILABLE ->
                        R.string.scanner_camera_unavailable_title
                }
            ),
            subtitle = getString(
                when (reason) {
                    CameraManualFallbackReason.PERMISSION_DENIED ->
                        R.string.scanner_camera_permission_manual_message
                    CameraManualFallbackReason.UNAVAILABLE ->
                        R.string.scanner_camera_unavailable_manual_message
                }
            ),
            meta = getSpotMetaText(),
            showProgress = false,
        )
        statusTitle.maxLines = 2
        statusText.maxLines = 4
        resultScanAgainButton.setText(R.string.scanner_retry_camera)
        resultManualEntryButton.setText(R.string.scanner_enter_number)
        resultActionsContainer.isVisible = true
        manualFallbackButton.isVisible = false
        (statusContainer as? androidx.core.widget.NestedScrollView)?.scrollTo(0, 0)
        updateOperatorInteractionState()
    }

    /** Returns true whenever a sticky camera fallback must block automatic camera startup. */
    private fun renderPendingCameraFallbackIfPossible(): Boolean {
        val reason = cameraManualFallbackReason ?: return false
        if (!hasActiveOperatorMutation() && !vehicleScanCompleted) {
            renderCameraManualFallback(reason)
        }
        return true
    }

    /** Invalidates the released camera's mode/session without replacing mutation/result UI. */
    private fun markScannerCameraUnavailable() {
        cameraManualFallbackReason = CameraManualFallbackReason.UNAVAILABLE
        stopVehicleScanner(releaseCamera = false)
        stopOperatorQrCameraScanner(releaseCamera = false)
        cancelScanTimeout()
        stopScanLineAnimation()
        if (::scannerFrameReadinessGate.isInitialized) {
            scannerFrameReadinessGate.updatePreviewStreaming(false)
            publishScannerFrameReadiness()
        }
        updateFlashToggleVisibility(false)
    }

    /** Replaces an acknowledged terminal panel with the sticky manual camera fallback. */
    private fun showPendingCameraFallbackAfterTerminal(): Boolean {
        val reason = cameraManualFallbackReason ?: return false
        clearPersistentOperatorError()
        operatorOperationInProgress = false
        vehicleScanCompleted = false
        pendingOperatorRequestId = null
        currentVehicleNumber = null
        currentQrCode = null
        clearCandidateBuffer()
        moveScannerLifecycle(ScannerLifecycleEvent.RESET)
        renderCameraManualFallback(reason)
        return true
    }

    private fun retryCameraAfterManualFallback() {
        if (hasActiveOperatorMutation() || vehicleScanCompleted) return
        cameraManualFallbackReason = null
        configureScannerUi()
        updateScannerLayoutForScreen()
        updateOperatorInteractionState()
        if (checkCameraPermission()) {
            prepareScannerOrStart()
        } else {
            requestCameraPermission()
        }
    }

    private fun applyScannerChromeLayout(
        chromeLayout: ScannerChromeLayout,
        frameTranslationXPx: Float,
        frameTranslationYPx: Float,
    ) {
        val unset = ConstraintSet.UNSET
        val parent = ConstraintSet.PARENT_ID
        val gap = chromeLayout.sideRailGapPx
        val railCompensation = ScannerChromeLayoutCalculator.sideRailCompensation(
            frameTranslationXPx = frameTranslationXPx,
            isRtl = scannerContentContainer.layoutDirection == View.LAYOUT_DIRECTION_RTL,
        )

        (topControlsContainer.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            params.width = if (chromeLayout.usesSideRails) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
            params.startToStart = parent
            params.endToStart = if (chromeLayout.usesSideRails) {
                R.id.scanning_frame_container
            } else {
                unset
            }
            params.endToEnd = if (chromeLayout.usesSideRails) unset else parent
            params.topToTop = parent
            params.topToBottom = unset
            params.bottomToBottom = unset
            params.topMargin = chromeLayout.topControlsTopMarginPx
            params.marginEnd = if (chromeLayout.usesSideRails) {
                gap + railCompensation.startPx
            } else {
                0
            }
            topControlsContainer.layoutParams = params
        }
        (spotSelectorPill.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = if (chromeLayout.usesSideRails) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            spotSelectorPill.minimumWidth = if (chromeLayout.usesSideRails) 0 else dp(148)
            spotSelectorPill.layoutParams = params
        }

        (modeDockContainer.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            params.width = 0
            params.startToStart = parent
            params.startToEnd = unset
            params.endToStart = if (chromeLayout.usesSideRails) {
                R.id.scanning_frame_container
            } else {
                unset
            }
            params.endToEnd = if (chromeLayout.usesSideRails) unset else parent
            params.topToBottom = R.id.scanner_top_controls
            params.topToTop = unset
            params.bottomToBottom = unset
            params.topMargin = chromeLayout.modeDockTopGapPx
            params.marginEnd = if (chromeLayout.usesSideRails) {
                gap + railCompensation.startPx
            } else {
                0
            }
            modeDockContainer.layoutParams = params
        }

        (statusContainer.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            params.width = 0
            params.height = if (chromeLayout.usesSideRails) {
                0
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            params.startToStart = if (chromeLayout.usesSideRails) unset else parent
            params.startToEnd = if (chromeLayout.usesSideRails) {
                R.id.scanning_frame_container
            } else {
                unset
            }
            params.endToStart = unset
            params.endToEnd = parent
            params.topToTop = if (chromeLayout.usesSideRails) parent else unset
            params.topToBottom = if (chromeLayout.usesSideRails) {
                unset
            } else {
                R.id.scanning_frame_container
            }
            params.bottomToTop = unset
            params.bottomToBottom = parent
            params.topMargin = if (chromeLayout.usesSideRails) {
                chromeLayout.statusTopMarginPx
            } else {
                dp(50) + frameTranslationYPx.coerceAtLeast(0f).roundToInt()
            }
            params.bottomMargin = 0
            params.constrainedHeight = true
            params.verticalBias = if (chromeLayout.usesSideRails) 0f else 1f
            params.marginStart = if (chromeLayout.usesSideRails) {
                gap + railCompensation.endPx
            } else {
                0
            }
            statusContainer.layoutParams = params
        }

        applyScannerModeDockSizing(chromeLayout.usesNarrowSideRails)
        applyCompactScannerStatus(
            compact = chromeLayout.usesSideRails,
            narrow = chromeLayout.usesNarrowSideRails,
            largeText = resources.configuration.fontScale > 1.2f,
        )
    }

    private fun applyScannerModeDockSizing(narrow: Boolean) {
        val dockPadding = dp(if (narrow) 2 else 10)
        modeDockContainer.setPadding(dockPadding, dockPadding, dockPadding, dockPadding)

        (operationToggleContainer.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.height = dp(48)
            operationToggleContainer.layoutParams = params
        }
        (scannerInputToggleContainer.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.height = dp(48)
            params.topMargin = dp(if (narrow) 2 else 8)
            scannerInputToggleContainer.layoutParams = params
        }

        spotSelectorLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (narrow) 12f else 14f)
        checkInLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (narrow) 11f else 14f)
        checkOutLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (narrow) 11f else 14f)
        plateInputLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (narrow) 11f else 13f)
        qrInputLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (narrow) 11f else 13f)
        listOf(checkInLabel, checkOutLabel, plateInputLabel, qrInputLabel).forEach { label ->
            label.maxLines = 1
            label.ellipsize = android.text.TextUtils.TruncateAt.END
        }
    }

    private fun applyCompactScannerStatus(
        compact: Boolean,
        narrow: Boolean,
        largeText: Boolean,
    ) {
        val horizontalPadding = dp(
            when {
                narrow && largeText -> 6
                narrow -> 8
                compact -> 12
                else -> 18
            }
        )
        val verticalPadding = dp(
            when {
                narrow && largeText -> 6
                narrow -> 8
                compact -> 12
                else -> 16
            }
        )
        statusContainer.minimumHeight = if (compact) 0 else dp(162)
        statusContainer.setPadding(
            horizontalPadding,
            verticalPadding,
            horizontalPadding,
            verticalPadding,
        )

        statusIconContainer.visibility = if (narrow) View.GONE else View.VISIBLE
        statusBadge.visibility = if (narrow) View.GONE else View.VISIBLE
        (statusIconContainer.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            val iconSize = dp(if (compact) 36 else 44)
            params.width = iconSize
            params.height = iconSize
            statusIconContainer.layoutParams = params
        }
        val iconPadding = dp(if (compact) 8 else 10)
        statusIconContainer.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)

        (statusBadge.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.marginStart = if (narrow) 0 else dp(12)
            statusBadge.layoutParams = params
        }
        val badgeHorizontalPadding = dp(if (narrow) 8 else 12)
        val badgeVerticalPadding = dp(if (narrow) 4 else 6)
        statusBadge.setPadding(
            badgeHorizontalPadding,
            badgeVerticalPadding,
            badgeHorizontalPadding,
            badgeVerticalPadding,
        )
        statusBadge.maxLines = 1
        statusBadge.ellipsize = android.text.TextUtils.TruncateAt.END
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (narrow) 10f else 11f)

        (statusProgress.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            val progressSize = dp(if (narrow) 16 else 18)
            params.width = progressSize
            params.height = progressSize
            statusProgress.layoutParams = params
        }

        statusTitle.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            when {
                // fontScale is already > 1.2 here. A compact base size preserves an accessible
                // rendered size while leaving room for the full instruction in a short rail.
                narrow && largeText -> 14f
                narrow -> 18f
                compact -> 20f
                else -> 26f
            },
        )
        statusText.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            when {
                narrow && largeText -> 10f
                narrow -> 12f
                compact -> 13f
                else -> 15f
            },
        )
        statusMeta.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            when {
                narrow && largeText -> 9f
                narrow -> 11f
                compact -> 12f
                else -> 14f
            },
        )
        statusTitle.maxLines = if (narrow || largeText) 2 else 1
        statusText.maxLines = if (narrow && largeText) 5 else if (narrow || largeText) 3 else 2
        statusMeta.maxLines = if (narrow || largeText) 2 else 1
        statusText.setLineSpacing(dp(if (compact) 1 else 2).toFloat(), 1f)
        setTopMargin(
            statusTitle,
            dp(if (narrow && largeText) 3 else if (narrow) 6 else if (compact) 8 else 14),
        )
        setTopMargin(
            statusText,
            dp(if (narrow && largeText) 2 else if (narrow) 4 else if (compact) 6 else 8),
        )
        setTopMargin(
            statusMeta,
            dp(if (narrow && largeText) 3 else if (narrow) 4 else if (compact) 6 else 8),
        )

        (resultActionsContainer as? LinearLayout)?.orientation = if (compact) {
            LinearLayout.VERTICAL
        } else {
            LinearLayout.HORIZONTAL
        }
        setTopMargin(resultActionsContainer, dp(if (compact) 8 else 14))
        configureResultActionLayout(
            resultScanAgainButton,
            compact,
            narrow,
            largeText,
            first = true,
        )
        configureResultActionLayout(
            resultManualEntryButton,
            compact,
            narrow,
            largeText,
            first = false,
        )

        (manualFallbackButton.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.topMargin = dp(if (narrow && largeText) 6 else if (compact) 8 else 14)
            manualFallbackButton.layoutParams = params
        }
        manualFallbackButton.minimumHeight = dp(if (compact) 48 else 50)
        manualFallbackButton.isSingleLine = false
        manualFallbackButton.maxLines = 2
        val fallbackHorizontalPadding = dp(if (narrow) 6 else 12)
        manualFallbackButton.setPadding(
            fallbackHorizontalPadding,
            if (narrow) 0 else manualFallbackButton.paddingTop,
            fallbackHorizontalPadding,
            if (narrow) 0 else manualFallbackButton.paddingBottom,
        )
        manualFallbackButton.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            if (narrow && largeText) 11f else if (compact) 13f else 14f,
        )
    }

    private fun configureResultActionLayout(
        button: MaterialButton,
        compact: Boolean,
        narrow: Boolean,
        largeText: Boolean,
        first: Boolean,
    ) {
        (button.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = if (compact) ViewGroup.LayoutParams.MATCH_PARENT else 0
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.weight = if (compact) 0f else 1f
            params.marginStart = if (!compact && !first) dp(8) else 0
            params.marginEnd = if (!compact && first) dp(8) else 0
            params.topMargin = if (compact && !first) dp(6) else 0
            params.bottomMargin = 0
            button.layoutParams = params
        }
        button.minimumHeight = dp(if (compact) 48 else 50)
        button.isSingleLine = false
        button.maxLines = 2
        val horizontalPadding = dp(if (narrow) 6 else 12)
        button.setPadding(
            horizontalPadding,
            if (narrow) 0 else button.paddingTop,
            horizontalPadding,
            if (narrow) 0 else button.paddingBottom,
        )
        button.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            if (narrow && largeText) 10.5f else if (compact) 12f else 14f,
        )
    }

    private fun setTopMargin(view: View, topMarginPx: Int) {
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.topMargin = topMarginPx
            view.layoutParams = params
        }
    }

    private fun isVehicleScan(): Boolean {
        return scanType.uppercase(Locale.ROOT).contains("VEHICLE")
    }

    private fun isOperatorPlateMode(): Boolean {
        return isVehicleScan() && selectedScannerInputMode == ScannerInputMode.PLATE
    }

    private fun isOperatorQrMode(): Boolean {
        return isVehicleScan() && selectedScannerInputMode == ScannerInputMode.QR
    }

    private fun scannerMetricMode(): String {
        return when {
            isOperatorQrMode() -> "qr"
            isOperatorPlateMode() -> "plate"
            else -> "booking_qr"
        }
    }

    private fun scannerFrameReadinessMode(): ScannerFrameReadinessMode {
        return when (selectedScannerInputMode) {
            ScannerInputMode.PLATE -> ScannerFrameReadinessMode.PLATE
            ScannerInputMode.QR -> ScannerFrameReadinessMode.QR
        }
    }

    private fun setScannerInputModeState(inputMode: ScannerInputMode) {
        val changed = selectedScannerInputMode != inputMode
        selectedScannerInputMode = inputMode
        if (changed) {
            detectorRuntimeFailurePolicy.reset()
        }
        if (::scannerFrameReadinessGate.isInitialized) {
            scannerFrameReadinessGate.selectMode(scannerFrameReadinessMode())
            publishScannerFrameReadiness()
        }
        if (changed && ::scannerPerformance.isInitialized) {
            scannerPerformance.modeSwitched(scannerMetricMode(), scannerMetricOperation())
            updatePerformanceUiState()
        }
    }

    private fun scannerMetricOperation(): String {
        return when (selectedOperationType) {
            OperationType.CHECK_IN -> "entry"
            OperationType.CHECK_OUT -> "exit"
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun moveScannerLifecycle(event: ScannerLifecycleEvent) {
        if (!::scannerStateMachine.isInitialized) return
        val transition = scannerStateMachine.accept(event)
        if (transition.changed && ::scannerPerformance.isInitialized) {
            scannerPerformance.lifecycleState(transition.current.name.lowercase(Locale.ROOT))
        }
        performanceMetricsStateHolder?.state?.putState(
            "scanner_lifecycle",
            transition.current.name.lowercase(Locale.ROOT),
        )
    }

    private fun getIdleScanMessage(): String {
        return if (isOperatorQrMode()) {
            getString(R.string.scanner_qr_scan_hint)
        } else {
            getString(R.string.vehicle_scan_detecting)
        }
    }

    private fun resetCornerTints() {
        val idleColor = ContextCompat.getColor(this, R.color.scanner_corner_idle)
        scannerUiRenderer.resetCornerColor(idleColor)
    }

    private fun observeOperatorViewModel() {
        operatorViewModel.checkInState.observe(this) { state ->
            if (!isVehicleScan()) return@observe
            handleOperationState(state, OperationType.CHECK_IN)
        }
        operatorViewModel.checkOutState.observe(this) { state ->
            if (!isVehicleScan()) return@observe
            handleOperationState(state, OperationType.CHECK_OUT)
        }
    }

    private fun restoreRetainedOperatorOperation() {
        val activeRequestId = operatorViewModel.activeRequestId() ?: return
        pendingOperatorRequestId = activeRequestId
        operatorOperationInProgress = true
        vehicleScanCompleted = true
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            AUDIO_PERMISSION_REQUEST
        )
    }

    private fun startScannerFeatureRefreshLoop() {
        if (!isVehicleScan()) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val refreshedConfig = try {
                        RemoteConfigManager.refreshIfStale(this@QrScannerActivity)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    refreshedConfig?.let { config ->
                        applyScannerFeatureControls(
                            ScannerFeatureControls.from(
                                customSettings = config.customSettings,
                                featureToggles = config.features.featureToggleMap,
                            )
                        )
                    }
                    delay(SCANNER_CONFIG_POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun applyScannerFeatureControls(newControls: ScannerFeatureControls) {
        val activeMutation = hasActiveOperatorMutation()
        val previousControls = scannerFeatureControls
        val recognitionPolicyChanged =
            newControls.invalidatesRecognitionWorkComparedTo(previousControls)
        val analysisSizeChanged = newControls.analysisWidth != previousControls.analysisWidth ||
            newControls.analysisHeight != previousControls.analysisHeight
        // Publish the immutable control snapshot first. A frame racing this update either keeps
        // the old generation and is rejected below, or starts with both the new generation and
        // the new policy.
        scannerFeatureControls = newControls
        val update = automaticRecognitionGate.updateEnabled(
            newEnabled = newControls.automaticRecognitionEnabled,
            mutationInFlight = activeMutation,
            invalidateExistingWork = recognitionPolicyChanged,
        )
        if (!update.shouldInvalidateAutomaticWork) return

        if (update.changed) {
            scannerPerformance.automaticRecognitionAvailability(update.currentEnabled)
        }
        detectorRuntimeFailurePolicy.reset()
        clearCandidateBuffer()
        scannerCameraController.invalidateScanRegion()

        if (!update.currentEnabled) {
            stopAutomaticRecognitionPipelineForFeatureDisable(
                releaseCamera = !update.preserveInFlightMutation,
            )
            if (update.preserveInFlightMutation || persistentOperatorErrorMessage != null) return

            // A pending OCR confirmation is not a mutation and must not survive the kill switch.
            if (uncertainPlateDialog?.isShowing == true) {
                uncertainPlateDialog?.dismiss()
                uncertainPlateDialog = null
                vehicleScanCompleted = false
                currentVehicleNumber = null
                currentQrCode = null
            }
            // Preserve an already-rendered terminal/success result. Its normal recovery callback
            // will enter the disabled fallback without restarting CameraX.
            if (vehicleScanCompleted) return

            setScannerInputModeState(ScannerInputMode.PLATE)
            updateScannerInputToggleUi(animated = false)
            renderAutomaticRecognitionFallback()
            return
        }

        val hadPendingPlateConfirmation = uncertainPlateDialog?.isShowing == true
        if (hadPendingPlateConfirmation) {
            uncertainPlateDialog?.dismiss()
            uncertainPlateDialog = null
            vehicleScanCompleted = false
            currentVehicleNumber = null
            currentQrCode = null
        }
        val rebindDecision = ScannerCameraConfigUpdatePolicy.decide(
            analysisSizeChanged = analysisSizeChanged,
            protectedOperatorState = update.preserveInFlightMutation ||
                vehicleScanCompleted || persistentOperatorErrorMessage != null,
        )
        scannerCameraRebindPending = scannerCameraRebindPending ||
            rebindDecision.rebindNow || rebindDecision.keepPending
        if (rebindDecision.rebindNow) consumePendingScannerCameraRebind()
        if (update.preserveInFlightMutation || vehicleScanCompleted ||
            persistentOperatorErrorMessage != null
        ) {
            return
        }
        if (update.changed || analysisSizeChanged || hadPendingPlateConfirmation) {
            restoreAutomaticRecognitionPipeline()
        } else {
            applyScannerZoomDefault()
            applyExposureCompensation(0)
            scanningFrameContainer.post { refreshScanRegion() }
        }
    }

    /**
     * Stops automatic work immediately without touching an in-flight ViewModel mutation or its UI.
     * This is intentionally separate from rendering/resetting the idle fallback state.
     */
    private fun stopAutomaticRecognitionPipelineForFeatureDisable(releaseCamera: Boolean = true) {
        stopVehicleScanner(releaseCamera = false)
        stopOperatorQrCameraScanner(releaseCamera = false)
        cancelScanTimeout()
        stopScanLineAnimation()
        resetScannerQualityState()
        resetCameraZoom()
        if (releaseCamera && scannerCameraController.isBound) releaseScannerCameraSession()
    }

    private fun restoreAutomaticRecognitionPipeline() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            hasActiveOperatorMutation() || vehicleScanCompleted
        ) {
            return
        }
        configureScannerUi()
        updateScannerLayoutForScreen()
        updateOperatorInteractionState()
        if (renderPendingCameraFallbackIfPossible()) {
            return
        } else if (checkCameraPermission()) {
            prepareScannerOrStart()
        } else {
            requestCameraPermission()
        }
    }

    private fun hasActiveOperatorMutation(): Boolean =
        operatorOperationInProgress || operatorViewModel.hasActiveOperation()

    private fun isAutomaticRecognitionEnabled(): Boolean =
        ::automaticRecognitionGate.isInitialized &&
            automaticRecognitionGate.isAutomaticRecognitionEnabled()

    private fun automaticRecognitionCommitDecision(
        source: ScannerAutomaticRecognitionSource,
        recognitionGeneration: Long,
    ): ScannerRecognitionDecision = automaticRecognitionGate.automaticCommitDecision(
        source = source,
        startedAtGeneration = recognitionGeneration,
        mutationInFlight = hasActiveOperatorMutation(),
    )

    private fun canCommitAutomaticRecognition(
        source: ScannerAutomaticRecognitionSource,
        recognitionGeneration: Long,
    ): Boolean = automaticRecognitionCommitDecision(
        source = source,
        recognitionGeneration = recognitionGeneration,
    ) == ScannerRecognitionDecision.ALLOW

    /** Final main-thread guard for work tied to one live analyzer session. */
    private fun isAutomaticRecognitionCommitContextValid(
        source: ScannerAutomaticRecognitionSource,
        scanSessionId: Long,
        allowStoppedPlateConfirmation: Boolean = false,
    ): Boolean {
        val sourceModeActive = when (source) {
            ScannerAutomaticRecognitionSource.QR -> isOperatorQrMode()
            ScannerAutomaticRecognitionSource.PLATE -> isOperatorPlateMode()
        }
        val scannerRunning = when (source) {
            ScannerAutomaticRecognitionSource.QR -> operatorQrScannerRunning
            ScannerAutomaticRecognitionSource.PLATE -> vehicleScannerRunning
        }
        return ScannerAutomaticCommitContext(
            expectedSessionId = scanSessionId,
            currentSessionId = vehicleScannerSessionId,
            scannerRunning = scannerRunning,
            sourceModeActive = sourceModeActive,
            lifecycleResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            viewportReady = scannerViewportSupportsLiveScanning,
            detectorReady = scannerReadinessController.state == ScannerReadinessState.READY,
            blockingInteraction = scannerSpotSelectionDialog?.isShowing == true ||
                manualEntrySheet?.isShowing == true ||
                voiceRecognitionInProgress || voiceListeningInProgress ||
                manualEntryInput.hasFocus(),
            activityClosing = isFinishing || isDestroyed,
            allowStoppedOperatorConfirmation = allowStoppedPlateConfirmation &&
                source == ScannerAutomaticRecognitionSource.PLATE,
        ).isValid()
    }

    private fun isRecognitionFrameRuntimeCurrent(
        frameContext: ScannerRecognitionFrameContext,
    ): Boolean {
        val mutationInFlight = hasActiveOperatorMutation()
        val sourceModeActive = when (frameContext.source) {
            ScannerAutomaticRecognitionSource.QR -> isOperatorQrMode()
            ScannerAutomaticRecognitionSource.PLATE -> isOperatorPlateMode()
        }
        val scannerRunning = when (frameContext.source) {
            ScannerAutomaticRecognitionSource.QR -> operatorQrScannerRunning
            ScannerAutomaticRecognitionSource.PLATE -> vehicleScannerRunning
        }
        return ScannerFrameRuntimeGuard(
            expectedSessionId = frameContext.scanSessionId,
            currentSessionId = vehicleScannerSessionId,
            scannerRunning = scannerRunning,
            sourceModeActive = sourceModeActive,
            operationActive = selectedOperationType == frameContext.operationType,
            scanCompleted = vehicleScanCompleted,
            mutationInFlight = mutationInFlight,
            activityClosing = isFinishing || isDestroyed,
            recognitionDecision = automaticRecognitionGate.automaticCommitDecision(
                source = frameContext.source,
                startedAtGeneration = frameContext.recognitionGeneration,
                mutationInFlight = mutationInFlight,
            ),
        ).isCurrent()
    }

    private fun isCurrentDetectorTask(frameContext: ScannerRecognitionFrameContext): Boolean {
        return isRecognitionFrameRuntimeCurrent(frameContext) &&
            isAutomaticRecognitionCommitContextValid(
                source = frameContext.source,
                scanSessionId = frameContext.scanSessionId,
            )
    }

    private fun recordDetectorTaskSuccess(frameContext: ScannerRecognitionFrameContext) {
        runOnUiThread {
            if (isCurrentDetectorTask(frameContext)) {
                detectorRuntimeFailurePolicy.recordSuccess()
            }
        }
    }

    private fun handleDetectorTaskFailure(
        frameContext: ScannerRecognitionFrameContext,
        transientMessageRes: Int,
    ) {
        runOnUiThread {
            if (!isCurrentDetectorTask(frameContext)) {
                return@runOnUiThread
            }
            when (detectorRuntimeFailurePolicy.recordFailure()) {
                ScannerDetectorRuntimeFailureDecision.KEEP_SCANNING -> setStatus(
                    getString(transientMessageRes),
                    ScanState.WARNING,
                    showProgress = false,
                )
                ScannerDetectorRuntimeFailureDecision.SHOW_FALLBACK ->
                    showDetectorRuntimeFallback(frameContext.source)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cameraManualFallbackReason = ScannerCameraFallbackPolicy.afterPermissionCheck(
                        currentReason = cameraManualFallbackReason,
                        permissionGranted = true,
                    )
                    if (renderPendingCameraFallbackIfPossible()) {
                        return
                    }
                    configureScannerUi()
                    updateScannerLayoutForScreen()
                    prepareScannerOrStart()
                } else if (isVehicleScan() && !isAutomaticRecognitionEnabled()) {
                    renderAutomaticRecognitionFallback()
                } else if (isVehicleScan()) {
                    Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
                    renderCameraManualFallback(CameraManualFallbackReason.PERMISSION_DENIED)
                } else {
                    Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            AUDIO_PERMISSION_REQUEST -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (granted && pendingVoiceStartAfterPermission) {
                    pendingVoiceStartAfterPermission = false
                    startVoiceVehicleInput()
                } else if (!granted) {
                    pendingVoiceStartAfterPermission = false
                    showScannerNotification(
                        title = getString(R.string.voice_input),
                        message = getString(R.string.vehicle_scan_voice_permission_required),
                        isError = true
                    )
                }
            }
        }
    }

    private fun prepareScannerOrStart() {
        if (!isVehicleScan()) {
            startScanner()
            return
        }
        if (!isAutomaticRecognitionEnabled()) {
            renderAutomaticRecognitionFallback()
            return
        }
        if (renderDetectorRuntimeFallbackIfPresent()) return
        if (renderPendingCameraFallbackIfPossible()) return
        when (scannerReadinessController.state) {
            ScannerReadinessState.READY -> startScanner()
            ScannerReadinessState.PREPARING -> {
                if (scannerModelPreparationInFlight) {
                    renderScannerReadiness(ScannerReadinessState.PREPARING)
                } else {
                    prepareOperatorScannerModels()
                }
            }
            ScannerReadinessState.UNAVAILABLE -> renderScannerReadiness(ScannerReadinessState.UNAVAILABLE)
        }
    }

    private fun prepareOperatorScannerModels() {
        if (!isVehicleScan() || !isAutomaticRecognitionEnabled() ||
            scannerModelPreparationInFlight || isFinishing || isDestroyed ||
            hasActiveOperatorMutation() || vehicleScanCompleted ||
            persistentOperatorErrorMessage != null
        ) return

        val requestId = scannerReadinessController.beginPreparation()
        moveScannerLifecycle(ScannerLifecycleEvent.PREPARE)
        scannerModelPreparationInFlight = true
        stopVehicleScanner(releaseCamera = false)
        stopOperatorQrCameraScanner(releaseCamera = false)
        renderScannerReadiness(ScannerReadinessState.PREPARING)
        scannerPerformance.readinessState("preparing")

        moduleInstallClient.areModulesAvailable(textRecognizer, qrBarcodeScanner)
            .addOnSuccessListener(ContextCompat.getMainExecutor(this)) { availability ->
                if (!scannerReadinessController.isActive(requestId) || isFinishing || isDestroyed) {
                    return@addOnSuccessListener
                }
                if (availability.areModulesAvailable()) {
                    completeScannerModelPreparation(requestId)
                    return@addOnSuccessListener
                }

                val installRequest = ModuleInstallRequest.newBuilder()
                    .addApi(textRecognizer)
                    .addApi(qrBarcodeScanner)
                    .build()
                moduleInstallClient.installModules(installRequest)
                    .addOnSuccessListener(ContextCompat.getMainExecutor(this)) {
                        completeScannerModelPreparation(requestId)
                    }
                    .addOnFailureListener(ContextCompat.getMainExecutor(this)) { failure ->
                        failScannerModelPreparation(requestId, failure)
                    }
            }
            .addOnFailureListener(ContextCompat.getMainExecutor(this)) { failure ->
                failScannerModelPreparation(requestId, failure)
            }
    }

    private fun completeScannerModelPreparation(requestId: Long) {
        if (!scannerReadinessController.markReady(requestId) || isFinishing || isDestroyed) return
        scannerModelPreparationInFlight = false
        val protectedState = hasActiveOperatorMutation() || vehicleScanCompleted ||
            persistentOperatorErrorMessage != null
        if (!protectedState) moveScannerLifecycle(ScannerLifecycleEvent.READY)
        scannerPerformance.readinessState("ready")
        renderScannerReadiness(ScannerReadinessState.READY)
        if (!protectedState && isAutomaticRecognitionEnabled() && checkCameraPermission() &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        ) {
            startScanner()
        }
    }

    private fun failScannerModelPreparation(requestId: Long, failure: Exception) {
        if (!scannerReadinessController.markUnavailable(requestId) || isFinishing || isDestroyed) return
        scannerModelPreparationInFlight = false
        if (!hasActiveOperatorMutation() && !vehicleScanCompleted &&
            persistentOperatorErrorMessage == null
        ) {
            moveScannerLifecycle(ScannerLifecycleEvent.MODEL_UNAVAILABLE)
        }
        val reason = failure.javaClass.simpleName.ifBlank { "module_install_failure" }
        scannerPerformance.readinessState("unavailable", reason)
        scannerPerformance.detectorInitializationFailed("ocr_and_qr", reason)
        renderScannerReadiness(ScannerReadinessState.UNAVAILABLE)
    }

    private fun renderScannerReadiness(state: ScannerReadinessState) {
        if (!isVehicleScan() || !::statusContainer.isInitialized) return
        if (!isAutomaticRecognitionEnabled()) {
            renderAutomaticRecognitionFallback()
            return
        }
        if (renderDetectorRuntimeFallbackIfPresent()) return
        if (renderPendingCameraFallbackIfPossible()) return
        if (ScannerViewportStatePolicy.isUnsupported(
                scannerViewportLayoutKnown,
                scannerViewportSupportsLiveScanning,
            )
        ) {
            renderSmallViewportFallback()
            return
        }
        if (operatorOperationInProgress) {
            setStatus(getActiveProcessingMessage(selectedOperationType), ScanState.SCANNING, showProgress = true)
            return
        }
        if (renderPersistentOperatorError()) return

        when (state) {
            ScannerReadinessState.PREPARING -> {
                stopScanLineAnimation()
                scanningFrameContainer.alpha = 0.78f
                renderScannerPanel(
                    mode = ScannerPanelMode.SCANNING,
                    operationType = selectedOperationType,
                    title = getString(R.string.scanner_preparing_title),
                    subtitle = getString(R.string.scanner_preparing_message),
                    meta = getSpotMetaText(),
                    showProgress = true,
                )
                resultActionsContainer.isVisible = false
                manualFallbackButton.isVisible = isOperatorPlateMode()
            }
            ScannerReadinessState.READY -> {
                scanningFrameContainer.alpha = 1f
                resultScanAgainButton.setText(R.string.vehicle_scan_retry_button)
                resultManualEntryButton.setText(R.string.scanner_enter_number)
                resultActionsContainer.isVisible = false
                manualFallbackButton.isVisible = isOperatorPlateMode()
            }
            ScannerReadinessState.UNAVAILABLE -> {
                stopScanLineAnimation()
                scanningFrameContainer.alpha = 0.64f
                renderScannerPanel(
                    mode = ScannerPanelMode.ERROR,
                    operationType = selectedOperationType,
                    title = getString(R.string.scanner_unavailable_title),
                    subtitle = getString(R.string.scanner_unavailable_message),
                    meta = getSpotMetaText(),
                    showProgress = false,
                )
                resultScanAgainButton.setText(R.string.scanner_retry_setup)
                resultManualEntryButton.setText(R.string.scanner_enter_number)
                resultActionsContainer.isVisible = true
                manualFallbackButton.isVisible = false
            }
        }
        scanningFrameContainer.isEnabled = state == ScannerReadinessState.READY
        updateOperatorInteractionState()
    }

    private fun showDetectorRuntimeFallback(source: ScannerAutomaticRecognitionSource) {
        if (!isVehicleScan() || detectorRuntimeFallbackMode != null ||
            cameraManualFallbackReason != null || hasActiveOperatorMutation() ||
            vehicleScanCompleted || persistentOperatorErrorMessage != null
        ) {
            return
        }
        detectorRuntimeFallbackMode = when (source) {
            ScannerAutomaticRecognitionSource.PLATE -> ScannerInputMode.PLATE
            ScannerAutomaticRecognitionSource.QR -> ScannerInputMode.QR
        }
        cancelVehicleScanResume()
        stopVehicleScanner(releaseCamera = false)
        stopOperatorQrCameraScanner(releaseCamera = false)
        cancelScanTimeout()
        stopScanLineAnimation()
        clearCandidateBuffer()
        operatorOperationInProgress = false
        vehicleScanCompleted = true
        currentVehicleNumber = null
        currentQrCode = null
        moveScannerLifecycle(ScannerLifecycleEvent.LOCAL_ERROR)
        renderDetectorRuntimeFallbackPanel()
        scannerPerformance.errorAwaitingOperatorAction()
        scannerPerformance.finishAttempt(
            "detector_runtime_failure_${source.name.lowercase(Locale.ROOT)}"
        )
        provideFeedback(FeedbackType.ERROR)
    }

    private fun renderDetectorRuntimeFallbackIfPresent(): Boolean {
        if (detectorRuntimeFallbackMode == null || !isVehicleScan() ||
            !::statusContainer.isInitialized || cameraManualFallbackReason != null ||
            hasActiveOperatorMutation() || persistentOperatorErrorMessage != null
        ) {
            return false
        }
        vehicleScanCompleted = true
        stopVehicleScanner(releaseCamera = false)
        stopOperatorQrCameraScanner(releaseCamera = false)
        cancelScanTimeout()
        stopScanLineAnimation()
        renderDetectorRuntimeFallbackPanel()
        return true
    }

    private fun renderDetectorRuntimeFallbackPanel() {
        scanningFrameContainer.alpha = 0.64f
        scanningFrameContainer.isEnabled = false
        renderScannerPanel(
            mode = ScannerPanelMode.ERROR,
            operationType = selectedOperationType,
            title = getString(R.string.scanner_unavailable_title),
            subtitle = getString(R.string.scanner_unavailable_message),
            meta = getSpotMetaText(),
            showProgress = false,
        )
        resultScanAgainButton.setText(R.string.vehicle_scan_retry_button)
        resultManualEntryButton.setText(R.string.scanner_enter_number)
        resultActionsContainer.isVisible = true
        manualFallbackButton.isVisible = false
        (statusContainer as? androidx.core.widget.NestedScrollView)?.scrollTo(0, 0)
        updateOperatorInteractionState()
    }

    private fun clearDetectorRuntimeFallback() {
        detectorRuntimeFallbackMode = null
        detectorRuntimeFailurePolicy.reset()
        if (::scanningFrameContainer.isInitialized) {
            scanningFrameContainer.alpha = 1f
            scanningFrameContainer.isEnabled = isAutomaticRecognitionEnabled() &&
                scannerReadinessController.state == ScannerReadinessState.READY &&
                cameraManualFallbackReason == null && scannerViewportSupportsLiveScanning
        }
    }

    private fun renderPersistentOperatorError(): Boolean {
        val message = persistentOperatorErrorMessage ?: return false
        if (!isVehicleScan() || !::statusContainer.isInitialized) return false

        vehicleScanCompleted = true
        stopScanLineAnimation()
        setStatus(message, ScanState.ERROR, showProgress = false)
        resultScanAgainButton.setText(R.string.vehicle_scan_retry_button)
        resultManualEntryButton.setText(R.string.scanner_enter_number)
        resultActionsContainer.isVisible = true
        manualFallbackButton.isVisible = false
        updateOperatorInteractionState()
        return true
    }

    private fun clearPersistentOperatorError() {
        persistentOperatorErrorMessage = null
    }

    private fun startScanner() {
        if (isVehicleScan() && !isAutomaticRecognitionEnabled()) {
            renderAutomaticRecognitionFallback()
            return
        }
        if (isVehicleScan() && renderDetectorRuntimeFallbackIfPresent()) return
        if (isVehicleScan() && renderPendingCameraFallbackIfPossible()) return
        if (isVehicleScan() && !scannerViewportLayoutKnown) return
        if (isVehicleScan() && !scannerViewportSupportsLiveScanning) {
            renderSmallViewportFallback()
            return
        }
        if (isVehicleScan() && !checkCameraPermission()) {
            renderCameraManualFallback(
                cameraManualFallbackReason ?: CameraManualFallbackReason.PERMISSION_DENIED
            )
            return
        }
        if (renderPersistentOperatorError()) return
        if (isVehicleScan() && scannerReadinessController.state != ScannerReadinessState.READY) {
            renderScannerReadiness(scannerReadinessController.state)
            return
        }
        if (isVehicleScan() && operatorOperationInProgress) {
            setStatus(getActiveProcessingMessage(selectedOperationType), ScanState.SCANNING, showProgress = true)
            updateOperatorInteractionState()
            return
        }
        if (isOperatorPlateMode()) {
            stopQrScanner()
            stopOperatorQrCameraScanner()
            setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
            startVehicleScanner()
        } else if (isOperatorQrMode()) {
            setStatus(getIdleScanMessage(), ScanState.SCANNING, showProgress = true)
            startOperatorQrCameraScannerWhenReady()
        } else {
            startQrScannerWhenReady()
        }
    }

    // region QR Scanner (for bookings)
    private fun startQrScannerWhenReady() {
        if (!::barcodeView.isInitialized) return
        barcodeView.visibility = View.VISIBLE
        barcodeView.post {
            if (isFinishing || isDestroyed) return@post
            if (isVehicleScan() && !isOperatorQrMode()) return@post
            startQrScanner()
        }
    }

    private fun startQrScanner() {
        if (!::barcodeView.isInitialized) return
        barcodeView.setDecoderFactory(DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE)))
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result?.let {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastScanTimestamp < 1500L) return
                    lastScanTimestamp = now
                    handleScanResult(it.text)
                }
            }

            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
        })
        qrScannerStarted = true
        barcodeView.resume()
    }

    private fun stopQrScanner() {
        if (!::barcodeView.isInitialized) return
        barcodeView.barcodeView.stopDecoding()
        barcodeView.pause()
        qrScannerStarted = false
        lastScanTimestamp = 0L
    }
    // endregion

    // region Operator QR scanner (CameraX)
    private fun startOperatorQrCameraScannerWhenReady() {
        if (!::vehiclePreview.isInitialized || !scannerViewportSupportsLiveScanning ||
            !isAutomaticRecognitionEnabled()
        ) return
        vehiclePreview.visibility = View.VISIBLE
        vehiclePreview.post {
            if (isFinishing || isDestroyed) return@post
            if (!isOperatorQrMode()) return@post
            startOperatorQrCameraScanner()
        }
    }

    private fun startOperatorQrCameraScanner() {
        if (renderPendingCameraFallbackIfPossible()) return
        if (!checkCameraPermission()) {
            renderCameraManualFallback(
                cameraManualFallbackReason ?: CameraManualFallbackReason.PERMISSION_DENIED
            )
            return
        }
        consumePendingScannerCameraRebind()
        if (!isOperatorQrMode() ||
            !isAutomaticRecognitionEnabled() ||
            !scannerViewportSupportsLiveScanning ||
            scannerReadinessController.state != ScannerReadinessState.READY ||
            detectorRuntimeFallbackMode != null ||
            operatorQrScannerRunning ||
            vehicleScanCompleted ||
            operatorOperationInProgress ||
            scannerSpotSelectionDialog?.isShowing == true ||
            manualEntrySheet?.isShowing == true
        ) {
            return
        }

        detectorRuntimeFailurePolicy.reset()
        operatorQrScannerRunning = true
        val sessionId = ++vehicleScannerSessionId
        statusProgress.isVisible = true

        bindScannerCamera(
            shouldContinue = {
                operatorQrScannerRunning &&
                    sessionId == vehicleScannerSessionId &&
                    !isFinishing &&
                    !isDestroyed
            },
        )
    }

    private fun processOperatorQrFrame(
        imageProxy: ImageProxy,
        scanSessionId: Long,
        frameToken: ScannerFrameToken,
    ) {
        if (!isOperatorQrMode() ||
            !operatorQrScannerRunning ||
            scanSessionId != vehicleScannerSessionId ||
            !scannerViewportSupportsLiveScanning ||
            scannerReadinessController.state != ScannerReadinessState.READY ||
            vehicleScanCompleted ||
            operatorOperationInProgress
        ) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }
        val recognitionGeneration = automaticRecognitionGate.beginAutomaticRecognition(
            source = ScannerAutomaticRecognitionSource.QR,
            mutationInFlight = hasActiveOperatorMutation(),
        )
        if (recognitionGeneration == ScannerAutomaticRecognitionGate.REJECTED_GENERATION) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }
        val frameContext = ScannerRecognitionFrameContext(
            source = ScannerAutomaticRecognitionSource.QR,
            operationType = selectedOperationType,
            scanSessionId = scanSessionId,
            recognitionGeneration = recognitionGeneration,
            performanceToken = frameToken,
        )

        val imageToPreviewTransform = cropToScanRegion(
            imageProxy = imageProxy,
            shrinkForPlate = false,
            frameContext = frameContext,
        )
        if (imageToPreviewTransform == null) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }
        val inputPreparationStartedAtMs = SystemClock.elapsedRealtime()
        val image = scannerInputImageFactory.create(imageProxy)
        if (image == null) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }
        scannerPerformance.inputPrepared(
            token = frameToken,
            width = image.width,
            height = image.height,
            preparationMs = SystemClock.elapsedRealtime() - inputPreparationStartedAtMs,
        )

        estimateFrameQuality(imageProxy)?.let { handleFrameQuality(it, frameContext) }
        if (!isRecognitionFrameRuntimeCurrent(frameContext)) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }

        // ML Kit decodes the same centre region shown to the operator. KEEP_ONLY_LATEST limits the
        // analyzer to one in-flight frame and prevents a queue from forming on slower devices.
        val analysisToken = scannerPerformance.analysisStarted(frameToken)
        synchronized(scannerCameraAdjustmentLock) {
            activeQrAutoZoomContext = frameContext
        }
        val analysisTask = operatorQrAnalyzer.process(image)
        markScannerAnalyzerFrameRouted(ScannerInputMode.QR)
        analysisTask
            .addOnSuccessListener(mlTaskExecutor) { barcodes ->
                recordDetectorTaskSuccess(frameContext)
                if (!isRecognitionFrameRuntimeCurrent(frameContext)) {
                    return@addOnSuccessListener
                }
                val scanRegion = scannerCameraController.scanRegionBounds()
                    ?: return@addOnSuccessListener
                val qrSelection = operatorQrAnalyzer.selectAcceptedValue(
                    barcodes = barcodes,
                    scanRegion = scanRegion,
                    mapBoundsToPreview = { bounds ->
                        scannerCameraController.mapImageRectToPreview(
                            bounds,
                            imageToPreviewTransform,
                        )
                    },
                )
                val qrCode = when (qrSelection) {
                    OperatorQrSelection.None -> return@addOnSuccessListener
                    OperatorQrSelection.Ambiguous -> {
                        runOnUiThread {
                            if (!isOperatorQrMode() || vehicleScanCompleted ||
                                operatorOperationInProgress
                            ) {
                                return@runOnUiThread
                            }
                            val decision = automaticRecognitionCommitDecision(
                                source = ScannerAutomaticRecognitionSource.QR,
                                recognitionGeneration = frameContext.recognitionGeneration,
                            )
                            if (decision != ScannerRecognitionDecision.ALLOW) {
                                if (decision == ScannerRecognitionDecision.AUTOMATIC_RECOGNITION_DISABLED) {
                                    renderAutomaticRecognitionFallback()
                                }
                                return@runOnUiThread
                            }
                            if (!isAutomaticRecognitionCommitContextValid(
                                    source = ScannerAutomaticRecognitionSource.QR,
                                    scanSessionId = frameContext.scanSessionId,
                                )
                            ) {
                                return@runOnUiThread
                            }
                            setStatus(
                                getString(R.string.scanner_one_qr_at_a_time_warning),
                                ScanState.WARNING,
                                showProgress = false,
                            )
                        }
                        return@addOnSuccessListener
                    }
                    is OperatorQrSelection.Accepted -> qrSelection.value
                }
                runOnUiThread {
                    if (!isOperatorQrMode() || vehicleScanCompleted || operatorOperationInProgress) {
                        return@runOnUiThread
                    }
                    val decision = automaticRecognitionCommitDecision(
                        source = ScannerAutomaticRecognitionSource.QR,
                        recognitionGeneration = frameContext.recognitionGeneration,
                    )
                    if (decision != ScannerRecognitionDecision.ALLOW) {
                        if (decision == ScannerRecognitionDecision.AUTOMATIC_RECOGNITION_DISABLED) {
                            renderAutomaticRecognitionFallback()
                        }
                        return@runOnUiThread
                    }
                    if (!isAutomaticRecognitionCommitContextValid(
                            source = ScannerAutomaticRecognitionSource.QR,
                            scanSessionId = frameContext.scanSessionId,
                        )
                    ) {
                        return@runOnUiThread
                    }
                    moveScannerLifecycle(ScannerLifecycleEvent.CANDIDATE_FOUND)
                    scannerPerformance.candidateDetected(frameToken)
                    scannerPerformance.recognitionConfirmed(frameToken)
                    // Keep the camera preview bound; setting the in-progress flags below halts
                    // further decoding without tearing the preview to black and rebinding.
                    handleScanResult(qrCode)
                }
            }
            .addOnFailureListener(mlTaskExecutor) { failure ->
                scannerPerformance.detectorProcessingFailed(
                    detector = ScannerDetectorMetric.QR,
                    failure = failure,
                    token = analysisToken,
                )
                handleDetectorTaskFailure(
                    frameContext = frameContext,
                    transientMessageRes = R.string.scanner_qr_scan_hint,
                )
            }
            .addOnCompleteListener(mlTaskExecutor) {
                synchronized(scannerCameraAdjustmentLock) {
                    if (activeQrAutoZoomContext === frameContext) {
                        activeQrAutoZoomContext = null
                    }
                }
                scannerPerformance.analysisCompleted(analysisToken)
                imageProxy.close()
            }
    }

    private fun estimateFrameQuality(imageProxy: ImageProxy): ScannerFrameQuality? {
        val lumaPlane = imageProxy.planes.firstOrNull() ?: return null
        val crop = imageProxy.cropRect
        return ScannerFrameQualityEstimator.estimate(
            buffer = lumaPlane.buffer,
            rowStride = lumaPlane.rowStride,
            pixelStride = lumaPlane.pixelStride,
            left = crop.left,
            top = crop.top,
            right = crop.right,
            bottom = crop.bottom
        )
    }

    private fun handleFrameQuality(
        quality: ScannerFrameQuality,
        frameContext: ScannerRecognitionFrameContext,
    ) {
        val lightingUpdate = synchronized(scannerCameraAdjustmentLock) {
            if (!isRecognitionFrameRuntimeCurrent(frameContext)) {
                return
            }
            if (quality.meanLuma <= 46) {
                scannerPerformance.lowLightFrame(frameContext.performanceToken)
            }
            if (frameContext.source == ScannerAutomaticRecognitionSource.PLATE &&
                scannerFeatureControls.reflectivePlateExposureCompensationEnabled
            ) {
                when (reflectivePlateExposureAdvisor.observe(quality.meanLuma)) {
                    ReflectivePlateExposureAdvisor.Update.REDUCE_EXPOSURE ->
                        applyExposureCompensationLocked(-1)
                    ReflectivePlateExposureAdvisor.Update.RESTORE_EXPOSURE ->
                        applyExposureCompensationLocked(0)
                    ReflectivePlateExposureAdvisor.Update.NONE -> Unit
                }
            }
            lightingMonitor.observe(quality.meanLuma).also { update ->
                when (update) {
                    ScannerLightingMonitor.Update.BECAME_DARK -> lowLightActive = true
                    ScannerLightingMonitor.Update.RECOVERED -> lowLightActive = false
                    ScannerLightingMonitor.Update.NONE -> Unit
                }
            }
        }
        when (lightingUpdate) {
            ScannerLightingMonitor.Update.BECAME_DARK -> {
                runOnUiThread {
                    if (isRecognitionFrameRuntimeCurrent(frameContext) && !isTorchOn
                    ) {
                        setStatus(
                            getString(R.string.scanner_low_light_hint),
                            ScanState.WARNING,
                            showProgress = false
                        )
                        pulseTorchGuidance()
                    }
                }
            }
            ScannerLightingMonitor.Update.RECOVERED -> {
                runOnUiThread {
                    if (isRecognitionFrameRuntimeCurrent(frameContext)) {
                        setStatus(getIdleScanMessage(), ScanState.SCANNING, showProgress = true)
                    }
                }
            }
            ScannerLightingMonitor.Update.NONE -> Unit
        }
    }

    private fun pulseTorchGuidance() {
        if (!flashToggle.isVisible || isTorchOn) return
        flashToggle.animate().cancel()
        flashToggle.animate()
            .scaleX(1.12f)
            .scaleY(1.12f)
            .setDuration(140L)
            .withEndAction {
                flashToggle.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .start()
            }
            .start()
    }

    private fun requestQualityFocusRefresh(
        quality: ScannerFrameQuality,
        frameContext: ScannerRecognitionFrameContext,
    ) {
        if (quality.meanLuma >= 20 && quality.detailScore >= 2.2) return
        runOnUiThread {
            if (!isRecognitionFrameRuntimeCurrent(frameContext)) return@runOnUiThread
            updateMeteringRegion(force = false)
        }
    }

    private fun resetScannerQualityState() {
        synchronized(scannerCameraAdjustmentLock) {
            activeQrAutoZoomContext = null
            lightingMonitor.reset()
            reflectivePlateExposureAdvisor.reset()
            plateFrameUsabilityGate.reset()
            plateFrameSequence = 0L
            lowLightActive = false
        }
    }

    private fun stopOperatorQrCameraScanner(releaseCamera: Boolean = true) {
        if (!operatorQrScannerRunning) return
        operatorQrScannerRunning = false
        vehicleScannerSessionId++
        synchronized(scannerCameraAdjustmentLock) {
            activeQrAutoZoomContext = null
        }
        if (releaseCamera) {
            releaseScannerCameraSession()
        }
    }

    /** Plate and QR share one analyzer; changing mode only changes this dispatch branch. */
    private fun dispatchScannerFrame(imageProxy: ImageProxy) {
        if (!isAutomaticRecognitionEnabled()) {
            imageProxy.close()
            return
        }
        when {
            vehicleScannerRunning && isOperatorPlateMode() -> {
                val scanSessionId = vehicleScannerSessionId
                val frameToken = scannerPerformance.frameReceived("plate")
                processVehicleFrame(imageProxy, scanSessionId, frameToken)
            }
            operatorQrScannerRunning && isOperatorQrMode() -> {
                val scanSessionId = vehicleScannerSessionId
                val frameToken = scannerPerformance.frameReceived("qr")
                processOperatorQrFrame(imageProxy, scanSessionId, frameToken)
            }
            else -> imageProxy.close()
        }
    }

    private fun bindScannerCamera(
        shouldContinue: () -> Boolean,
        previewOnly: Boolean = false,
    ) {
        val bindingToken = scannerPerformance.cameraBindingStarted()
        scannerCameraController.bind(
            analysisWidth = scannerFeatureControls.analysisWidth,
            analysisHeight = scannerFeatureControls.analysisHeight,
            shouldContinue = shouldContinue,
            analyzeFrame = ::dispatchScannerFrame,
            onBound = { reused ->
                if (!shouldContinue()) return@bind
                scannerPerformance.cameraReady(reused = reused, token = bindingToken)
                applyScannerZoomDefault()
                applyExposureCompensation(0)
                updateFlashToggleVisibility(!previewOnly)
                if (!previewOnly) scanningFrameContainer.post { refreshScanRegion() }
            },
            onError = { failure ->
                scannerPerformance.cameraBindFailed(failure, bindingToken)
                if (!shouldContinue()) return@bind
                if (previewOnly) {
                    cameraManualFallbackReason = CameraManualFallbackReason.UNAVAILABLE
                    runOnUiThread { updateFlashToggleVisibility(false) }
                    return@bind
                }
                if (isOperatorQrMode()) {
                    operatorQrScannerRunning = false
                } else {
                    vehicleScannerRunning = false
                }
                runOnUiThread {
                    Toast.makeText(
                        this,
                        R.string.vehicle_scan_error_camera,
                        Toast.LENGTH_SHORT,
                    ).show()
                    renderCameraManualFallback(CameraManualFallbackReason.UNAVAILABLE)
                }
            },
            onRuntimeError = { failure ->
                scannerPerformance.cameraRuntimeFailed(
                    reason = failure.reason,
                    failure = failure,
                )
                if (!shouldContinue()) return@bind
                markScannerCameraUnavailable()
                if (previewOnly || hasActiveOperatorMutation()) {
                    return@bind
                }
                runOnUiThread {
                    Toast.makeText(
                        this,
                        R.string.vehicle_scan_error_camera,
                        Toast.LENGTH_SHORT,
                    ).show()
                    renderCameraManualFallback(CameraManualFallbackReason.UNAVAILABLE)
                }
            },
        )
    }

    /** Restores a live processing backdrop after lifecycle CameraX teardown without routing ML. */
    private fun ensureRetainedOperationPreviewBound(requestId: Long) {
        if (scannerCameraController.isBound || !isAutomaticRecognitionEnabled() ||
            !ScannerViewportStatePolicy.canStartLiveScanning(
                scannerViewportLayoutKnown,
                scannerViewportSupportsLiveScanning,
            ) || !checkCameraPermission()
        ) {
            return
        }
        vehiclePreview.visibility = View.VISIBLE
        vehiclePreview.post {
            if (isFinishing || isDestroyed ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                return@post
            }
            bindScannerCamera(
                shouldContinue = {
                    !isFinishing && !isDestroyed &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        pendingOperatorRequestId == requestId &&
                        operatorViewModel.operationPresentation(requestId)?.isInFlight == true
                },
                previewOnly = true,
            )
        }
    }

    private fun consumePendingScannerCameraRebind() {
        if (!scannerCameraRebindPending) return
        stopVehicleScanner(releaseCamera = false)
        stopOperatorQrCameraScanner(releaseCamera = false)
        if (scannerCameraController.isBound) releaseScannerCameraSession()
        scannerCameraRebindPending = false
    }

    private fun releaseScannerCameraSession() {
        if (isTorchOn) scannerCameraController.setTorch(false)
        isTorchOn = false
        scannerCameraController.release()
        if (::scannerFrameReadinessGate.isInitialized) {
            scannerFrameReadinessGate.updatePreviewStreaming(false)
            publishScannerFrameReadiness()
        }
        updateFlashToggleVisibility(false)
    }

    private fun refreshScanRegion(remainingAttempts: Int = 3) {
        if (!isAutomaticRecognitionEnabled()) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            vehiclePreview.post { refreshScanRegion(remainingAttempts) }
            return
        }

        val transformReady = scannerCameraController.updateScanRegion(getOverlayRectOnPreview())
        if (!transformReady && remainingAttempts > 0) {
            vehiclePreview.postDelayed({ refreshScanRegion(remainingAttempts - 1) }, 50L)
            return
        }
        if (transformReady) updateMeteringRegion()
    }

    private fun requestScanRegionRefresh() {
        if (!scanRegionRefreshPending.compareAndSet(false, true)) return
        vehiclePreview.post {
            scanRegionRefreshPending.set(false)
            refreshScanRegion()
        }
    }
    // endregion

    // region Vehicle number scanner (ML Kit Text Recognition)
    private fun startVehicleScanner() {
        if (renderPendingCameraFallbackIfPossible()) return
        if (!checkCameraPermission()) {
            renderCameraManualFallback(
                cameraManualFallbackReason ?: CameraManualFallbackReason.PERMISSION_DENIED
            )
            return
        }
        consumePendingScannerCameraRebind()
        if (!isOperatorPlateMode() ||
            !isAutomaticRecognitionEnabled() ||
            !scannerViewportSupportsLiveScanning ||
            scannerReadinessController.state != ScannerReadinessState.READY ||
            detectorRuntimeFallbackMode != null ||
            vehicleScannerRunning ||
            vehicleScanCompleted ||
            operatorOperationInProgress ||
            voiceRecognitionInProgress ||
            scannerSpotSelectionDialog?.isShowing == true ||
            manualEntrySheet?.isShowing == true ||
            manualEntryInput.hasFocus()
        ) {
            return
        }
        detectorRuntimeFailurePolicy.reset()
        vehicleScannerRunning = true
        val sessionId = ++vehicleScannerSessionId
        statusProgress.isVisible = true
        startScanLineAnimation()
        startScanTimeout()

        bindScannerCamera(
            shouldContinue = {
                vehicleScannerRunning &&
                    sessionId == vehicleScannerSessionId &&
                    !isFinishing &&
                    !isDestroyed
            },
        )
    }

    private fun processVehicleFrame(
        imageProxy: ImageProxy,
        scanSessionId: Long,
        frameToken: ScannerFrameToken,
    ) {
        if (!isOperatorPlateMode() ||
            !vehicleScannerRunning ||
            scanSessionId != vehicleScannerSessionId ||
            !scannerViewportSupportsLiveScanning ||
            scannerReadinessController.state != ScannerReadinessState.READY ||
            vehicleScanCompleted ||
            operatorOperationInProgress
        ) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }
        val recognitionGeneration = automaticRecognitionGate.beginAutomaticRecognition(
            source = ScannerAutomaticRecognitionSource.PLATE,
            mutationInFlight = hasActiveOperatorMutation(),
        )
        if (recognitionGeneration == ScannerAutomaticRecognitionGate.REJECTED_GENERATION) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }
        val frameContext = ScannerRecognitionFrameContext(
            source = ScannerAutomaticRecognitionSource.PLATE,
            operationType = selectedOperationType,
            scanSessionId = scanSessionId,
            recognitionGeneration = recognitionGeneration,
            performanceToken = frameToken,
        )

        val useTightPlateRoi = synchronized(scannerCameraAdjustmentLock) {
            if (!isRecognitionFrameRuntimeCurrent(frameContext)) {
                null
            } else {
                plateFrameSequence++
                plateFrameSequence % plateWideRoiInterval != 0L
            }
        }
        if (useTightPlateRoi == null) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }
        val imageToPreviewTransform = cropToScanRegion(
            imageProxy,
            shrinkForPlate = useTightPlateRoi,
            frameContext = frameContext,
        )
        if (imageToPreviewTransform == null) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }
        val frameQuality = estimateFrameQuality(imageProxy)
        if (frameQuality != null) {
            handleFrameQuality(frameQuality, frameContext)
            val shouldAnalyze = synchronized(scannerCameraAdjustmentLock) {
                isRecognitionFrameRuntimeCurrent(frameContext) &&
                    plateFrameUsabilityGate.shouldAnalyze(frameQuality)
            }
            if (!shouldAnalyze) {
                scannerPerformance.frameSkipped(frameToken)
                requestQualityFocusRefresh(frameQuality, frameContext)
                imageProxy.close()
                return
            }
        }
        val inputPreparationStartedAtMs = SystemClock.elapsedRealtime()
        val image = scannerInputImageFactory.create(imageProxy)
        if (image == null) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }
        scannerPerformance.inputPrepared(
            token = frameToken,
            width = image.width,
            height = image.height,
            preparationMs = SystemClock.elapsedRealtime() - inputPreparationStartedAtMs,
        )
        if (!isRecognitionFrameRuntimeCurrent(frameContext)) {
            scannerPerformance.frameSkipped(frameToken)
            imageProxy.close()
            return
        }

        val analysisToken = scannerPerformance.analysisStarted(frameToken)
        val analysisTask = vehiclePlateAnalyzer.process(image)
        markScannerAnalyzerFrameRouted(ScannerInputMode.PLATE)
        analysisTask
            .addOnSuccessListener(mlTaskExecutor) { text ->
                recordDetectorTaskSuccess(frameContext)
                if (isRecognitionFrameRuntimeCurrent(frameContext)) {
                    val candidate = vehiclePlateAnalyzer.selectBestCandidate(
                        text = text,
                        scanRegion = scannerCameraController.scanRegionBounds(),
                        mapBoundsToPreview = { bounds ->
                            scannerCameraController.mapImageRectToPreview(
                                bounds,
                                imageToPreviewTransform,
                            )
                        },
                    )
                    handleOcrResult(candidate, frameContext)
                }
            }
            .addOnFailureListener(mlTaskExecutor) { failure ->
                scannerPerformance.detectorProcessingFailed(
                    detector = ScannerDetectorMetric.PLATE,
                    failure = failure,
                    token = analysisToken,
                )
                clearCandidateBufferIfCurrent(frameContext)
                handleDetectorTaskFailure(
                    frameContext = frameContext,
                    transientMessageRes = R.string.vehicle_scan_error_generic,
                )
            }
            .addOnCompleteListener(mlTaskExecutor) {
                scannerPerformance.analysisCompleted(analysisToken)
                imageProxy.close()
            }
    }

    private fun handleOcrResult(
        candidate: NormalizedPlateCandidate?,
        frameContext: ScannerRecognitionFrameContext,
    ) {
        if (!isRecognitionFrameRuntimeCurrent(frameContext)) {
            if (!isAutomaticRecognitionEnabled()) {
                runOnUiThread(::renderAutomaticRecognitionFallback)
            }
            return
        }

        if (candidate != null) {
            moveScannerLifecycle(ScannerLifecycleEvent.CANDIDATE_FOUND)
            scannerPerformance.candidateDetected(frameContext.performanceToken)
            bufferCandidate(candidate, frameContext)
        } else {
            clearCandidateBufferIfCurrent(frameContext)
            runOnUiThread {
                if (isRecognitionFrameRuntimeCurrent(frameContext)) {
                    setStatus(
                        getString(R.string.vehicle_scan_not_clear_hint),
                        ScanState.WARNING
                    )
                }
            }
        }
    }

    private fun bufferCandidate(
        candidate: NormalizedPlateCandidate,
        frameContext: ScannerRecognitionFrameContext,
    ) {
        val confirmedCandidate = synchronized(scannerCameraAdjustmentLock) {
            if (!isRecognitionFrameRuntimeCurrent(frameContext)) return
            vehiclePlateAnalyzer.observeConsensus(
                candidate = candidate,
                observedAtMs = SystemClock.elapsedRealtime(),
                cleanRequiredMatches = scannerFeatureControls.cleanPlateConsensusMatches,
                correctedRequiredMatches = scannerFeatureControls.correctedPlateConsensusMatches,
                allowedGapMs = scannerFeatureControls.plateConsensusMaxGapMs,
            )
        }

        if (confirmedCandidate != null && VehicleNumberValidator.isValid(confirmedCandidate.value)) {
            val requiresConfirmation = vehiclePlateInterpreter.requiresOperatorConfirmation(
                candidate = confirmedCandidate,
                forceManualConfirmation = scannerFeatureControls.forceManualPlateConfirmation,
            )
            if (requiresConfirmation) {
                showUncertainPlateConfirmation(
                    confirmedCandidate,
                    frameContext,
                )
            } else {
                finalizePlate(confirmedCandidate.value, frameContext)
            }
        } else {
            runOnUiThread {
                if (isRecognitionFrameRuntimeCurrent(frameContext)) {
                    setStatus(
                        getString(R.string.vehicle_scan_best_guess, candidate.value),
                        ScanState.WARNING
                    )
                }
            }
        }
    }

    private fun showUncertainPlateConfirmation(
        candidate: NormalizedPlateCandidate,
        frameContext: ScannerRecognitionFrameContext,
    ) {
        runOnUiThread {
            if (uncertainPlateDialog?.isShowing == true ||
                vehicleScanCompleted ||
                operatorOperationInProgress ||
                !isOperatorPlateMode() ||
                !canCommitAutomaticRecognition(
                    ScannerAutomaticRecognitionSource.PLATE,
                    frameContext.recognitionGeneration,
                ) ||
                !isAutomaticRecognitionCommitContextValid(
                    source = ScannerAutomaticRecognitionSource.PLATE,
                    scanSessionId = frameContext.scanSessionId,
                )
            ) {
                return@runOnUiThread
            }

            vehicleScanCompleted = true
            currentVehicleNumber = candidate.value
            stopVehicleScanner(releaseCamera = false)
            stopScanLineAnimation()
            updateOperatorInteractionState()
            setStatus(
                getString(R.string.scanner_plate_confirmation_needed, candidate.value),
                ScanState.WARNING,
                showProgress = false,
            )
            scannerPerformance.uncertainPlateConfirmation(
                outcome = "presented",
                correctedCharacters = candidate.correctedCharacters,
            )

            var decisionHandled = false
            val messageRes = if (candidate.correctedCharacters > 0) {
                R.string.scanner_plate_confirm_corrected_message
            } else {
                R.string.scanner_plate_confirm_message
            }
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.scanner_plate_confirm_title)
                .setMessage(getString(messageRes, candidate.value))
                .setPositiveButton(R.string.scanner_confirm_plate) { _, _ ->
                    decisionHandled = true
                    scannerPerformance.uncertainPlateConfirmation(
                        outcome = "confirmed",
                        correctedCharacters = candidate.correctedCharacters,
                    )
                    vehicleScanCompleted = false
                    finalizePlate(
                        plate = candidate.value,
                        frameContext = frameContext,
                        allowStoppedPlateConfirmation = true,
                    )
                }
                .setNegativeButton(R.string.vehicle_scan_retry_button) { _, _ ->
                    decisionHandled = true
                    scannerPerformance.uncertainPlateConfirmation(
                        outcome = "scan_again",
                        correctedCharacters = candidate.correctedCharacters,
                    )
                    vehicleScanCompleted = false
                    currentVehicleNumber = null
                    restartVehicleScan()
                }
                .setNeutralButton(R.string.scanner_enter_number) { _, _ ->
                    decisionHandled = true
                    scannerPerformance.uncertainPlateConfirmation(
                        outcome = "manual_entry",
                        correctedCharacters = candidate.correctedCharacters,
                    )
                    vehicleScanCompleted = false
                    handler.post { showManualEntrySheet() }
                }
                .create()
            uncertainPlateDialog = dialog
            dialog.setOnCancelListener {
                if (decisionHandled) return@setOnCancelListener
                decisionHandled = true
                scannerPerformance.uncertainPlateConfirmation(
                    outcome = "dismissed",
                    correctedCharacters = candidate.correctedCharacters,
                )
                vehicleScanCompleted = false
                currentVehicleNumber = null
                restartVehicleScan()
            }
            dialog.setOnDismissListener {
                if (uncertainPlateDialog === dialog) uncertainPlateDialog = null
            }
            dialog.show()
        }
    }

    private fun finalizePlate(
        plate: String,
        frameContext: ScannerRecognitionFrameContext,
        allowStoppedPlateConfirmation: Boolean = false,
    ) {
        runOnUiThread {
            val decision = automaticRecognitionCommitDecision(
                ScannerAutomaticRecognitionSource.PLATE,
                frameContext.recognitionGeneration,
            )
            if (decision != ScannerRecognitionDecision.ALLOW) {
                clearCandidateBufferIfCurrent(frameContext)
                if (decision == ScannerRecognitionDecision.AUTOMATIC_RECOGNITION_DISABLED) {
                    renderAutomaticRecognitionFallback()
                }
                return@runOnUiThread
            }
            if (!isAutomaticRecognitionCommitContextValid(
                    source = ScannerAutomaticRecognitionSource.PLATE,
                    scanSessionId = frameContext.scanSessionId,
                    allowStoppedPlateConfirmation = allowStoppedPlateConfirmation,
                )
            ) {
                clearCandidateBufferIfCurrent(frameContext)
                return@runOnUiThread
            }
            val normalizedPlate = normalizeVehicleNumber(plate) ?: return@runOnUiThread
            if (vehicleScanCompleted) return@runOnUiThread
            vehicleScanCompleted = true
            scannerPerformance.recognitionConfirmed(frameContext.performanceToken)
            currentVehicleNumber = normalizedPlate
            operatorOperationInProgress = true
            clearCandidateBuffer()
            cancelScanTimeout()
            updateOperatorInteractionState()
            animateCornerBrackets(scaleUp = true)
            beginOperatorVehicleProcessing(normalizedPlate)
        }
    }

    private fun clearCandidateBuffer() {
        synchronized(scannerCameraAdjustmentLock) {
            vehiclePlateAnalyzer.clearConsensus()
        }
    }

    private fun clearCandidateBufferIfCurrent(
        frameContext: ScannerRecognitionFrameContext,
    ) {
        synchronized(scannerCameraAdjustmentLock) {
            if (isRecognitionFrameRuntimeCurrent(frameContext)) {
                vehiclePlateAnalyzer.clearConsensus()
            }
        }
    }

    /** Applies the visible centre frame to the underlying camera image without bitmap conversion. */
    private fun cropToScanRegion(
        imageProxy: ImageProxy,
        shrinkForPlate: Boolean,
        frameContext: ScannerRecognitionFrameContext,
    ): ScannerFrameTransform? {
        val transform = scannerCameraController.cropToScanRegion(
            imageProxy = imageProxy,
            horizontalInsetRatio = if (shrinkForPlate) ocrCropHorizontalInsetRatio else 0f,
            verticalInsetRatio = if (shrinkForPlate) ocrCropVerticalInsetRatio else 0f,
        )
        if (transform == null && isRecognitionFrameRuntimeCurrent(frameContext)) {
            requestScanRegionRefresh()
        }
        return transform
    }

    @SuppressLint("SuspiciousIndentation")
    private fun getOverlayRectOnPreview(): RectF? {
        if (!this::vehiclePreview.isInitialized || !this::scanningFrameContainer.isInitialized) return null
        if (!isAutomaticRecognitionEnabled()) return null
        if (!scannerViewportSupportsLiveScanning) return null
        if (vehiclePreview.width == 0 || vehiclePreview.height == 0) return null
        if (scanningFrameContainer.width == 0 || scanningFrameContainer.height == 0) return null

        val previewLocation = IntArray(2)
        val frameLocation = IntArray(2)
        vehiclePreview.getLocationOnScreen(previewLocation)
        scanningFrameContainer.getLocationOnScreen(frameLocation)

        val left = (frameLocation[0] - previewLocation[0]).toFloat()
        val top = (frameLocation[1] - previewLocation[1]).toFloat()
        val right = left + scanningFrameContainer.width
        val bottom = top + scanningFrameContainer.height

        return RectF(
            left.coerceIn(0f, vehiclePreview.width.toFloat()),
            top.coerceIn(0f, vehiclePreview.height.toFloat()),
            right.coerceIn(0f, vehiclePreview.width.toFloat()),
            bottom.coerceIn(0f, vehiclePreview.height.toFloat())
        )
    }

    // endregion

    private fun handleScanResult(value: String) {
        if (isOperatorQrMode()) {
            barcodeView.pause()
            processOperatorQrInput(value)
            return
        }

        when {
            isVehicleScan() -> {
                stopVehicleScanner()
                cancelScanTimeout()
                stopScanLineAnimation()
            }
            else -> barcodeView.pause()
        }

        val resultIntent = createScannerResultIntent(value)
        setResult(RESULT_QR_SCANNED, resultIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        jankStats?.isTrackingEnabled = true
        operatorViewModel.setTerminalDeliveryActive(true)
        if (isVehicleScan()) {
            val hasLocalTerminalResult = hasUnacknowledgedTerminalResult()
            operatorViewModel.operationPresentation()
                ?.takeUnless { hasLocalTerminalResult }
                ?.let { presentation ->
                    pendingOperatorRequestId = presentation.requestId
                    operatorOperationInProgress = true
                    vehicleScanCompleted = true
                }
            val cameraAvailable = checkCameraPermission()
            val previousFallbackReason = cameraManualFallbackReason
            cameraManualFallbackReason = ScannerCameraFallbackPolicy.afterPermissionCheck(
                currentReason = previousFallbackReason,
                permissionGranted = cameraAvailable,
            )
            if (previousFallbackReason != null && cameraManualFallbackReason == null &&
                !hasLocalTerminalResult && operatorViewModel.operationPresentation() == null &&
                persistentOperatorErrorMessage == null && !vehicleScanCompleted
            ) {
                configureScannerUi()
            }
            updateScannerLayoutForScreen()
            animatePreviewGlassEffect(enabled = false, immediate = true)
            updateOperatorInteractionState()
            if (hasLocalTerminalResult) {
                // The existing panel is the authoritative result. A completed retained operation
                // may briefly be claimable again after onPause; do not cover it with processing UI.
                operatorOperationInProgress = false
                vehicleScanCompleted = true
                updateOperatorInteractionState()
            } else if (operatorViewModel.operationPresentation() != null) {
                restoreRetainedOperatorPresentation()
            } else if (persistentOperatorErrorMessage != null) {
                renderPersistentOperatorError()
            } else if (vehicleScanCompleted) {
                // Keep an already-rendered terminal panel intact across a short background trip.
                updateOperatorInteractionState()
            } else if (!isAutomaticRecognitionEnabled()) {
                renderAutomaticRecognitionFallback()
            } else if (renderPendingCameraFallbackIfPossible()) {
                return
            } else if (!cameraAvailable) {
                renderCameraManualFallback(
                    cameraManualFallbackReason ?: CameraManualFallbackReason.PERMISSION_DENIED
                )
            } else if (scannerReadinessController.state != ScannerReadinessState.READY) {
                prepareScannerOrStart()
            } else if (isOperatorQrMode()) {
                resumeOperatorQrScanning()
            } else {
                resumeLiveVehicleScanning()
            }
        } else if (::barcodeView.isInitialized) {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        jankStats?.isTrackingEnabled = false
        operatorViewModel.setTerminalDeliveryActive(false)
        cancelOperatorOperationTimeout()
        super.onPause()
        if (isVehicleScan()) {
            releaseSpeechRecognizer(destroyRecognizer = false)
            stopVehicleScanner()
            stopOperatorQrCameraScanner()
            if (::scannerCameraController.isInitialized && scannerCameraController.isBound) {
                releaseScannerCameraSession()
            }
            if (::barcodeView.isInitialized) {
                barcodeView.pause()
            }
            stopScanLineAnimation()
            animatePreviewGlassEffect(enabled = false, immediate = true)
        } else if (::barcodeView.isInitialized) {
            barcodeView.pause()
        }
    }

    private fun stopVehicleScanner(releaseCamera: Boolean = true) {
        if (!vehicleScannerRunning) return
        vehicleScannerRunning = false
        vehicleScannerSessionId++
        cancelScanTimeout()
        clearCandidateBuffer()
        if (releaseCamera) {
            releaseScannerCameraSession()
        }
    }

    private fun updateOperatorInteractionState() {
        if (!isVehicleScan() || !::voiceEntryButton.isInitialized) return

        val canInteract = !operatorOperationInProgress &&
            !vehicleScanCompleted &&
            scannerSpotSelectionDialog?.isShowing != true
        val manualActive = ::manualEntryInput.isInitialized && manualEntryInput.hasFocus()
        val voiceActive = voiceRecognitionInProgress || voiceListeningInProgress
        val canUseManual = canInteract && !voiceActive
        val canUseOperationToggle = canInteract && !voiceActive && !manualActive
        val canUseScannerInputToggle = canUseOperationToggle && isAutomaticRecognitionEnabled()
        val canUseSpotPicker = canInteract && !voiceActive && !manualActive

        manualEntryInput.isEnabled = canUseManual
        manualEntryInputLayout.isEnabled = canUseManual
        manualEntrySubmitButton.isEnabled = canUseManual
        manualFallbackButton.isEnabled = canUseManual
        resultManualEntryButton.isEnabled = !operatorOperationInProgress
        resultScanAgainButton.isEnabled = !operatorOperationInProgress
        checkInSegment.isEnabled = canUseOperationToggle
        checkOutSegment.isEnabled = canUseOperationToggle
        plateInputSegment.isEnabled = canUseScannerInputToggle
        qrInputSegment.isEnabled = canUseScannerInputToggle
        spotSelectorPill.isEnabled = canUseSpotPicker

        manualEntryCard.alpha = if (canUseManual || manualActive) 1f else 0.72f
        operationToggleContainer.alpha = if (canUseOperationToggle) 1f else 0.72f
        scannerInputToggleContainer.alpha = if (canUseScannerInputToggle) 1f else 0.72f
        spotSelectorPill.alpha = if (canUseSpotPicker) 1f else 0.72f
        updateVoiceButtonUi()
    }

    private fun resumeLiveVehicleScanning() {
        if (!isAutomaticRecognitionEnabled()) {
            renderAutomaticRecognitionFallback()
            return
        }
        if (!isOperatorPlateMode()) return
        if (detectorRuntimeFallbackMode != null) return
        if (renderPendingCameraFallbackIfPossible()) return
        if (!scannerViewportLayoutKnown) return
        if (!scannerViewportSupportsLiveScanning) {
            renderSmallViewportFallback()
            return
        }
        if (
            vehicleScanCompleted ||
            operatorOperationInProgress ||
            voiceRecognitionInProgress ||
            scannerSpotSelectionDialog?.isShowing == true ||
            manualEntrySheet?.isShowing == true ||
            manualEntryInput.hasFocus() ||
            !checkCameraPermission()
        ) {
            return
        }
        if (scannerReadinessController.state != ScannerReadinessState.READY) {
            scannerPerformance.nextScanReady()
            renderScannerReadiness(scannerReadinessController.state)
            return
        }

        currentVehicleNumber = null
        clearCandidateBuffer()
        resetScannerQualityState()
        applyExposureCompensation(0)
        scannerPerformance.nextScanReady()
        scannerPerformance.beginAttempt(scannerMetricMode(), scannerMetricOperation())
        moveScannerLifecycle(ScannerLifecycleEvent.RESET)
        stopOperatorQrCameraScanner(releaseCamera = false)
        animatePreviewGlassEffect(enabled = false)
        setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
        startVehicleScanner()
        updateOperatorInteractionState()
    }

    private fun resumeOperatorQrScanning() {
        if (!isAutomaticRecognitionEnabled()) {
            renderAutomaticRecognitionFallback()
            return
        }
        if (!isOperatorQrMode()) return
        if (detectorRuntimeFallbackMode != null) return
        if (renderPendingCameraFallbackIfPossible()) return
        if (!scannerViewportLayoutKnown) return
        if (!scannerViewportSupportsLiveScanning) {
            renderSmallViewportFallback()
            return
        }
        if (
            operatorOperationInProgress ||
            scannerSpotSelectionDialog?.isShowing == true ||
            manualEntrySheet?.isShowing == true ||
            !checkCameraPermission()
        ) {
            return
        }
        if (scannerReadinessController.state != ScannerReadinessState.READY) {
            vehicleScanCompleted = false
            scannerPerformance.nextScanReady()
            renderScannerReadiness(scannerReadinessController.state)
            return
        }

        currentVehicleNumber = null
        currentQrCode = null
        vehicleScanCompleted = false
        clearCandidateBuffer()
        resetScannerQualityState()
        resetCameraZoom()
        applyExposureCompensation(0)
        scannerPerformance.nextScanReady()
        scannerPerformance.beginAttempt(scannerMetricMode(), scannerMetricOperation())
        moveScannerLifecycle(ScannerLifecycleEvent.RESET)
        animatePreviewGlassEffect(enabled = false)
        setStatus(getIdleScanMessage(), ScanState.SCANNING, showProgress = true)
        consumePendingScannerCameraRebind()
        // If the preview is still bound (the common scan -> result -> resume cycle) just nudge
        // focus and let the analyzer pick back up; otherwise bind the camera fresh.
        if (operatorQrScannerRunning && scannerCameraController.isBound) {
            scanningFrameContainer.post { updateMeteringRegion() }
        } else {
            startOperatorQrCameraScannerWhenReady()
        }
        updateOperatorInteractionState()
    }

    private fun resumeScannerAfterBlockingInteraction() {
        if (!isVehicleScan() || isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            operatorOperationInProgress || vehicleScanCompleted
        ) {
            return
        }
        when {
            !isAutomaticRecognitionEnabled() -> renderAutomaticRecognitionFallback()
            cameraManualFallbackReason != null -> renderPendingCameraFallbackIfPossible()
            !scannerViewportLayoutKnown -> rootView.post { updateScannerLayoutForScreen() }
            !scannerViewportSupportsLiveScanning -> renderSmallViewportFallback()
            !checkCameraPermission() -> renderCameraManualFallback(
                cameraManualFallbackReason ?: CameraManualFallbackReason.PERMISSION_DENIED
            )
            scannerReadinessController.state != ScannerReadinessState.READY ->
                prepareScannerOrStart()
            isOperatorQrMode() -> resumeOperatorQrScanning()
            else -> resumeLiveVehicleScanning()
        }
    }

    private fun processOperatorQrInput(rawQrCode: String) {
        val qrCode = rawQrCode.trim()
        if (qrCode.isBlank()) {
            currentQrCode = null
            operatorOperationInProgress = false
            vehicleScanCompleted = false
            updateOperatorInteractionState()
            setStatus(getString(R.string.scanner_qr_required), ScanState.ERROR, showProgress = false)
            scannerPerformance.finishAttempt("validation_error")
            provideFeedback(FeedbackType.ERROR)
            scheduleOperatorScanResume(delayMs = scannerFeatureControls.errorResultHoldMs)
            return
        }

        cancelVehicleScanResume()
        cancelOperatorOperationTimeout()
        currentQrCode = qrCode
        currentVehicleNumber = null
        vehicleScanCompleted = true
        operatorOperationInProgress = true
        updateOperatorInteractionState()

        val operationType = determineOperationType()
        if (operationType == null) {
            operatorOperationInProgress = false
            vehicleScanCompleted = false
            currentQrCode = null
            updateOperatorInteractionState()
            setStatus(getString(R.string.vehicle_scan_error_generic), ScanState.ERROR, showProgress = false)
            showScannerNotification(
                title = getString(R.string.scanner_error),
                message = getString(R.string.unsupported_operator_scan_mode),
                isError = true
            )
            scannerPerformance.finishAttempt("configuration_error")
            scheduleOperatorScanResume(delayMs = scannerFeatureControls.errorResultHoldMs)
            return
        }

        val network = ensureOperatorNetworkReady() ?: return

        val requestId = SystemClock.elapsedRealtimeNanos()
        moveScannerLifecycle(ScannerLifecycleEvent.SUBMIT)
        pendingOperatorRequestId = requestId
        setStatus(
            getNetworkAwareProcessingMessage(getQrProcessingMessage(operationType), network),
            ScanState.SCANNING,
            showProgress = true,
        )
        val networkTraceId = scannerPerformance.reserveApiTrace()
        if (performQrOperation(operationType, qrCode, requestId, networkTraceId)) {
            scannerPerformance.apiStarted(networkTraceId)
            startOperatorOperationTimeout(requestId)
        }
    }

    private fun beginOperatorVehicleProcessing(
        vehicleNumber: String,
        lifecycleEvent: ScannerLifecycleEvent = ScannerLifecycleEvent.SUBMIT,
    ) {
        val normalizedVehicle = normalizeVehicleNumber(vehicleNumber)
        if (normalizedVehicle == null) {
            operatorOperationInProgress = false
            updateOperatorInteractionState()
            setStatus(getString(R.string.vehicle_sheet_manual_error), ScanState.ERROR, showProgress = false)
            showScannerNotification(
                title = getString(R.string.scanner_error),
                message = getString(R.string.vehicle_sheet_manual_error),
                isError = true
            )
            scannerPerformance.finishAttempt("validation_error")
            scheduleVehicleScanResume(delayMs = scannerFeatureControls.errorResultHoldMs)
            return
        }
        stopScanLineAnimation()
        cancelVehicleScanResume()
        cancelOperatorOperationTimeout()
        currentVehicleNumber = normalizedVehicle
        val operationType = determineOperationType()
        if (operationType == null) {
            operatorOperationInProgress = false
            updateOperatorInteractionState()
            setStatus(getString(R.string.vehicle_scan_error_generic), ScanState.ERROR, showProgress = false)
            showScannerNotification(
                title = getString(R.string.scanner_error),
                message = getString(R.string.unsupported_operator_scan_mode),
                isError = true
            )
            scannerPerformance.finishAttempt("configuration_error")
            scheduleVehicleScanResume(delayMs = scannerFeatureControls.errorResultHoldMs)
            return
        }
        val network = ensureOperatorNetworkReady() ?: return

        val requestId = SystemClock.elapsedRealtimeNanos()
        moveScannerLifecycle(lifecycleEvent)
        pendingOperatorRequestId = requestId
        setStatus(
            getNetworkAwareProcessingMessage(getProcessingMessage(operationType), network),
            ScanState.SCANNING,
            showProgress = true,
        )
        val networkTraceId = scannerPerformance.reserveApiTrace()
        if (performOperation(operationType, requestId, networkTraceId)) {
            scannerPerformance.apiStarted(networkTraceId)
            startOperatorOperationTimeout(requestId)
        }
    }

    private fun processOperatorVehicleInput(vehicleNumber: String) {
        if (automaticRecognitionGate.manualPlateCommitDecision(
                mutationInFlight = hasActiveOperatorMutation(),
            ) != ScannerRecognitionDecision.ALLOW
        ) {
            // The retained coordinator owns the active request. Do not overwrite its subject,
            // operation, or pending UI with a second manual/voice value.
            operatorViewModel.activeRequestId()?.let { activeRequestId ->
                pendingOperatorRequestId = activeRequestId
                operatorOperationInProgress = true
                vehicleScanCompleted = true
                updateOperatorInteractionState()
            }
            return
        }
        val normalizedVehicle = normalizeVehicleNumber(vehicleNumber)
        if (normalizedVehicle == null) {
            currentVehicleNumber = null
            updateOperatorInteractionState()
            resumeLiveVehicleScanning()
            return
        }

        beginManualOperatorAttempt()
        vehicleScanCompleted = true
        currentVehicleNumber = normalizedVehicle
        operatorOperationInProgress = true
        clearCandidateBuffer()
        cancelScanTimeout()
        updateOperatorInteractionState()
        beginOperatorVehicleProcessing(
            normalizedVehicle,
            lifecycleEvent = ScannerLifecycleEvent.MANUAL_SUBMIT,
        )
    }

    private fun beginManualOperatorAttempt() {
        scannerPerformance.beginAttempt(
            mode = "plate",
            operation = scannerMetricOperation(),
        )
    }

    @SuppressLint("SuspiciousIndentation")
    private fun ensureOperatorNetworkReady(): ScannerNetworkSnapshot? {
        val network = scannerNetworkMonitor.snapshot()
        scannerPerformance.networkPreflight(
            readiness = network.readiness.name.lowercase(Locale.ROOT),
            transport = network.transport,
        )
        if (network.canSubmitMutation) return network

        cancelOperatorOperationTimeout()
        pendingOperatorRequestId = null
        operatorOperationInProgress = false
        vehicleScanCompleted = true
        val message = getString(R.string.scanner_operator_offline)
        persistentOperatorErrorMessage = message
        moveScannerLifecycle(ScannerLifecycleEvent.LOCAL_ERROR)
        setStatus(message, ScanState.ERROR, showProgress = false)
        scannerPerformance.errorAwaitingOperatorAction()
        scannerPerformance.finishAttempt("offline_preflight")
        provideFeedback(FeedbackType.ERROR)
        updateOperatorInteractionState()
        return null
    }

    private fun getNetworkAwareProcessingMessage(
        processingMessage: String,
        network: ScannerNetworkSnapshot,
    ): String {
        return if (network.readiness == ScannerNetworkReadiness.DEGRADED) {
            getString(R.string.scanner_operator_slow_network, processingMessage)
        } else {
            processingMessage
        }
    }

    private fun startScanLineAnimation() {
        if (!isOperatorPlateMode() ||
            !scannerViewportSupportsLiveScanning ||
            scannerReadinessController.state != ScannerReadinessState.READY
        ) {
            return
        }
        scanLine.visibility = View.VISIBLE
        scanLineAnimator?.cancel()
        scanningFrameContainer.post {
            val travel = scanningFrameContainer.height / 2f
            if (travel <= 0f) return@post
            scanLine.translationY = -travel
            scanLineAnimator = ObjectAnimator.ofFloat(
                scanLine,
                View.TRANSLATION_Y,
                -travel,
                travel
            ).apply {
                duration = 3000L // Smooth 3 second cycle
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f) // Material easing
                start()
            }
        }
    }

    private fun stopScanLineAnimation() {
        scanLineAnimator?.cancel()
        scanLineAnimator = null
        scanLine.translationY = 0f
        scanLine.visibility = View.GONE
    }

    private fun animateHintFadeIn() {
        vehicleHint.visibility = View.VISIBLE
        vehicleHint.alpha = 0f
        vehicleHint.animate()
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // Auto-fade out after 1.5 seconds (faster for minimal feel)
                handler.postDelayed({
                    animateHintFadeOut()
                }, 1500)
            }
            .start()
    }

    private fun animateHintFadeOut() {
        vehicleHint.animate()
            .alpha(0f)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                vehicleHint.visibility = View.GONE
            }
            .start()
    }

    private fun animateCornerBrackets(scaleUp: Boolean) {
        val scale = if (scaleUp) 1.05f else 1.0f // Subtle scale for minimal design
        val duration = 300L

        cornerTopLeft.animate().scaleX(scale).scaleY(scale).setDuration(duration).start()
        cornerTopRight.animate().scaleX(scale).scaleY(scale).setDuration(duration).start()
        cornerBottomLeft.animate().scaleX(scale).scaleY(scale).setDuration(duration).start()
        cornerBottomRight.animate().scaleX(scale).scaleY(scale).setDuration(duration).start()
    }

    /**
     * Animate corner glow color based on scan state
     * Subtle tint transitions for success/error feedback
     */
    private fun animateCornerGlow(state: ScanState) {
        val targetColor = when(state) {
            ScanState.SUCCESS -> ContextCompat.getColor(this, R.color.scanner_corner_success)
            ScanState.ERROR -> ContextCompat.getColor(this, R.color.scanner_corner_error)
            ScanState.WARNING -> ContextCompat.getColor(this, R.color.scanner_corner_warning)
            ScanState.SCANNING -> ContextCompat.getColor(this, R.color.scanner_corner_idle)
        }

        scannerUiRenderer.animateCornerColor(targetColor)
    }

    private fun setStatus(
        message: String,
        state: ScanState = ScanState.SCANNING,
        showProgress: Boolean = true
    ) {
        if (!isVehicleScan()) return

        if (statusContainer.visibility != View.VISIBLE) {
            statusContainer.alpha = 0f
            statusContainer.translationY = 20f
            statusContainer.visibility = View.VISIBLE
            statusContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        }

        val plate = currentVehicleNumber?.takeIf { it.isNotBlank() }
        val idleState = !operatorOperationInProgress && plate.isNullOrBlank() && currentQrCode.isNullOrBlank()
        val panelMode = when {
            state == ScanState.SUCCESS -> ScannerPanelMode.SUCCESS
            state == ScanState.ERROR -> ScannerPanelMode.ERROR
            state == ScanState.WARNING -> ScannerPanelMode.WARNING
            operatorOperationInProgress -> ScannerPanelMode.PROCESSING
            else -> ScannerPanelMode.SCANNING
        }
        val subject = plate ?: currentQrCode?.takeIf { it.isNotBlank() }?.let {
            getString(R.string.scanner_booking_qr)
        }
        val idleMessage = when {
            voiceRecognitionInProgress || voiceListeningInProgress -> message
            isOperatorQrMode() -> getString(R.string.scanner_qr_scan_hint)
            else -> getString(R.string.vehicle_scan_hint)
        }
        val title = when (panelMode) {
            ScannerPanelMode.SCANNING -> when {
                !subject.isNullOrBlank() -> subject
                isOperatorQrMode() -> getString(R.string.scanner_scan_qr_title)
                else -> getString(R.string.scanner_scan_plate_title)
            }
            ScannerPanelMode.PROCESSING -> subject ?: getString(R.string.scanner_processing_title)
            ScannerPanelMode.SUCCESS -> getOperationSuccessTitle(selectedOperationType)
            ScannerPanelMode.ERROR -> getString(R.string.scanner_do_not_allow)
            ScannerPanelMode.WARNING -> getString(R.string.scanner_check_again)
        }
        val subtitle = when {
            panelMode == ScannerPanelMode.SUCCESS -> message
            panelMode == ScannerPanelMode.ERROR || panelMode == ScannerPanelMode.WARNING -> {
                normalizeNotificationMessage(message)
            }
            idleState -> idleMessage
            else -> normalizeNotificationMessage(message)
        }

        val meta = getSpotMetaText()
        val shouldShowProgress = showProgress && panelMode == ScannerPanelMode.PROCESSING
        val highPriority = panelMode == ScannerPanelMode.PROCESSING ||
            panelMode == ScannerPanelMode.SUCCESS ||
            panelMode == ScannerPanelMode.ERROR ||
            voiceRecognitionInProgress ||
            voiceListeningInProgress
        if (!scannerUiUpdateGate.shouldRender(
                modeKey = panelMode.ordinal,
                operationKey = selectedOperationType.ordinal,
                title = title,
                subtitle = subtitle,
                meta = meta,
                showProgress = shouldShowProgress,
                highPriority = highPriority,
                nowMs = SystemClock.elapsedRealtime()
            )
        ) {
            return
        }

        renderScannerPanel(
            mode = panelMode,
            operationType = selectedOperationType,
            title = title,
            subtitle = subtitle,
            meta = meta,
            showProgress = shouldShowProgress
        )

        animateCornerGlow(state)
    }

    private fun showScannerStatusPill(
        badgeText: String,
        badgeBackgroundRes: Int,
        badgeTextColorRes: Int,
        title: String,
        subtitle: String,
        showProgress: Boolean,
        progressTintRes: Int = badgeTextColorRes
    ) {
        val mode = if (badgeText.equals(getString(R.string.scanner_status_failed_badge), ignoreCase = true)) {
            ScannerPanelMode.ERROR
        } else {
            ScannerPanelMode.SCANNING
        }
        renderScannerPanel(
            mode = mode,
            operationType = selectedOperationType,
            title = title,
            subtitle = subtitle,
            meta = getSpotMetaText(),
            showProgress = showProgress,
            badgeBackgroundRes = badgeBackgroundRes,
            badgeTextColorRes = badgeTextColorRes,
            badgeText = badgeText,
            progressTintRes = progressTintRes
        )
    }

    private fun renderScannerPanel(
        mode: ScannerPanelMode,
        operationType: OperationType,
        title: String,
        subtitle: String,
        meta: String?,
        showProgress: Boolean,
        badgeBackgroundRes: Int = getOperationBadgeBackground(operationType),
        badgeTextColorRes: Int = getOperationBadgeTextColor(operationType),
        badgeText: String = getOperationBadgeText(operationType),
        progressTintRes: Int = badgeTextColorRes
    ) {
        val panelBackgroundRes = when (mode) {
            ScannerPanelMode.SUCCESS -> R.drawable.bg_scanner_panel_success
            ScannerPanelMode.ERROR -> R.drawable.bg_scanner_panel_error
            ScannerPanelMode.WARNING -> R.drawable.bg_scanner_panel_warning
            ScannerPanelMode.SCANNING,
            ScannerPanelMode.PROCESSING -> R.drawable.bg_scanner_panel_neutral
        }
        val iconBackgroundRes = when (mode) {
            ScannerPanelMode.SUCCESS -> R.drawable.bg_scanner_status_icon_success
            ScannerPanelMode.ERROR -> R.drawable.bg_scanner_status_icon_error
            else -> R.drawable.bg_scanner_status_icon_neutral
        }
        val iconRes = when (mode) {
            ScannerPanelMode.SUCCESS -> R.drawable.ic_check
            ScannerPanelMode.ERROR -> R.drawable.ic_cancel
            ScannerPanelMode.WARNING -> R.drawable.ic_manual_entry
            ScannerPanelMode.PROCESSING -> R.drawable.ic_scanner_filled
            ScannerPanelMode.SCANNING -> if (isOperatorQrMode()) R.drawable.ic_qr_code else R.drawable.ic_car_compact
        }
        val iconTintColor = ContextCompat.getColor(
            this,
            if (mode == ScannerPanelMode.SUCCESS || mode == ScannerPanelMode.ERROR) {
                R.color.scanner_primary
            } else {
                R.color.scanner_text_primary
            }
        )
        val badgeTextColor = ContextCompat.getColor(this, badgeTextColorRes)
        val progressTintColor = ContextCompat.getColor(this, progressTintRes)

        val renderedBadgeText = uppercaseIfNeeded(badgeText)
        val renderedTitle = title.ifBlank { getString(R.string.scanner_status_ready_title) }
        val renderedSubtitle = normalizeNotificationMessage(subtitle)
            .ifBlank { getString(R.string.vehicle_scan_hint) }
        val renderedMeta = meta.orEmpty()
        val showResultActions = mode == ScannerPanelMode.ERROR
        val showManualFallback = isOperatorPlateMode() &&
            (mode == ScannerPanelMode.SCANNING || mode == ScannerPanelMode.WARNING)
        scannerUiRenderer.render(
            ScannerPanelRenderState(
                panelBackgroundRes = panelBackgroundRes,
                iconBackgroundRes = iconBackgroundRes,
                iconRes = iconRes,
                iconTintColor = iconTintColor,
                badgeBackgroundRes = badgeBackgroundRes,
                badgeTextColor = badgeTextColor,
                badgeText = renderedBadgeText,
                title = renderedTitle,
                subtitle = renderedSubtitle,
                meta = renderedMeta,
                showMeta = !meta.isNullOrBlank(),
                showProgress = showProgress,
                progressTintColor = progressTintColor,
                showResultActions = showResultActions,
                showManualFallback = showManualFallback,
            )
        )
        keepStatusActionVisible()
    }

    /** Keeps recovery controls on-screen when a constrained status card must scroll. */
    private fun keepStatusActionVisible() {
        val scrollView = statusContainer as? androidx.core.widget.NestedScrollView ?: return
        scrollView.post {
            if (resultActionsContainer.isVisible || manualFallbackButton.isVisible) {
                scrollView.fullScroll(View.FOCUS_DOWN)
            } else {
                scrollView.scrollTo(0, 0)
            }
        }
    }

    private fun uppercaseIfNeeded(value: String): String {
        return if (value.none(Char::isLowerCase)) value else value.uppercase(Locale.ROOT)
    }

    private fun getOperationBadgeText(operationType: OperationType): String {
        return when (operationType) {
            OperationType.CHECK_IN -> getString(R.string.scanner_entry)
            OperationType.CHECK_OUT -> getString(R.string.scanner_exit)
        }
    }

    private fun getOperationSuccessTitle(operationType: OperationType): String {
        return when (operationType) {
            OperationType.CHECK_IN -> getString(R.string.scanner_entry_ok)
            OperationType.CHECK_OUT -> getString(R.string.scanner_exit_ok)
        }
    }

    private fun getSpotMetaText(): String? {
        return operatorParkingSpotName
            ?.takeIf { it.isNotBlank() }
            ?: operatorParkingSpotId?.takeIf { it.isNotBlank() }
    }

    private fun getOperationBadgeBackground(operationType: OperationType): Int {
        return when (operationType) {
            OperationType.CHECK_IN -> R.drawable.status_outlined_active
            OperationType.CHECK_OUT -> R.drawable.status_outlined_pending
        }
    }

    private fun getOperationBadgeTextColor(operationType: OperationType): Int {
        return when (operationType) {
            OperationType.CHECK_IN -> R.color.booking_status_active_text
            OperationType.CHECK_OUT -> R.color.booking_status_pending_text
        }
    }

    private fun startScanTimeout() {
        if (!isOperatorPlateMode()) return
        cancelScanTimeout()
        val runnable = Runnable {
            scanTimeoutRunnable = null
            if (!vehicleScanCompleted) {
                setStatus(
                    getString(R.string.vehicle_scan_not_clear_hint),
                    ScanState.WARNING,
                    showProgress = false
                )
            }
        }
        scanTimeoutRunnable = runnable
        handler.postDelayed(runnable, scanTimeoutMs)
    }

    private fun cancelScanTimeout() {
        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
        scanTimeoutRunnable = null
    }


    private fun provideFeedback(type: FeedbackType) {
        when (type) {
            FeedbackType.SUCCESS -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            FeedbackType.ERROR -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 220)
            FeedbackType.TIMEOUT -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
        }

        vibrator?.let { vib ->
            val pattern = when (type) {
                FeedbackType.SUCCESS -> longArrayOf(0, 60, 100, 60)
                FeedbackType.ERROR -> longArrayOf(0, 200)
                FeedbackType.TIMEOUT -> longArrayOf(0, 40, 80, 40, 80, 40)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudePattern = IntArray(pattern.size) { VibrationEffect.DEFAULT_AMPLITUDE }
                val effect = VibrationEffect.createWaveform(pattern, amplitudePattern, -1)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, -1)
            }
        }
    }

    private fun toggleFlash() {
        if (!isOperatorPlateMode() && !isOperatorQrMode()) return
        if (!scannerCameraController.hasFlashUnit) {
            Toast.makeText(this, R.string.vehicle_scan_flash_off, Toast.LENGTH_SHORT).show()
            return
        }
        setTorch(!isTorchOn)
    }

    private fun applyQrAutoZoomSuggestion(suggestedRatio: Float): Boolean {
        return synchronized(scannerCameraAdjustmentLock) {
            val frameContext = activeQrAutoZoomContext ?: return@synchronized false
            if (!scannerFeatureControls.qrAutoZoomEnabled ||
                frameContext.source != ScannerAutomaticRecognitionSource.QR ||
                !isRecognitionFrameRuntimeCurrent(frameContext)
            ) {
                return@synchronized false
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastQrAutoZoomAtMs < qrAutoZoomCooldownMs) {
                return@synchronized false
            }
            when (scannerCameraController.setZoomRatio(
                requestedRatio = suggestedRatio,
                productMaximum = qrAutoZoomMaxRatio,
            )) {
                CameraValueUpdate.UNAVAILABLE -> false
                CameraValueUpdate.UNCHANGED -> true
                CameraValueUpdate.APPLIED -> {
                    lastQrAutoZoomAtMs = now
                    scannerPerformance.autoZoomApplied(frameContext.performanceToken)
                    true
                }
            }
        }
    }

    private fun resetCameraZoom() {
        applyScannerZoomDefault()
    }

    private fun applyScannerZoomDefault() {
        synchronized(scannerCameraAdjustmentLock) {
            applyScannerZoomDefaultLocked()
        }
    }

    private fun applyScannerZoomDefaultLocked() {
        lastQrAutoZoomAtMs = 0L
        val requestedRatio = if (isOperatorPlateMode()) {
            scannerFeatureControls.plateInitialZoomRatio
        } else {
            1f
        }
        scannerCameraController.setZoomRatio(requestedRatio)
    }

    private fun applyExposureCompensation(requestedIndex: Int) {
        synchronized(scannerCameraAdjustmentLock) {
            applyExposureCompensationLocked(requestedIndex)
        }
    }

    private fun applyExposureCompensationLocked(requestedIndex: Int) {
        scannerCameraController.setExposureCompensation(requestedIndex)
    }

    private fun setTorch(enabled: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { setTorch(enabled) }
            return
        }
        if (!scannerCameraController.hasFlashUnit) {
            isTorchOn = false
            updateFlashToggleIcon()
            return
        }
        isTorchOn = scannerCameraController.setTorch(enabled) && enabled
        updateFlashToggleIcon()
        if (enabled && lowLightActive && !vehicleScanCompleted && !operatorOperationInProgress) {
            setStatus(getIdleScanMessage(), ScanState.SCANNING, showProgress = true)
        }
    }

    private fun updateFlashToggleVisibility(show: Boolean) {
        if (!this::flashToggle.isInitialized) return
        val hasFlash = scannerCameraController.hasFlashUnit
        flashToggle.animate().cancel()
        if (show && hasFlash && (isOperatorPlateMode() || isOperatorQrMode())) {
            // Visibility must be immediate. A delayed fade-out callback from the previous camera
            // mode can otherwise hide the newly enabled QR torch after its camera has bound.
            flashToggle.alpha = 1f
            flashToggle.visibility = View.VISIBLE
            flashToggle.isEnabled = true
            updateFlashToggleIcon()
        } else {
            flashToggle.isEnabled = false
            flashToggle.alpha = 1f
            flashToggle.visibility = View.GONE
            setTorch(false)
        }
    }

    private fun updateFlashToggleIcon() {
        if (!this::flashToggle.isInitialized) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { updateFlashToggleIcon() }
            return
        }
        val icon = if (isTorchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        val description = if (isTorchOn) R.string.vehicle_scan_flash_on else R.string.vehicle_scan_flash_off

        // Smooth icon transition with scale animation
        flashToggle.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(100)
            .withEndAction {
                flashToggle.setImageResource(icon)
                flashToggle.contentDescription = getString(description)
                flashToggle.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                    .start()
            }
            .start()
    }

    private fun updateMeteringRegion(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (scannerCameraController.scanRegionBounds() == null) {
            scannerCameraController.updateScanRegion(getOverlayRectOnPreview())
        }
        scannerCameraController.focusOnScanRegion(
            force = force,
            elapsedRealtimeMs = now,
            cooldownMs = focusMeteringCooldownMs,
            onStarted = scannerPerformance::focusStarted,
            onCompleted = scannerPerformance::focusCompleted,
        )
    }

    private fun View.isManualForced(): Boolean = (tag as? Boolean) == true
    private fun View.setManualForced(value: Boolean) {
        tag = value
    }

    private fun restartVehicleScan() {
        if (operatorViewModel.hasActiveOperation()) {
            operatorOperationInProgress = true
            vehicleScanCompleted = true
            pendingOperatorRequestId = operatorViewModel.activeRequestId()
            updateOperatorInteractionState()
            return
        }
        if (cameraManualFallbackReason != null) {
            showPendingCameraFallbackAfterTerminal()
            return
        }
        if (!isOperatorPlateMode()) {
            resumeOperatorQrScanning()
            return
        }
        if (!isAutomaticRecognitionEnabled()) {
            operatorOperationInProgress = false
            vehicleScanCompleted = false
            pendingOperatorRequestId = null
            currentVehicleNumber = null
            currentQrCode = null
            clearCandidateBuffer()
            renderAutomaticRecognitionFallback()
            return
        }
        if (!scannerViewportLayoutKnown) {
            rootView.post { updateScannerLayoutForScreen() }
            return
        }
        if (!scannerViewportSupportsLiveScanning) {
            operatorOperationInProgress = false
            vehicleScanCompleted = false
            pendingOperatorRequestId = null
            currentVehicleNumber = null
            currentQrCode = null
            clearCandidateBuffer()
            renderSmallViewportFallback()
            return
        }
        cancelVehicleScanResume()
        cancelOperatorOperationTimeout()
        operatorOperationInProgress = false
        pendingOperatorRequestId = null
        currentVehicleNumber = null
        currentQrCode = null
        vehicleScanCompleted = false
        clearCandidateBuffer()
        resetScannerQualityState()
        resetCameraZoom()
        applyExposureCompensation(0)
        scannerPerformance.nextScanReady()
        if (scannerReadinessController.state != ScannerReadinessState.READY) {
            updateOperatorInteractionState()
            renderScannerReadiness(scannerReadinessController.state)
            return
        }
        scannerPerformance.beginAttempt(scannerMetricMode(), scannerMetricOperation())
        animatePreviewGlassEffect(enabled = false)
        resetInlineManualEntryForm()
        animateCornerBrackets(scaleUp = false)
        updateOperatorInteractionState()
        setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
        consumePendingScannerCameraRebind()
        if (vehicleScannerRunning) {
            startScanLineAnimation()
            startScanTimeout()
            updateFlashToggleVisibility(true)
            scanningFrameContainer.post { updateMeteringRegion() }
        } else {
            startVehicleScanner()
        }
    }


    private fun handleOperationState(state: CheckInState, operationType: OperationType) {
        val stateRequestId = when (state) {
            is CheckInState.Loading -> state.requestId
            is CheckInState.Success -> state.requestId
            is CheckInState.CoolingDown -> state.requestId
            is CheckInState.Error -> state.requestId
            CheckInState.Idle -> null
        }
        if (stateRequestId != null) {
            val pendingRequestId = pendingOperatorRequestId
            if (pendingRequestId != null && stateRequestId != pendingRequestId) return
            if (pendingRequestId == null) {
                // LiveData may re-deliver Loading/terminal state after rotation. Adopt the
                // retained ViewModel request instead of starting the scanner again.
                pendingOperatorRequestId = stateRequestId
                operatorOperationInProgress = state is CheckInState.Loading ||
                    operatorViewModel.hasActiveOperation()
                vehicleScanCompleted = true
            }
        }

        when (state) {
            CheckInState.Idle -> {
                if (pendingOperatorRequestId != null &&
                    !operatorViewModel.hasActiveOperation() &&
                    operatorViewModel.operationPresentation() == null
                ) {
                    cancelOperatorOperationTimeout()
                    operatorTimedOutRequestId = null
                    pendingOperatorRequestId = null
                    operatorOperationInProgress = false
                    vehicleScanCompleted = false
                    currentVehicleNumber = null
                    currentQrCode = null
                    moveScannerLifecycle(ScannerLifecycleEvent.RESET)
                    updateOperatorInteractionState()
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        scheduleOperatorScanResume(delayMs = 0L)
                    }
                }
            }
            is CheckInState.Loading -> {
                operatorOperationInProgress = true
                updateOperatorInteractionState()
                if (operatorViewModel.operationPresentation(state.requestId) != null &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    restoreRetainedOperatorPresentation()
                } else {
                    setStatus(
                        getActiveProcessingMessage(operationType),
                        ScanState.SCANNING,
                        showProgress = true,
                    )
                }
            }
            is CheckInState.CoolingDown -> {
                val isFirstPresentation =
                    lastPresentedOperatorTerminalRequestId != state.requestId
                lastPresentedOperatorTerminalRequestId = state.requestId
                if (isFirstPresentation) {
                    moveScannerLifecycle(ScannerLifecycleEvent.REJECTED_BEFORE_START)
                }
                cancelOperatorOperationTimeout()
                operatorTimedOutRequestId = null
                pendingOperatorRequestId = null
                operatorOperationInProgress = false
                vehicleScanCompleted = true
                clearPersistentOperatorError()
                val seconds = ((state.remainingMs.coerceAtLeast(0L) + 999L) / 1_000L)
                    .coerceAtLeast(1L)
                setStatus(
                    resources.getQuantityString(
                        R.plurals.scanner_recent_scan_cooldown,
                        seconds.toInt(),
                        seconds,
                    ),
                    ScanState.WARNING,
                    showProgress = false,
                )
                if (isFirstPresentation) {
                    scannerPerformance.finishAttempt("cooldown")
                }
                updateOperatorInteractionState()
                scheduleOperatorScanResume(
                    delayMs = maxOf(
                        state.remainingMs,
                        scannerFeatureControls.errorResultHoldMs,
                    )
                )
            }
            is CheckInState.Success -> {
                val isFirstPresentation =
                    lastPresentedOperatorTerminalRequestId != state.requestId
                lastPresentedOperatorTerminalRequestId = state.requestId
                if (isFirstPresentation) {
                    moveScannerLifecycle(ScannerLifecycleEvent.SUCCEEDED)
                }
                cancelOperatorOperationTimeout()
                operatorTimedOutRequestId = null
                if (isFirstPresentation) {
                    scannerPerformance.apiCompleted()
                }
                clearPersistentOperatorError()
                pendingOperatorRequestId = null
                operatorOperationInProgress = false
                vehicleScanCompleted = true
                val vehicleNumber = state.booking.vehicleNumber ?: currentVehicleNumber.orEmpty()
                currentVehicleNumber = vehicleNumber.takeIf { it.isNotBlank() }
                renderOperationSuccess(operationType, state.booking, vehicleNumber)
                if (isFirstPresentation) {
                    scannerPerformance.successRendered()
                    scannerPerformance.finishAttempt("success")
                    provideFeedback(FeedbackType.SUCCESS)
                }
                updateOperatorInteractionState()
                scheduleOperatorScanResume(delayMs = scannerFeatureControls.successResultHoldMs)
            }
            is CheckInState.Error -> {
                val isFirstPresentation =
                    lastPresentedOperatorTerminalRequestId != state.requestId
                lastPresentedOperatorTerminalRequestId = state.requestId
                if (isFirstPresentation) {
                    moveScannerLifecycle(ScannerLifecycleEvent.FAILED)
                }
                cancelOperatorOperationTimeout()
                operatorTimedOutRequestId = null
                if (isFirstPresentation) {
                    scannerPerformance.apiCompleted()
                    scannerPerformance.errorAwaitingOperatorAction()
                    scannerPerformance.finishAttempt("error_${state.category.metricValue}")
                }
                pendingOperatorRequestId = null
                operatorOperationInProgress = false
                vehicleScanCompleted = true
                val message = normalizeNotificationMessage(state.message).ifBlank {
                    getString(R.string.vehicle_scan_error_generic)
                }
                persistentOperatorErrorMessage = message
                setStatus(message, ScanState.ERROR, showProgress = false)
                if (isFirstPresentation) {
                    provideFeedback(FeedbackType.ERROR)
                }
                updateOperatorInteractionState()
            }
        }
    }

    private fun renderOperationSuccess(operationType: OperationType, booking: Booking, vehicleNumber: String) {
        val subject = vehicleNumber.takeIf { it.isNotBlank() } ?: getString(R.string.scanner_booking_qr)
        val operationTime = formatOperationTime(getOperationTimestamp(operationType, booking))
        val timeLabel = when (operationType) {
            OperationType.CHECK_IN -> getString(R.string.scanner_checked_in_at, operationTime)
            OperationType.CHECK_OUT -> getString(R.string.scanner_checked_out_at, operationTime)
        }
        val spotLabel = getSpotMetaText()?.let { getString(R.string.scanner_spot_meta, it) }
        val meta = listOfNotNull(spotLabel, timeLabel).joinToString("  |  ")
        renderScannerPanel(
            mode = ScannerPanelMode.SUCCESS,
            operationType = operationType,
            title = getOperationSuccessTitle(operationType),
            subtitle = subject,
            meta = meta,
            showProgress = false
        )
        animateCornerGlow(ScanState.SUCCESS)
    }

    private fun getOperationTimestamp(operationType: OperationType, booking: Booking): java.util.Date? {
        return when (operationType) {
            OperationType.CHECK_IN -> booking.actualCheckInTime
            OperationType.CHECK_OUT -> booking.actualCheckOutTime
        }
    }

    private fun formatOperationTime(date: java.util.Date?): String {
        return DateFormat.getTimeFormat(this).format(date ?: java.util.Date())
    }

    private fun getActiveProcessingMessage(operationType: OperationType): String {
        return if (isOperatorQrMode()) getQrProcessingMessage(operationType) else getProcessingMessage(operationType)
    }

    private fun getProcessingMessage(operationType: OperationType): String {
        return when (operationType) {
            OperationType.CHECK_IN -> getString(R.string.vehicle_sheet_processing_checkin)
            OperationType.CHECK_OUT -> getString(R.string.vehicle_sheet_processing_checkout)
        }
    }

    private fun getQrProcessingMessage(operationType: OperationType): String {
        return when (operationType) {
            OperationType.CHECK_IN -> getString(R.string.scanner_processing_qr_checkin)
            OperationType.CHECK_OUT -> getString(R.string.scanner_processing_qr_checkout)
        }
    }

    private fun getSuccessMessage(operationType: OperationType, vehicleNumber: String): String {
        if (vehicleNumber.isBlank()) {
            return when (operationType) {
                OperationType.CHECK_IN -> "Check-in completed"
                OperationType.CHECK_OUT -> "Check-out completed"
            }
        }
        return when (operationType) {
            OperationType.CHECK_IN -> getString(R.string.vehicle_sheet_checkin_success, vehicleNumber)
            OperationType.CHECK_OUT -> getString(R.string.vehicle_sheet_checkout_success, vehicleNumber)
        }
    }

    private fun startVehicleProcessing(operationType: OperationType, vehicleNumber: String) {
        val ui = vehicleSheetUi ?: return
        beginManualOperatorAttempt()
        currentVehicleNumber = vehicleNumber
        operatorOperationInProgress = true
        updateOperatorInteractionState()
        val requestId = SystemClock.elapsedRealtimeNanos()
        pendingOperatorRequestId = requestId
        ui.progress.isVisible = true
        ui.statusText.text = getProcessingMessage(operationType)
        val networkTraceId = scannerPerformance.reserveApiTrace()
        if (performOperation(operationType, requestId, networkTraceId)) {
            scannerPerformance.apiStarted(networkTraceId)
            startOperatorOperationTimeout(requestId)
        }
    }

    private fun hasUnacknowledgedTerminalResult(): Boolean {
        return operatorViewModel.checkInState.value.isTerminalPresentation() ||
            operatorViewModel.checkOutState.value.isTerminalPresentation()
    }

    private fun CheckInState?.isTerminalPresentation(): Boolean {
        return this is CheckInState.Success || this is CheckInState.Error
    }

    private fun acknowledgePresentedOperatorState() {
        val checkInState = operatorViewModel.checkInState.value
        if (checkInState is CheckInState.Success ||
            checkInState is CheckInState.Error ||
            checkInState is CheckInState.CoolingDown
        ) {
            operatorViewModel.resetCheckInState()
        }
        val checkOutState = operatorViewModel.checkOutState.value
        if (checkOutState is CheckInState.Success ||
            checkOutState is CheckInState.Error ||
            checkOutState is CheckInState.CoolingDown
        ) {
            operatorViewModel.resetCheckOutState()
        }
        lastPresentedOperatorTerminalRequestId = null
    }

    private fun normalizeVehicleNumber(raw: String): String? {
        val cleaned = VehicleNumberValidator.normalize(raw)
        return cleaned.takeIf { VehicleNumberValidator.isValid(it) }
    }

    private fun normalizeOrCorrectPlateCandidate(raw: String): String? {
        return vehiclePlateInterpreter.normalizeCandidate(raw)
            ?.value
            ?.takeIf(VehicleNumberValidator::isValid)
    }

    private fun rankSpokenVehicleMatches(
        spokenMatches: List<String>,
        confidenceScores: FloatArray? = null
    ): List<SpeechPlateCandidate> {
        if (spokenMatches.isEmpty()) return emptyList()

        val rankedCandidates = linkedMapOf<String, SpeechPlateCandidate>()
        spokenMatches.forEachIndexed { index, spoken ->
            val candidatePlate = normalizeSpokenVehicleNumber(spoken) ?: return@forEachIndexed
            val confidence = confidenceScores?.getOrNull(index)?.takeIf { it >= 0f }
            val rankBonus = (spokenMatches.size - index) * 3
            val certaintyBonus = if (vehiclePlateInterpreter.isStrongAutomaticCandidate(candidatePlate)) 8 else 0
            val confidenceBonus = ((confidence ?: 0f) * 24f).roundToInt()
            val candidate = SpeechPlateCandidate(
                plate = candidatePlate,
                score = vehiclePlateInterpreter.score(candidatePlate) + rankBonus + certaintyBonus + confidenceBonus,
                confidence = confidence,
                heardPhrase = spoken
            )
            val existing = rankedCandidates[candidatePlate]
            if (existing == null || candidate.score > existing.score) {
                rankedCandidates[candidatePlate] = candidate
            }
        }

        return rankedCandidates.values.sortedByDescending { it.score }
    }

    private fun normalizeSpokenVehicleNumber(raw: String): String? {
        normalizeOrCorrectPlateCandidate(raw)?.let { return it }

        val tokens = raw
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return null

        val builder = StringBuilder()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]

            when {
                token in fillerSpeechTokens -> {
                    index++
                }
                token == "double" || token == "triple" -> {
                    val repeatCount = if (token == "double") 2 else 3
                    val repeated = tokens.getOrNull(index + 1)?.let(::mapSpeechTokenToCodeUnit)
                    if (repeated != null) {
                        repeat(repeatCount) { builder.append(repeated) }
                        index += 2
                    } else {
                        index++
                    }
                }
                else -> {
                    val numberMatch = parseSpokenNumber(tokens, index)
                    if (numberMatch != null) {
                        builder.append(numberMatch.first)
                        index += numberMatch.second
                        continue
                    }

                    val codeUnit = mapSpeechTokenToCodeUnit(token)
                    if (codeUnit != null) {
                        builder.append(codeUnit)
                    }
                    index++
                }
            }
        }

        if (builder.isEmpty()) return null
        return normalizeOrCorrectPlateCandidate(builder.toString())
    }

    private fun parseSpokenNumber(tokens: List<String>, startIndex: Int): Pair<String, Int>? {
        val token = tokens[startIndex]
        spokenWholeNumberMap[token]?.let { return it to 1 }

        val tensValue = spokenTensMap[token] ?: return null
        val nextToken = tokens.getOrNull(startIndex + 1)
        val unitValue = nextToken?.let { spokenDigitMap[it] }
        return if (unitValue != null) {
            "${tensValue}${unitValue}" to 2
        } else {
            tensValue to 1
        }
    }

    private fun mapSpeechTokenToCodeUnit(token: String): String? {
        spokenDigitMap[token]?.let { return it }
        spokenLetterMap[token]?.let { return it }

        return when {
            token.length == 1 && token[0].isLetterOrDigit() -> token.uppercase(Locale.ROOT)
            token.length in 2..3 && token.all { it.isLetter() } -> token.uppercase(Locale.ROOT)
            token.all { it.isDigit() } -> token
            else -> null
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun startVoiceVehicleInput() {
        if (!isOperatorPlateMode() ||
            scannerReadinessController.state != ScannerReadinessState.READY ||
            vehicleScanCompleted ||
            operatorOperationInProgress ||
            scannerSpotSelectionDialog?.isShowing == true
        ) {
            return
        }
        if (voiceListeningInProgress) {
            stopVoiceRecognition()
            return
        }
        if (voiceRecognitionInProgress) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showScannerNotification(
                title = getString(R.string.voice_input),
                message = getString(R.string.vehicle_scan_voice_unavailable),
                isError = true
            )
            return
        }

        if (!checkAudioPermission()) {
            pendingVoiceStartAfterPermission = true
            requestAudioPermission()
            return
        }

        val recognizer = ensureSpeechRecognizer() ?: run {
            showScannerNotification(
                title = getString(R.string.voice_input),
                message = getString(R.string.vehicle_scan_voice_unavailable),
                isError = true
            )
            return
        }

        cancelVoiceAutoStop()
        cancelVehicleScanResume()
        voiceStopRequested = false
        voiceListeningInProgress = true
        voiceRecognitionInProgress = true
        if (manualEntryInput.hasFocus()) {
            hideKeyboard(manualEntryInput)
            manualEntryInput.clearFocus()
        }
        updateVoiceButtonUi()
        updateOperatorInteractionState()
        animatePreviewGlassEffect(enabled = true)
        stopVehicleScanner(releaseCamera = false)
        stopScanLineAnimation()
        setStatus(getString(R.string.vehicle_scan_voice_listening), ScanState.SCANNING, showProgress = true)

        runCatching { recognizer.startListening(buildVoiceRecognizerIntent()) }
            .onFailure {
                voiceListeningInProgress = false
                voiceRecognitionInProgress = false
                updateOperatorInteractionState()
                showScannerNotification(
                    title = getString(R.string.voice_input),
                    message = getString(R.string.vehicle_scan_voice_start_error),
                    isError = true
                )
                resumeScannerAfterVoiceFlow()
            }
            .onSuccess {
                scheduleVoiceAutoStop()
            }
    }

    private fun ensureSpeechRecognizer(): SpeechRecognizer? {
        val existing = speechRecognizer
        if (existing != null) return existing

        val recognizer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
        } catch (_: Exception) {
            null
        } ?: return null

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                setStatus(getString(R.string.vehicle_scan_voice_listening), ScanState.SCANNING, showProgress = true)
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (voiceRecognitionInProgress) {
                    setStatus(getString(R.string.vehicle_scan_voice_processing), ScanState.SCANNING, showProgress = true)
                    scheduleVoiceProcessingTimeout()
                }
            }

            @Suppress("SwitchIntDef")
            override fun onError(error: Int) {
                val wasVoiceActive = voiceRecognitionInProgress
                clearVoiceRecognitionState()
                if (!wasVoiceActive) return

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        resumeScannerAfterVoiceFlow()
                        return
                    }
                }

                val message = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> getString(R.string.vehicle_scan_voice_busy)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.vehicle_scan_voice_permission_required)
                    else -> getString(R.string.vehicle_scan_voice_start_error)
                }
                showScannerNotification(
                    title = getString(R.string.voice_input),
                    message = message,
                    isError = true,
                )
                resumeScannerAfterVoiceFlow()
            }

            override fun onResults(results: Bundle?) {
                val rankedCandidates = extractSpeechCandidates(results)
                clearVoiceRecognitionState()
                handleSpeechRecognitionCandidates(rankedCandidates)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val previewCandidate = extractSpeechCandidates(partialResults).firstOrNull()
                if (previewCandidate != null && voiceListeningInProgress) {
                    setStatus(
                        getString(R.string.vehicle_scan_best_guess, previewCandidate.plate),
                        ScanState.WARNING,
                        showProgress = true
                    )
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        speechRecognizer = recognizer
        return recognizer
    }

    private fun buildVoiceRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceRecognitionLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.vehicle_scan_voice_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
        }
    }

    private fun stopVoiceRecognition() {
        if (!voiceListeningInProgress) return
        voiceStopRequested = true
        voiceListeningInProgress = false
        cancelVoiceAutoStop()
        scheduleVoiceProcessingTimeout()
        updateVoiceButtonUi()
        setStatus(getString(R.string.vehicle_scan_voice_processing), ScanState.SCANNING, showProgress = true)
        runCatching { speechRecognizer?.stopListening() }
    }

    private fun clearVoiceRecognitionState() {
        cancelVoiceAutoStop()
        cancelVoiceProcessingTimeout()
        voiceListeningInProgress = false
        voiceRecognitionInProgress = false
        voiceStopRequested = false
        updateOperatorInteractionState()
    }

    private fun releaseSpeechRecognizer(destroyRecognizer: Boolean) {
        cancelVoiceAutoStop()
        cancelVoiceProcessingTimeout()
        runCatching { speechRecognizer?.cancel() }
        clearVoiceRecognitionState()
        if (destroyRecognizer) {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
    }

    private fun updateVoiceButtonUi() {
        if (!::voiceEntryButton.isInitialized) return
        val canStartVoice = isOperatorPlateMode() &&
            scannerReadinessController.state == ScannerReadinessState.READY &&
            !operatorOperationInProgress &&
            !vehicleScanCompleted &&
            scannerSpotSelectionDialog?.isShowing != true &&
            !manualEntryInput.hasFocus()
        val iconTint = if (voiceListeningInProgress) {
            ContextCompat.getColor(this, R.color.booking_status_active_text)
        } else {
            ContextCompat.getColor(this, android.R.color.white)
        }
        voiceEntryButton.isEnabled = if (voiceRecognitionInProgress || voiceListeningInProgress) {
            true
        } else {
            canStartVoice
        }
        voiceEntryButton.alpha = when {
            voiceListeningInProgress -> 1f
            voiceEntryButton.isEnabled -> 0.96f
            else -> 0.58f
        }
        voiceEntryButton.scaleX = if (voiceListeningInProgress) 1.12f else 1f
        voiceEntryButton.scaleY = if (voiceListeningInProgress) 1.12f else 1f
        ImageViewCompat.setImageTintList(voiceEntryButton, ColorStateList.valueOf(iconTint))
    }

    private fun scheduleVoiceAutoStop() {
        cancelVoiceAutoStop()
        val runnable = Runnable {
            voiceAutoStopRunnable = null
            stopVoiceRecognition()
        }
        voiceAutoStopRunnable = runnable
        handler.postDelayed(runnable, voiceAutoStopMs)
    }

    private fun cancelVoiceAutoStop() {
        voiceAutoStopRunnable?.let(handler::removeCallbacks)
        voiceAutoStopRunnable = null
    }

    private fun scheduleVoiceProcessingTimeout() {
        cancelVoiceProcessingTimeout()
        val runnable = Runnable {
            voiceProcessingTimeoutRunnable = null
            if (!voiceRecognitionInProgress) return@Runnable
            clearVoiceRecognitionState()
            resumeScannerAfterVoiceFlow()
        }
        voiceProcessingTimeoutRunnable = runnable
        handler.postDelayed(runnable, voiceProcessingTimeoutMs)
    }

    private fun cancelVoiceProcessingTimeout() {
        voiceProcessingTimeoutRunnable?.let(handler::removeCallbacks)
        voiceProcessingTimeoutRunnable = null
    }

    private fun extractSpeechCandidates(results: Bundle?): List<SpeechPlateCandidate> {
        if (results == null) return emptyList()
        val spokenMatches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        return rankSpokenVehicleMatches(spokenMatches, confidences)
    }

    private fun handleSpeechRecognitionCandidates(candidates: List<SpeechPlateCandidate>) {
        if (candidates.isEmpty()) {
            resumeScannerAfterVoiceFlow()
            return
        }

        val bestCandidate = candidates.first()
        processOperatorVehicleInput(bestCandidate.plate)
    }

    private fun resumeScannerAfterVoiceFlow() {
        resumeLiveVehicleScanning()
    }

    private fun determineOperationType(): OperationType? {
        return if (isVehicleScan()) selectedOperationType else resolveOperationType(scanType)
    }

    private fun currentVehicleScanType(): String {
        return when (selectedOperationType) {
            OperationType.CHECK_IN -> "VEHICLE_CHECK_IN"
            OperationType.CHECK_OUT -> "VEHICLE_CHECK_OUT"
        }
    }

    private fun createScannerResultIntent(qrCode: String? = null): Intent {
        return Intent().apply {
            qrCode?.let { putExtra(EXTRA_QR_CODE, it) }
            putExtra(
                EXTRA_SCAN_TYPE,
                if (isVehicleScan()) currentVehicleScanType() else scanType
            )
            operatorParkingSpotId?.let { putExtra(EXTRA_PARKING_SPOT_ID, it) }
            operatorParkingSpotName?.let { putExtra(EXTRA_PARKING_SPOT_NAME, it) }
            currentOperatorParkingLotId()?.let { putExtra(EXTRA_PARKING_LOT_ID, it) }
        }
    }

    private fun resolveOperationType(rawType: String?): OperationType? {
        val type = rawType.orEmpty().uppercase(Locale.ROOT)
        return when {
            type.contains("CHECK_OUT") || type.contains("CHECK-OUT") || type.contains("CHECK OUT") -> OperationType.CHECK_OUT
            type.contains("CHECK_IN") || type.contains("CHECK-IN") || type.contains("CHECK IN") || type.contains("VEHICLE") -> OperationType.CHECK_IN
            else -> null
        }
    }

    private fun performOperation(
        operationType: OperationType,
        requestId: Long,
        networkTraceId: String?,
    ): Boolean {
        val plate = currentVehicleNumber ?: return false
        val accepted = when (operationType) {
            OperationType.CHECK_IN -> operatorViewModel.checkInByVehicleNumber(
                plate,
                operatorParkingSpotId,
                requestId,
                parkingLotId = currentOperatorParkingLotId(),
                networkTraceId = networkTraceId,
            )
            OperationType.CHECK_OUT -> operatorViewModel.checkOutByVehicleNumber(
                plate,
                operatorParkingSpotId,
                requestId,
                parkingLotId = currentOperatorParkingLotId(),
                networkTraceId = networkTraceId,
            )
        }
        if (!accepted && operatorViewModel.hasActiveOperation()) {
            cancelOperatorOperationTimeout()
            pendingOperatorRequestId = operatorViewModel.activeRequestId()
            operatorOperationInProgress = true
            vehicleScanCompleted = true
            updateOperatorInteractionState()
        }
        return accepted
    }

    private fun performQrOperation(
        operationType: OperationType,
        qrCode: String,
        requestId: Long,
        networkTraceId: String?,
    ): Boolean {
        val accepted = when (operationType) {
            OperationType.CHECK_IN -> operatorViewModel.checkInByQrCode(
                qrCode,
                operatorParkingSpotId,
                requestId,
                parkingLotId = currentOperatorParkingLotId(),
                networkTraceId = networkTraceId,
            )
            OperationType.CHECK_OUT -> operatorViewModel.checkOutByQrCode(
                qrCode,
                operatorParkingSpotId,
                requestId,
                parkingLotId = currentOperatorParkingLotId(),
                networkTraceId = networkTraceId,
            )
        }
        if (!accepted && operatorViewModel.hasActiveOperation()) {
            cancelOperatorOperationTimeout()
            pendingOperatorRequestId = operatorViewModel.activeRequestId()
            operatorOperationInProgress = true
            vehicleScanCompleted = true
            updateOperatorInteractionState()
        }
        return accepted
    }

    private fun getOperationTitle(operationType: OperationType, isError: Boolean): String {
        return when {
            operationType == OperationType.CHECK_IN && isError -> "Check-In Failed"
            operationType == OperationType.CHECK_OUT && isError -> "Check-Out Failed"
            operationType == OperationType.CHECK_IN -> "Check-In Complete"
            else -> "Check-Out Complete"
        }
    }

    private fun showScannerNotification(title: String, message: String, isError: Boolean) {
        val normalizedMessage = normalizeNotificationMessage(message)
        val operationType = resolveOperationType(title) ?: resolveOperationType(normalizedMessage)
        val vehicleNumber = currentVehicleNumber
            ?.takeIf { it.isNotBlank() }
            ?: extractVehicleNumber(title)
            ?: extractVehicleNumber(normalizedMessage)
        val detailMessage = vehicleNumber?.let {
            normalizedMessage.replaceFirst(it, "").trimStart('.', ' ', '-', ':')
        } ?: normalizedMessage
        if (isError) {
            val subtitle = when {
                operationType != null && detailMessage.isNotBlank() -> {
                    getString(
                        R.string.scanner_status_result_failed,
                        getOperationBadgeText(operationType)
                    ) + ". " + detailMessage
                }
                operationType != null -> {
                    getString(
                        R.string.scanner_status_result_failed,
                        getOperationBadgeText(operationType)
                    )
                }
                else -> detailMessage
            }
            showScannerStatusPill(
                badgeText = getString(R.string.scanner_status_failed_badge),
                badgeBackgroundRes = R.drawable.status_outlined_error,
                badgeTextColorRes = R.color.scanner_error_red,
                title = getString(R.string.scanner_do_not_allow),
                subtitle = subtitle,
                showProgress = false,
                progressTintRes = R.color.scanner_error_red
            )
        } else {
            val successfulOperation = operationType ?: selectedOperationType
            showScannerStatusPill(
                badgeText = getOperationBadgeText(successfulOperation),
                badgeBackgroundRes = getOperationBadgeBackground(successfulOperation),
                badgeTextColorRes = getOperationBadgeTextColor(successfulOperation),
                title = getOperationSuccessTitle(successfulOperation),
                subtitle = getString(
                    R.string.scanner_status_result_success,
                    getOperationBadgeText(successfulOperation)
                ),
                showProgress = false,
                progressTintRes = getOperationBadgeTextColor(successfulOperation)
            )
        }
    }

    private fun extractVehicleNumber(source: String?): String? {
        return source
            .orEmpty()
            .uppercase(Locale.ROOT)
            .split(Regex("[^A-Z0-9]+"))
            .asSequence()
            .map { VehicleNumberValidator.normalize(it) }
            .firstOrNull { it.isNotBlank() && VehicleNumberValidator.isValid(it) }
    }

    private fun normalizeNotificationMessage(message: String): String {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty()) return ""
        if ('\n' !in trimmedMessage &&
            '\r' !in trimmedMessage &&
            '\t' !in trimmedMessage &&
            "  " !in trimmedMessage
        ) {
            return trimmedMessage
        }

        return trimmedMessage
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun scheduleVehicleScanResume(delayMs: Long = vehicleScanResumeDelayMs) {
        cancelVehicleScanResume()
        val runnable = Runnable {
            scanResumeRunnable = null
            if (isFinishing || isDestroyed) return@Runnable
            when (
                OperatorScannerResumePolicy.decide(
                    hasActiveMutation = operatorViewModel.hasActiveOperation(),
                    isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                    hasUnacknowledgedTerminalResult = hasUnacknowledgedTerminalResult(),
                )
            ) {
                OperatorScannerResumeDecision.KEEP_MUTATION_LOCKED -> {
                    operatorOperationInProgress = true
                    vehicleScanCompleted = true
                    pendingOperatorRequestId = operatorViewModel.activeRequestId()
                    updateOperatorInteractionState()
                    return@Runnable
                }
                OperatorScannerResumeDecision.RETAIN_TERMINAL_RESULT -> {
                    operatorOperationInProgress = false
                    vehicleScanCompleted = true
                    updateOperatorInteractionState()
                    return@Runnable
                }
                OperatorScannerResumeDecision.CLEAR_TRANSIENT_STATE -> {
                    acknowledgePresentedOperatorState()
                    operatorOperationInProgress = false
                    vehicleScanCompleted = false
                    currentVehicleNumber = null
                    return@Runnable
                }
                OperatorScannerResumeDecision.RESUME_SCANNER ->
                    acknowledgePresentedOperatorState()
            }
            if (showPendingCameraFallbackAfterTerminal()) return@Runnable
            restartVehicleScan()
        }
        scanResumeRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun scheduleOperatorScanResume(delayMs: Long = vehicleScanResumeDelayMs) {
        if (!isOperatorQrMode()) {
            scheduleVehicleScanResume(delayMs)
            return
        }

        cancelVehicleScanResume()
        val runnable = Runnable {
            scanResumeRunnable = null
            if (isFinishing || isDestroyed) return@Runnable
            when (
                OperatorScannerResumePolicy.decide(
                    hasActiveMutation = operatorViewModel.hasActiveOperation(),
                    isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                    hasUnacknowledgedTerminalResult = hasUnacknowledgedTerminalResult(),
                )
            ) {
                OperatorScannerResumeDecision.KEEP_MUTATION_LOCKED -> {
                    operatorOperationInProgress = true
                    vehicleScanCompleted = true
                    pendingOperatorRequestId = operatorViewModel.activeRequestId()
                    updateOperatorInteractionState()
                    return@Runnable
                }
                OperatorScannerResumeDecision.RETAIN_TERMINAL_RESULT -> {
                    operatorOperationInProgress = false
                    vehicleScanCompleted = true
                    updateOperatorInteractionState()
                    return@Runnable
                }
                OperatorScannerResumeDecision.CLEAR_TRANSIENT_STATE -> {
                    acknowledgePresentedOperatorState()
                    operatorOperationInProgress = false
                    vehicleScanCompleted = false
                    currentVehicleNumber = null
                    currentQrCode = null
                    return@Runnable
                }
                OperatorScannerResumeDecision.RESUME_SCANNER ->
                    acknowledgePresentedOperatorState()
            }
            if (showPendingCameraFallbackAfterTerminal()) return@Runnable
            operatorOperationInProgress = false
            vehicleScanCompleted = false
            currentVehicleNumber = null
            currentQrCode = null
            resetScannerQualityState()
            resetCameraZoom()
            scannerPerformance.beginAttempt(scannerMetricMode(), scannerMetricOperation())
            resumeOperatorQrScanning()
        }
        scanResumeRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelVehicleScanResume() {
        scanResumeRunnable?.let { handler.removeCallbacks(it) }
        scanResumeRunnable = null
    }

    private fun startOperatorOperationTimeout(requestId: Long) {
        cancelOperatorOperationTimeout()
        val presentation = operatorViewModel.operationPresentation(requestId) ?: return
        when (
            val decision = OperatorOperationTimeoutPolicy.decide(
                startedAtElapsedMs = presentation.startedAtElapsedMs,
                nowElapsedMs = SystemClock.elapsedRealtime(),
                timeoutMs = operatorOperationTimeoutMs,
                isInFlight = presentation.isInFlight,
            )
        ) {
            OperatorOperationTimeoutDecision.Completed -> return
            OperatorOperationTimeoutDecision.TimedOut -> {
                renderOperatorOperationTimeout(requestId)
                return
            }
            is OperatorOperationTimeoutDecision.Wait -> {
                val runnable = Runnable {
                    operationTimeoutRunnable = null
                    val latest = operatorViewModel.operationPresentation(requestId)
                    if (pendingOperatorRequestId != requestId || latest?.isInFlight != true) {
                        return@Runnable
                    }
                    renderOperatorOperationTimeout(requestId)
                }
                operationTimeoutRunnable = runnable
                handler.postDelayed(runnable, decision.remainingMs)
            }
        }
    }

    private fun restoreRetainedOperatorPresentation() {
        val presentation = operatorViewModel.operationPresentation() ?: return
        pendingOperatorRequestId = presentation.requestId
        operatorOperationInProgress = true
        vehicleScanCompleted = true
        scannerPerformance.reattachApi(
            startedAtElapsedMs = presentation.startedAtElapsedMs,
            traceId = presentation.networkTraceId,
        )
        moveScannerLifecycle(ScannerLifecycleEvent.RESTORE_PROCESSING)
        updateOperatorInteractionState()
        ensureRetainedOperationPreviewBound(presentation.requestId)
        when (
            OperatorOperationTimeoutPolicy.decide(
                startedAtElapsedMs = presentation.startedAtElapsedMs,
                nowElapsedMs = SystemClock.elapsedRealtime(),
                timeoutMs = operatorOperationTimeoutMs,
                isInFlight = presentation.isInFlight,
            )
        ) {
            OperatorOperationTimeoutDecision.Completed -> {
                // The retained terminal is about to be delivered by the ViewModel. Keep the
                // mutation UI locked during that short hand-off and never report it as slow.
                setStatus(
                    getActiveProcessingMessage(selectedOperationType),
                    ScanState.SCANNING,
                    showProgress = true,
                )
            }
            OperatorOperationTimeoutDecision.TimedOut ->
                renderOperatorOperationTimeout(presentation.requestId)
            is OperatorOperationTimeoutDecision.Wait -> {
                setStatus(
                    getActiveProcessingMessage(selectedOperationType),
                    ScanState.SCANNING,
                    showProgress = true,
                )
                startOperatorOperationTimeout(presentation.requestId)
            }
        }
    }

    private fun renderOperatorOperationTimeout(requestId: Long) {
        val presentation = operatorViewModel.operationPresentation(requestId)
        if (pendingOperatorRequestId != requestId || presentation?.isInFlight != true) return
        operatorOperationInProgress = true
        vehicleScanCompleted = true
        val firstTimeout = operatorTimedOutRequestId != requestId &&
            scannerStateMachine.state != ScannerLifecycleState.TIMEOUT
        operatorTimedOutRequestId = requestId
        if (firstTimeout) {
            scannerPerformance.apiSlow()
        }
        moveScannerLifecycle(ScannerLifecycleEvent.TIMED_OUT)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (firstTimeout) provideFeedback(FeedbackType.TIMEOUT)
        setStatus(
            getString(R.string.scanner_operator_request_still_processing),
            ScanState.WARNING,
            showProgress = true,
        )
        updateOperatorInteractionState()
    }

    private fun cancelOperatorOperationTimeout() {
        operationTimeoutRunnable?.let { handler.removeCallbacks(it) }
        operationTimeoutRunnable = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun getVehicleStatusMessage(
        operationType: OperationType?,
        vehicleNumber: String
    ): String {
        return when (operationType) {
            OperationType.CHECK_OUT -> getString(R.string.vehicle_sheet_status_checkout_ready, vehicleNumber)
            OperationType.CHECK_IN -> getString(R.string.vehicle_sheet_status_checkin_ready, vehicleNumber)
            else -> getString(R.string.vehicle_sheet_status_ready, vehicleNumber)
        }
    }

    private fun showVehicleResultSheet(vehicleNumber: String, forceManualEntry: Boolean = false) {
        if (isFinishing) return
        stopScanLineAnimation()
        vehicleResultSheet?.dismiss()

        val sheet = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        sheet.setContentView(R.layout.bottom_sheet_vehicle_scan_success)
        val view = requireNotNull(
            sheet.findViewById<View>(R.id.bottom_sheet_vehicle_scan_success_root)
        )

        val ui = VehicleSheetUi(
            plateLabel = view.findViewById(R.id.tv_sheet_plate),
            statusText = view.findViewById(R.id.tv_sheet_status),
            progress = view.findViewById(R.id.sheet_progress),
            scanAgainButton = view.findViewById(R.id.btn_sheet_scan_again),
            manualToggleButton = view.findViewById(R.id.btn_show_manual_entry),
            manualEntryContainer = view.findViewById(R.id.manual_entry_container),
            manualInputLayout = view.findViewById(R.id.manual_input_container),
            manualInput = view.findViewById(R.id.input_manual_plate),
            manualSubmit = view.findViewById(R.id.btn_manual_submit)
        )
        vehicleSheetUi = ui

        currentVehicleNumber = vehicleNumber
        ui.plateLabel.text = vehicleNumber
        ui.manualInput.setText(vehicleNumber)
        ui.manualInputLayout.error = null
        ui.progress.isVisible = false
        val operationType = determineOperationType()
        ui.statusText.text = getVehicleStatusMessage(operationType, vehicleNumber)
        ui.manualEntryContainer.isVisible = forceManualEntry
        ui.manualEntryContainer.setManualForced(forceManualEntry)
        ui.manualToggleButton.isVisible = !forceManualEntry
        ui.manualInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                ui.manualInputLayout.error = null
            }
        }

        if (!forceManualEntry && operationType != null) {
            startVehicleProcessing(operationType, vehicleNumber)
        }
        ui.scanAgainButton.setOnClickListener {
            cancelAutoClose()
            vehicleResultSheet?.dismiss()
            restartVehicleScan()
        }
        ui.manualToggleButton.setOnClickListener {
            val shouldShow = !ui.manualEntryContainer.isVisible
            ui.manualEntryContainer.isVisible = shouldShow
            ui.manualEntryContainer.setManualForced(shouldShow)
            if (shouldShow) {
                cancelAutoClose()
            } else {
                currentVehicleNumber?.let { scheduleAutoClose(it) }
            }
        }
        ui.manualSubmit.setOnClickListener {
            val manualPlate = ui.manualInput.text?.toString().orEmpty()
            val normalized = normalizeVehicleNumber(manualPlate)
            if (normalized == null) {
                ui.manualInputLayout.error = VehicleNumberValidator.getError(manualPlate)
                    ?: getString(R.string.vehicle_sheet_manual_error)
            } else {
                ui.manualInputLayout.error = null
                ui.manualInput.setText(normalized)
                ui.plateLabel.text = normalized
                currentVehicleNumber = normalized
                ui.statusText.text = getVehicleStatusMessage(operationType, normalized)
                ui.manualEntryContainer.setManualForced(true)
                if (operationType != null) {
                    startVehicleProcessing(operationType, normalized)
                } else {
                    scheduleAutoClose(normalized, allowForced = true)
                }
            }
        }

        sheet.setOnShowListener {
            val bottomSheet =
                sheet.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
            sheet.behavior.isGestureInsetBottomIgnored = true
        }
        sheet.setCancelable(true)
        sheet.setOnDismissListener {
            vehicleSheetUi = null
            vehicleResultSheet = null
            cancelAutoClose()
        }
        sheet.show()
        vehicleResultSheet = sheet
    }

    private fun scheduleAutoClose(plate: String, allowForced: Boolean = false) {
        val ui = vehicleSheetUi
        if (isVehicleScan()) {
            if (!allowForced && ui?.manualEntryContainer?.isManualForced() == true) return
        } else if (ui != null && !allowForced && ui.manualEntryContainer.isManualForced()) {
            return
        }
        cancelAutoClose()
        val runnable = Runnable {
            autoCloseRunnable = null
            if (isVehicleScan()) {
                vehicleResultSheet?.dismiss()
                restartVehicleScan()
            } else {
                handleScanResult(plate)
            }
        }
        autoCloseRunnable = runnable
        handler.postDelayed(runnable, autoCloseDelayMs)
    }

    private fun cancelAutoClose() {
        autoCloseRunnable?.let { handler.removeCallbacks(it) }
        autoCloseRunnable = null
    }

    private fun closeScanner() {
        releaseSpeechRecognizer(destroyRecognizer = true)
        cancelAutoClose()
        cancelVehicleScanResume()
        cancelOperatorOperationTimeout()
        vehicleResultSheet?.dismiss()
        manualEntrySheet?.dismiss()
        uncertainPlateDialog?.dismiss()
        scannerSpotSelectionDialog?.dismiss()
        if (!operatorViewModel.hasActiveOperation()) {
            acknowledgePresentedOperatorState()
        }
        animatePreviewGlassEffect(enabled = false, immediate = true)
        if (isVehicleScan()) {
            stopVehicleScanner()
            stopOperatorQrCameraScanner()
            if (::barcodeView.isInitialized) {
                barcodeView.pause()
            }
            stopScanLineAnimation()
            setResult(RESULT_CANCELED, createScannerResultIntent())
        } else if (::barcodeView.isInitialized) {
            barcodeView.pause()
        }
        finish()
    }

    private fun updateScannerSpotPill(displayName: String?) {
        if (displayName.isNullOrBlank()) {
            spotSelectorLabel.text = getString(R.string.op_spot_id_hint)
            spotSelectorLabel.setTextColor(ContextCompat.getColor(this, R.color.scanner_text_secondary))
        } else {
            spotSelectorLabel.text = displayName
            spotSelectorLabel.setTextColor(ContextCompat.getColor(this, R.color.scanner_text_primary))
        }
    }

    private fun switchOperationType(operationType: OperationType, sourceView: View) {
        if (!isVehicleScan() || selectedOperationType == operationType) return
        if (operatorOperationInProgress || vehicleScanCompleted) return

        selectedOperationType = operationType
        scannerPerformance.operationChanged(scannerMetricOperation())
        updatePerformanceUiState()
        sourceView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        animateOperationTogglePress(sourceView)
        updateOperationToggleUi()
    }

    private fun switchScannerInputMode(inputMode: ScannerInputMode, sourceView: View) {
        if (!isVehicleScan() || selectedScannerInputMode == inputMode) return
        if (operatorOperationInProgress || vehicleScanCompleted || voiceRecognitionInProgress || voiceListeningInProgress) return
        if (!isAutomaticRecognitionEnabled()) {
            sourceView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            renderAutomaticRecognitionFallback()
            return
        }

        beginScannerModeSwitchTrace(inputMode)
        setScannerInputModeState(inputMode)
        scannerCameraController.invalidateScanRegion()
        currentVehicleNumber = null
        currentQrCode = null
        vehicleScanCompleted = false
        clearCandidateBuffer()
        cancelVehicleScanResume()
        cancelScanTimeout()
        resetCameraZoom()
        resetScannerQualityState()

        if (manualEntryInput.hasFocus()) {
            hideKeyboard(manualEntryInput)
            manualEntryInput.clearFocus()
        }

        sourceView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        animateOperationTogglePress(sourceView)

        if (inputMode == ScannerInputMode.QR) {
            stopVehicleScanner(releaseCamera = false)
            stopQrScanner()
            stopScanLineAnimation()
            resetInlineManualEntryForm()
        } else {
            stopOperatorQrCameraScanner(releaseCamera = false)
            stopQrScanner()
        }

        configureScannerUi()
        rootView.post { updateScannerLayoutForScreen() }
        if (checkCameraPermission()) {
            if (inputMode == ScannerInputMode.QR) {
                resumeOperatorQrScanning()
            } else {
                resumeLiveVehicleScanning()
            }
        }
    }

    private fun updatePerformanceUiState() {
        performanceMetricsStateHolder?.state?.apply {
            putState("scanner_mode", scannerMetricMode())
            putState("scanner_operation", scannerMetricOperation())
            putState("scanner_lifecycle", scannerStateMachine.state.name.lowercase(Locale.ROOT))
        }
    }

    private fun beginScannerPreviewTrace() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || scannerPreviewTraceOpen) return
        Trace.beginAsyncSection(SCANNER_PREVIEW_READY_TRACE, scannerTraceCookie)
        scannerPreviewTraceOpen = true
    }

    private fun endScannerPreviewTrace() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !scannerPreviewTraceOpen) return
        Trace.endAsyncSection(SCANNER_PREVIEW_READY_TRACE, scannerTraceCookie)
        scannerPreviewTraceOpen = false
    }

    private fun beginScannerModeSwitchTrace(mode: ScannerInputMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        synchronized(scannerTraceLock) {
            closeScannerModeSwitchTraceLocked()
            scannerModeSwitchTraceSequence++
            scannerModeSwitchTraceCookie = scannerTraceCookie xor scannerModeSwitchTraceSequence
            scannerModeSwitchTraceExpectedMode = mode
            scannerModeSwitchTraceLayoutReady = false
            scannerModeSwitchTraceFrameReady = false
            Trace.beginAsyncSection(SCANNER_MODE_SWITCH_TRACE, scannerModeSwitchTraceCookie)
            scannerModeSwitchTraceOpen = true
        }
    }

    private fun markScannerModeSwitchLayoutReady(mode: ScannerInputMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        synchronized(scannerTraceLock) {
            if (!scannerModeSwitchTraceOpen || scannerModeSwitchTraceExpectedMode != mode) return
            scannerModeSwitchTraceLayoutReady = true
            completeScannerModeSwitchTraceIfReadyLocked()
        }
    }

    private fun markScannerModeSwitchFrameReady(mode: ScannerInputMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        synchronized(scannerTraceLock) {
            if (!scannerModeSwitchTraceOpen || scannerModeSwitchTraceExpectedMode != mode) return
            scannerModeSwitchTraceFrameReady = true
            completeScannerModeSwitchTraceIfReadyLocked()
        }
    }

    /** Called only after a valid ROI/input image has reached the selected ML analyzer. */
    private fun markScannerAnalyzerFrameRouted(mode: ScannerInputMode) {
        markScannerModeSwitchFrameReady(mode)
        scannerFrameReadinessGate.analyzerFrameRouted(
            when (mode) {
                ScannerInputMode.PLATE -> ScannerFrameReadinessMode.PLATE
                ScannerInputMode.QR -> ScannerFrameReadinessMode.QR
            }
        )
        publishScannerFrameReadiness()
    }

    private fun publishScannerFrameReadiness() {
        if (!::scannerFrameReadinessGate.isInitialized ||
            !::scannerBenchmarkReadinessMarker.isInitialized
        ) {
            return
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scannerBenchmarkReadinessMarker.post(::publishScannerFrameReadiness)
            return
        }
        val state = scannerFrameReadinessGate.snapshot()
        if (state.isReady) {
            endScannerPreviewTrace()
            markScannerModeSwitchReadinessReady(state.selectedMode)
        }
        if (!BuildConfig.SCANNER_BENCHMARK_MARKERS_ENABLED) return
        scannerBenchmarkReadinessMarker.contentDescription = when (state.selectedMode) {
            ScannerFrameReadinessMode.PLATE -> if (state.isReady) {
                BENCHMARK_READY_PLATE
            } else {
                BENCHMARK_WAITING_PLATE
            }
            ScannerFrameReadinessMode.QR -> if (state.isReady) {
                BENCHMARK_READY_QR
            } else {
                BENCHMARK_WAITING_QR
            }
        }
    }

    private fun completeScannerModeSwitchTraceIfReadyLocked() {
        if (scannerModeSwitchTraceLayoutReady &&
            scannerModeSwitchTraceFrameReady &&
            scannerFrameReadinessGate.snapshot().isReady
        ) {
            closeScannerModeSwitchTraceLocked()
        }
    }

    private fun markScannerModeSwitchReadinessReady(mode: ScannerFrameReadinessMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val inputMode = when (mode) {
            ScannerFrameReadinessMode.PLATE -> ScannerInputMode.PLATE
            ScannerFrameReadinessMode.QR -> ScannerInputMode.QR
        }
        synchronized(scannerTraceLock) {
            if (!scannerModeSwitchTraceOpen || scannerModeSwitchTraceExpectedMode != inputMode) return
            completeScannerModeSwitchTraceIfReadyLocked()
        }
    }

    private fun closeScannerModeSwitchTraceLocked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !scannerModeSwitchTraceOpen) return
        Trace.endAsyncSection(SCANNER_MODE_SWITCH_TRACE, scannerModeSwitchTraceCookie)
        scannerModeSwitchTraceOpen = false
        scannerModeSwitchTraceExpectedMode = null
        scannerModeSwitchTraceLayoutReady = false
        scannerModeSwitchTraceFrameReady = false
    }

    private fun closeScannerTraces() {
        endScannerPreviewTrace()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        synchronized(scannerTraceLock) {
            closeScannerModeSwitchTraceLocked()
        }
    }

    private fun animateOperationTogglePress(view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(90)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(110)
                    .start()
            }
            .start()
    }

    private fun configureSegmentAccessibility(segment: View, label: TextView) {
        segment.contentDescription = label.text
        ViewCompat.setAccessibilityDelegate(segment, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.RadioButton::class.java.name
                info.isCheckable = true
                info.isChecked = host.isSelected
            }
        })
    }

    private fun updateSegmentSelection(segment: View, label: TextView, selected: Boolean) {
        segment.isSelected = selected
        segment.isActivated = selected
        // Refresh after locale/configuration changes so the full, untruncated label is spoken.
        segment.contentDescription = label.text
    }

    private fun updateOperationToggleUi(animated: Boolean = true) {
        val isCheckInSelected = selectedOperationType == OperationType.CHECK_IN
        updateSegmentSelection(checkInSegment, checkInLabel, isCheckInSelected)
        updateSegmentSelection(checkOutSegment, checkOutLabel, !isCheckInSelected)
        checkInSegment.setBackgroundResource(if (isCheckInSelected) R.drawable.bg_segment_slider else 0)
        checkOutSegment.setBackgroundResource(if (isCheckInSelected) 0 else R.drawable.bg_segment_slider)

        val selectedTextColor = ContextCompat.getColor(this, R.color.scanner_toggle_selected_text)
        val unselectedTextColor = ContextCompat.getColor(this, R.color.scanner_text_primary)
        checkInLabel.setTextColor(if (isCheckInSelected) selectedTextColor else unselectedTextColor)
        checkOutLabel.setTextColor(if (isCheckInSelected) unselectedTextColor else selectedTextColor)

        if (animated) {
            val activeSegment = if (isCheckInSelected) checkInSegment else checkOutSegment
            activeSegment.alpha = 0.92f
            activeSegment.animate().alpha(1f).setDuration(140).start()
        }

        updateManualEntryUi()
        updateOperatorInteractionState()
        if (isVehicleScan() && !vehicleScanCompleted && !operatorOperationInProgress) {
            if (isAutomaticRecognitionEnabled()) {
                setStatus(
                    getIdleScanMessage(),
                    ScanState.SCANNING,
                    showProgress = vehicleScannerRunning || isOperatorQrMode()
                )
            } else {
                renderAutomaticRecognitionFallback()
            }
        }
    }

    private fun updateScannerInputToggleUi(animated: Boolean = true) {
        if (!::plateInputSegment.isInitialized || !::qrInputSegment.isInitialized) return

        val isPlateSelected = selectedScannerInputMode == ScannerInputMode.PLATE
        updateSegmentSelection(plateInputSegment, plateInputLabel, isPlateSelected)
        updateSegmentSelection(qrInputSegment, qrInputLabel, !isPlateSelected)
        plateInputSegment.setBackgroundResource(if (isPlateSelected) R.drawable.bg_segment_slider else 0)
        qrInputSegment.setBackgroundResource(if (isPlateSelected) 0 else R.drawable.bg_segment_slider)

        val selectedTextColor = ContextCompat.getColor(this, R.color.scanner_toggle_selected_text)
        val unselectedTextColor = ContextCompat.getColor(this, R.color.scanner_text_primary)
        plateInputLabel.setTextColor(if (isPlateSelected) selectedTextColor else unselectedTextColor)
        qrInputLabel.setTextColor(if (isPlateSelected) unselectedTextColor else selectedTextColor)

        if (animated) {
            val activeSegment = if (isPlateSelected) plateInputSegment else qrInputSegment
            activeSegment.alpha = 0.92f
            activeSegment.animate().alpha(1f).setDuration(140).start()
        }

        updateOperatorInteractionState()
    }

    private fun updateManualEntryUi() {
        if (!::manualEntryModeChip.isInitialized || !::manualEntrySubmitButton.isInitialized) return

        val isCheckInSelected = selectedOperationType == OperationType.CHECK_IN
        val chipBackground = if (isCheckInSelected) {
            R.drawable.status_outlined_active
        } else {
            R.drawable.status_outlined_pending
        }
        val chipTextColor = if (isCheckInSelected) {
            R.color.booking_status_active_text
        } else {
            R.color.booking_status_pending_text
        }
        val chipText = getString(
            if (isCheckInSelected) R.string.op_check_in else R.string.op_check_out
        ).uppercase(Locale.ROOT)
        val submitText = getString(
            if (isCheckInSelected) R.string.vehicle_sheet_check_in else R.string.vehicle_sheet_check_out
        )

        manualEntryModeChip.setBackgroundResource(chipBackground)
        manualEntryModeChip.setTextColor(ContextCompat.getColor(this, chipTextColor))
        manualEntryModeChip.text = chipText
        manualEntrySubmitButton.text = submitText
    }

    private fun submitInlineManualEntry() {
        val raw = manualEntryInput.text?.toString().orEmpty()
        val normalized = normalizeVehicleNumber(raw)
        if (normalized == null) {
            manualEntryInputLayout.error = VehicleNumberValidator.getError(raw)
                ?: getString(R.string.vehicle_sheet_manual_error)
            manualEntryInput.requestFocus()
            pauseScannerForManualEntry()
            return
        }

        manualEntryInputLayout.error = null
        manualEntryInput.setText(normalized)
        manualEntryInput.setSelection(normalized.length)
        manualEntryInput.clearFocus()
        hideKeyboard(manualEntryInput)
        animatePreviewGlassEffect(enabled = false)
        stopVehicleScanner(releaseCamera = false)
        stopScanLineAnimation()
        processOperatorVehicleInput(normalized)
    }

    private fun pauseScannerForManualEntry() {
        if (!isOperatorPlateMode() || vehicleScanCompleted || operatorOperationInProgress) return
        if (vehicleScannerRunning) {
            stopVehicleScanner(releaseCamera = false)
            stopScanLineAnimation()
        }
        animatePreviewGlassEffect(enabled = true)
        updateOperatorInteractionState()
    }

    private fun resumeScannerAfterManualEntry() {
        resumeLiveVehicleScanning()
    }

    private fun resetInlineManualEntryForm(clearText: Boolean = true) {
        if (!::manualEntryInput.isInitialized || !::manualEntryInputLayout.isInitialized) return
        manualEntryInputLayout.error = null
        if (clearText) {
            manualEntryInput.text?.clear()
        }
        manualEntryInput.clearFocus()
    }

    @SuppressLint("SuspiciousIndentation")
    private fun showManualEntrySheet() {
        if (!isVehicleScan() || manualEntrySheet?.isShowing == true || isFinishing || isDestroyed) return
        beginManualOperatorAttempt()
        scannerPerformance.manualFallbackOpened(
            when {
                !isAutomaticRecognitionEnabled() -> "recognition_disabled"
                cameraManualFallbackReason == CameraManualFallbackReason.PERMISSION_DENIED ->
                    "camera_permission_denied"
                cameraManualFallbackReason == CameraManualFallbackReason.UNAVAILABLE ->
                    "camera_unavailable"
                detectorRuntimeFallbackMode != null -> "detector_runtime_failure"
                ScannerViewportStatePolicy.isUnsupported(
                    scannerViewportLayoutKnown,
                    scannerViewportSupportsLiveScanning,
                ) -> "small_viewport"
                scannerReadinessController.state == ScannerReadinessState.UNAVAILABLE ->
                    "model_unavailable"
                else -> "operator_action"
            },
        )
        clearDetectorRuntimeFallback()
        cancelVehicleScanResume()
        cancelScanTimeout()
        val wasPlateScanning = vehicleScannerRunning
        val wasQrMode = isOperatorQrMode()
        if (wasPlateScanning) {
            stopVehicleScanner(releaseCamera = false)
            stopScanLineAnimation()
        }
        if (wasQrMode) {
            stopOperatorQrCameraScanner(releaseCamera = false)
            if (::barcodeView.isInitialized) {
                barcodeView.pause()
            }
        }

        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        manualEntrySheet = dialog
        dialog.setContentView(R.layout.bottom_sheet_scanner_manual_entry)
        val view = requireNotNull(
            dialog.findViewById<View>(R.id.bottom_sheet_scanner_manual_entry_root)
        )
        val inputLayout = view.findViewById<TextInputLayout>(R.id.layout_manual_vehicle_number)
        val input = view.findViewById<TextInputEditText>(R.id.input_manual_vehicle_number)
        val submit = view.findViewById<MaterialButton>(R.id.btn_manual_vehicle_submit)
        val cancel = view.findViewById<MaterialButton>(R.id.btn_manual_vehicle_cancel)

        currentVehicleNumber?.takeIf { it.isNotBlank() }?.let {
            input.setText(it)
            input.setSelection(it.length)
        }
        submit.text = getString(
            if (selectedOperationType == OperationType.CHECK_IN) {
                R.string.vehicle_sheet_check_in
            } else {
                R.string.vehicle_sheet_check_out
            }
        )
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) inputLayout.error = null
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.performClick()
                true
            } else {
                false
            }
        }
        submit.setOnClickListener {
            val raw = input.text?.toString().orEmpty()
            val normalized = normalizeVehicleNumber(raw)
            if (normalized == null) {
                inputLayout.error = VehicleNumberValidator.getError(raw)
                    ?: getString(R.string.vehicle_sheet_manual_error)
                input.requestFocus()
                return@setOnClickListener
            }
            inputLayout.error = null
            input.setText(normalized)
            input.setSelection(normalized.length)
            hideKeyboard(input)
            animatePreviewGlassEffect(enabled = false)
            stopVehicleScanner(releaseCamera = false)
            stopOperatorQrCameraScanner(releaseCamera = false)
            stopScanLineAnimation()
            if (::barcodeView.isInitialized) {
                barcodeView.pause()
            }
            processOperatorVehicleInput(normalized)
            dialog.dismiss()
        }
        cancel.setOnClickListener { dialog.dismiss() }

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
            dialog.behavior.isGestureInsetBottomIgnored = true
            input.requestFocus()
            input.post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.setOnDismissListener {
            manualEntrySheet = null
            hideKeyboard(input)
            resumeScannerAfterBlockingInteraction()
            updateOperatorInteractionState()
        }
        dialog.show()
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun showScannerSpotSelectionSheet() {
        if (scannerSpotSelectionDialog?.isShowing == true || isFinishing || isDestroyed) return

        val wasScanning = vehicleScannerRunning
        val wasQrScanning = isOperatorQrMode()
        if (wasScanning) {
            stopVehicleScanner(releaseCamera = false)
            stopScanLineAnimation()
        }
        if (wasQrScanning) {
            stopOperatorQrCameraScanner(releaseCamera = false)
            if (::barcodeView.isInitialized) {
                barcodeView.pause()
            }
        }
        animatePreviewGlassEffect(enabled = false)
        updateOperatorInteractionState()

        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        scannerSpotSelectionDialog = dialog

        val sheetBinding = BottomSheetOperatorSpotSelectionBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                params?.height = ViewGroup.LayoutParams.MATCH_PARENT
                sheet.layoutParams = params
                sheet.minimumHeight = resources.displayMetrics.heightPixels
                sheet.requestLayout()
            }
            dialog.behavior.peekHeight = resources.displayMetrics.heightPixels
            dialog.behavior.isGestureInsetBottomIgnored = true
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheet?.post {
                dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialog.window?.let { window ->
                    BlurViewHelper.applyWindowBlur(
                        window,
                        backgroundBlurRadius = 56,
                        blurBehindRadius = 18,
                        dimAmount = 0.12f
                    )
                }
            }
        }
        dialog.setOnDismissListener {
            scannerSpotSelectionDialog = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialog.window?.let(BlurViewHelper::clearWindowBlur)
            }
            // Re-evaluate current state instead of stale pre-dialog booleans. A remote switch may
            // have enabled/disabled recognition while this modal owned the foreground.
            resumeScannerAfterBlockingInteraction()
            updateOperatorInteractionState()
        }

        sheetBinding.btnClose.setOnClickListener { dialog.dismiss() }

        val adapter = com.gridee.parking.ui.operator.OperatorGroupedSpotAdapter(
            onSpotSelected = spotSelected@{ spot ->
                val assignedLotId = normalizeLotId(AuthSession.getParkingLotId(this))
                val spotLotId = normalizeLotId(spot.lotId)
                if (assignedLotId == null || spotLotId != assignedLotId) {
                    Toast.makeText(
                        this,
                        R.string.scanner_spot_outside_assigned_lot,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@spotSelected
                }
                val displayName = getSpotDisplayName(spot)
                operatorParkingSpotId = spot.id
                operatorParkingSpotName = displayName
                operatorParkingLotId = assignedLotId
                updateScannerSpotPill(displayName)
                dialog.dismiss()
            },
        )

        sheetBinding.rvSpots.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvSpots.adapter = adapter
        if (!operatorParkingSpotId.isNullOrBlank()) {
            adapter.setSelectedSpot(operatorParkingSpotId, currentOperatorParkingLotId())
        }

        sheetBinding.progressBar.visibility = View.VISIBLE
        sheetBinding.tvEmptyState.text = getString(R.string.op_select_spot_loading)
        sheetBinding.tvEmptyState.visibility = View.VISIBLE
        sheetBinding.rvSpots.visibility = View.GONE

        dialog.show()

        lifecycleScope.launch {
            val result = loadScannerParkingSpots()
            if (scannerSpotSelectionDialog !== dialog) return@launch
            val spots = result.spots
            sheetBinding.progressBar.visibility = View.GONE
            if (spots.isEmpty()) {
                sheetBinding.tvEmptyState.text = result.emptyMessage
                    ?: getString(R.string.op_select_spot_empty)
                sheetBinding.tvEmptyState.visibility = View.VISIBLE
                sheetBinding.rvSpots.visibility = View.GONE
                sheetBinding.tvEmptyState.isClickable = result.retryable
                sheetBinding.tvEmptyState.setOnClickListener(
                    if (result.retryable) {
                        View.OnClickListener {
                            dialog.dismiss()
                            rootView.post { showScannerSpotSelectionSheet() }
                        }
                    } else {
                        null
                    }
                )
            } else {
                sheetBinding.tvEmptyState.visibility = View.GONE
                sheetBinding.rvSpots.visibility = View.VISIBLE
                adapter.submitSpots(spots) {
                    sheetBinding.rvSpots.requestLayout()
                }
                if (!operatorParkingSpotId.isNullOrBlank()) {
                    adapter.setSelectedSpot(operatorParkingSpotId, currentOperatorParkingLotId())
                }
            }
        }
    }

    private suspend fun loadScannerParkingSpots(): OperatorParkingSpotLoader.LoadResult {
        return OperatorParkingSpotLoader.load(this, parkingRepository)
    }

    private fun currentOperatorParkingLotId(): String? {
        // The authenticated assignment is authoritative; an Intent or selected spot may not
        // switch the operator into another lot context.
        return normalizeLotId(AuthSession.getParkingLotId(this))
    }

    private fun normalizeLotId(raw: String?): String? {
        return ScannerSpotStatePolicy.normalizeId(raw)
    }

    private fun getSpotDisplayName(spot: ParkingSpot): String {
        return OperatorParkingSpotLoader.getSpotDisplayName(spot)
    }

    override fun onDestroy() {
        jankStats?.isTrackingEnabled = false
        closeScannerTraces()
        super.onDestroy()
        if (::scannerPerformance.isInitialized) scannerPerformance.close()
        if (::scannerUiRenderer.isInitialized) scannerUiRenderer.close()
        previewGlassAnimator?.cancel()
        previewGlassAnimator = null
        animatePreviewGlassEffect(enabled = false, immediate = true)
        releaseSpeechRecognizer(destroyRecognizer = true)
        stopVehicleScanner()
        stopOperatorQrCameraScanner()
        if (::scannerCameraController.isInitialized) releaseScannerCameraSession()
        if (::barcodeView.isInitialized) {
            barcodeView.pause()
        }
        vehiclePlateAnalyzer.close()
        operatorQrAnalyzer.close()
        cameraExecutor.shutdown()
        cancelScanTimeout()
        setTorch(false)
        toneGenerator?.release()
        cancelAutoClose()
        cancelVehicleScanResume()
        cancelOperatorOperationTimeout()
        vehicleResultSheet?.dismiss()
        manualEntrySheet?.dismiss()
        uncertainPlateDialog?.dismiss()
        scannerSpotSelectionDialog?.dismiss()
    }

    @SuppressLint("SuspiciousIndentation")
    private fun animatePreviewGlassEffect(enabled: Boolean, immediate: Boolean = false) {
        if (!isOperatorPlateMode() || !::vehiclePreview.isInitialized) return

        val targetProgress = if (enabled) 1f else 0f
        previewGlassAnimator?.cancel()

        if (immediate || abs(previewGlassProgress - targetProgress) < 0.02f) {
            applyPreviewGlassEffect(targetProgress)
            return
        }

        previewGlassAnimator = ValueAnimator.ofFloat(previewGlassProgress, targetProgress).apply {
            duration = if (enabled) 180L else 150L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { animator ->
                applyPreviewGlassEffect(animator.animatedValue as Float)
            }
            start()
        }
    }

    private fun applyPreviewGlassEffect(progress: Float) {
        val safeProgress = progress.coerceIn(0f, 1f)
        previewGlassProgress = safeProgress

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (safeProgress <= 0.02f) {
                BlurViewHelper.removeBlur(vehiclePreview)
            } else {
                BlurViewHelper.applyModernBlur(
                    vehiclePreview,
                    blurRadius = 1.5f + (safeProgress * 5.5f),
                    saturation = 1f - (safeProgress * 0.05f)
                )
            }
        } else {
            vehiclePreview.alpha = 1f - (safeProgress * 0.02f)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (isVehicleScan()) {
            outState.putString(STATE_SELECTED_OPERATION_TYPE, selectedOperationType.name)
            outState.putString(STATE_SELECTED_SCANNER_INPUT_MODE, selectedScannerInputMode.name)
            outState.putString(STATE_OPERATOR_PARKING_SPOT_ID, operatorParkingSpotId)
            outState.putString(STATE_OPERATOR_PARKING_SPOT_NAME, operatorParkingSpotName)
            outState.putString(STATE_OPERATOR_PARKING_LOT_ID, operatorParkingLotId)
            if (::scannerStateMachine.isInitialized) {
                outState.putString(STATE_SCANNER_LIFECYCLE, scannerStateMachine.state.name)
            }
            lastPresentedOperatorTerminalRequestId?.let {
                outState.putLong(STATE_LAST_PRESENTED_OPERATOR_TERMINAL_REQUEST, it)
            }
            persistentOperatorErrorMessage?.let {
                outState.putString(STATE_PERSISTENT_OPERATOR_ERROR, it)
            }
            cameraManualFallbackReason?.let {
                outState.putString(STATE_CAMERA_MANUAL_FALLBACK_REASON, it.name)
            }
            detectorRuntimeFallbackMode?.let {
                outState.putString(STATE_DETECTOR_RUNTIME_FALLBACK_MODE, it.name)
            }
        }
    }
}
