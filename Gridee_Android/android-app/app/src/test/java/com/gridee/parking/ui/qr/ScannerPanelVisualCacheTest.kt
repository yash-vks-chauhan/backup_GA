package com.gridee.parking.ui.qr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerPanelVisualCacheTest {

    @Test
    fun `applies a visual value once until it changes`() {
        val cache = ScannerPanelVisualCache()

        assertTrue(cache.shouldApply(ScannerPanelVisualCache.Field.ICON_RESOURCE, 10))
        assertFalse(cache.shouldApply(ScannerPanelVisualCache.Field.ICON_RESOURCE, 10))
        assertTrue(cache.shouldApply(ScannerPanelVisualCache.Field.ICON_RESOURCE, 11))
    }

    @Test
    fun `tracks visual fields independently`() {
        val cache = ScannerPanelVisualCache()

        assertTrue(cache.shouldApply(ScannerPanelVisualCache.Field.ICON_RESOURCE, 10))
        assertTrue(cache.shouldApply(ScannerPanelVisualCache.Field.ICON_TINT_COLOR, 10))
        assertFalse(cache.shouldApply(ScannerPanelVisualCache.Field.ICON_RESOURCE, 10))
        assertFalse(cache.shouldApply(ScannerPanelVisualCache.Field.ICON_TINT_COLOR, 10))
    }

    @Test
    fun `reset reapplies an unchanged visual value`() {
        val cache = ScannerPanelVisualCache()

        assertTrue(cache.shouldApply(ScannerPanelVisualCache.Field.PANEL_BACKGROUND, 20))
        assertFalse(cache.shouldApply(ScannerPanelVisualCache.Field.PANEL_BACKGROUND, 20))
        cache.reset()

        assertTrue(cache.shouldApply(ScannerPanelVisualCache.Field.PANEL_BACKGROUND, 20))
    }
}
