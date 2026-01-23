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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.gridee.parking.R
import com.gridee.parking.ui.operator.CheckInState
import com.gridee.parking.ui.operator.OperatorViewModel
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class QrScannerActivity : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var vehiclePreview: PreviewView
    private lateinit var vehicleHint: TextView
    private lateinit var scannerContentContainer: View
    private lateinit var scanningFrameContainer: FrameLayout
    private lateinit var overlayView: View
    private lateinit var scanLine: View
    private lateinit var statusContainer: View
    private lateinit var statusText: TextView
    private lateinit var statusProgress: ProgressBar
    private lateinit var cornerTopLeft: ImageView
    private lateinit var cornerTopRight: ImageView
    private lateinit var cornerBottomLeft: ImageView
    private lateinit var cornerBottomRight: ImageView
    private lateinit var flashToggle: ImageButton
    private lateinit var closeButton: ImageButton

    private var lastScanTimestamp: Long = 0L
    private var scanType: String = ""
    private var vehicleScannerRunning = false
    private var vehicleScanCompleted = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var cameraExecutor: ExecutorService
    private var scanLineAnimator: ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoCloseRunnable: Runnable? = null
    private val autoCloseDelayMs = 3000L
    private var scanTimeoutRunnable: Runnable? = null
    private val scanTimeoutMs = 7000L
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var vehicleResultSheet: BottomSheetDialog? = null
    private var timeoutSheet: BottomSheetDialog? = null
    private var camera: Camera? = null
    private var isTorchOn = false
    private var lastAmbientLuma = 255f
    private var manualTorchOverrideUntil = 0L
    private val torchOnThreshold = 55f
    private val torchOffThreshold = 90f
    private val manualTorchOverrideDurationMs = 6000L

    private val candidateBuffer = ArrayDeque<String>()
    private val maxCandidateBufferSize = 12
    private val minConfidenceHits = 2
    private val operatorViewModel: OperatorViewModel by viewModels()
    private var vehicleSheetUi: VehicleSheetUi? = null
    private var currentVehicleNumber: String? = null
    
    // Scan state for corner glow transitions
    private enum class ScanState {
        SCANNING, SUCCESS, ERROR, WARNING
    }

    private enum class FeedbackType {
        SUCCESS, ERROR, TIMEOUT
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

    companion object {
        const val EXTRA_BOOKING_ID = "booking_id"
        const val EXTRA_SCAN_TYPE = "scan_type" // CHECK_IN, CHECK_OUT, VEHICLE_CHECK_IN, VEHICLE_CHECK_OUT
        const val EXTRA_QR_CODE = "qr_code"
        const val RESULT_QR_SCANNED = 100
        private const val CAMERA_PERMISSION_REQUEST = 101
        private val STRICT_PLATE_PATTERN = Regex("^[A-Z]{2}[0-9]{1,2}[A-Z]{0,2}[0-9]{3,4}$")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        scanType = intent?.getStringExtra(EXTRA_SCAN_TYPE) ?: ""
        cameraExecutor = Executors.newSingleThreadExecutor()

        barcodeView = findViewById(R.id.barcode_scanner)
        vehiclePreview = findViewById(R.id.vehicle_preview)
        vehicleHint = findViewById(R.id.tv_vehicle_hint)
        scannerContentContainer = findViewById(R.id.scanner_content_container)
        scanningFrameContainer = findViewById(R.id.scanning_frame_container)
        overlayView = findViewById(R.id.vehicle_overlay)
        scanLine = findViewById(R.id.vehicle_scan_line)
        statusContainer = findViewById(R.id.vehicle_status_container)
        statusText = findViewById(R.id.tv_vehicle_status)
        statusProgress = findViewById(R.id.pb_vehicle_scanning)
        cornerTopLeft = findViewById(R.id.corner_top_left)
        cornerTopRight = findViewById(R.id.corner_top_right)
        cornerBottomLeft = findViewById(R.id.corner_bottom_left)
        cornerBottomRight = findViewById(R.id.corner_bottom_right)
        flashToggle = findViewById(R.id.btn_flash_toggle)
        closeButton = findViewById(R.id.btn_close_scanner)

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
        flashToggle.setOnClickListener { toggleFlash() }
        closeButton.setOnClickListener { closeScanner() }

        if (checkCameraPermission()) {
            startScanner()
        } else {
            requestCameraPermission()
        }
    }

    private fun configureScannerUi() {
        if (isVehicleScan()) {
            barcodeView.visibility = View.GONE
            vehiclePreview.visibility = View.VISIBLE
            scannerContentContainer.visibility = View.VISIBLE
            scanningFrameContainer.visibility = View.VISIBLE
            overlayView.visibility = View.VISIBLE
            scanLine.visibility = View.VISIBLE
            vehicleHint.visibility = View.VISIBLE
            statusContainer.visibility = View.VISIBLE
            setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
            startScanLineAnimation()
            animateHintFadeIn()
            updateFlashToggleVisibility(true)
            resetCornerTints()
            animateCornerGlow(ScanState.SCANNING)
        } else {
            barcodeView.visibility = View.VISIBLE
            vehiclePreview.visibility = View.GONE
            scannerContentContainer.visibility = View.GONE
            scanningFrameContainer.visibility = View.GONE
            overlayView.visibility = View.GONE
            scanLine.visibility = View.GONE
            vehicleHint.visibility = View.GONE
            statusContainer.visibility = View.GONE
            stopScanLineAnimation()
            updateFlashToggleVisibility(false)
        }
    }

    private fun isVehicleScan(): Boolean {
        return scanType.uppercase(Locale.ROOT).contains("VEHICLE")
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

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner()
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startScanner() {
        if (isVehicleScan()) {
            setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
            startVehicleScanner()
        } else {
            startQrScanner()
        }
    }

    // region QR Scanner (for bookings)
    private fun startQrScanner() {
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
    }
    // endregion

    // region Vehicle number scanner (ML Kit Text Recognition)
    private fun startVehicleScanner() {
        if (vehicleScannerRunning || vehicleScanCompleted) return
        vehicleScannerRunning = true
        statusProgress.isVisible = true
        startScanLineAnimation()
        startScanTimeout()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(vehiclePreview.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processVehicleFrame(imageProxy)
                }

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
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
        if (vehicleScanCompleted) {
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

        val cleaned = rawCandidates.mapNotNull { normalizePlate(it) }
        if (cleaned.isEmpty()) return null

        return cleaned.maxByOrNull { scoreCandidate(it) }
    }

    private fun normalizePlate(raw: String): String? {
        val cleaned = raw.uppercase(Locale.ROOT).replace("[^A-Z0-9]".toRegex(), "")
        if (cleaned.length !in 6..10) return null
        if (!cleaned.any { it.isLetter() } || !cleaned.any { it.isDigit() }) return null
        return cleaned
    }

    private fun scoreCandidate(value: String): Int {
        var score = value.length
        val letters = value.count { it.isLetter() }
        val digits = value.count { it.isDigit() }
        score += letters
        score += digits
        if (STRICT_PLATE_PATTERN.matches(value)) score += 6
        if (value.startsWith("IND")) score -= 2
        return score
    }

    private fun bufferCandidate(candidate: String) {
        if (candidateBuffer.size >= maxCandidateBufferSize) {
            candidateBuffer.removeFirst()
        }
        candidateBuffer.addLast(candidate)

        val counts = candidateBuffer.groupingBy { it }.eachCount()
        val maxEntry = counts.maxByOrNull { it.value }
        val best = maxEntry?.key ?: candidate
        val hits = maxEntry?.value ?: 1

        if (hits >= minConfidenceHits && STRICT_PLATE_PATTERN.matches(best)) {
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
        if (vehicleScanCompleted) return
        vehicleScanCompleted = true
        currentVehicleNumber = plate
        clearCandidateBuffer()
        runOnUiThread {
            cancelScanTimeout()
            stopVehicleScanner()
            provideFeedback(FeedbackType.SUCCESS)
            animateCornerBrackets(scaleUp = true)
            animateCornerGlow(ScanState.SUCCESS) // Subtle green glow
            setStatus(
                getString(R.string.vehicle_scan_detected, plate),
                ScanState.SUCCESS,
                showProgress = false
            )
            Toast.makeText(
                this,
                getString(R.string.vehicle_scan_success_toast, plate),
                Toast.LENGTH_SHORT
            ).show()
            showVehicleResultSheet(plate)
        }
    }

    private fun clearCandidateBuffer() {
        candidateBuffer.clear()
    }

    private fun cropToOverlay(imageProxy: ImageProxy): Boolean {
        val overlayRect = getOverlayRectOnPreview() ?: return false
        val previewWidth = vehiclePreview.width.takeIf { it > 0 } ?: return false
        val previewHeight = vehiclePreview.height.takeIf { it > 0 } ?: return false

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

        val rect = Rect(leftPx, topPx, rightPx, bottomPx)
        imageProxy.setCropRect(rect)
        return true
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
        if (isVehicleScan()) {
            stopVehicleScanner()
            cancelScanTimeout()
            stopScanLineAnimation()
        } else {
            barcodeView.pause()
        }

        val resultIntent = Intent().apply {
            putExtra(EXTRA_QR_CODE, value)
            putExtra(EXTRA_SCAN_TYPE, scanType)
        }
        setResult(RESULT_QR_SCANNED, resultIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (isVehicleScan()) {
            if (checkCameraPermission() && !vehicleScanCompleted) {
                startVehicleScanner()
            }
        } else if (::barcodeView.isInitialized) {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isVehicleScan()) {
            stopVehicleScanner()
            stopScanLineAnimation()
        } else if (::barcodeView.isInitialized) {
            barcodeView.pause()
        }
    }

    private fun stopVehicleScanner() {
        if (!vehicleScannerRunning) return
        vehicleScannerRunning = false
        cameraProvider?.unbindAll()
        cancelScanTimeout()
        clearCandidateBuffer()
        setTorch(false)
        updateFlashToggleVisibility(false)
    }

    private fun startScanLineAnimation() {
        if (!isVehicleScan()) return
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
        
        // Neumorphic entrance animation
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
        
        statusText.text = message
        statusProgress.isVisible = showProgress && !vehicleScanCompleted
        
        val (textColorRes, backgroundRes, progressTintRes) = when (state) {
            ScanState.SUCCESS -> Triple(
                R.color.scanner_status_success_text,
                R.color.scanner_status_success_bg,
                R.color.scanner_status_success_tint
            )
            ScanState.ERROR -> Triple(
                R.color.scanner_status_error_text,
                R.color.scanner_status_error_bg,
                R.color.scanner_status_error_tint
            )
            ScanState.WARNING -> Triple(
                R.color.scanner_status_warning_text,
                R.color.scanner_status_warning_bg,
                R.color.scanner_status_warning_tint
            )
            else -> Triple(
                R.color.scanner_status_scanning_text,
                R.color.scanner_status_scanning_bg,
                R.color.scanner_status_scanning_tint
            )
        }

        val textColor = ContextCompat.getColor(this, textColorRes)
        statusText.setTextColor(textColor)

        val backgroundColor = ContextCompat.getColor(this, backgroundRes)
        ViewCompat.setBackgroundTintList(statusContainer, ColorStateList.valueOf(backgroundColor))

        val progressColor = ContextCompat.getColor(this, progressTintRes)
        statusProgress.indeterminateTintList = ColorStateList.valueOf(progressColor)

        animateCornerGlow(state)
    }

    private fun startScanTimeout() {
        cancelScanTimeout()
        val runnable = Runnable {
            if (!vehicleScanCompleted) {
                vehicleScanCompleted = true
                provideFeedback(FeedbackType.TIMEOUT)
                animateCornerGlow(ScanState.WARNING) // Subtle warning glow
                stopVehicleScanner()
                stopScanLineAnimation()
                showTimeoutSheet()
            }
        }
        scanTimeoutRunnable = runnable
        handler.postDelayed(runnable, scanTimeoutMs)
    }

    private fun cancelScanTimeout() {
        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
        scanTimeoutRunnable = null
    }

    private fun showTimeoutSheet() {
        if (isFinishing) return
        timeoutSheet?.dismiss()
        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_vehicle_scan_timeout, null, false)

        val manualInputLayout = view.findViewById<TextInputLayout>(R.id.timeout_manual_input_container)
        val manualInput = view.findViewById<TextInputEditText>(R.id.input_timeout_manual_plate)
        manualInputLayout.error = null

        view.findViewById<View>(R.id.btn_timeout_retry)?.setOnClickListener {
            timeoutSheet?.dismiss()
            vehicleScanCompleted = false
            setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
            startVehicleScanner()
        }

        view.findViewById<View>(R.id.btn_timeout_manual)?.setOnClickListener {
            timeoutSheet?.dismiss()
            finish()
        }
        
        view.findViewById<View>(R.id.btn_timeout_manual_submit)?.setOnClickListener {
            val manualPlate = manualInput.text?.toString().orEmpty()
            val normalized = normalizeVehicleNumber(manualPlate)
            if (normalized == null) {
                manualInputLayout.error = getString(R.string.vehicle_sheet_manual_error)
            } else {
                manualInputLayout.error = null
                timeoutSheet?.dismiss()
                handleManualEntryFromTimeout(normalized)
            }
        }

        sheet.setContentView(view)
        sheet.setCancelable(true)
        sheet.setOnDismissListener { timeoutSheet = null }
        sheet.show()
        timeoutSheet = sheet
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
        if (camera?.cameraInfo?.hasFlashUnit() != true) {
            Toast.makeText(this, R.string.vehicle_scan_flash_off, Toast.LENGTH_SHORT).show()
            return
        }
        manualTorchOverrideUntil = System.currentTimeMillis() + manualTorchOverrideDurationMs
        setTorch(!isTorchOn)
    }

    private fun setTorch(enabled: Boolean) {
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
        if (show && hasFlash && isVehicleScan()) {
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

    private fun View.isManualForced(): Boolean = (tag as? Boolean) == true
    private fun View.setManualForced(value: Boolean) {
        tag = value
    }

    private fun restartVehicleScan() {
        currentVehicleNumber = null
        vehicleScanCompleted = false
        clearCandidateBuffer()
        setStatus(getString(R.string.vehicle_scan_detecting), ScanState.SCANNING)
        startVehicleScanner()
    }

    private fun handleManualEntryFromTimeout(plate: String) {
        currentVehicleNumber = plate
        vehicleScanCompleted = true
        showVehicleResultSheet(plate, forceManualEntry = true)
    }

    private fun handleOperationState(state: CheckInState, operationType: OperationType) {
        val ui = vehicleSheetUi ?: return
        when (state) {
            CheckInState.Idle -> { }
            CheckInState.Loading -> {
                ui.progress.isVisible = true
                ui.statusText.text = getProcessingMessage(operationType)
            }
            is CheckInState.Success -> {
                ui.progress.isVisible = false
                val vehicleNumber = state.booking.vehicleNumber ?: currentVehicleNumber.orEmpty()
                ui.statusText.text = getSuccessMessage(operationType, vehicleNumber)
                provideFeedback(FeedbackType.SUCCESS)
                scheduleAutoClose(vehicleNumber, allowForced = true)
                resetOperatorState(operationType)
            }
            is CheckInState.Error -> {
                ui.progress.isVisible = false
                ui.statusText.text = state.message
                resetOperatorState(operationType)
            }
        }
    }

    private fun getProcessingMessage(operationType: OperationType): String {
        return when (operationType) {
            OperationType.CHECK_IN -> getString(R.string.vehicle_sheet_processing_checkin)
            OperationType.CHECK_OUT -> getString(R.string.vehicle_sheet_processing_checkout)
        }
    }

    private fun getSuccessMessage(operationType: OperationType, vehicleNumber: String): String {
        return when (operationType) {
            OperationType.CHECK_IN -> getString(R.string.vehicle_sheet_checkin_success, vehicleNumber)
            OperationType.CHECK_OUT -> getString(R.string.vehicle_sheet_checkout_success, vehicleNumber)
        }
    }

    private fun startVehicleProcessing(operationType: OperationType, vehicleNumber: String) {
        val ui = vehicleSheetUi ?: return
        currentVehicleNumber = vehicleNumber
        ui.progress.isVisible = true
        ui.statusText.text = getProcessingMessage(operationType)
        performOperation(operationType)
    }

    private fun resetOperatorState(operationType: OperationType) {
        when (operationType) {
            OperationType.CHECK_IN -> operatorViewModel.resetCheckInState()
            OperationType.CHECK_OUT -> operatorViewModel.resetCheckOutState()
        }
    }

    private fun normalizeVehicleNumber(raw: String): String? {
        val cleaned = raw.trim().uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
        return if (cleaned.length in 4..15) cleaned else null
    }

    private fun determineOperationType(): OperationType? {
        val type = scanType.uppercase(Locale.ROOT)
        return when {
            type.contains("CHECK_OUT") -> OperationType.CHECK_OUT
            type.contains("CHECK_IN") || type.contains("VEHICLE") -> OperationType.CHECK_IN
            else -> null
        }
    }

    private fun performOperation(operationType: OperationType) {
        val plate = currentVehicleNumber ?: return
        when (operationType) {
            OperationType.CHECK_IN -> operatorViewModel.checkInByVehicleNumber(plate)
            OperationType.CHECK_OUT -> operatorViewModel.checkOutByVehicleNumber(plate)
        }
    }

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

        val sheet = BottomSheetDialog(this)
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
                ui.manualInputLayout.error = getString(R.string.vehicle_sheet_manual_error)
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
        cancelAutoClose()
        vehicleResultSheet?.dismiss()
        timeoutSheet?.dismiss()
        if (isVehicleScan()) {
            stopVehicleScanner()
            stopScanLineAnimation()
        } else if (::barcodeView.isInitialized) {
            barcodeView.pause()
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVehicleScanner()
        cameraExecutor.shutdown()
        textRecognizer.close()
        cancelScanTimeout()
        setTorch(false)
        toneGenerator?.release()
        cancelAutoClose()
        vehicleResultSheet?.dismiss()
        timeoutSheet?.dismiss()
    }
}
