package com.gridee.parking.ui.qr

/**
 * Maps ML Kit coordinates from a physically cropped, rotated input back to the original camera
 * buffer. CameraX can then map those raw-buffer coordinates to PreviewView with the original
 * shared viewport intact.
 */
internal data class ScannerCropMapping(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
) {
    init {
        require(width > 0 && height > 0)
        require(rotationDegrees == 0 || rotationDegrees == 90 ||
            rotationDegrees == 180 || rotationDegrees == 270)
    }

    fun mapRotatedBoundsToRaw(bounds: ScannerBounds): ScannerBounds? {
        if (!bounds.isUsable ||
            !bounds.left.isFinite() ||
            !bounds.top.isFinite() ||
            !bounds.right.isFinite() ||
            !bounds.bottom.isFinite()
        ) {
            return null
        }

        val localRaw = when (rotationDegrees) {
            0 -> bounds
            90 -> ScannerBounds(
                left = bounds.top,
                top = height - bounds.right,
                right = bounds.bottom,
                bottom = height - bounds.left,
            )
            180 -> ScannerBounds(
                left = width - bounds.right,
                top = height - bounds.bottom,
                right = width - bounds.left,
                bottom = height - bounds.top,
            )
            270 -> ScannerBounds(
                left = width - bounds.bottom,
                top = bounds.left,
                right = width - bounds.top,
                bottom = bounds.right,
            )
            else -> return null
        }
        if (!localRaw.isUsable) return null
        return ScannerBounds(
            left = left + localRaw.left,
            top = top + localRaw.top,
            right = left + localRaw.right,
            bottom = top + localRaw.bottom,
        )
    }
}
