package com.gridee.parking.ui.qr

import java.nio.ByteBuffer
import kotlin.math.abs

internal data class ScannerFrameQuality(
    val meanLuma: Int,
    val detailScore: Double
)

/** Samples a small luma grid, keeping quality checks cheap enough to run before every ML frame. */
internal object ScannerFrameQualityEstimator {
    private const val GRID_COLUMNS = 18
    private const val GRID_ROWS = 12

    fun estimate(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): ScannerFrameQuality? {
        if (rowStride <= 0 || pixelStride <= 0 || right <= left || bottom <= top) return null

        val safeBuffer = buffer.duplicate()
        val baseOffset = safeBuffer.position()
        val width = right - left
        val height = bottom - top
        val columns = minOf(GRID_COLUMNS, width).coerceAtLeast(2)
        val rows = minOf(GRID_ROWS, height).coerceAtLeast(2)
        val samples = IntArray(columns * rows)

        var total = 0L
        var index = 0
        for (row in 0 until rows) {
            val y = top + ((row.toLong() * (height - 1)) / (rows - 1)).toInt()
            for (column in 0 until columns) {
                val x = left + ((column.toLong() * (width - 1)) / (columns - 1)).toInt()
                val bufferIndex = baseOffset.toLong() +
                    y.toLong() * rowStride.toLong() +
                    x.toLong() * pixelStride.toLong()
                if (bufferIndex !in baseOffset.toLong() until safeBuffer.limit().toLong()) return null
                val value = safeBuffer.get(bufferIndex.toInt()).toInt() and 0xFF
                samples[index++] = value
                total += value
            }
        }

        var detailTotal = 0L
        var detailPairs = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val sampleIndex = row * columns + column
                if (column + 1 < columns) {
                    detailTotal += abs(samples[sampleIndex] - samples[sampleIndex + 1])
                    detailPairs++
                }
                if (row + 1 < rows) {
                    detailTotal += abs(samples[sampleIndex] - samples[sampleIndex + columns])
                    detailPairs++
                }
            }
        }

        return ScannerFrameQuality(
            meanLuma = (total / samples.size).toInt(),
            detailScore = if (detailPairs == 0) 0.0 else detailTotal.toDouble() / detailPairs.toDouble()
        )
    }
}

internal class ScannerLightingMonitor(
    private val darkThreshold: Int = 46,
    private val recoveryThreshold: Int = 62,
    private val darkFramesRequired: Int = 4,
    private val recoveryFramesRequired: Int = 3
) {
    enum class Update {
        NONE,
        BECAME_DARK,
        RECOVERED
    }

    private var darkFrames = 0
    private var brightFrames = 0
    private var dark = false

    @Synchronized
    fun observe(meanLuma: Int): Update {
        if (!dark) {
            brightFrames = 0
            darkFrames = if (meanLuma <= darkThreshold) darkFrames + 1 else 0
            if (darkFrames >= darkFramesRequired) {
                dark = true
                darkFrames = 0
                return Update.BECAME_DARK
            }
            return Update.NONE
        }

        darkFrames = 0
        brightFrames = if (meanLuma >= recoveryThreshold) brightFrames + 1 else 0
        if (brightFrames >= recoveryFramesRequired) {
            dark = false
            brightFrames = 0
            return Update.RECOVERED
        }
        return Update.NONE
    }

    @Synchronized
    fun reset() {
        darkFrames = 0
        brightFrames = 0
        dark = false
    }
}

/** Hysteresis for reducing AE compensation only during sustained reflective overexposure. */
internal class ReflectivePlateExposureAdvisor(
    private val reflectiveThreshold: Int = 205,
    private val recoveryThreshold: Int = 178,
    private val reflectiveFramesRequired: Int = 3,
    private val recoveryFramesRequired: Int = 3,
) {
    enum class Update {
        NONE,
        REDUCE_EXPOSURE,
        RESTORE_EXPOSURE,
    }

    private var reflectiveFrames = 0
    private var recoveryFrames = 0
    private var reduced = false

    @Synchronized
    fun observe(meanLuma: Int): Update {
        if (!reduced) {
            recoveryFrames = 0
            reflectiveFrames = if (meanLuma >= reflectiveThreshold) reflectiveFrames + 1 else 0
            if (reflectiveFrames >= reflectiveFramesRequired) {
                reflectiveFrames = 0
                reduced = true
                return Update.REDUCE_EXPOSURE
            }
            return Update.NONE
        }

        reflectiveFrames = 0
        recoveryFrames = if (meanLuma <= recoveryThreshold) recoveryFrames + 1 else 0
        if (recoveryFrames >= recoveryFramesRequired) {
            recoveryFrames = 0
            reduced = false
            return Update.RESTORE_EXPOSURE
        }
        return Update.NONE
    }

    @Synchronized
    fun reset() {
        reflectiveFrames = 0
        recoveryFrames = 0
        reduced = false
    }
}

/**
 * Skips only clearly unusable plate frames and still lets every third rejected frame through. This
 * avoids starving OCR on unusual cameras while reducing work during severe darkness or blur.
 */
internal class PlateFrameUsabilityGate(
    private val minimumLuma: Int = 20,
    private val minimumDetailScore: Double = 2.2,
    private val forcedAnalysisInterval: Int = 3
) {
    private var rejectedFrames = 0

    @Synchronized
    fun shouldAnalyze(quality: ScannerFrameQuality): Boolean {
        val usable = quality.meanLuma >= minimumLuma && quality.detailScore >= minimumDetailScore
        if (usable) {
            rejectedFrames = 0
            return true
        }

        rejectedFrames++
        return rejectedFrames % forcedAnalysisInterval == 0
    }

    @Synchronized
    fun reset() {
        rejectedFrames = 0
    }
}
