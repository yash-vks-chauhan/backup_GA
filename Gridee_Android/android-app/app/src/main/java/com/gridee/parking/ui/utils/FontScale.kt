package com.gridee.parking.ui.utils

import android.content.Context
import android.content.res.Configuration

/**
 * Upper bound on the effective system font scale. The user's "Font size" accessibility
 * setting is respected up to this value; beyond it we clamp so extreme settings (1.5x–1.8x)
 * can't overflow/clip layouts on narrow screens. Heroes (wallet balance, etc.) additionally
 * use autoSizeText, so this is a safety net for the remaining fixed-size text.
 */
const val MAX_FONT_SCALE = 1.30f

/**
 * Returns a context whose font scale is capped at [maxScale]. Apply from an Activity's
 * attachBaseContext: `super.attachBaseContext(newBase.withClampedFontScale())`.
 * Returns the same context untouched when the user's scale is already within bounds.
 */
fun Context.withClampedFontScale(maxScale: Float = MAX_FONT_SCALE): Context {
    if (resources.configuration.fontScale <= maxScale) return this
    val config = Configuration(resources.configuration)
    config.fontScale = maxScale
    return createConfigurationContext(config)
}
