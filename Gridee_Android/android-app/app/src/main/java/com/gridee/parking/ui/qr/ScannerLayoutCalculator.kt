package com.gridee.parking.ui.qr

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ScannerFrameLayout(
    val widthPx: Int,
    val heightPx: Int,
    val translationXPx: Float,
    val translationYPx: Float,
)

internal data class ScannerChromeLayout(
    val usesSideRails: Boolean,
    val usesNarrowSideRails: Boolean,
    val isLiveScanningSupported: Boolean,
    val topControlsTopMarginPx: Int,
    val statusTopMarginPx: Int,
    val sideRailGapPx: Int,
    val modeDockTopGapPx: Int,
    val sideRailContentWidthPx: Int,
)

internal data class ScannerSideRailCompensation(
    val startPx: Int,
    val endPx: Int,
)

internal data class ScannerLogicalSideInsets(
    val startPx: Int,
    val endPx: Int,
)

/**
 * Portrait stacks scanner chrome above and below the immutable centre ROI. Landscape moves the
 * controls into side rails so short screens cannot place either panel over the scan target.
 */
internal object ScannerChromeLayoutCalculator {
    private const val NARROW_RAIL_THRESHOLD_DP = 156
    private const val NARROW_HEIGHT_THRESHOLD_DP = 340
    private const val MINIMUM_ACTIVE_FRAME_DP = 144
    private const val MINIMUM_SIDE_RAIL_CONTENT_DP = 120
    private const val PORTRAIT_TOP_CHROME_BOTTOM_DP = 206
    private const val PORTRAIT_FRAME_CHROME_GAP_DP = 16
    private const val PORTRAIT_STATUS_FRAME_GAP_DP = 50
    private const val PORTRAIT_STATUS_MINIMUM_HEIGHT_DP = 162
    private const val PORTRAIT_CONTENT_BOTTOM_PADDING_DP = 36
    private const val STANDARD_RAIL_CONTROLS_HEIGHT_DP = 272
    private const val NARROW_RAIL_CONTROLS_HEIGHT_DP = 226

    fun usesSideRails(screenWidthPx: Int, screenHeightPx: Int): Boolean {
        return screenWidthPx > screenHeightPx
    }

