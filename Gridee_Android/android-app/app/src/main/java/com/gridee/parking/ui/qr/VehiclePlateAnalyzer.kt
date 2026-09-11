package com.gridee.parking.ui.qr

import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Owns the ML Kit OCR client and the bounded, line-first plate candidate pipeline. */
internal class VehiclePlateAnalyzer(
    private val interpreter: VehiclePlateInterpreter,
    private val frameRanker: PlateCandidateFrameRanker = PlateCandidateFrameRanker(),
    private val consensus: PlateCandidateConsensus = PlateCandidateConsensus(),
) {
    val detector: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun process(image: InputImage): Task<Text> = detector.process(image)

    fun selectBestCandidate(
        text: Text,
        scanRegion: ScannerBounds?,
        mapBoundsToPreview: (Rect?) -> ScannerBounds?,
    ): NormalizedPlateCandidate? {
        frameRanker.reset()
        text.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                observe(
                    raw = line.text,
                    source = PlateCandidateSource.LINE,
                    bounds = mapBoundsToPreview(line.boundingBox),
                    scanRegion = scanRegion,
                )
            }
            observe(
                raw = block.text,
                source = PlateCandidateSource.BLOCK,
                bounds = mapBoundsToPreview(block.boundingBox),
                scanRegion = scanRegion,
            )
        }
        observe(
            raw = text.text,
            source = PlateCandidateSource.FULL_TEXT,
            bounds = null,
            scanRegion = scanRegion,
        )
        return frameRanker.best()?.let {
            NormalizedPlateCandidate(it.value, it.correctedCharacters)
        }
    }

    fun close() = detector.close()

    fun observeConsensus(
        candidate: NormalizedPlateCandidate,
        observedAtMs: Long,
        cleanRequiredMatches: Int,
        correctedRequiredMatches: Int,
        allowedGapMs: Long,
    ): NormalizedPlateCandidate? {
        val requiredMatches = if (candidate.correctedCharacters > 0) {
            correctedRequiredMatches
        } else {
            cleanRequiredMatches
        }
        return consensus.observe(
            candidate = candidate.value,
            observedAtMs = observedAtMs,
            requiredMatches = requiredMatches,
            allowedGapMs = allowedGapMs,
        )?.let { confirmedValue -> candidate.copy(value = confirmedValue) }
    }

    fun clearConsensus() = consensus.clear()

    private fun observe(
        raw: String,
        source: PlateCandidateSource,
        bounds: ScannerBounds?,
        scanRegion: ScannerBounds?,
    ) {
        val candidate = interpreter.normalizeCandidate(raw) ?: return
        frameRanker.observe(
            candidate = candidate,
            formatScore = interpreter.score(candidate.value),
            source = source,
            bounds = bounds,
            scanRegion = scanRegion,
        )
    }
}
