package com.gridee.parking.ui.qr

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.format.DateFormat
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Surface
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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.gridee.parking.R
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.databinding.BottomSheetOperatorSpotSelectionBinding
import com.gridee.parking.ui.booking.ParkingSpotSelectionAdapter
import com.gridee.parking.ui.operator.CheckInState
import com.gridee.parking.ui.operator.OperatorParkingSpotLoader
import com.gridee.parking.ui.operator.OperatorViewModel
import com.gridee.parking.ui.utils.BlurViewHelper
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.VehicleNumberType
import com.gridee.parking.utils.VehicleNumberValidator
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class QrScannerActivity : AppCompatActivity() {

    private lateinit var rootView: FrameLayout
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

    private var lastScanTimestamp: Long = 0L
    private var scanType: String = ""
    private var selectedOperationType = OperationType.CHECK_IN
    private var selectedScannerInputMode = ScannerInputMode.PLATE
    private var vehicleScannerRunning = false
    private var operatorQrScannerRunning = false
    private var vehicleScanCompleted = false
    private var qrScannerStarted = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var cameraExecutor: ExecutorService
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
    private val scannerResultHoldMs = 3000L
    private var operationTimeoutRunnable: Runnable? = null
    private val operatorOperationTimeoutMs = 12000L
    private var scanTimeoutRunnable: Runnable? = null
    private val scanTimeoutMs = 7000L
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var vehicleResultSheet: BottomSheetDialog? = null
    private var manualEntrySheet: BottomSheetDialog? = null
    private var camera: Camera? = null
    private var vehicleScannerSessionId: Long = 0L
    private var isTorchOn = false
    private var lastAmbientLuma = 255f
    private var manualTorchOverrideUntil = 0L
    private val torchOnThreshold = 55f
    private val torchOffThreshold = 90f
    private val manualTorchOverrideDurationMs = 6000L
    private val analysisTargetResolution = Size(1280, 720)
    private val ocrCropHorizontalInsetRatio = 0.035f
    private val ocrCropVerticalInsetRatio = 0.16f
    private val regularInstantPattern = Regex("^[A-Z]{2}\\d{2}[A-Z]{1,3}\\d{4}$")
    private val ocrNoisePattern = Regex("[^A-Z0-9]")
    private val supportedPlateTemplates: List<String> = buildPlateTemplates()

    private val candidateBuffer = ArrayDeque<String>()
    private val maxCandidateBufferSize = 12
    private val minConfidenceHits = 2
    private val processedVehicleCooldownMs = 3500L
    private val recentlyProcessedVehicles = LinkedHashMap<String, Long>()
    private val voiceRecognitionLocale: Locale = Locale.forLanguageTag("en-IN")
    private val voiceAutoStopMs = 7000L
    private val voiceProcessingTimeoutMs = 2200L
    private val operatorViewModel: OperatorViewModel by viewModels()
    private val qrBarcodeScanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    private var vehicleSheetUi: VehicleSheetUi? = null
    private var currentVehicleNumber: String? = null
    private var currentQrCode: String? = null
    private var operatorParkingSpotId: String? = null
    private var operatorParkingSpotName: String? = null
    private var operatorParkingLotId: String? = null
    private var operatorOperationInProgress = false
    private val parkingRepository = ParkingRepository()
    private var scannerSpotSelectionDialog: BottomSheetDialog? = null
    private var voiceRecognitionInProgress = false
    private var voiceListeningInProgress = false
    private var pendingVoiceStartAfterPermission = false
    private var voiceStopRequested = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var voiceAutoStopRunnable: Runnable? = null
    private var voiceProcessingTimeoutRunnable: Runnable? = null
    private var pendingOperatorRequestId: Long? = null
    
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

    private data class CorrectedPlateVariant(
        val value: String,
        val correctedCharacters: Int
    )

    private data class SpeechPlateCandidate(
        val plate: String,
        val score: Int,
        val confidence: Float?,
        val heardPhrase: String
    )

    private fun buildPlateTemplates(): List<String> {
        val templates = linkedSetOf(
            "DDBHDDDDL",
            "DDBHDDDDLL",
            "TDDDDLLDDDDL",
            "TDDDDLLDDDDLL",
            "LLVALLDDDD"
        )

        for (districtDigits in 1..2) {
            for (seriesLetters in 1..3) {
                for (numberDigits in 1..4) {
                    templates += buildString {
                        append("LL")
                        append("D".repeat(districtDigits))
                        append("L".repeat(seriesLetters))
                        append("D".repeat(numberDigits))
                    }
                }
            }
        }

        return templates.toList()
    }

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
        operatorParkingSpotId = intent?.getStringExtra(EXTRA_PARKING_SPOT_ID)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        operatorParkingSpotName = intent?.getStringExtra(EXTRA_PARKING_SPOT_NAME)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        operatorParkingLotId = intent?.getStringExtra(EXTRA_PARKING_LOT_ID)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: AuthSession.getParkingLotId(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        barcodeView = findViewById(R.id.barcode_scanner)
        configureBarcodeViewChrome()
        vehiclePreview = findViewById(R.id.vehicle_preview)
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
        manualEntryCard = findViewById(R.id.card_inline_manual_entry)
        manualEntryModeChip = findViewById(R.id.tv_manual_entry_mode_chip)
        manualEntryInputLayout = findViewById(R.id.layout_inline_manual_input)
        manualEntryInput = findViewById(R.id.input_inline_manual_plate)
        manualEntrySubmitButton = findViewById(R.id.btn_manual_submit_inline)

        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

        configureScannerUi()
        observeOperatorViewModel()
        setupScannerLayout()
        flashToggle.setOnClickListener { toggleFlash() }
        closeButton.setOnClickListener { closeScanner() }
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
        resultManualEntryButton.setOnClickListener { showManualEntrySheet() }
        resultScanAgainButton.setOnClickListener {
            cancelVehicleScanResume()
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

        if (checkCameraPermission()) {
            startScanner()
        } else {
            requestCameraPermission()
        }
    }

    private fun configureScannerUi() {
        if (isVehicleScan()) {
            val plateMode = isOperatorPlateMode()
            val qrMode = isOperatorQrMode()
            barcodeView.visibility = View.GONE
            vehiclePreview.visibility = if (plateMode || qrMode) View.VISIBLE else View.GONE
            scannerContentContainer.visibility = View.VISIBLE
            scanningFrameContainer.visibility = View.VISIBLE
            overlayView.visibility = View.VISIBLE
            scanLine.visibility = if (plateMode) View.VISIBLE else View.GONE
            vehicleHint.visibility = if (plateMode) View.VISIBLE else View.GONE
            statusContainer.visibility = View.VISIBLE
            setStatus(getIdleScanMessage(), ScanState.SCANNING, showProgress = plateMode)
            if (plateMode) {
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
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            updateTopButtonInsets(closeButton, systemBars.top, systemBars.left, alignStart = true)
            updateTopButtonInsets(flashToggle, systemBars.top, systemBars.right, alignStart = false)

            (voiceEntryButton.layoutParams as? FrameLayout.LayoutParams)?.let { voiceParams ->
                voiceParams.topMargin = dp(88) + systemBars.top
                voiceParams.leftMargin = dp(20) + systemBars.left
                voiceEntryButton.layoutParams = voiceParams
            }

            // Apply top inset to centered scanner controls.
            (topControlsContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { topControlsParams ->
                topControlsParams.topMargin = dp(20) + systemBars.top
                topControlsContainer.layoutParams = topControlsParams
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
            layoutParams.leftMargin = dp(20) + sideInset
        } else {
            layoutParams.rightMargin = dp(20) + sideInset
        }
        view.layoutParams = layoutParams
    }

    private fun updateScannerLayoutForScreen() {
        if (!::scanningFrameContainer.isInitialized) return
        val screenWidth = rootView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val availableWidth = (screenWidth - scannerContentContainer.paddingLeft - scannerContentContainer.paddingRight)
            .coerceAtLeast(dp(240))
        val isQrMode = isOperatorQrMode()
        val targetWidth = if (isQrMode) {
            (availableWidth * 0.74f).roundToInt().coerceIn(dp(224), dp(320))
        } else {
            (availableWidth * 0.88f).roundToInt().coerceIn(dp(248), dp(356))
        }
        val targetHeight = if (isQrMode) {
            targetWidth
        } else {
            (targetWidth * 0.64f).roundToInt().coerceIn(dp(176), dp(228))
        }

        scanningFrameContainer.layoutParams?.let { layoutParams ->
            if (layoutParams.width != targetWidth || layoutParams.height != targetHeight) {
                layoutParams.width = targetWidth
                layoutParams.height = targetHeight
            }
            (layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.verticalBias =
                if (isQrMode) 0.5f else 0.52f
            scanningFrameContainer.layoutParams = layoutParams
        }

        if (isQrMode && ::barcodeView.isInitialized) {
            barcodeView.barcodeView.setFramingRectSize(
                com.journeyapps.barcodescanner.Size(targetWidth, targetHeight)
            )
        }

        (scanLine.layoutParams as? FrameLayout.LayoutParams)?.let { layoutParams ->
            val lineWidth = (targetWidth * 0.78f).roundToInt()
            if (layoutParams.width != lineWidth) {
                layoutParams.width = lineWidth
                scanLine.layoutParams = layoutParams
            }
        }

        vehicleHint.maxWidth = targetWidth + dp(48)
        statusContainer.minimumWidth = (targetWidth * 0.64f).roundToInt().coerceAtLeast(dp(180))

        manualFallbackButton.isVisible = isOperatorPlateMode() && !operatorOperationInProgress && !vehicleScanCompleted
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

    private fun getIdleScanMessage(): String {
        return if (isOperatorQrMode()) {
            getString(R.string.scanner_qr_scan_hint)
        } else {
            getString(R.string.vehicle_scan_detecting)
        }
    }

    private fun resetCornerTints() {
        val idleColor = ContextCompat.getColor(this, R.color.scanner_corner_idle)
        val tint = ColorStateList.valueOf(idleColor)
        ImageViewCompat.setImageTintList(cornerTopLeft, tint)
        ImageViewCompat.setImageTintList(cornerTopRight, tint)
        ImageViewCompat.setImageTintList(cornerBottomLeft, tint)
        ImageViewCompat.setImageTintList(cornerBottomRight, tint)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startScanner()
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
                        title = "Voice Input",
                        message = getString(R.string.vehicle_scan_voice_permission_required),
                        isError = true
                    )
                }
            }
        }
    }

    private fun startScanner() {
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
                    val now = System.currentTimeMillis()
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
        if (!::vehiclePreview.isInitialized) return
        vehiclePreview.visibility = View.VISIBLE
        vehiclePreview.post {
            if (isFinishing || isDestroyed) return@post
            if (!isOperatorQrMode()) return@post
            startOperatorQrCameraScanner()
        }
    }

    private fun startOperatorQrCameraScanner() {
        if (!isOperatorQrMode() ||
            operatorQrScannerRunning ||
            vehicleScanCompleted ||
            operatorOperationInProgress ||
            scannerSpotSelectionDialog?.isShowing == true ||
            !checkCameraPermission()
        ) {
            return
        }

        operatorQrScannerRunning = true
        val sessionId = ++vehicleScannerSessionId
        statusProgress.isVisible = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            if (!operatorQrScannerRunning || sessionId != vehicleScannerSessionId || isFinishing || isDestroyed) {
                return@addListener
            }
            try {
                cameraProvider = cameraProviderFuture.get()
                if (!operatorQrScannerRunning || sessionId != vehicleScannerSessionId || isFinishing || isDestroyed) {
                    cameraProvider?.unbindAll()
                    return@addListener
                }

                val targetRotation = vehiclePreview.display?.rotation ?: Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build()
                    .also { it.setSurfaceProvider(vehiclePreview.surfaceProvider) }

                val analysis = ImageAnalysis.Builder()
                    .setTargetRotation(targetRotation)
                    .setTargetResolution(analysisTargetResolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (!operatorQrScannerRunning || sessionId != vehicleScannerSessionId || !isOperatorQrMode()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    processOperatorQrFrame(imageProxy)
                }

                cameraProvider?.unbindAll()
                if (!operatorQrScannerRunning || sessionId != vehicleScannerSessionId) {
                    return@addListener
                }
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                if (!operatorQrScannerRunning || sessionId != vehicleScannerSessionId) {
                    cameraProvider?.unbindAll()
                    camera = null
                    return@addListener
                }
                updateFlashToggleVisibility(false)
                scanningFrameContainer.post { updateMeteringRegion() }
            } catch (ex: Exception) {
                operatorQrScannerRunning = false
                runOnUiThread {
                    setStatus(
                        getString(R.string.vehicle_scan_error_camera),
                        ScanState.ERROR,
                        showProgress = false
                    )
                    Toast.makeText(this, R.string.vehicle_scan_error_camera, Toast.LENGTH_SHORT).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processOperatorQrFrame(imageProxy: ImageProxy) {
        if (!isOperatorQrMode() || vehicleScanCompleted || operatorOperationInProgress) {
            imageProxy.close()
            return
        }

        maybeHandleAutoTorch(imageProxy)

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // ML Kit decodes straight from the camera frame with the correct rotation: no manual
        // crop/luminance handling, robust to angle and distance, and fast enough to feel instant.
        // STRATEGY_KEEP_ONLY_LATEST keeps just one frame in flight, so we simply close on complete.
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        qrBarcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val qrCode = barcodes.firstNotNullOfOrNull { barcode ->
                    barcode.rawValue?.takeIf { it.isNotBlank() }
                } ?: return@addOnSuccessListener
                runOnUiThread {
                    if (!isOperatorQrMode() || vehicleScanCompleted || operatorOperationInProgress) {
                        return@runOnUiThread
                    }
                    // Keep the camera preview bound; setting the in-progress flags below halts
                    // further decoding without tearing the preview to black and rebinding.
                    handleScanResult(qrCode)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun stopOperatorQrCameraScanner() {
        if (!operatorQrScannerRunning) return
        operatorQrScannerRunning = false
        vehicleScannerSessionId++
        cameraProvider?.unbindAll()
        camera = null
        setTorch(false)
        updateFlashToggleVisibility(false)
    }
    // endregion

    // region Vehicle number scanner (ML Kit Text Recognition)
    private fun startVehicleScanner() {
        if (!isOperatorPlateMode() ||
            vehicleScannerRunning ||
            vehicleScanCompleted ||
            operatorOperationInProgress ||
            voiceRecognitionInProgress ||
            scannerSpotSelectionDialog?.isShowing == true ||
            manualEntryInput.hasFocus()
        ) {
            return
        }
        vehicleScannerRunning = true
        val sessionId = ++vehicleScannerSessionId
        statusProgress.isVisible = true
        startScanLineAnimation()
        startScanTimeout()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            if (!vehicleScannerRunning || sessionId != vehicleScannerSessionId || isFinishing || isDestroyed) {
                return@addListener
            }
            try {
                cameraProvider = cameraProviderFuture.get()
                if (!vehicleScannerRunning || sessionId != vehicleScannerSessionId || isFinishing || isDestroyed) {
                    cameraProvider?.unbindAll()
                    return@addListener
                }
                val targetRotation = vehiclePreview.display?.rotation ?: Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build()
                    .also {
                    it.setSurfaceProvider(vehiclePreview.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setTargetRotation(targetRotation)
                    .setTargetResolution(analysisTargetResolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (!vehicleScannerRunning || sessionId != vehicleScannerSessionId) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    processVehicleFrame(imageProxy)
                }

                cameraProvider?.unbindAll()
                if (!vehicleScannerRunning || sessionId != vehicleScannerSessionId) {
                    return@addListener
                }
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                if (!vehicleScannerRunning || sessionId != vehicleScannerSessionId) {
                    cameraProvider?.unbindAll()
                    camera = null
                    return@addListener
                }
                updateFlashToggleVisibility(true)
                scanningFrameContainer.post { updateMeteringRegion() }
            } catch (ex: Exception) {
                vehicleScannerRunning = false
                runOnUiThread {
                    setStatus(
                        getString(R.string.vehicle_scan_error_camera),
                        ScanState.ERROR,
                        showProgress = false
                    )
                    Toast.makeText(this, R.string.vehicle_scan_error_camera, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processVehicleFrame(imageProxy: ImageProxy) {
        if (!isOperatorPlateMode() || vehicleScanCompleted || operatorOperationInProgress) {
            imageProxy.close()
            return
        }

        maybeHandleAutoTorch(imageProxy)
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        if (!cropToOverlay(imageProxy)) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        textRecognizer.process(image)
            .addOnSuccessListener { text ->
                handleOcrResult(text)
            }
            .addOnFailureListener {
                runOnUiThread {
                    setStatus(
                        getString(R.string.vehicle_scan_error_generic),
                        ScanState.ERROR
                    )
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleOcrResult(text: Text) {
        if (vehicleScanCompleted) return

        val candidate = pickBestCandidate(text)
        if (candidate != null) {
            bufferCandidate(candidate)
        } else {
            runOnUiThread {
                setStatus(
                    getString(R.string.vehicle_scan_not_clear_hint),
                    ScanState.WARNING
                )
            }
        }
    }

    private fun pickBestCandidate(text: Text): String? {
        val rawCandidates = mutableListOf<String>()
        text.textBlocks.forEach { block ->
            rawCandidates.add(block.text)
            block.lines.forEach { line ->
                rawCandidates.add(line.text)
                line.elements.forEach { element -> rawCandidates.add(element.text) }
            }
        }
        rawCandidates.add(text.text)

        val cleaned = rawCandidates
            .asSequence()
            .mapNotNull(::normalizePlate)
            .distinct()
            .toList()
        if (cleaned.isEmpty()) return null

        return cleaned.maxByOrNull { scoreCandidate(it) }
    }

    private fun normalizePlate(raw: String): String? {
        var cleaned = VehicleNumberValidator.normalize(raw)
            .replace(ocrNoisePattern, "")
        if (cleaned.startsWith("IND") && cleaned.length > 3) {
            cleaned = cleaned.removePrefix("IND")
        }
        if (cleaned.length !in 6..16) return null
        if (!cleaned.any { it.isLetter() } || !cleaned.any { it.isDigit() }) return null
        return correctPlateOcr(cleaned)?.value ?: cleaned
    }

    private fun scoreCandidate(value: String): Int {
        var score = value.length * 2
        val letters = value.count { it.isLetter() }
        val digits = value.count { it.isDigit() }
        score += letters
        score += digits
        if (value.take(2).all { it.isLetter() }) score += 4

        when (VehicleNumberValidator.parseType(value)) {
            VehicleNumberType.BH -> score += 28
            VehicleNumberType.TEMPORARY -> score += 30
            VehicleNumberType.VINTAGE -> score += 30
            VehicleNumberType.REGULAR -> score += 22
            VehicleNumberType.UNKNOWN -> score -= 6
        }

        if (regularInstantPattern.matches(value)) score += 12
        if (value.contains("BH")) score += 6
        if (value.startsWith("T")) score += 4
        if (value.contains("VA")) score += 5
        return score
    }

    private fun bufferCandidate(candidate: String) {
        if (shouldFinalizeImmediately(candidate)) {
            finalizePlate(candidate)
            return
        }

        if (candidateBuffer.size >= maxCandidateBufferSize) {
            candidateBuffer.removeFirst()
        }
        candidateBuffer.addLast(candidate)

        val counts = candidateBuffer.groupingBy { it }.eachCount()
        val maxEntry = counts.maxByOrNull { it.value }
        val best = maxEntry?.key ?: candidate
        val hits = maxEntry?.value ?: 1

        if ((hits >= minConfidenceHits && VehicleNumberValidator.isValid(best)) || shouldFinalizeImmediately(best)) {
            finalizePlate(best)
        } else {
            runOnUiThread {
                setStatus(
                    getString(R.string.vehicle_scan_best_guess, best),
                    ScanState.WARNING
                )
            }
        }
    }

    private fun finalizePlate(plate: String) {
        val normalizedPlate = normalizeVehicleNumber(plate) ?: return
        if (vehicleScanCompleted || isPlateOnCooldown(normalizedPlate)) return
        vehicleScanCompleted = true
        currentVehicleNumber = normalizedPlate
        operatorOperationInProgress = true
        clearCandidateBuffer()
        runOnUiThread {
            cancelScanTimeout()
            updateOperatorInteractionState()
            animateCornerBrackets(scaleUp = true)
            beginOperatorVehicleProcessing(normalizedPlate)
        }
    }

    private fun clearCandidateBuffer() {
        candidateBuffer.clear()
    }

    private fun cropToOverlay(imageProxy: ImageProxy): Boolean {
        val overlayRect = getOverlayRectOnPreview()?.let(::shrinkOverlayRectForOcr) ?: return false
        val rect = computeImageCropRect(imageProxy, overlayRect) ?: return false
        imageProxy.setCropRect(rect)
        return true
    }

    /**
     * Maps a preview-space overlay rectangle to a crop [Rect] in the analysis image's pixel
     * coordinates, accounting for the frame rotation. Shared by the plate OCR and QR decoders.
     */
    private fun computeImageCropRect(imageProxy: ImageProxy, overlayRect: RectF): Rect? {
        val previewWidth = vehiclePreview.width.takeIf { it > 0 } ?: return null
        val previewHeight = vehiclePreview.height.takeIf { it > 0 } ?: return null

        val normalizedLeft = (overlayRect.left / previewWidth).coerceIn(0f, 1f)
        val normalizedTop = (overlayRect.top / previewHeight).coerceIn(0f, 1f)
        val normalizedRight = (overlayRect.right / previewWidth).coerceIn(normalizedLeft, 1f)
        val normalizedBottom = (overlayRect.bottom / previewHeight).coerceIn(normalizedTop, 1f)

        val imageWidth = imageProxy.width
        val imageHeight = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees

        val topLeftPoint = mapNormalizedToImage(
            normalizedLeft,
            normalizedTop,
            rotation,
            imageWidth,
            imageHeight
        )
        val bottomRightPoint = mapNormalizedToImage(
            normalizedRight,
            normalizedBottom,
            rotation,
            imageWidth,
            imageHeight
        )

        val leftPx = minOf(topLeftPoint.x, bottomRightPoint.x).roundToInt().coerceIn(0, imageWidth - 2)
        val topPx = minOf(topLeftPoint.y, bottomRightPoint.y).roundToInt().coerceIn(0, imageHeight - 2)
        val rightPx = maxOf(topLeftPoint.x, bottomRightPoint.x).roundToInt().coerceIn(leftPx + 1, imageWidth)
        val bottomPx = maxOf(topLeftPoint.y, bottomRightPoint.y).roundToInt().coerceIn(topPx + 1, imageHeight)

        return Rect(leftPx, topPx, rightPx, bottomPx)
    }

    private fun shrinkOverlayRectForOcr(overlayRect: RectF): RectF {
        val horizontalInset = overlayRect.width() * ocrCropHorizontalInsetRatio
        val verticalInset = overlayRect.height() * ocrCropVerticalInsetRatio
        val cropped = RectF(
            overlayRect.left + horizontalInset,
            overlayRect.top + verticalInset,
            overlayRect.right - horizontalInset,
            overlayRect.bottom - verticalInset
        )

        return if (cropped.width() >= overlayRect.width() * 0.55f &&
            cropped.height() >= overlayRect.height() * 0.4f
        ) {
            cropped
        } else {
            overlayRect
        }
    }

    private fun getOverlayRectOnPreview(): RectF? {
        if (!this::vehiclePreview.isInitialized || !this::scanningFrameContainer.isInitialized) return null
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

    private fun mapNormalizedToImage(
        normalizedX: Float,
        normalizedY: Float,
        rotationDegrees: Int,
        imageWidth: Int,
        imageHeight: Int
    ): PointF {
        return when (rotationDegrees) {
            0 -> PointF(
                normalizedX * imageWidth,
                normalizedY * imageHeight
            )
            90 -> PointF(
                normalizedY * imageWidth,
                (1f - normalizedX) * imageHeight
            )
            180 -> PointF(
                (1f - normalizedX) * imageWidth,
                (1f - normalizedY) * imageHeight
            )
            270 -> PointF(
                (1f - normalizedY) * imageWidth,
                normalizedX * imageHeight
            )
            else -> PointF(
                normalizedX * imageWidth,
                normalizedY * imageHeight
            )
        }
    }
    // endregion

    private fun handleScanResult(value: String) {
        if (isOperatorQrMode()) {
            barcodeView.pause()
            processOperatorQrInput(value)
            return
        }

        if (isVehicleScan()) {
            stopVehicleScanner()
            cancelScanTimeout()
            stopScanLineAnimation()
        } else {
            barcodeView.pause()
        }

        val resultIntent = createScannerResultIntent(value)
        setResult(RESULT_QR_SCANNED, resultIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (isVehicleScan()) {
            updateScannerLayoutForScreen()
            animatePreviewGlassEffect(enabled = false, immediate = true)
            updateOperatorInteractionState()
            if (isOperatorQrMode()) {
                resumeOperatorQrScanning()
            } else {
                resumeLiveVehicleScanning()
            }
        } else if (::barcodeView.isInitialized) {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isVehicleScan()) {
            releaseSpeechRecognizer(destroyRecognizer = false)
            stopVehicleScanner()
            stopOperatorQrCameraScanner()
            if (::barcodeView.isInitialized) {
                barcodeView.pause()
            }
            stopScanLineAnimation()
            animatePreviewGlassEffect(enabled = false, immediate = true)
        } else if (::barcodeView.isInitialized) {
            barcodeView.pause()
        }
    }

    private fun stopVehicleScanner() {
        if (!vehicleScannerRunning) return
        vehicleScannerRunning = false
        vehicleScannerSessionId++
        cameraProvider?.unbindAll()
        camera = null
        cancelScanTimeout()
        clearCandidateBuffer()
        setTorch(false)
        updateFlashToggleVisibility(false)
    }

    private fun updateOperatorInteractionState() {
        if (!isVehicleScan() || !::voiceEntryButton.isInitialized) return

        val canInteract = !operatorOperationInProgress &&
            !vehicleScanCompleted &&
            scannerSpotSelectionDialog?.isShowing != true
        val manualActive = ::manualEntryInput.isInitialized && manualEntryInput.hasFocus()
        val voiceActive = voiceRecognitionInProgress || voiceListeningInProgress
        val canUseManual = canInteract && !voiceActive
        val canUseToggle = canInteract && !voiceActive && !manualActive
        val canUseSpotPicker = canInteract && !voiceActive && !manualActive

        manualEntryInput.isEnabled = canUseManual
        manualEntryInputLayout.isEnabled = canUseManual
        manualEntrySubmitButton.isEnabled = canUseManual
        manualFallbackButton.isEnabled = canUseManual
        resultManualEntryButton.isEnabled = !operatorOperationInProgress
        resultScanAgainButton.isEnabled = !operatorOperationInProgress
        checkInSegment.isEnabled = canUseToggle
        checkOutSegment.isEnabled = canUseToggle
        plateInputSegment.isEnabled = canUseToggle
        qrInputSegment.isEnabled = canUseToggle
        spotSelectorPill.isEnabled = canUseSpotPicker

        manualEntryCard.alpha = if (canUseManual || manualActive) 1f else 0.72f
        operationToggleContainer.alpha = if (canUseToggle) 1f else 0.72f
        scannerInputToggleContainer.alpha = if (canUseToggle) 1f else 0.72f
        spotSelectorPill.alpha = if (canUseSpotPicker) 1f else 0.72f
        updateVoiceButtonUi()
    }

    private fun resumeLiveVehicleScanning() {
        if (!isOperatorPlateMode() ||
            vehicleScanCompleted ||
            operatorOperationInProgress ||
            voiceRecognitionInProgress ||
            scannerSpotSelectionDialog?.isShowing == true ||
            manualEntryInput.hasFocus() ||
            !checkCameraPermission()
        ) {
            return
        }

        currentVehicleNumber = null
        clearCandidateBuffer()
        stopOperatorQrCameraScanner()
        animatePreviewGlassEffect(enabled = false)
        setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
        startVehicleScanner()
        updateOperatorInteractionState()
    }

    private fun resumeOperatorQrScanning() {
        if (!isOperatorQrMode() ||
            operatorOperationInProgress ||
            scannerSpotSelectionDialog?.isShowing == true ||
            !checkCameraPermission()
        ) {
            return
        }

        currentVehicleNumber = null
        currentQrCode = null
        vehicleScanCompleted = false
        clearCandidateBuffer()
        animatePreviewGlassEffect(enabled = false)
        setStatus(getIdleScanMessage(), ScanState.SCANNING, showProgress = true)
        // If the preview is still bound (the common scan -> result -> resume cycle) just nudge
        // focus and let the analyzer pick back up; otherwise bind the camera fresh.
        if (operatorQrScannerRunning && camera != null) {
            scanningFrameContainer.post { updateMeteringRegion() }
        } else {
            startOperatorQrCameraScannerWhenReady()
        }
        updateOperatorInteractionState()
    }

    private fun processOperatorQrInput(rawQrCode: String) {
        val qrCode = rawQrCode.trim()
        if (qrCode.isBlank()) {
            currentQrCode = null
            operatorOperationInProgress = false
            vehicleScanCompleted = false
            updateOperatorInteractionState()
            setStatus(getString(R.string.scanner_qr_required), ScanState.ERROR, showProgress = false)
            provideFeedback(FeedbackType.ERROR)
            scheduleOperatorScanResume(delayMs = scannerResultHoldMs)
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
                title = "Scanner Error",
                message = "Unsupported operator scan mode",
                isError = true
            )
            scheduleOperatorScanResume(delayMs = scannerResultHoldMs)
            return
        }

        val requestId = SystemClock.elapsedRealtimeNanos()
        pendingOperatorRequestId = requestId
        setStatus(getQrProcessingMessage(operationType), ScanState.SCANNING, showProgress = true)
        startOperatorOperationTimeout(operationType, requestId)
        performQrOperation(operationType, qrCode, requestId)
    }

    private fun beginOperatorVehicleProcessing(vehicleNumber: String) {
        val normalizedVehicle = normalizeVehicleNumber(vehicleNumber)
        if (normalizedVehicle == null) {
            operatorOperationInProgress = false
            updateOperatorInteractionState()
            setStatus(getString(R.string.vehicle_sheet_manual_error), ScanState.ERROR, showProgress = false)
            showScannerNotification(
                title = "Scanner Error",
                message = getString(R.string.vehicle_sheet_manual_error),
                isError = true
            )
            scheduleVehicleScanResume(delayMs = scannerResultHoldMs)
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
                title = "Scanner Error",
                message = "Unsupported operator scan mode",
                isError = true
            )
            scheduleVehicleScanResume(delayMs = scannerResultHoldMs)
            return
        }
        val requestId = SystemClock.elapsedRealtimeNanos()
        pendingOperatorRequestId = requestId
        setStatus(getProcessingMessage(operationType), ScanState.SCANNING, showProgress = true)
        startOperatorOperationTimeout(operationType, requestId)
        performOperation(operationType, requestId)
    }

    private fun processOperatorVehicleInput(vehicleNumber: String) {
        val normalizedVehicle = normalizeVehicleNumber(vehicleNumber)
        if (normalizedVehicle == null) {
            currentVehicleNumber = null
            updateOperatorInteractionState()
            resumeLiveVehicleScanning()
            return
        }

        vehicleScanCompleted = true
        currentVehicleNumber = normalizedVehicle
        operatorOperationInProgress = true
        clearCandidateBuffer()
        cancelScanTimeout()
        updateOperatorInteractionState()
        beginOperatorVehicleProcessing(normalizedVehicle)
    }

    private fun startScanLineAnimation() {
        if (!isOperatorPlateMode()) return
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
        
        // Smooth color transition
        val corners = listOf(cornerTopLeft, cornerTopRight, cornerBottomLeft, cornerBottomRight)
        corners.forEach { corner ->
            val startColor = ImageViewCompat.getImageTintList(corner)?.defaultColor
                ?: ContextCompat.getColor(this, R.color.scanner_corner_idle)
            ValueAnimator.ofArgb(startColor, targetColor).apply {
                duration = 400
                addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    ImageViewCompat.setImageTintList(corner, ColorStateList.valueOf(color))
                }
                start()
            }
        }
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
            idleState -> idleMessage
            else -> normalizeNotificationMessage(message)
        }

        renderScannerPanel(
            mode = panelMode,
            operationType = selectedOperationType,
            title = title,
            subtitle = subtitle,
            meta = getSpotMetaText(),
            showProgress = showProgress && panelMode == ScannerPanelMode.PROCESSING
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
        statusContainer.setBackgroundResource(
            when (mode) {
                ScannerPanelMode.SUCCESS -> R.drawable.bg_scanner_panel_success
                ScannerPanelMode.ERROR -> R.drawable.bg_scanner_panel_error
                ScannerPanelMode.WARNING -> R.drawable.bg_scanner_panel_warning
                ScannerPanelMode.SCANNING,
                ScannerPanelMode.PROCESSING -> R.drawable.bg_scanner_panel_neutral
            }
        )
        statusIconContainer.setBackgroundResource(
            when (mode) {
                ScannerPanelMode.SUCCESS -> R.drawable.bg_scanner_status_icon_success
                ScannerPanelMode.ERROR -> R.drawable.bg_scanner_status_icon_error
                else -> R.drawable.bg_scanner_status_icon_neutral
            }
        )
        statusIcon.setImageResource(
            when (mode) {
                ScannerPanelMode.SUCCESS -> R.drawable.ic_check
                ScannerPanelMode.ERROR -> R.drawable.ic_cancel
                ScannerPanelMode.WARNING -> R.drawable.ic_manual_entry
                ScannerPanelMode.PROCESSING -> R.drawable.ic_scanner_filled
                ScannerPanelMode.SCANNING -> if (isOperatorQrMode()) R.drawable.ic_qr_code else R.drawable.ic_car_compact
            }
        )
        statusIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (mode == ScannerPanelMode.SUCCESS || mode == ScannerPanelMode.ERROR) {
                    R.color.scanner_primary
                } else {
                    R.color.scanner_text_primary
                }
            )
        )
        statusBadge.setBackgroundResource(badgeBackgroundRes)
        statusBadge.setTextColor(ContextCompat.getColor(this, badgeTextColorRes))
        statusBadge.text = badgeText.uppercase(Locale.ROOT)
        statusTitle.text = title.ifBlank { getString(R.string.scanner_status_ready_title) }
        statusText.text = normalizeNotificationMessage(subtitle).ifBlank { getString(R.string.vehicle_scan_hint) }
        statusMeta.text = meta.orEmpty()
        statusMeta.isVisible = !meta.isNullOrBlank()
        statusProgress.isVisible = showProgress
        statusProgress.indeterminateTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, progressTintRes)
        )
        resultActionsContainer.isVisible = mode == ScannerPanelMode.ERROR
        manualFallbackButton.isVisible = mode == ScannerPanelMode.SCANNING || mode == ScannerPanelMode.WARNING
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
            if (!vehicleScanCompleted) {
                provideFeedback(FeedbackType.TIMEOUT)
                animateCornerGlow(ScanState.WARNING)
                stopVehicleScanner()
                stopScanLineAnimation()
                // Silently reset and restart scanning without showing a bottom sheet
                vehicleScanCompleted = false
                setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
                startVehicleScanner()
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

    private fun maybeHandleAutoTorch(imageProxy: ImageProxy) {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return
        if (System.currentTimeMillis() < manualTorchOverrideUntil) return

        val luma = measureLuma(imageProxy) ?: return
        lastAmbientLuma = (lastAmbientLuma * 0.7f) + (luma * 0.3f)

        if (!isTorchOn && lastAmbientLuma < torchOnThreshold) {
            setTorch(true)
        } else if (isTorchOn && lastAmbientLuma > torchOffThreshold) {
            setTorch(false)
        }
    }

    private fun measureLuma(imageProxy: ImageProxy): Float? {
        val yPlane = imageProxy.planes.firstOrNull() ?: return null
        val buffer = yPlane.buffer
        val remaining = buffer.remaining()
        if (remaining <= 0) return null

        val sampleCount = 1500.coerceAtMost(remaining)
        val step = (remaining / sampleCount).coerceAtLeast(1)
        var sum = 0L
        var count = 0
        var index = 0
        while (index < remaining) {
            sum += buffer.get(index).toInt() and 0xFF
            count++
            index += step
        }
        return if (count > 0) sum.toFloat() / count else null
    }

    private fun toggleFlash() {
        if (!isOperatorPlateMode()) return
        if (camera?.cameraInfo?.hasFlashUnit() != true) {
            Toast.makeText(this, R.string.vehicle_scan_flash_off, Toast.LENGTH_SHORT).show()
            return
        }
        manualTorchOverrideUntil = System.currentTimeMillis() + manualTorchOverrideDurationMs
        setTorch(!isTorchOn)
    }

    private fun setTorch(enabled: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { setTorch(enabled) }
            return
        }
        if (camera?.cameraInfo?.hasFlashUnit() != true) {
            isTorchOn = false
            updateFlashToggleIcon()
            return
        }
        isTorchOn = enabled
        camera?.cameraControl?.enableTorch(enabled)
        updateFlashToggleIcon()
    }

    private fun updateFlashToggleVisibility(show: Boolean) {
        if (!this::flashToggle.isInitialized) return
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
        if (show && hasFlash && isOperatorPlateMode()) {
            if (flashToggle.visibility != View.VISIBLE) {
                // Smooth fade in animation
                flashToggle.alpha = 0f
                flashToggle.visibility = View.VISIBLE
                flashToggle.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(DecelerateInterpolator(2f))
                    .start()
            }
            flashToggle.isEnabled = true
            updateFlashToggleIcon()
        } else {
            if (flashToggle.visibility == View.VISIBLE) {
                // Smooth fade out animation
                flashToggle.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        flashToggle.visibility = View.GONE
                    }
                    .start()
            }
            flashToggle.isEnabled = false
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

    private fun updateMeteringRegion() {
        val cam = camera ?: return
        if (vehiclePreview.width == 0 || vehiclePreview.height == 0) return

        val overlayRect = getOverlayRectOnPreview() ?: return
        val factory = SurfaceOrientedMeteringPointFactory(
            vehiclePreview.width.toFloat(),
            vehiclePreview.height.toFloat()
        )

        val centerX = overlayRect.centerX().coerceAtLeast(0f)
        val centerY = overlayRect.centerY().coerceAtLeast(0f)
        val point = factory.createPoint(centerX, centerY)

        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        cam.cameraControl.startFocusAndMetering(action)
    }

    private fun correctPlateOcr(raw: String): CorrectedPlateVariant? {
        if (VehicleNumberValidator.isValid(raw)) {
            return CorrectedPlateVariant(raw, correctedCharacters = 0)
        }

        val candidates = linkedSetOf<CorrectedPlateVariant>()
        supportedPlateTemplates
            .asSequence()
            .filter { it.length == raw.length }
            .forEach { template ->
                coerceToTemplate(raw, template)?.let(candidates::add)
            }

        return candidates
            .filter { VehicleNumberValidator.isValid(it.value) }
            .maxWithOrNull(
                compareBy<CorrectedPlateVariant> { scoreCandidate(it.value) - (it.correctedCharacters * 2) }
                    .thenByDescending { it.value.length }
            )
    }

    private fun coerceToTemplate(source: String, template: String): CorrectedPlateVariant? {
        if (source.length != template.length) return null

        val builder = StringBuilder(template.length)
        var corrections = 0

        for (index in template.indices) {
            val sourceChar = source[index]
            val templateChar = template[index]
            val correctedChar = when (templateChar) {
                'L' -> coerceToLetter(sourceChar)
                'D' -> coerceToDigit(sourceChar)
                else -> coerceToLiteral(sourceChar, templateChar)
            } ?: return null

            if (correctedChar != sourceChar) corrections++
            builder.append(correctedChar)
        }

        return CorrectedPlateVariant(builder.toString(), corrections)
    }

    private fun coerceToDigit(char: Char): Char? {
        val upper = char.uppercaseChar()
        return when {
            upper.isDigit() -> upper
            upper == 'O' || upper == 'Q' -> '0'
            upper == 'I' || upper == 'L' -> '1'
            upper == 'Z' -> '2'
            upper == 'S' -> '5'
            upper == 'G' -> '6'
            upper == 'B' -> '8'
            else -> null
        }
    }

    private fun coerceToLetter(char: Char): Char? {
        val upper = char.uppercaseChar()
        return when {
            upper in 'A'..'Z' -> upper
            upper == '0' -> 'O'
            upper == '1' -> 'I'
            upper == '2' -> 'Z'
            upper == '4' -> 'A'
            upper == '5' -> 'S'
            upper == '6' -> 'G'
            upper == '7' -> 'T'
            upper == '8' -> 'B'
            else -> null
        }
    }

    private fun coerceToLiteral(sourceChar: Char, literal: Char): Char? {
        if (sourceChar.uppercaseChar() == literal) return literal

        return when {
            literal.isDigit() -> coerceToDigit(sourceChar)?.takeIf { it == literal }
            literal.isLetter() -> coerceToLetter(sourceChar)?.takeIf { it == literal }
            else -> null
        }
    }

    private fun shouldFinalizeImmediately(candidate: String): Boolean {
        if (!VehicleNumberValidator.isValid(candidate)) return false

        return when (VehicleNumberValidator.parseType(candidate)) {
            VehicleNumberType.BH,
            VehicleNumberType.TEMPORARY,
            VehicleNumberType.VINTAGE -> true

            VehicleNumberType.REGULAR -> regularInstantPattern.matches(candidate)
            VehicleNumberType.UNKNOWN -> false
        }
    }

    private fun View.isManualForced(): Boolean = (tag as? Boolean) == true
    private fun View.setManualForced(value: Boolean) {
        tag = value
    }

    private fun restartVehicleScan() {
        if (!isOperatorPlateMode()) {
            resumeOperatorQrScanning()
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
        animatePreviewGlassEffect(enabled = false)
        resetInlineManualEntryForm()
        animateCornerBrackets(scaleUp = false)
        updateOperatorInteractionState()
        setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
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
            is CheckInState.Error -> state.requestId
            CheckInState.Idle -> null
        }
        if (stateRequestId != null && stateRequestId != pendingOperatorRequestId) return

        when (state) {
            CheckInState.Idle -> { }
            is CheckInState.Loading -> {
                operatorOperationInProgress = true
                updateOperatorInteractionState()
                setStatus(getActiveProcessingMessage(operationType), ScanState.SCANNING, showProgress = true)
            }
            is CheckInState.Success -> {
                cancelOperatorOperationTimeout()
                pendingOperatorRequestId = null
                operatorOperationInProgress = false
                val vehicleNumber = state.booking.vehicleNumber ?: currentVehicleNumber.orEmpty()
                currentVehicleNumber = vehicleNumber.takeIf { it.isNotBlank() }
                renderOperationSuccess(operationType, state.booking, vehicleNumber)
                provideFeedback(FeedbackType.SUCCESS)
                if (!isOperatorQrMode()) {
                    markPlateProcessed(vehicleNumber)
                }
                updateOperatorInteractionState()
                resetOperatorState(operationType)
                scheduleOperatorScanResume(delayMs = scannerResultHoldMs)
            }
            is CheckInState.Error -> {
                cancelOperatorOperationTimeout()
                pendingOperatorRequestId = null
                operatorOperationInProgress = false
                val message = normalizeNotificationMessage(state.message)
                setStatus(message, ScanState.ERROR, showProgress = false)
                provideFeedback(FeedbackType.ERROR)
                if (!isOperatorQrMode()) {
                    markPlateProcessed(currentVehicleNumber)
                }
                updateOperatorInteractionState()
                resetOperatorState(operationType)
                scheduleOperatorScanResume(delayMs = scannerResultHoldMs)
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
        currentVehicleNumber = vehicleNumber
        operatorOperationInProgress = true
        updateOperatorInteractionState()
        val requestId = SystemClock.elapsedRealtimeNanos()
        pendingOperatorRequestId = requestId
        ui.progress.isVisible = true
        ui.statusText.text = getProcessingMessage(operationType)
        startOperatorOperationTimeout(operationType, requestId)
        performOperation(operationType, requestId)
    }

    private fun resetOperatorState(operationType: OperationType) {
        when (operationType) {
            OperationType.CHECK_IN -> operatorViewModel.resetCheckInState()
            OperationType.CHECK_OUT -> operatorViewModel.resetCheckOutState()
        }
    }

    private fun normalizeVehicleNumber(raw: String): String? {
        val cleaned = VehicleNumberValidator.normalize(raw)
        return cleaned.takeIf { VehicleNumberValidator.isValid(it) }
    }

    private fun normalizeOrCorrectPlateCandidate(raw: String): String? {
        val cleaned = VehicleNumberValidator.normalize(raw)
        if (cleaned.isEmpty()) return null

        normalizeVehicleNumber(cleaned)?.let { return it }
        return correctPlateOcr(cleaned)?.value?.takeIf(VehicleNumberValidator::isValid)
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
            val certaintyBonus = if (shouldFinalizeImmediately(candidatePlate)) 8 else 0
            val confidenceBonus = ((confidence ?: 0f) * 24f).roundToInt()
            val candidate = SpeechPlateCandidate(
                plate = candidatePlate,
                score = scoreCandidate(candidatePlate) + rankBonus + certaintyBonus + confidenceBonus,
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

    private fun startVoiceVehicleInput() {
        if (!isOperatorPlateMode() ||
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
                title = "Voice Input",
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
                title = "Voice Input",
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
        stopVehicleScanner()
        stopScanLineAnimation()
        setStatus(getString(R.string.vehicle_scan_voice_listening), ScanState.SCANNING, showProgress = true)

        runCatching { recognizer.startListening(buildVoiceRecognizerIntent()) }
            .onFailure {
                voiceListeningInProgress = false
                voiceRecognitionInProgress = false
                updateOperatorInteractionState()
                showScannerNotification(
                    title = "Voice Input",
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
                showScannerNotification(title = "Voice Input", message = message, isError = true)
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
            putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE, true)
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
            !operatorOperationInProgress &&
            !vehicleScanCompleted &&
            scannerSpotSelectionDialog?.isShowing != true &&
            !manualEntryInput.hasFocus()
        val iconTint = if (voiceListeningInProgress) {
            ContextCompat.getColor(this, R.color.booking_status_active_text)
        } else {
            ContextCompat.getColor(this, android.R.color.white)
        }
        voiceEntryButton.isEnabled = if (voiceRecognitionInProgress || voiceListeningInProgress) true else canStartVoice
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

    private fun performOperation(operationType: OperationType, requestId: Long) {
        val plate = currentVehicleNumber ?: return
        when (operationType) {
            OperationType.CHECK_IN -> operatorViewModel.checkInByVehicleNumber(
                plate,
                operatorParkingSpotId,
                requestId,
                parkingLotId = currentOperatorParkingLotId()
            )
            OperationType.CHECK_OUT -> operatorViewModel.checkOutByVehicleNumber(
                plate,
                operatorParkingSpotId,
                requestId,
                parkingLotId = currentOperatorParkingLotId()
            )
        }
    }

    private fun performQrOperation(operationType: OperationType, qrCode: String, requestId: Long) {
        // The QR value is the booking id, so avoid adding a selected spot constraint here too.
        when (operationType) {
            OperationType.CHECK_IN -> operatorViewModel.checkInByQrCode(
                qrCode,
                null,
                requestId,
                parkingLotId = currentOperatorParkingLotId()
            )
            OperationType.CHECK_OUT -> operatorViewModel.checkOutByQrCode(
                qrCode,
                null,
                requestId,
                parkingLotId = currentOperatorParkingLotId()
            )
        }
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
        return message
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
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                operatorOperationInProgress = false
                vehicleScanCompleted = false
                currentVehicleNumber = null
                return@Runnable
            }
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
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                operatorOperationInProgress = false
                vehicleScanCompleted = false
                currentVehicleNumber = null
                currentQrCode = null
                return@Runnable
            }
            operatorOperationInProgress = false
            vehicleScanCompleted = false
            currentVehicleNumber = null
            currentQrCode = null
            resumeOperatorQrScanning()
        }
        scanResumeRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelVehicleScanResume() {
        scanResumeRunnable?.let { handler.removeCallbacks(it) }
        scanResumeRunnable = null
    }

    private fun startOperatorOperationTimeout(operationType: OperationType, requestId: Long) {
        cancelOperatorOperationTimeout()
        val runnable = Runnable {
            operationTimeoutRunnable = null
            if (pendingOperatorRequestId != requestId) return@Runnable

            val timedOutVehicleNumber = currentVehicleNumber
            val timedOutQrCode = currentQrCode
            pendingOperatorRequestId = null
            operatorOperationInProgress = false
            vehicleScanCompleted = false
            currentVehicleNumber = null
            currentQrCode = null
            provideFeedback(FeedbackType.TIMEOUT)
            resetOperatorState(operationType)

            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                return@Runnable
            }

            val message = getString(R.string.vehicle_sheet_processing_timeout)
            setStatus(message, ScanState.ERROR, showProgress = false)
            showScannerNotification(
                title = getOperationTitle(operationType, isError = true),
                message = when {
                    !timedOutVehicleNumber.isNullOrBlank() -> "$timedOutVehicleNumber. $message"
                    !timedOutQrCode.isNullOrBlank() -> "Booking QR. $message"
                    else -> message
                },
                isError = true
            )
            updateOperatorInteractionState()
            scheduleOperatorScanResume(delayMs = scannerResultHoldMs)
        }
        operationTimeoutRunnable = runnable
        handler.postDelayed(runnable, operatorOperationTimeoutMs)
    }

    private fun cancelOperatorOperationTimeout() {
        operationTimeoutRunnable?.let { handler.removeCallbacks(it) }
        operationTimeoutRunnable = null
    }

    private fun markPlateProcessed(vehicleNumber: String?) {
        val plate = vehicleNumber?.let(VehicleNumberValidator::normalize)?.takeIf { it.isNotEmpty() } ?: return
        pruneProcessedVehicleCooldowns()
        recentlyProcessedVehicles[plate] = System.currentTimeMillis()
    }

    private fun isPlateOnCooldown(vehicleNumber: String): Boolean {
        val plate = VehicleNumberValidator.normalize(vehicleNumber)
        pruneProcessedVehicleCooldowns()
        val processedAt = recentlyProcessedVehicles[plate] ?: return false
        return System.currentTimeMillis() - processedAt < processedVehicleCooldownMs
    }

    private fun pruneProcessedVehicleCooldowns() {
        val now = System.currentTimeMillis()
        val iterator = recentlyProcessedVehicles.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value >= processedVehicleCooldownMs) {
                iterator.remove()
            }
        }
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
        val view = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_vehicle_scan_success, null, false)

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

        sheet.setContentView(view)
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
        scannerSpotSelectionDialog?.dismiss()
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
        sourceView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        animateOperationTogglePress(sourceView)
        updateOperationToggleUi()
    }

    private fun switchScannerInputMode(inputMode: ScannerInputMode, sourceView: View) {
        if (!isVehicleScan() || selectedScannerInputMode == inputMode) return
        if (operatorOperationInProgress || vehicleScanCompleted || voiceRecognitionInProgress || voiceListeningInProgress) return

        selectedScannerInputMode = inputMode
        currentVehicleNumber = null
        currentQrCode = null
        vehicleScanCompleted = false
        clearCandidateBuffer()
        cancelVehicleScanResume()
        cancelScanTimeout()

        if (manualEntryInput.hasFocus()) {
            hideKeyboard(manualEntryInput)
            manualEntryInput.clearFocus()
        }

        sourceView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        animateOperationTogglePress(sourceView)

        if (inputMode == ScannerInputMode.QR) {
            stopVehicleScanner()
            stopQrScanner()
            stopScanLineAnimation()
            resetInlineManualEntryForm()
        } else {
            stopOperatorQrCameraScanner()
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

    private fun updateOperationToggleUi(animated: Boolean = true) {
        val isCheckInSelected = selectedOperationType == OperationType.CHECK_IN
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
            setStatus(
                getIdleScanMessage(),
                ScanState.SCANNING,
                showProgress = vehicleScannerRunning || isOperatorQrMode()
            )
        }
    }

    private fun updateScannerInputToggleUi(animated: Boolean = true) {
        if (!::plateInputSegment.isInitialized || !::qrInputSegment.isInitialized) return

        val isPlateSelected = selectedScannerInputMode == ScannerInputMode.PLATE
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
        stopVehicleScanner()
        stopScanLineAnimation()
        processOperatorVehicleInput(normalized)
    }

    private fun pauseScannerForManualEntry() {
        if (!isOperatorPlateMode() || vehicleScanCompleted || operatorOperationInProgress) return
        if (vehicleScannerRunning) {
            stopVehicleScanner()
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

    private fun showManualEntrySheet() {
        if (!isVehicleScan() || manualEntrySheet?.isShowing == true || isFinishing || isDestroyed) return
        cancelVehicleScanResume()
        cancelScanTimeout()
        val wasPlateScanning = vehicleScannerRunning
        val wasQrMode = isOperatorQrMode()
        if (wasPlateScanning) {
            stopVehicleScanner()
            stopScanLineAnimation()
        }
        if (wasQrMode) {
            stopOperatorQrCameraScanner()
            if (::barcodeView.isInitialized) {
                barcodeView.pause()
            }
        }

        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        manualEntrySheet = dialog
        val view = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_scanner_manual_entry, null, false)
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
            vehicleScanCompleted = true
            operatorOperationInProgress = true
            currentVehicleNumber = normalized
            updateOperatorInteractionState()
            dialog.dismiss()
            animatePreviewGlassEffect(enabled = false)
            stopVehicleScanner()
            stopOperatorQrCameraScanner()
            stopScanLineAnimation()
            if (::barcodeView.isInitialized) {
                barcodeView.pause()
            }
            processOperatorVehicleInput(normalized)
        }
        cancel.setOnClickListener { dialog.dismiss() }

        dialog.setContentView(view)
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
            if (!operatorOperationInProgress && !vehicleScanCompleted) {
                if (wasQrMode) {
                    resumeOperatorQrScanning()
                } else if (wasPlateScanning || isOperatorPlateMode()) {
                    resumeLiveVehicleScanning()
                }
            }
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
            stopVehicleScanner()
            stopScanLineAnimation()
        }
        if (wasQrScanning) {
            stopOperatorQrCameraScanner()
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
            // Resume scanning if it was running before
            if (wasScanning && !vehicleScanCompleted && !operatorOperationInProgress) {
                vehicleScanCompleted = false
                setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
                startVehicleScanner()
            } else if (wasQrScanning && !vehicleScanCompleted && !operatorOperationInProgress) {
                resumeOperatorQrScanning()
            }
            updateOperatorInteractionState()
        }

        sheetBinding.btnClose.setOnClickListener { dialog.dismiss() }

        val adapter = ParkingSpotSelectionAdapter(
            onItemClick = { spot ->
                val displayName = getSpotDisplayName(spot)
                operatorParkingSpotId = spot.id
                operatorParkingSpotName = displayName
                operatorParkingLotId = normalizeLotId(spot.lotId) ?: AuthSession.getParkingLotId(this)
                updateScannerSpotPill(displayName)
                dialog.dismiss()
            },
            allowUnavailableSelection = true
        )

        sheetBinding.rvSpots.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvSpots.adapter = adapter
        if (!operatorParkingSpotId.isNullOrBlank()) {
            adapter.setSelectedSpot(operatorParkingSpotId)
        }

        sheetBinding.progressBar.visibility = View.VISIBLE
        sheetBinding.tvEmptyState.text = getString(R.string.op_select_spot_loading)
        sheetBinding.tvEmptyState.visibility = View.VISIBLE
        sheetBinding.rvSpots.visibility = View.GONE

        dialog.show()

        lifecycleScope.launch {
            val spots = loadScannerParkingSpots()
            if (scannerSpotSelectionDialog !== dialog) return@launch
            android.util.Log.d("QrScannerActivity", "Showing ${spots.size} scanner parking spots")
            sheetBinding.progressBar.visibility = View.GONE
            if (spots.isEmpty()) {
                sheetBinding.tvEmptyState.text = getString(R.string.op_select_spot_empty)
                sheetBinding.tvEmptyState.visibility = View.VISIBLE
                sheetBinding.rvSpots.visibility = View.GONE
            } else {
                sheetBinding.tvEmptyState.visibility = View.GONE
                sheetBinding.rvSpots.visibility = View.VISIBLE
                adapter.submitList(spots) {
                    sheetBinding.rvSpots.requestLayout()
                }
                if (!operatorParkingSpotId.isNullOrBlank()) {
                    adapter.setSelectedSpot(operatorParkingSpotId)
                }
            }
        }
    }

    private suspend fun loadScannerParkingSpots(): List<ParkingSpot> {
        return OperatorParkingSpotLoader.load(this, parkingRepository, "QrScannerActivity")
    }

    private fun currentOperatorParkingLotId(): String? {
        return normalizeLotId(operatorParkingLotId) ?: AuthSession.getParkingLotId(this)
    }

    private fun normalizeLotId(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun getSpotDisplayName(spot: ParkingSpot): String {
        return OperatorParkingSpotLoader.getSpotDisplayName(spot)
    }

    override fun onDestroy() {
        super.onDestroy()
        previewGlassAnimator?.cancel()
        previewGlassAnimator = null
        animatePreviewGlassEffect(enabled = false, immediate = true)
        releaseSpeechRecognizer(destroyRecognizer = true)
        stopVehicleScanner()
        stopOperatorQrCameraScanner()
        if (::barcodeView.isInitialized) {
            barcodeView.pause()
        }
        cameraExecutor.shutdown()
        textRecognizer.close()
        qrBarcodeScanner.close()
        cancelScanTimeout()
        setTorch(false)
        toneGenerator?.release()
        cancelAutoClose()
        cancelVehicleScanResume()
        cancelOperatorOperationTimeout()
        vehicleResultSheet?.dismiss()
        manualEntrySheet?.dismiss()
        scannerSpotSelectionDialog?.dismiss()
    }

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
        }
    }
}