    fun calculate(
        screenWidthPx: Int,
        screenHeightPx: Int,
        topInsetPx: Int,
        bottomInsetPx: Int,
        contentPaddingLeftPx: Int,
        contentPaddingRightPx: Int,
        frameWidthPx: Int,
        frameHeightPx: Int,
        density: Float,
        fontScale: Float,
    ): ScannerChromeLayout {
        val safeDensity = density.takeIf { it > 0f && it.isFinite() } ?: 1f
        fun dp(value: Int): Int = (value * safeDensity).roundToInt()
        val usesSideRails = usesSideRails(screenWidthPx, screenHeightPx)
        val standardGapPx = dp(12)
        val physicalRailWidthPx = ((screenWidthPx - frameWidthPx) / 2f).roundToInt()
        val railContentWithStandardGapPx = (
            physicalRailWidthPx -
                max(contentPaddingLeftPx, contentPaddingRightPx) -
                standardGapPx
            ).coerceAtLeast(0)
        val availableHeightDp = (screenHeightPx - topInsetPx).coerceAtLeast(0) / safeDensity
        val usesNarrowSideRails = usesSideRails && (
            railContentWithStandardGapPx < dp(NARROW_RAIL_THRESHOLD_DP) ||
                availableHeightDp < NARROW_HEIGHT_THRESHOLD_DP ||
                fontScale > 1.2f
            )
        val sideRailGapPx = if (usesSideRails) {
            dp(if (usesNarrowSideRails) 8 else 12)
        } else {
            0
        }
        val sideRailContentWidthPx = if (usesSideRails) {
            (
                physicalRailWidthPx -
                    max(contentPaddingLeftPx, contentPaddingRightPx) -
                    sideRailGapPx
                ).coerceAtLeast(0)
        } else {
            0
        }
        val minimumActiveFramePx = dp(MINIMUM_ACTIVE_FRAME_DP)
        val frameHasUsableSize = frameWidthPx >= minimumActiveFramePx &&
            frameHeightPx >= if (frameWidthPx == frameHeightPx) {
                minimumActiveFramePx
            } else {
                (minimumActiveFramePx * 0.64f).roundToInt()
            }
        val safeBottomPx = (screenHeightPx - bottomInsetPx).coerceAtLeast(topInsetPx)
        val isLiveScanningSupported = if (usesSideRails) {
            val controlsHeightPx = dp(
                if (usesNarrowSideRails) {
                    NARROW_RAIL_CONTROLS_HEIGHT_DP
                } else {
                    STANDARD_RAIL_CONTROLS_HEIGHT_DP
                }
            )
            val frameTopPx = (screenHeightPx - frameHeightPx) / 2f
            val frameBottomPx = frameTopPx + frameHeightPx
            frameHasUsableSize &&
                sideRailContentWidthPx >= dp(MINIMUM_SIDE_RAIL_CONTENT_DP) &&
                topInsetPx + controlsHeightPx <= safeBottomPx &&
                frameTopPx >= topInsetPx &&
                frameBottomPx <= safeBottomPx
        } else {
            val frameTopPx = (screenHeightPx - frameHeightPx) / 2f
            val frameBottomPx = frameTopPx + frameHeightPx
            val topChromeBottomPx = topInsetPx + dp(PORTRAIT_TOP_CHROME_BOTTOM_DP)
            val statusSafeBottomPx = safeBottomPx - dp(PORTRAIT_CONTENT_BOTTOM_PADDING_DP)
            val availableStatusHeightPx = statusSafeBottomPx -
                (frameBottomPx + dp(PORTRAIT_STATUS_FRAME_GAP_DP))
            frameHasUsableSize &&
                frameTopPx >= topChromeBottomPx + dp(PORTRAIT_FRAME_CHROME_GAP_DP) &&
                availableStatusHeightPx >= dp(PORTRAIT_STATUS_MINIMUM_HEIGHT_DP)
        }
        return ScannerChromeLayout(
            usesSideRails = usesSideRails,
            usesNarrowSideRails = usesNarrowSideRails,
            isLiveScanningSupported = isLiveScanningSupported,
            topControlsTopMarginPx = topInsetPx + dp(
                when {
                    usesNarrowSideRails -> 72
                    usesSideRails -> 88
                    else -> 20
                }
            ),
            statusTopMarginPx = if (usesSideRails) {
                topInsetPx + dp(if (usesNarrowSideRails) 72 else 84)
            } else {
                0
            },
            sideRailGapPx = sideRailGapPx,
            modeDockTopGapPx = if (usesSideRails) {
                dp(if (usesNarrowSideRails) 4 else 12)
            } else {
                dp(14)
            },
            sideRailContentWidthPx = sideRailContentWidthPx,
        )
    }

    fun logicalSideInsets(
        physicalLeftPx: Int,
        physicalRightPx: Int,
        isRtl: Boolean,
    ): ScannerLogicalSideInsets {
        return if (isRtl) {
            ScannerLogicalSideInsets(startPx = physicalRightPx, endPx = physicalLeftPx)
        } else {
            ScannerLogicalSideInsets(startPx = physicalLeftPx, endPx = physicalRightPx)
        }
    }

    fun sideRailCompensation(
        frameTranslationXPx: Float,
        isRtl: Boolean,
    ): ScannerSideRailCompensation {
        val physicalLeftCompensation = (-frameTranslationXPx).coerceAtLeast(0f).roundToInt()
        val physicalRightCompensation = frameTranslationXPx.coerceAtLeast(0f).roundToInt()
        return if (isRtl) {
            ScannerSideRailCompensation(
                startPx = physicalRightCompensation,
                endPx = physicalLeftCompensation,
            )
        } else {
            ScannerSideRailCompensation(
                startPx = physicalLeftCompensation,
                endPx = physicalRightCompensation,
            )
        }
    }
}

/** Deterministic frame sizing keeps the ROI centre independent of UI copy and system insets. */
internal object ScannerLayoutCalculator {
    private const val QR_ASPECT_RATIO = 1f
    private const val PLATE_HEIGHT_TO_WIDTH_RATIO = 0.64f
    private const val SIDE_RAIL_GAP_DP = 12
    private const val MINIMUM_SIDE_RAIL_CONTENT_DP = 120
    private const val LARGE_TEXT_MINIMUM_SIDE_RAIL_CONTENT_DP = 132
    private const val MINIMUM_DEGRADED_FRAME_WIDTH_DP = 96
    private const val MINIMUM_VERTICAL_EDGE_GAP_DP = 12
    private const val PORTRAIT_TOP_FRAME_RESERVATION_DP = 222
    private const val PORTRAIT_BOTTOM_FRAME_RESERVATION_DP = 248

