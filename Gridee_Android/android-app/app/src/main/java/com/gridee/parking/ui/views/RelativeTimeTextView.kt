package com.gridee.parking.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.gridee.parking.R
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

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
        val elapsed = (now - reference).coerceAtLeast(0L)
        val relative = when {
            elapsed < MINUTE_MILLIS -> context.getString(R.string.just_now)
            elapsed < HOUR_MILLIS -> {
                val minutes = (elapsed / MINUTE_MILLIS).toInt()
                resources.getQuantityString(R.plurals.minutes_ago, minutes, minutes)
            }
            elapsed < DAY_MILLIS -> {
                val hours = (elapsed / HOUR_MILLIS).toInt()
                resources.getQuantityString(R.plurals.hours_ago, hours, hours)
            }
            elapsed < WEEK_MILLIS -> {
                val days = (elapsed / DAY_MILLIS).toInt()
                resources.getQuantityString(R.plurals.days_ago, days, days)
            }
            else -> {
                val locale = resources.configuration.locales[0]
                DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                }.format(Date(reference))
            }
        }

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
            diff < MINUTE_MILLIS -> SECOND_MILLIS
            diff < HOUR_MILLIS -> MINUTE_MILLIS
            diff < DAY_MILLIS -> HOUR_MILLIS
            else -> DAY_MILLIS
        }

        postDelayed(updateRunnable, delayMillis)
    }

    private companion object {
        const val SECOND_MILLIS = 1_000L
        const val MINUTE_MILLIS = 60 * SECOND_MILLIS
        const val HOUR_MILLIS = 60 * MINUTE_MILLIS
        const val DAY_MILLIS = 24 * HOUR_MILLIS
        const val WEEK_MILLIS = 7 * DAY_MILLIS
    }
}
