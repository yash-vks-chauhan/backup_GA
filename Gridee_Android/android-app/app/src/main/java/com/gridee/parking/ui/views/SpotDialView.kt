package com.gridee.parking.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.gridee.parking.R
import com.gridee.parking.ui.motion.AnimatorSettingsCompat

/**
 * The app's 44dp circular row tile, made live: a ring showing how much of a lot is
 * free with the free-spot count as the numeral inside. Same geometry as the icon
 * tiles on Profile, Wallet and Booking History, so picker rows sit naturally beside
 * the rest of the app — but this one carries real data instead of a static glyph.
 *
 * [setSpots] sweeps the ring open and counts the numeral up on first bind. The view
 * is purely informational — selection is communicated by the row's check and the
 * choice dock, not by the dial.
 *
 * Sizes are density-based rather than sp so the dial's geometry stays exact at any
 * font scale. Motion respects the system animator toggle.
 */
class SpotDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        const val SWEEP_MS = 720L
        const val START_ANGLE = -90f
        const val TRACK_ALPHA = 90
    }

    private val density = resources.displayMetrics.density
    private val ringWidth = 2.5f * density
    private val ringInset = 2f * density

    private val fillInk = ContextCompat.getColor(context, R.color.circle_icon_bg)
    private val ink = ContextCompat.getColor(context, R.color.text_primary)
    private val mutedInk = ContextCompat.getColor(context, R.color.text_tertiary)
    private val trackInk = ContextCompat.getColor(context, R.color.divider_hairline)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillInk
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
        strokeCap = Paint.Cap.ROUND
        color = trackInk
        alpha = TRACK_ALPHA
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
        strokeCap = Paint.Cap.ROUND
        color = ink
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * density
        fontFeatureSettings = "tnum"
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_semibold) }
            .getOrNull() ?: Typeface.DEFAULT_BOLD
    }

    private val ringBounds = RectF()
    // easeOutCubic — the app's content-reveal curve (matches SkeletonShimmer).
    private val smoothDecelerate = PathInterpolator(0.33f, 1f, 0.68f, 1f)

    private var total = 0
    private var available = 0

    /** 0..1 sweep of the ring; also drives the numeral count-up. */
    private var sweep = 1f
    private var sweepAnimator: ValueAnimator? = null

    fun setSpots(total: Int, available: Int, animate: Boolean) {
        val t = total.coerceAtLeast(0)
        val a = available.coerceIn(0, t)
        if (t == this.total && a == this.available) return
        this.total = t
        this.available = a

        sweepAnimator?.cancel()
        if (!animate || !AnimatorSettingsCompat.areEnabled(context)) {
            sweep = 1f
            invalidate()
            return
        }
        sweep = 0f
        sweepAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SWEEP_MS
            interpolator = smoothDecelerate
            addUpdateListener {
                sweep = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f

        canvas.drawCircle(cx, cy, radius, fillPaint)

        val ringRadius = radius - ringInset - ringWidth / 2f
        ringBounds.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        canvas.drawCircle(cx, cy, ringRadius, trackPaint)

        val hasData = total > 0
        val fraction = if (hasData) available.toFloat() / total else 0f
        if (fraction > 0f) {
            canvas.drawArc(ringBounds, START_ANGLE, 360f * fraction * sweep, false, arcPaint)
        }

        val shown = if (hasData) Math.round(available * sweep) else 0
        val label = if (hasData) shown.toString() else "–"
        textPaint.color = if (hasData && available > 0) ink else mutedInk
        // Center on the glyph box rather than the font's full line box.
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, cx, baseline, textPaint)
    }

    override fun onDetachedFromWindow() {
        sweepAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
