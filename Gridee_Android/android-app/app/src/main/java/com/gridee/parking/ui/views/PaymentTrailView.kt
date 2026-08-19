package com.gridee.parking.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import com.gridee.parking.R

/**
 * Shows where the user's money physically is, as a three-stop journey:
 * **Your bank → Gridee → Wallet**.
 *
 * After a payment ends badly the only question that matters is "have I been charged?". Text
 * alone makes people re-read it twice; a filled track that visibly stops at their own bank
 * answers it at a glance and is much harder to misread.
 *
 * The track fills to [progress]: 0 means the money never moved, 1 means it landed in the wallet.
 */
class PaymentTrailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Stage {
        /** Money never left the user's account. */
        AT_BANK,

        /** In flight — the gateway has it, the wallet does not. */
        IN_TRANSIT,

        /** Landed. */
        IN_WALLET
    }

    private val density = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.divider)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nodeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.divider)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
        typeface = Typeface.DEFAULT
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val labels = listOf(
        context.getString(R.string.payment_trail_bank),
        context.getString(R.string.payment_trail_gateway),
        context.getString(R.string.payment_trail_wallet)
    )

    private var stage: Stage = Stage.AT_BANK
    private var progress = 0f
    private var pulsePhase = 0f

    private var fillAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    init {
        trackPaint.strokeWidth = TRACK_STROKE_DP * density
        fillPaint.strokeWidth = TRACK_STROKE_DP * density
        nodeRingPaint.strokeWidth = 1.5f * density
    }

    /** Sets the stage and animates the track filling to it. */
    fun render(stage: Stage) {
        this.stage = stage
        val accent = ContextCompat.getColor(context, stage.accentColorRes())
        fillPaint.color = accent
        pulsePaint.color = accent

        val target = when (stage) {
            // Deliberately not 0: a sliver of fill at the user's own bank shows the track is
            // "live" and starting from them, rather than looking like nothing rendered.
            Stage.AT_BANK -> 0f
            Stage.IN_TRANSIT -> 0.5f
            Stage.IN_WALLET -> 1f
        }

        fillAnimator?.cancel()
        fillAnimator = ValueAnimator.ofFloat(progress, target).apply {
            duration = FILL_DURATION_MS
            startDelay = FILL_DELAY_MS
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        pulseAnimator?.cancel()
        if (stage == Stage.IN_TRANSIT) startPulse()
    }

    /** A travelling highlight on the in-flight leg, so "in transit" looks like transit. */
    private fun startPulse() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PULSE_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            startDelay = FILL_DELAY_MS + FILL_DURATION_MS
            addUpdateListener {
                pulsePhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (NODE_RADIUS_DP * 2 + 26) * density
        setMeasuredDimension(width, height.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val nodeRadius = NODE_RADIUS_DP * density
        val left = paddingLeft + nodeRadius + EDGE_INSET_DP * density
        val right = width - paddingRight - nodeRadius - EDGE_INSET_DP * density
        val y = nodeRadius + (2 * density)
        val span = right - left
        if (span <= 0) return

        canvas.drawLine(left, y, right, y, trackPaint)
        if (progress > 0f) {
            canvas.drawLine(left, y, left + span * progress, y, fillPaint)
        }

        if (stage == Stage.IN_TRANSIT) {
            // Rides only the leg that is actually in flight (bank → gateway).
            val head = left + span * (pulsePhase * 0.5f)
            pulsePaint.alpha = ((1f - pulsePhase) * 200).toInt().coerceIn(0, 255)
            canvas.drawCircle(head, y, PULSE_RADIUS_DP * density, pulsePaint)
            pulsePaint.alpha = 255
        }

        val reached = ContextCompat.getColor(context, stage.accentColorRes())
        val pending = ContextCompat.getColor(context, R.color.background_secondary)
        val labelReached = ContextCompat.getColor(context, R.color.text_primary)
        val labelPendingColor = ContextCompat.getColor(context, R.color.text_tertiary)

        labels.forEachIndexed { index, label ->
            val fraction = index / (labels.size - 1).toFloat()
            val cx = left + span * fraction
            val isReached = progress + 0.001f >= fraction

            nodePaint.color = if (isReached) reached else pending
            canvas.drawCircle(cx, y, nodeRadius, nodePaint)
            if (!isReached) canvas.drawCircle(cx, y, nodeRadius, nodeRingPaint)

            labelPaint.color = if (isReached) labelReached else labelPendingColor
            labelPaint.typeface = if (isReached) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(label, cx, y + nodeRadius + 16 * density, labelPaint)
        }
    }

    override fun onDetachedFromWindow() {
        fillAnimator?.cancel()
        pulseAnimator?.cancel()
        fillAnimator = null
        pulseAnimator = null
        super.onDetachedFromWindow()
    }

    /** Monochrome; the travelling pulse is what says "moving", not a colour change. */
    private fun Stage.accentColorRes(): Int = when (this) {
        Stage.AT_BANK -> R.color.text_tertiary
        Stage.IN_TRANSIT -> R.color.text_primary
        Stage.IN_WALLET -> R.color.status_text_credit
    }

    private companion object {
        const val TRACK_STROKE_DP = 2.5f
        const val NODE_RADIUS_DP = 6f
        const val PULSE_RADIUS_DP = 4.5f
        const val EDGE_INSET_DP = 4f
        const val FILL_DURATION_MS = 620L
        const val FILL_DELAY_MS = 260L
        const val PULSE_DURATION_MS = 1_500L
    }
}
