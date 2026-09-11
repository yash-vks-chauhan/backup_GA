package com.gridee.parking.ui.qr

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal enum class CameraValueUpdate {
    UNAVAILABLE,
    UNCHANGED,
    APPLIED,
}

@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
internal class ScannerFrameTransform(
    val cropMapping: ScannerCropMapping,
    val rawImageToPreview: CoordinateTransform,
)

/**
 * Owns the shared CameraX session used by operator Plate and QR modes.
 *
 * The Activity decides which analyzer is active and renders the UI; this controller exclusively
 * owns provider/use-case binding, focus, torch, zoom, exposure, resolution selection, and the
 * preview-to-analysis coordinate transform. Keeping those responsibilities in one place prevents
 * Plate/QR startup paths from drifting apart or rebinding the camera during a mode switch.
 */
@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
internal class ScannerCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val analysisExecutor: Executor,
) {
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    @Volatile
    private var camera: Camera? = null
    @Volatile
    private var previewOutputTransform: OutputTransform? = null
    @Volatile
    private var scanRegionInPreview: RectF? = null
    private var lastFocusMeteringAtMs: Long = 0L
    private var bindGeneration = 0L
    private var bindPending = false
    private var pendingViewportRetry: Runnable? = null
    private var observedCameraState: LiveData<CameraState>? = null
    private var cameraStateObserver: Observer<CameraState>? = null
    private var firstPreviewWatchdog: Runnable? = null
    private var firstPreviewObservedGeneration: Long? = null

    val isBound: Boolean
        get() = camera != null && cameraProvider != null && analysisUseCase != null

    val hasFlashUnit: Boolean
        get() = camera?.cameraInfo?.hasFlashUnit() == true

    fun bind(
        analysisWidth: Int,
        analysisHeight: Int,
        shouldContinue: () -> Boolean,
        analyzeFrame: (ImageProxy) -> Unit,
        onBound: (reused: Boolean) -> Unit,
        onError: (Throwable) -> Unit,
        onRuntimeError: (ScannerCameraRuntimeException) -> Unit,
    ) {
        if (isBound) {
            // Plate and QR deliberately retain one CameraX session. Refresh the runtime callbacks
            // on every reuse so the observer does not keep the stopped mode's session predicate.
            val boundCamera = camera ?: return
            val generation = bindGeneration
            startRuntimeMonitoring(
                boundCamera = boundCamera,
                generation = generation,
                shouldContinue = shouldContinue,
                onRuntimeError = onRuntimeError,
            )
            if (isActiveSession(generation, shouldContinue)) onBound(true)
            return
        }

        cancelPendingBind()
        val generation = ++bindGeneration
        bindPending = true

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                if (!canContinueBind(generation, shouldContinue)) {
                    finishCancelledBind(generation)
                    return@addListener
                }
                try {
                    val provider = providerFuture.get()
                    bindWhenViewportReady(
                        provider = provider,
                        analysisWidth = analysisWidth,
                        analysisHeight = analysisHeight,
                        generation = generation,
                        retryAttempt = 0,
                        shouldContinue = shouldContinue,
                        analyzeFrame = analyzeFrame,
                        onBound = onBound,
                        onError = onError,
                        onRuntimeError = onRuntimeError,
                    )
                } catch (error: Throwable) {
                    failBind(generation, error, onError)
                }
            },
            mainExecutor,
        )
    }

    fun release() {
        cancelPendingBind()
        stopRuntimeMonitoring()
        bindGeneration++
        analysisUseCase?.clearAnalyzer()
        cameraProvider?.unbindAll()
        previewUseCase = null
        analysisUseCase = null
        camera = null
        previewOutputTransform = null
        scanRegionInPreview = null
        lastFocusMeteringAtMs = 0L
    }

    private fun bindWhenViewportReady(
        provider: ProcessCameraProvider,
        analysisWidth: Int,
        analysisHeight: Int,
        generation: Long,
        retryAttempt: Int,
        shouldContinue: () -> Boolean,
        analyzeFrame: (ImageProxy) -> Unit,
        onBound: (reused: Boolean) -> Unit,
        onError: (Throwable) -> Unit,
        onRuntimeError: (ScannerCameraRuntimeException) -> Unit,
    ) {
        val canContinue = canContinueBind(generation, shouldContinue)
        val viewport = previewView.viewPort
        when (
            ScannerViewportBindPolicy.decide(
                viewportAvailable = viewport != null,
                shouldContinue = canContinue,
                retryAttempt = retryAttempt,
            )
        ) {
            ScannerViewportBindAction.CANCEL -> finishCancelledBind(generation)
            ScannerViewportBindAction.RETRY -> {
                val retry = Runnable {
                    if (generation != bindGeneration) return@Runnable
                    pendingViewportRetry = null
                    bindWhenViewportReady(
                        provider = provider,
                        analysisWidth = analysisWidth,
                        analysisHeight = analysisHeight,
                        generation = generation,
                        retryAttempt = retryAttempt + 1,
                        shouldContinue = shouldContinue,
                        analyzeFrame = analyzeFrame,
                        onBound = onBound,
                        onError = onError,
                        onRuntimeError = onRuntimeError,
                    )
                }
                pendingViewportRetry = retry
                if (!previewView.postDelayed(retry, ScannerViewportBindPolicy.RETRY_DELAY_MS)) {
                    pendingViewportRetry = null
                    failBind(
                        generation,
                        ScannerViewportUnavailableException(
                            "PreviewView rejected the ViewPort bind retry"
                        ),
                        onError,
                    )
                }
            }
            ScannerViewportBindAction.FAIL -> failBind(
                generation,
                ScannerViewportUnavailableException(
                    "PreviewView ViewPort was unavailable after the bounded layout wait"
                ),
                onError,
            )
            ScannerViewportBindAction.BIND -> bindUseCases(
                provider = provider,
                viewport = checkNotNull(viewport),
                analysisWidth = analysisWidth,
                analysisHeight = analysisHeight,
                generation = generation,
                shouldContinue = shouldContinue,
                analyzeFrame = analyzeFrame,
                onBound = onBound,
                onError = onError,
                onRuntimeError = onRuntimeError,
            )
        }
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        viewport: androidx.camera.core.ViewPort,
        analysisWidth: Int,
        analysisHeight: Int,
        generation: Long,
        shouldContinue: () -> Boolean,
        analyzeFrame: (ImageProxy) -> Unit,
        onBound: (reused: Boolean) -> Unit,
        onError: (Throwable) -> Unit,
        onRuntimeError: (ScannerCameraRuntimeException) -> Unit,
    ) {
        if (!canContinueBind(generation, shouldContinue)) {
            finishCancelledBind(generation)
            return
        }
        try {
            val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val preview = Preview.Builder()
                .setTargetRotation(targetRotation)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setTargetRotation(targetRotation)
                .setResolutionSelector(buildResolutionSelector(analysisWidth, analysisHeight))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { useCase ->
                    useCase.setAnalyzer(analysisExecutor) { imageProxy -> analyzeFrame(imageProxy) }
                }

            provider.unbindAll()
            if (!canContinueBind(generation, shouldContinue)) {
                analysis.clearAnalyzer()
                finishCancelledBind(generation)
                return
            }

            val group = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(analysis)
                .setViewPort(viewport)
                .build()
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                group,
            )
            if (!canContinueBind(generation, shouldContinue)) {
                analysis.clearAnalyzer()
                provider.unbind(preview, analysis)
                finishCancelledBind(generation)
                return
            }

            cameraProvider = provider
            previewUseCase = preview
            analysisUseCase = analysis
            camera = boundCamera
            startRuntimeMonitoring(
                boundCamera = boundCamera,
                generation = generation,
                shouldContinue = shouldContinue,
                onRuntimeError = onRuntimeError,
            )
            if (!isActiveSession(generation, shouldContinue)) return
            bindPending = false
            onBound(false)
        } catch (error: Throwable) {
            failBind(generation, error, onError)
        }
    }

    private fun canContinueBind(generation: Long, shouldContinue: () -> Boolean): Boolean {
        return generation == bindGeneration &&
            bindPending &&
            lifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED &&
            shouldContinue()
    }

    private fun finishCancelledBind(generation: Long) {
        if (generation != bindGeneration) return
        cancelPendingBind()
    }

    private fun failBind(generation: Long, error: Throwable, onError: (Throwable) -> Unit) {
        if (generation != bindGeneration || !bindPending) return
        release()
        onError(error)
    }

    /** Called by the Activity's existing PreviewView observer. */
    fun onPreviewStreamStateChanged(streaming: Boolean) {
        if (!streaming || firstPreviewWatchdog == null || !isBound) return
        firstPreviewObservedGeneration = bindGeneration
        cancelFirstPreviewWatchdog()
    }

    private fun startRuntimeMonitoring(
        boundCamera: Camera,
        generation: Long,
        shouldContinue: () -> Boolean,
        onRuntimeError: (ScannerCameraRuntimeException) -> Unit,
    ) {
        stopRuntimeMonitoring()
        firstPreviewObservedGeneration = null

        val stateSource = boundCamera.cameraInfo.cameraState
        val stateObserver = Observer<CameraState> { state ->
            if (!isActiveSession(generation, shouldContinue)) return@Observer
            val action = ScannerCameraRuntimePolicy.decide(
                phase = state.type.toRuntimePhase(),
                errorSeverity = when (state.error?.type) {
                    null -> ScannerCameraRuntimeErrorSeverity.NONE
                    CameraState.ErrorType.RECOVERABLE ->
                        ScannerCameraRuntimeErrorSeverity.RECOVERABLE
                    CameraState.ErrorType.CRITICAL -> ScannerCameraRuntimeErrorSeverity.CRITICAL
                },
            )
            if (action == ScannerCameraRuntimeAction.FAIL) {
                failRuntimeSession(
                    generation = generation,
                    error = ScannerCameraRuntimeException(
                        reason = ScannerCameraRuntimeFailureReason.CRITICAL_STATE,
                        cause = state.error?.cause,
                    ),
                    shouldContinue = shouldContinue,
                    onRuntimeError = onRuntimeError,
                )
            }
        }
        observedCameraState = stateSource
        cameraStateObserver = stateObserver
        stateSource.observe(lifecycleOwner, stateObserver)

        if (!isActiveSession(generation, shouldContinue)) return
        if (previewView.previewStreamState.value == PreviewView.StreamState.STREAMING) {
            firstPreviewObservedGeneration = generation
            return
        }
        val watchdog = Runnable {
            val generationMatches = generation == bindGeneration
            val cameraBound = generationMatches && isBound
            val lifecycleActive = cameraBound && lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
            val stillShouldContinue = lifecycleActive && shouldContinue()
            val shouldReport = ScannerCameraRuntimePolicy.shouldReportFirstPreviewTimeout(
                generationMatches = generationMatches,
                cameraBound = cameraBound,
                lifecycleActive = lifecycleActive,
                shouldContinue = stillShouldContinue,
                firstPreviewObserved = firstPreviewObservedGeneration == generation,
            )
            if (shouldReport) {
                failRuntimeSession(
                    generation = generation,
                    error = ScannerCameraRuntimeException(
                        ScannerCameraRuntimeFailureReason.FIRST_PREVIEW_TIMEOUT
                    ),
                    shouldContinue = shouldContinue,
                    onRuntimeError = onRuntimeError,
                )
            }
        }
        firstPreviewWatchdog = watchdog
        if (!previewView.postDelayed(
                watchdog,
                ScannerCameraRuntimePolicy.FIRST_PREVIEW_TIMEOUT_MS,
            )
        ) {
            firstPreviewWatchdog = null
            failRuntimeSession(
                generation = generation,
                error = ScannerCameraRuntimeException(
                    ScannerCameraRuntimeFailureReason.FIRST_PREVIEW_TIMEOUT
                ),
                shouldContinue = shouldContinue,
                onRuntimeError = onRuntimeError,
            )
        }
    }

    private fun failRuntimeSession(
        generation: Long,
        error: ScannerCameraRuntimeException,
        shouldContinue: () -> Boolean,
        onRuntimeError: (ScannerCameraRuntimeException) -> Unit,
    ) {
        if (!isActiveSession(generation, shouldContinue)) return
        release()
        onRuntimeError(error)
    }

    private fun isActiveSession(generation: Long, shouldContinue: () -> Boolean): Boolean {
        return generation == bindGeneration &&
            isBound &&
            lifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED &&
            shouldContinue()
    }

    private fun stopRuntimeMonitoring() {
        cancelFirstPreviewWatchdog()
        val observer = cameraStateObserver
        if (observer != null) observedCameraState?.removeObserver(observer)
        observedCameraState = null
        cameraStateObserver = null
        firstPreviewObservedGeneration = null
    }

    private fun cancelFirstPreviewWatchdog() {
        firstPreviewWatchdog?.let(previewView::removeCallbacks)
        firstPreviewWatchdog = null
    }

    private fun cancelPendingBind() {
        pendingViewportRetry?.let(previewView::removeCallbacks)
        pendingViewportRetry = null
        bindPending = false
    }

    private fun CameraState.Type.toRuntimePhase(): ScannerCameraRuntimePhase = when (this) {
        CameraState.Type.PENDING_OPEN -> ScannerCameraRuntimePhase.PENDING_OPEN
        CameraState.Type.OPENING -> ScannerCameraRuntimePhase.OPENING
        CameraState.Type.OPEN -> ScannerCameraRuntimePhase.OPEN
        CameraState.Type.CLOSING -> ScannerCameraRuntimePhase.CLOSING
        CameraState.Type.CLOSED -> ScannerCameraRuntimePhase.CLOSED
    }

    /** Caches Android View geometry on the main thread; analyzer callbacks only read the cache. */
    fun updateScanRegion(scanRegion: RectF?): Boolean {
        scanRegionInPreview = scanRegion
            ?.takeIf { it.width() >= 2f && it.height() >= 2f }
            ?.let(::RectF)
        previewOutputTransform = previewView.outputTransform
        return scanRegionInPreview != null && previewOutputTransform != null
    }

    /** Makes analyzer acceptance fail closed until the next post-layout transform is cached. */
    fun invalidateScanRegion() {
        scanRegionInPreview = null
        previewOutputTransform = null
    }

    fun scanRegionBounds(): ScannerBounds? = scanRegionInPreview?.toScannerBounds()

    /** Applies the visible centre frame to ImageProxy without allocating or converting a bitmap. */
    fun cropToScanRegion(
        imageProxy: ImageProxy,
        horizontalInsetRatio: Float = 0f,
        verticalInsetRatio: Float = 0f,
    ): ScannerFrameTransform? {
        val previewTransform = previewOutputTransform ?: return null
        val visibleRegion = scanRegionInPreview?.let(::RectF) ?: return null
        val requestedRegion = insetSafely(
            visibleRegion,
            horizontalInsetRatio,
            verticalInsetRatio,
        )
        val originalImageCrop = Rect(imageProxy.cropRect)
        // This transform deliberately targets the unrotated, full camera buffer. setCropRect()
        // also consumes raw-buffer coordinates, and retaining the original CameraX viewport is
        // required for an accurate later mapping back to PreviewView.
        val rawImageTransform = ImageProxyTransformFactory().apply {
            isUsingCropRect = false
            isUsingRotationDegrees = false
        }.getOutputTransform(imageProxy)

        CoordinateTransform(previewTransform, rawImageTransform).mapRect(requestedRegion)
        val imageRegion = Rect()
        requestedRegion.roundOut(imageRegion)
        if (!imageRegion.intersect(originalImageCrop) ||
            imageRegion.width() < 2 ||
            imageRegion.height() < 2
        ) {
            return null
        }

        // NV21/YUV420 chroma samples cover 2x2 luma pixels. Keep the ROI origin and dimensions
        // even so the reusable cropped input has deterministic U/V alignment on every device.
        if (imageRegion.left % 2 != 0) imageRegion.left++
        if (imageRegion.top % 2 != 0) imageRegion.top++
        if (imageRegion.width() % 2 != 0) imageRegion.right--
        if (imageRegion.height() % 2 != 0) imageRegion.bottom--
        if (imageRegion.width() < 2 || imageRegion.height() < 2) return null

        val frameTransform = ScannerFrameTransform(
            cropMapping = ScannerCropMapping(
                left = imageRegion.left,
                top = imageRegion.top,
                width = imageRegion.width(),
                height = imageRegion.height(),
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
            ),
            rawImageToPreview = CoordinateTransform(rawImageTransform, previewTransform),
        )
        imageProxy.setCropRect(imageRegion)
        return frameTransform
    }

    fun mapImageRectToPreview(
        imageBounds: Rect?,
        frameTransform: ScannerFrameTransform,
    ): ScannerBounds? {
        val bounds = imageBounds ?: return null
        val rawBounds = frameTransform.cropMapping.mapRotatedBoundsToRaw(
            ScannerBounds(
                left = bounds.left.toFloat(),
                top = bounds.top.toFloat(),
                right = bounds.right.toFloat(),
                bottom = bounds.bottom.toFloat(),
            )
        ) ?: return null
        val previewBounds = RectF(
            rawBounds.left,
            rawBounds.top,
            rawBounds.right,
            rawBounds.bottom,
        )
        frameTransform.rawImageToPreview.mapRect(previewBounds)
        if (!previewBounds.left.isFinite() ||
            !previewBounds.top.isFinite() ||
            !previewBounds.right.isFinite() ||
            !previewBounds.bottom.isFinite() ||
            previewBounds.isEmpty
        ) {
            return null
        }
        return previewBounds.toScannerBounds()
    }

    fun setZoomRatio(
        requestedRatio: Float,
        productMaximum: Float? = null,
        tolerance: Float = 0.04f,
    ): CameraValueUpdate {
        val activeCamera = camera ?: return CameraValueUpdate.UNAVAILABLE
        val zoomState = activeCamera.cameraInfo.zoomState.value
            ?: return CameraValueUpdate.UNAVAILABLE
        val maximum = productMaximum
            ?.let { minOf(it, zoomState.maxZoomRatio) }
            ?: zoomState.maxZoomRatio
        if (maximum < zoomState.minZoomRatio) return CameraValueUpdate.UNAVAILABLE
        val targetRatio = requestedRatio.coerceIn(zoomState.minZoomRatio, maximum)
        if (abs(targetRatio - zoomState.zoomRatio) < tolerance) {
            return CameraValueUpdate.UNCHANGED
        }
        activeCamera.cameraControl.setZoomRatio(targetRatio)
        return CameraValueUpdate.APPLIED
    }

    fun setExposureCompensation(requestedIndex: Int): CameraValueUpdate {
        val activeCamera = camera ?: return CameraValueUpdate.UNAVAILABLE
        val exposureState = activeCamera.cameraInfo.exposureState
        if (!exposureState.isExposureCompensationSupported) return CameraValueUpdate.UNAVAILABLE
        val supportedRange = exposureState.exposureCompensationRange
        val targetIndex = requestedIndex.coerceIn(supportedRange.lower, supportedRange.upper)
        if (exposureState.exposureCompensationIndex == targetIndex) {
            return CameraValueUpdate.UNCHANGED
        }
        activeCamera.cameraControl.setExposureCompensationIndex(targetIndex)
        return CameraValueUpdate.APPLIED
    }

    fun setTorch(enabled: Boolean): Boolean {
        val activeCamera = camera ?: return false
        if (!activeCamera.cameraInfo.hasFlashUnit()) return false
        activeCamera.cameraControl.enableTorch(enabled)
        return true
    }

    fun focusOnScanRegion(
        force: Boolean,
        elapsedRealtimeMs: Long,
        cooldownMs: Long,
        onStarted: () -> Unit,
        onCompleted: (successful: Boolean) -> Unit,
    ): Boolean {
        val activeCamera = camera ?: return false
        val region = scanRegionInPreview ?: return false
        if (previewView.width == 0 || previewView.height == 0) return false
        if (!force && elapsedRealtimeMs - lastFocusMeteringAtMs < cooldownMs) return false

        val point = previewView.meteringPointFactory.createPoint(
            region.centerX().coerceIn(0f, previewView.width.toFloat()),
            region.centerY().coerceIn(0f, previewView.height.toFloat()),
            0.18f,
        )
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        )
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()

        lastFocusMeteringAtMs = elapsedRealtimeMs
        onStarted()
        val result = activeCamera.cameraControl.startFocusAndMetering(action)
        result.addListener(
            {
                onCompleted(runCatching { result.get().isFocusSuccessful }.getOrDefault(false))
            },
            mainExecutor,
        )
        return true
    }

    private fun buildResolutionSelector(width: Int, height: Int): ResolutionSelector {
        val targetResolution = Size(width, height)
        val aspectRatioStrategy = if (width * 9 == height * 16) {
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        } else {
            AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        }
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .setResolutionStrategy(
                ResolutionStrategy(
                    targetResolution,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                )
            )
            .build()
    }

    private fun insetSafely(
        region: RectF,
        horizontalInsetRatio: Float,
        verticalInsetRatio: Float,
    ): RectF {
        if (horizontalInsetRatio <= 0f && verticalInsetRatio <= 0f) return region
        val cropped = RectF(
            region.left + (region.width() * horizontalInsetRatio.coerceIn(0f, 0.2f)),
            region.top + (region.height() * verticalInsetRatio.coerceIn(0f, 0.3f)),
            region.right - (region.width() * horizontalInsetRatio.coerceIn(0f, 0.2f)),
            region.bottom - (region.height() * verticalInsetRatio.coerceIn(0f, 0.3f)),
        )
        return if (cropped.width() >= region.width() * 0.55f &&
            cropped.height() >= region.height() * 0.4f
        ) {
            cropped
        } else {
            region
        }
    }

    private fun RectF.toScannerBounds(): ScannerBounds = ScannerBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )
}
