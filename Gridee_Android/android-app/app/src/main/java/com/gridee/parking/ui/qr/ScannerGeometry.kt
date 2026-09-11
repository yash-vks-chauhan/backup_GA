package com.gridee.parking.ui.qr

import kotlin.math.hypot

/** Android-independent rectangle math used by QR acceptance and OCR spatial ranking. */
internal data class ScannerBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val isUsable: Boolean get() = width > 0f && height > 0f
}

internal object ScannerGeometry {
    /** Fraction of [subject]'s area that lies inside [region]. */
    fun containmentRatio(subject: ScannerBounds, region: ScannerBounds): Float {
        if (!subject.isUsable || !region.isUsable) return 0f
        val intersectionWidth = (minOf(subject.right, region.right) -
            maxOf(subject.left, region.left)).coerceAtLeast(0f)
        val intersectionHeight = (minOf(subject.bottom, region.bottom) -
            maxOf(subject.top, region.top)).coerceAtLeast(0f)
        return ((intersectionWidth * intersectionHeight) / subject.area).coerceIn(0f, 1f)
    }

    /** 0 is centred; 1 is approximately one ROI half-diagonal away or farther. */
    fun normalizedCenterDistance(subject: ScannerBounds, region: ScannerBounds): Float {
        if (!subject.isUsable || !region.isUsable) return 1f
        val halfDiagonal = hypot(region.width / 2f, region.height / 2f)
        if (halfDiagonal <= 0f) return 1f
        return (hypot(subject.centerX - region.centerX, subject.centerY - region.centerY) /
            halfDiagonal).coerceIn(0f, 1f)
    }
}

/** Fail-closed centre-square policy for an operator QR result. */
internal class OperatorQrAcceptancePolicy(
    private val minimumContainmentRatio: Float = 0.70f,
) {
    init {
        require(minimumContainmentRatio in 0f..1f)
    }

    fun accepts(barcodeBounds: ScannerBounds?, scanRegion: ScannerBounds): Boolean {
        val bounds = barcodeBounds ?: return false
        return bounds.isUsable &&
            scanRegion.isUsable &&
            ScannerGeometry.containmentRatio(bounds, scanRegion) >= minimumContainmentRatio
    }
}