    fun calculate(
        screenWidthPx: Int,
        screenHeightPx: Int,
        contentPaddingLeftPx: Int,
        contentPaddingRightPx: Int,
        contentPaddingBottomPx: Int,
        systemTopInsetPx: Int,
        systemBottomInsetPx: Int,
        density: Float,
        fontScale: Float = 1f,
        qrMode: Boolean,
        usesSideRails: Boolean,
    ): ScannerFrameLayout {
        val safeDensity = density.takeIf { it > 0f && it.isFinite() } ?: 1f
        fun dp(value: Int): Int = (value * safeDensity).roundToInt()
        val availableWidth = (
            screenWidthPx - contentPaddingLeftPx - contentPaddingRightPx
            ).coerceAtLeast(1)
        val preferredWidth = if (qrMode) {
            (availableWidth * 0.74f).roundToInt().coerceIn(dp(224), dp(320))
        } else {
            (availableWidth * 0.88f).roundToInt().coerceIn(dp(248), dp(356))
        }

        // The frame is physically centred, so reserve the larger system inset on both vertical
        // edges. This prevents a centred frame from being clipped while preserving its aspect.
        val verticalEdgeReservationPx = if (usesSideRails) {
            max(
                dp(MINIMUM_VERTICAL_EDGE_GAP_DP),
                max(systemTopInsetPx, systemBottomInsetPx),
            )
        } else {
            max(
                systemTopInsetPx + dp(PORTRAIT_TOP_FRAME_RESERVATION_DP),
                systemBottomInsetPx + dp(PORTRAIT_BOTTOM_FRAME_RESERVATION_DP),
            )
        }
        val maximumFrameHeightPx = (
            screenHeightPx - (verticalEdgeReservationPx * 2)
            ).coerceAtLeast(1)

        // Landscape controls occupy both physical side rails. Reserve a usable rail on each side
        // before sizing the centre ROI; on exceptionally small windows the ROI degrades only as
        // far as space genuinely permits and never grows beyond the preview.
        val maximumFrameWidthByRailsPx = if (usesSideRails) {
            val minimumRailContentDp = if (fontScale.isFinite() && fontScale > 1.2f) {
                LARGE_TEXT_MINIMUM_SIDE_RAIL_CONTENT_DP
            } else {
                MINIMUM_SIDE_RAIL_CONTENT_DP
            }
            val perSideReservationPx =
                max(contentPaddingLeftPx, contentPaddingRightPx) +
                    dp(SIDE_RAIL_GAP_DP) +
                    dp(minimumRailContentDp)
            (screenWidthPx - (perSideReservationPx * 2))
                .coerceAtLeast(min(dp(MINIMUM_DEGRADED_FRAME_WIDTH_DP), availableWidth))
        } else {
            availableWidth
        }
        val maximumFrameWidthPx = min(availableWidth, maximumFrameWidthByRailsPx)
            .coerceAtLeast(1)

        val targetWidth = if (qrMode) {
            min(preferredWidth, min(maximumFrameWidthPx, maximumFrameHeightPx))
                .coerceAtLeast(1)
        } else {
            val maximumWidthByHeightPx = floor(
                maximumFrameHeightPx / PLATE_HEIGHT_TO_WIDTH_RATIO
            ).toInt().coerceAtLeast(1)
            min(preferredWidth, min(maximumFrameWidthPx, maximumWidthByHeightPx))
                .coerceAtLeast(1)
        }
        val targetHeight = if (qrMode) {
            (targetWidth * QR_ASPECT_RATIO).roundToInt()
        } else {
            (targetWidth * PLATE_HEIGHT_TO_WIDTH_RATIO).roundToInt()
                .coerceAtMost(maximumFrameHeightPx)
        }
        return ScannerFrameLayout(
            widthPx = targetWidth,
            heightPx = targetHeight,
            translationXPx = (contentPaddingRightPx - contentPaddingLeftPx) / 2f,
            translationYPx = contentPaddingBottomPx / 2f,
        )
    }
}
