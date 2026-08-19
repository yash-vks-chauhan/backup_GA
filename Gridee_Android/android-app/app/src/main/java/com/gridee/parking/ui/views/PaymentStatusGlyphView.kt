package com.gridee.parking.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import com.gridee.parking.R

/**
 * The status mark at the top of the payment-outcome sheet.
 *
 * Drawn rather than shipped as a static asset so the mark can *resolve* in front of the user:
 * the ring sweeps closed, then the glyph strokes itself on. A payment ending is a moment the
 * user is anxious about, and a mark that finishes drawing reads as "this is settled" in a way a
 * static icon does not.
 *
 * Colour discipline: everything is neutral except [State.PENDING], which is the one case that is
 * genuinely still in motion and earns the single gold accent.
 */
class PaymentStatusGlyphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { CANCELLED, PENDING, UNCONFIRMED, PAID }

    private val density = resources.displayMetrics.density

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.divider)
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val ringBounds = RectF()
    private val glyphPath = Path()
    private val drawnGlyph = Path()
    private val pathMeasure = PathMeasure()

    /** 0..1 — how much of the ring has closed. */
    private var ringSweep = 0f

    /** 0..1 — how much of the glyph stroke has been laid down. */
    private var glyphProgress = 0f

    /** Continuous rotation for the pending arc, in degrees. */
    private var pendingRotation = 0f

    private var state: State = State.CANCELLED
    private var entryAnimator: ValueAnimator? = null
    private var pendingAnimator: ValueAnimator? = null

    init {
        trackPaint.strokeWidth = TRACK_STROKE_DP * density
        ringPaint.strokeWidth = RING_STROKE_DP * density
        glyphPaint.strokeWidth = GLYPH_STROKE_DP * density
    }

    /** Sets the outcome and plays the draw-on. Safe to call before layout. */
    fun render(state: State) {
        this.state = state
        ringPaint.color = ContextCompat.getColor(context, state.accentColorRes())
        glyphPaint.color = ContextCompat.getColor(context, state.accentColorRes())

        ringSweep = 0f
        glyphProgress = 0f
        if (width > 0 && height > 0) buildGlyphPath()
        startEntryAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = RING_STROKE_DP * density
        ringBounds.set(inset, inset, w - inset, h - inset)
        buildGlyphPath()
    }

    private fun startEntryAnimation() {
        entryAnimator?.cancel()
        entryAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ENTRY_DURATION_MS
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                // The ring closes first, the glyph follows into the space it made.
                ringSweep = (t / RING_PHASE).coerceAtMost(1f)
                glyphProgress = ((t - RING_PHASE) / (1f - RING_PHASE)).coerceIn(0f, 1f)
                invalidate()
            }
            start()
        }

        pendingAnimator?.cancel()
        if (state == State.PENDING) startPendingSpin()
    }

    /** The one piece of continuous motion: something is still happening server-side. */
    private fun startPendingSpin() {
        pendingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = PENDING_SPIN_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            startDelay = ENTRY_DURATION_MS
            addUpdateListener {
                pendingRotation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun buildGlyphPath() {
        glyphPath.reset()
        val cx = width / 2f
        val cy = height / 2f
        val r = (width.coerceAtMost(height) / 2f) - (RING_STROKE_DP * density)

        when (state) {
            State.CANCELLED -> {
                // A cross, drawn as two strokes so it lands with a deliberate two-beat rhythm.
                val arm = r * 0.40f
                glyphPath.moveTo(cx - arm, cy - arm)
                glyphPath.lineTo(cx + arm, cy + arm)
                glyphPath.moveTo(cx + arm, cy - arm)
                glyphPath.lineTo(cx - arm, cy + arm)
            }

            State.PENDING -> {
                // Clock hands: the most legible "not finished yet" mark there is.
                val hour = r * 0.34f
                val minute = r * 0.50f
                glyphPath.moveTo(cx, cy)
                glyphPath.lineTo(cx, cy - minute)
                glyphPath.moveTo(cx, cy)
                glyphPath.lineTo(cx + hour, cy + hour * 0.35f)
            }

            State.UNCONFIRMED -> {
                // Exclamation: stem then dot, so the dot arrives last and punctuates.
                val top = cy - r * 0.46f
                val stemEnd = cy + r * 0.10f
                glyphPath.moveTo(cx, top)
                glyphPath.lineTo(cx, stemEnd)
                val dot = cy + r * 0.40f
                glyphPath.moveTo(cx, dot)
                glyphPath.lineTo(cx, dot + 0.5f)
            }

            State.PAID -> {
                // One continuous tick, so it strokes on in a single confident motion.
                glyphPath.moveTo(cx - r * 0.42f, cy + r * 0.02f)
                glyphPath.lineTo(cx - r * 0.10f, cy + r * 0.32f)
                glyphPath.lineTo(cx + r * 0.44f, cy - r * 0.30f)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ringBounds.isEmpty) return

        // Faint full circle underneath, so the sweep reads as filling something in.
        canvas.drawArc(ringBounds, 0f, 360f, false, trackPaint)

        if (state == State.PENDING && ringSweep >= 1f) {
            // Once closed, a short arc keeps orbiting: still working, not stuck.
            canvas.drawArc(ringBounds, pendingRotation - 90f, PENDING_ARC_DEGREES, false, ringPaint)
        } else if (ringSweep > 0f) {
            canvas.drawArc(ringBounds, START_ANGLE, 360f * ringSweep, false, ringPaint)
        }

        if (glyphProgress > 0f) {
            drawnGlyph.reset()
            // Walk every contour so multi-stroke glyphs draw on in order rather than at once.
            pathMeasure.setPath(glyphPath, false)
            var contour = 0
            val contours = countContours()
            do {
                val length = pathMeasure.length
                if (length > 0f) {
                    val share = 1f / contours
                    val local = ((glyphProgress - contour * share) / share).coerceIn(0f, 1f)
                    if (local > 0f) {
                        pathMeasure.getSegment(0f, length * local, drawnGlyph, true)
                    }
                }
                contour++
            } while (pathMeasure.nextContour())
            canvas.drawPath(drawnGlyph, glyphPaint)
        }
    }

    private fun countContours(): Int {
        val measure = PathMeasure(glyphPath, false)
        var count = 0
        do {
            count++
        } while (measure.nextContour())
        return count.coerceAtLeast(1)
    }

    override fun onDetachedFromWindow() {
        entryAnimator?.cancel()
        pendingAnimator?.cancel()
        entryAnimator = null
        pendingAnimator = null
        super.onDetachedFromWindow()
    }

    /**
     * Monochrome by design. State is carried by the glyph and by motion — the pending arc keeps
     * orbiting, the cancelled cross sits still — so colour has no work left to do, and a gold or
     * red mark would only add noise to a screen that exists to calm someone down.
     */
    private fun State.accentColorRes(): Int = when (this) {
        // Cancelling is a choice, not a failure, so it recedes rather than alarms.
        State.CANCELLED -> R.color.text_tertiary
        State.PENDING, State.UNCONFIRMED -> R.color.text_primary
        // The single exception: money actually arriving is worth the app's existing credit green.
        State.PAID -> R.color.status_text_credit
    }

    private companion object {
        const val RING_STROKE_DP = 2.5f
        const val TRACK_STROKE_DP = 1.5f
        const val GLYPH_STROKE_DP = 2.5f
        const val START_ANGLE = -90f
        const val ENTRY_DURATION_MS = 620L
        const val RING_PHASE = 0.62f
        const val PENDING_SPIN_DURATION_MS = 1_400L
        const val PENDING_ARC_DEGREES = 84f
    }
}
