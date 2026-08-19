package com.gridee.parking.utils

import android.graphics.Bitmap

/**
 * Hand-off state for the language-change animation.
 *
 * Changing the app locale recreates the activity, so the outgoing screen's
 * snapshot and the tap origin have to survive that. Mirrors the fields
 * [ThemeManager] uses for the theme-change reveal, minus the system-chrome
 * colours — a language change never alters the palette.
 */
object LocaleTransition {
    var bitmap: Bitmap? = null
    var center: IntArray? = null

    /** Endonym of the incoming language, e.g. "हिन्दी" — shown on the title card. */
    var languageLabel: String? = null

    /** Set once the reveal has run, so the landing screen knows to show the card. */
    var pendingConfirmationLabel: String? = null

    fun isPending(): Boolean = bitmap != null

    fun clear(recycleBitmap: Boolean) {
        if (recycleBitmap) {
            bitmap?.takeIf { !it.isRecycled }?.recycle()
        }
        bitmap = null
        center = null
        languageLabel = null
    }
}
