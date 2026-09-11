package com.gridee.parking.ui.qr

/** Drawable/color cache used by the scanner panel to avoid repeating identical View mutations. */
internal class ScannerPanelVisualCache {
    enum class Field {
        PANEL_BACKGROUND,
        ICON_BACKGROUND,
        ICON_RESOURCE,
        ICON_TINT_COLOR,
        BADGE_BACKGROUND,
        BADGE_TEXT_COLOR,
        PROGRESS_TINT_COLOR,
    }

    private val initialized = BooleanArray(Field.entries.size)
    private val values = IntArray(Field.entries.size)

    fun shouldApply(field: Field, value: Int): Boolean {
        val index = field.ordinal
        if (initialized[index] && values[index] == value) return false
        initialized[index] = true
        values[index] = value
        return true
    }

    fun reset() {
        initialized.fill(false)
        values.fill(0)
    }
}
