package com.gridee.parking.ui.views

import android.content.Context
import android.text.format.DateUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * A TextView that renders a "time ago" label and updates itself while it is attached.
 *
 * Update cadence:
 * - < 1 minute: every second
 * - < 1 hour: every minute
 * - < 1 day: every hour
 * - otherwise: daily
 */
class RelativeTimeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var referenceTimeMillis: Long? = null
    private var isInternalTextUpdate = false

    private val updateRunnable = Runnable {
        if (referenceTimeMillis == null) return@Runnable
        updateText()
        scheduleNextUpdate()
    }

    fun setReferenceTime(referenceTimeMillis: Long) {
        this.referenceTimeMillis = referenceTimeMillis
        if (isAttachedToWindow) {
            updateText()
            scheduleNextUpdate()
        }
    }

    fun clearReferenceTime() {
        referenceTimeMillis = null
        removeCallbacks(updateRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (referenceTimeMillis != null) {
            updateText()
            scheduleNextUpdate()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(updateRunnable)
        super.onDetachedFromWindow()
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        if (!isInternalTextUpdate) {
            // If something sets text directly, treat it as an override and stop relative updates.
            referenceTimeMillis = null
            removeCallbacks(updateRunnable)
        }
        super.setText(text, type)
    }

    private fun updateText() {
        val reference = referenceTimeMillis ?: return
        val now = System.currentTimeMillis()
        val relative = DateUtils.getRelativeTimeSpanString(
            reference,
            now,
            DateUtils.SECOND_IN_MILLIS
        ).toString()

        isInternalTextUpdate = true
        try {
            super.setText(relative, BufferType.NORMAL)
        } finally {
            isInternalTextUpdate = false
        }
    }

    private fun scheduleNextUpdate() {
        removeCallbacks(updateRunnable)

        val reference = referenceTimeMillis ?: return
        val now = System.currentTimeMillis()
        val diff = kotlin.math.abs(now - reference)

        val delayMillis = when {
            diff < DateUtils.MINUTE_IN_MILLIS -> DateUtils.SECOND_IN_MILLIS
            diff < DateUtils.HOUR_IN_MILLIS -> DateUtils.MINUTE_IN_MILLIS
            diff < DateUtils.DAY_IN_MILLIS -> DateUtils.HOUR_IN_MILLIS
            else -> DateUtils.DAY_IN_MILLIS
        }

        postDelayed(updateRunnable, delayMillis)
    }
}

