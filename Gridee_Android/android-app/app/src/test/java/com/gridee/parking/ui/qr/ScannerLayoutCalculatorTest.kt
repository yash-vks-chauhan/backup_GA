package com.gridee.parking.ui.qr

import kotlin.math.max
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerLayoutCalculatorTest {

    @Test
    fun `QR frame remains square and inside the preview across phone widths`() {
        listOf(720, 1080, 1440).forEach { width ->
            val frame = calculateFrame(
                screenWidthPx = width,
                screenHeightPx = 2_400,
                paddingLeftPx = 48,
                paddingRightPx = 48,
                bottomPaddingPx = 72,
                density = 3f,
                qrMode = true,
            )

            assertEquals(frame.widthPx, frame.heightPx)
            assertTrue(frame.widthPx <= width - 96)
            assertTrue(frame.heightPx <= 2_400)
        }
    }

    @Test
    fun `plate frame remains a centred horizontal rectangle`() {
        val frame = calculateFrame(
            screenWidthPx = 1_080,
            screenHeightPx = 2_400,
            paddingLeftPx = 72,
            paddingRightPx = 72,
            bottomPaddingPx = 96,
            density = 3f,
            qrMode = false,
        )

        assertTrue(frame.widthPx > frame.heightPx)
        assertEquals(frame.widthPx * 0.64f, frame.heightPx.toFloat(), 1f)
        assertEquals(48f, frame.translationYPx, 0.001f)
    }

    @Test
    fun `bottom inset correction restores physical preview centre`() {
        val previewHeight = 2_400f
        val bottomPadding = 120
        val constrainedContentCentre = (previewHeight - bottomPadding) / 2f
        val frame = calculateFrame(
            screenWidthPx = 1_080,
            screenHeightPx = previewHeight.toInt(),
            paddingLeftPx = 72,
            paddingRightPx = 72,
            bottomPaddingPx = bottomPadding,
            density = 3f,
            qrMode = false,
        )

        assertEquals(previewHeight / 2f, constrainedContentCentre + frame.translationYPx, 0.001f)
    }

    @Test
    fun `asymmetric side insets restore physical preview centre`() {
        val previewWidth = 1_080f
        val leftPadding = 120
        val rightPadding = 24
        val constrainedContentCentre = leftPadding +
            (previewWidth - leftPadding - rightPadding) / 2f
        val frame = calculateFrame(
            screenWidthPx = previewWidth.toInt(),
            screenHeightPx = 2_400,
            paddingLeftPx = leftPadding,
            paddingRightPx = rightPadding,
            bottomPaddingPx = 0,
            density = 3f,
            qrMode = true,
        )

        assertEquals(previewWidth / 2f, constrainedContentCentre + frame.translationXPx, 0.001f)
    }

    @Test
    fun `small high-density portrait never inflates a frame beyond real content`() {
        listOf(true, false).forEach { qrMode ->
            val frame = calculateFrame(
                screenWidthPx = 720,
                screenHeightPx = 1_280,
                paddingLeftPx = 72,
                paddingRightPx = 72,
                bottomPaddingPx = 108,
                density = 3f,
                qrMode = qrMode,
            )

            assertTrue(frame.widthPx <= 576)
            assertTrue(frame.heightPx <= 1_280)
            if (qrMode) assertEquals(frame.widthPx, frame.heightPx)
        }
    }

    @Test
    fun `small landscape matrix keeps ROI complete and reserves usable rails`() {
        listOf(2f, 2.625f, 3f).forEach { density ->
            val padding = (24 * density).roundToInt()
            val topInset = (24 * density).roundToInt()
            listOf(true, false).forEach { qrMode ->
                val frame = calculateFrame(
                    screenWidthPx = 1_280,
                    screenHeightPx = 720,
                    paddingLeftPx = padding,
                    paddingRightPx = padding,
                    bottomPaddingPx = (36 * density).roundToInt() + topInset,
                    topInsetPx = topInset,
                    bottomInsetPx = topInset,
                    density = density,
                    qrMode = qrMode,
                )
                val chrome = calculateChrome(
                    screenWidthPx = 1_280,
                    screenHeightPx = 720,
                    topInsetPx = topInset,
                    bottomInsetPx = topInset,
                    paddingLeftPx = padding,
                    paddingRightPx = padding,
                    frameWidthPx = frame.widthPx,
                    frameHeightPx = frame.heightPx,
                    density = density,
                )
                val verticalEdge = max(topInset, (12 * density).roundToInt())

                assertTrue(frame.widthPx <= 1_280 - (padding * 2))
                assertTrue(frame.heightPx <= 720 - (verticalEdge * 2))
                assertTrue(chrome.usesSideRails)
                assertTrue(chrome.usesNarrowSideRails)
                assertTrue(chrome.sideRailContentWidthPx >= (120 * density).roundToInt())
                assertEquals(density <= 2.625f, chrome.isLiveScanningSupported)
                assertEquals(
                    density <= 2.625f,
                    chrome.topControlsTopMarginPx +
                        (154 * density).roundToInt() <= 720 - topInset,
                )
                if (qrMode) {
                    assertEquals(frame.widthPx, frame.heightPx)
                } else {
                    assertEquals(frame.widthPx * 0.64f, frame.heightPx.toFloat(), 1f)
                }
                if (density == 2.625f) {
                    val bottomPadding = (36 * density).roundToInt() + topInset
                    assertEquals(460, frame.widthPx)
                    assertEquals(if (qrMode) 460 else 294, frame.heightPx)
                    assertEquals(
                        640f,
                        padding + (1_280 - (padding * 2)) / 2f + frame.translationXPx,
                        0.001f,
                    )
                    assertEquals(
                        360f,
                        (720 - bottomPadding) / 2f + frame.translationYPx,
                        0.001f,
                    )
                }
            }
        }
    }

    @Test
    fun `normal landscape retains production-sized centre frame and standard rails`() {
        val density = 2.625f
        val padding = (24 * density).roundToInt()
        val frame = calculateFrame(
            screenWidthPx = 2_400,
            screenHeightPx = 1_080,
            paddingLeftPx = padding,
            paddingRightPx = padding,
            bottomPaddingPx = (36 * density).roundToInt(),
            density = density,
            qrMode = true,
        )
        val chrome = calculateChrome(
            screenWidthPx = 2_400,
            screenHeightPx = 1_080,
            topInsetPx = 0,
            paddingLeftPx = padding,
            paddingRightPx = padding,
            frameWidthPx = frame.widthPx,
            density = density,
        )

        assertEquals((320 * density).roundToInt(), frame.widthPx)
        assertEquals(frame.widthPx, frame.heightPx)
        assertTrue(chrome.usesSideRails)
        assertFalse(chrome.usesNarrowSideRails)
        assertTrue(chrome.isLiveScanningSupported)
    }

    @Test
    fun `large font scale selects scrollable narrow rail treatment`() {
        val chrome = calculateChrome(
            screenWidthPx = 2_400,
            screenHeightPx = 1_080,
            topInsetPx = 0,
            paddingLeftPx = 63,
            paddingRightPx = 63,
            frameWidthPx = 840,
            density = 2.625f,
            fontScale = 1.5f,
        )

        assertTrue(chrome.usesSideRails)
        assertTrue(chrome.usesNarrowSideRails)
        assertEquals((72 * 2.625f).roundToInt(), chrome.topControlsTopMarginPx)
    }

    @Test
    fun `large text reserves wider landscape rails without moving the ROI centre`() {
        val regular = calculateFrame(
            screenWidthPx = 1_280,
            screenHeightPx = 720,
            paddingLeftPx = 48,
            paddingRightPx = 120,
            bottomPaddingPx = 96,
            density = 2f,
            fontScale = 1f,
            qrMode = false,
        )
        val largeText = calculateFrame(
            screenWidthPx = 1_280,
            screenHeightPx = 720,
            paddingLeftPx = 48,
            paddingRightPx = 120,
            bottomPaddingPx = 96,
            density = 2f,
            fontScale = 1.5f,
            qrMode = false,
        )

        assertTrue(largeText.widthPx < regular.widthPx)
        assertTrue(largeText.widthPx >= (144 * 2f).roundToInt())
        assertEquals(regular.translationXPx, largeText.translationXPx, 0.001f)
        assertEquals(regular.translationYPx, largeText.translationYPx, 0.001f)
    }

    @Test
    fun `landscape moves scanner chrome into side rails below top system controls`() {
        val density = 2.625f
        val topInset = 63
        val chrome = calculateChrome(
            screenWidthPx = 2_400,
            screenHeightPx = 1_080,
            topInsetPx = topInset,
            paddingLeftPx = 63,
            paddingRightPx = 63,
            frameWidthPx = 840,
            density = density,
        )

        assertTrue(chrome.usesSideRails)
        assertFalse(chrome.usesNarrowSideRails)
        assertTrue(chrome.isLiveScanningSupported)
        assertEquals(topInset + (88 * density).roundToInt(), chrome.topControlsTopMarginPx)
        assertEquals(topInset + (84 * density).roundToInt(), chrome.statusTopMarginPx)
        assertTrue(chrome.sideRailGapPx > 0)
    }

    @Test
    fun `portrait retains stacked scanner chrome`() {
        val chrome = calculateChrome(
            screenWidthPx = 1_080,
            screenHeightPx = 2_400,
            topInsetPx = 72,
            paddingLeftPx = 72,
            paddingRightPx = 72,
            frameWidthPx = 706,
            density = 3f,
        )

        assertFalse(chrome.usesSideRails)
        assertFalse(chrome.usesNarrowSideRails)
        assertTrue(chrome.isLiveScanningSupported)
        assertEquals(132, chrome.topControlsTopMarginPx)
        assertEquals(0, chrome.statusTopMarginPx)
        assertEquals(0, chrome.sideRailGapPx)
        assertEquals(0, chrome.sideRailContentWidthPx)
    }

    @Test
    fun `small portrait explicitly disables live scanning when centred ROI and chrome cannot coexist`() {
        val density = 2.625f
        val inset = (24 * density).roundToInt()
        val padding = (24 * density).roundToInt()
        val frame = calculateFrame(
            screenWidthPx = 720,
            screenHeightPx = 1_280,
            paddingLeftPx = padding,
            paddingRightPx = padding,
            bottomPaddingPx = (36 * density).roundToInt() + inset,
            topInsetPx = inset,
            bottomInsetPx = inset,
            density = density,
            qrMode = true,
        )
        val chrome = calculateChrome(
            screenWidthPx = 720,
            screenHeightPx = 1_280,
            topInsetPx = inset,
            bottomInsetPx = inset,
            paddingLeftPx = padding,
            paddingRightPx = padding,
            frameWidthPx = frame.widthPx,
            frameHeightPx = frame.heightPx,
            density = density,
            fontScale = 1.5f,
        )

        assertFalse(chrome.usesSideRails)
        assertFalse(chrome.isLiveScanningSupported)
    }

    @Test
    fun `extreme landscape fails closed instead of silently scanning a tiny ROI`() {
        val density = 3f
        val padding = (24 * density).roundToInt()
        val inset = (24 * density).roundToInt()
        val frame = calculateFrame(
            screenWidthPx = 960,
            screenHeightPx = 540,
            paddingLeftPx = padding,
            paddingRightPx = padding,
            bottomPaddingPx = (36 * density).roundToInt() + inset,
            topInsetPx = inset,
            bottomInsetPx = inset,
            density = density,
            qrMode = true,
        )
        val chrome = calculateChrome(
            screenWidthPx = 960,
            screenHeightPx = 540,
            topInsetPx = inset,
            bottomInsetPx = inset,
            paddingLeftPx = padding,
            paddingRightPx = padding,
            frameWidthPx = frame.widthPx,
            frameHeightPx = frame.heightPx,
            density = density,
        )

        assertTrue(frame.widthPx < (144 * density).roundToInt())
        assertFalse(chrome.isLiveScanningSupported)
    }

    @Test
    fun `logical top-button insets follow the physical side in LTR and RTL`() {
        val ltr = ScannerChromeLayoutCalculator.logicalSideInsets(
            physicalLeftPx = 110,
            physicalRightPx = 24,
            isRtl = false,
        )
        val rtl = ScannerChromeLayoutCalculator.logicalSideInsets(
            physicalLeftPx = 110,
            physicalRightPx = 24,
            isRtl = true,
        )

        assertEquals(110, ltr.startPx)
        assertEquals(24, ltr.endPx)
        assertEquals(24, rtl.startPx)
        assertEquals(110, rtl.endPx)
    }

    @Test
    fun `side rail compensation follows physical inset direction in LTR`() {
        val rightShift = ScannerChromeLayoutCalculator.sideRailCompensation(
            frameTranslationXPx = 24f,
            isRtl = false,
        )
        val leftShift = ScannerChromeLayoutCalculator.sideRailCompensation(
            frameTranslationXPx = -18f,
            isRtl = false,
        )

        assertEquals(0, rightShift.startPx)
        assertEquals(24, rightShift.endPx)
        assertEquals(18, leftShift.startPx)
        assertEquals(0, leftShift.endPx)
    }

    @Test
    fun `side rail compensation swaps logical rails in RTL`() {
        val rightShift = ScannerChromeLayoutCalculator.sideRailCompensation(
            frameTranslationXPx = 24f,
            isRtl = true,
        )
        val leftShift = ScannerChromeLayoutCalculator.sideRailCompensation(
            frameTranslationXPx = -18f,
            isRtl = true,
        )

        assertEquals(24, rightShift.startPx)
        assertEquals(0, rightShift.endPx)
        assertEquals(0, leftShift.startPx)
        assertEquals(18, leftShift.endPx)
    }

    private fun calculateFrame(
        screenWidthPx: Int,
        screenHeightPx: Int,
        paddingLeftPx: Int,
        paddingRightPx: Int,
        bottomPaddingPx: Int,
        density: Float,
        fontScale: Float = 1f,
        qrMode: Boolean,
        topInsetPx: Int = 0,
        bottomInsetPx: Int = 0,
    ): ScannerFrameLayout {
        return ScannerLayoutCalculator.calculate(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            contentPaddingLeftPx = paddingLeftPx,
            contentPaddingRightPx = paddingRightPx,
            contentPaddingBottomPx = bottomPaddingPx,
            systemTopInsetPx = topInsetPx,
            systemBottomInsetPx = bottomInsetPx,
            density = density,
            fontScale = fontScale,
            qrMode = qrMode,
            usesSideRails = ScannerChromeLayoutCalculator.usesSideRails(
                screenWidthPx,
                screenHeightPx,
            ),
        )
    }

    private fun calculateChrome(
        screenWidthPx: Int,
        screenHeightPx: Int,
        topInsetPx: Int,
        bottomInsetPx: Int = 0,
        paddingLeftPx: Int,
        paddingRightPx: Int,
        frameWidthPx: Int,
        frameHeightPx: Int = frameWidthPx,
        density: Float,
        fontScale: Float = 1f,
    ): ScannerChromeLayout {
        return ScannerChromeLayoutCalculator.calculate(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
            contentPaddingLeftPx = paddingLeftPx,
            contentPaddingRightPx = paddingRightPx,
            frameWidthPx = frameWidthPx,
            frameHeightPx = frameHeightPx,
            density = density,
            fontScale = fontScale,
        )
    }
}
