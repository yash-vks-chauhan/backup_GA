package com.gridee.parking.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import com.gridee.parking.R
import kotlin.math.sin

/**
 * The reward sheet's centrepiece number — the amount being minted, set in the
 * same gold as the hero coin so the value reads as "struck from the same metal".
 *
 * Two pieces of motion, both meaningful, never idle:
 *  • On reveal the figure **counts up** from 0 to its value, like a mint tally
 *    settling — the credit feels *earned* rather than printed.
 *  • A slow specular **glint** rakes across the digits on a long cycle, the exact
 *    light that crosses the coin, so the number and the medallion shimmer as one.
 *
 * Self-drawn with a vertical gold gradient + a SRC_ATOP light band, so it stays
 * crisp at any density and themes cleanly on both the light and dark surfaces.
 */
class RewardAmountView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    // Gold tuned per theme: deeper on the light grey surface for legibility,
    // brighter on black so it glows. Same family as RewardCoinView.
    private val goldTop = if (isDark) Color.parseColor("#FFE39A") else Color.parseColor("#E8B23E")
    private val goldMid = if (isDark) Color.parseColor("#F0C24E") else Color.parseColor("#C6930A")
    private val goldDeep = if (isDark) Color.parseColor("#C6930A") else Color.parseColor("#996F12")

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_bold) }
            .getOrNull() ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 60f * resources.displayMetrics.scaledDensity
        letterSpacing = -0.02f
    }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val measureBounds = Rect()

    /** The final value to display; the view animates up to it. */
    private var targetValue = 0
    /** What's painted right now (mid count-up it lands between 0 and target). */
    private var shownValue = 0
    private var shownText = "0"

    /** Fired once when the count-up reaches its final value (not when snapped). */
    var onCountSettled: (() -> Unit)? = null

    private var countAnimator: ValueAnimator? = null
    private var glintAnimator: ValueAnimator? = null
    private var glintAlpha = 0f
    private var glintProgress = 0f

    init {
        // Build the vertical gold shader once we know our height.
    }

    /**
     * Reserve layout width for [value] but hold the figure at 0, ready to be
     * counted up the moment the content blooms in. Avoids a width reflow once
     * the count-up reaches the final (widest) number.
     */
    fun prime(value: Int) {
        targetValue = value
        countAnimator?.cancel()
        shownValue = 0
        shownText = "0"
        requestLayout()
        invalidate()
    }

    /** Set the value and the count-up duration; pass animate=false to snap. */
    fun setAmount(value: Int, animate: Boolean = true) {
        targetValue = value
        if (!animate || !animatorsEnabled()) {
            countAnimator?.cancel()
            shownValue = value
            shownText = value.toString()
            invalidate()
            return
        }
        countAnimator?.cancel()
        countAnimator = ValueAnimator.ofInt(0, value).apply {
            duration = 760L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val v = it.animatedValue as Int
                if (v != shownValue) {
                    shownValue = v
                    shownText = v.toString()
                    invalidate()
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onCountSettled?.invoke()
                }
            })
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Reserve width for the *final* value so the digits never reflow mid-count.
        val widest = targetValue.coerceAtLeast(shownValue).toString().ifEmpty { "0" }
        textPaint.getTextBounds(widest, 0, widest.length, measureBounds)
        val w = measureBounds.width() + paddingLeft + paddingRight + (4 * density).toInt()
        val fm = textPaint.fontMetrics
        val h = (fm.descent - fm.ascent).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(w, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fm = textPaint.fontMetrics
        val top = paddingTop.toFloat()
        textPaint.shader = LinearGradient(
            0f, top, 0f, top + (fm.descent - fm.ascent),
            intArrayOf(goldTop, goldMid, goldDeep),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun getBaseline(): Int {
        // Let a sibling "coins" label sit on the same baseline in a LinearLayout.
        val fm = textPaint.fontMetrics
        return (paddingTop - fm.ascent).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val fm = textPaint.fontMetrics
        val baseline = paddingTop - fm.ascent

        val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        textPaint.xfermode = null
        canvas.drawText(shownText, cx, baseline, textPaint)

        if (glintAlpha > 0.01f) {
            val span = fm.descent - fm.ascent
            val travel = width * 1.6f
            val bandX = -travel * 0.3f + travel * glintProgress
            val bandW = width * 0.5f
            val whiteA = (0xCC * glintAlpha).toInt().coerceIn(0, 0xFF)
            val white = (whiteA shl 24) or 0x00FFFFFF
            glintPaint.shader = LinearGradient(
                bandX - bandW / 2f, 0f, bandX + bandW / 2f, 0f,
                intArrayOf(Color.TRANSPARENT, white, Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            // SRC_ATOP keeps the band only where the digits were just drawn.
            glintPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            canvas.drawRect(0f, paddingTop.toFloat(), width.toFloat(), paddingTop + span, glintPaint)
            glintPaint.xfermode = null
        }
        canvas.restoreToCount(saved)
    }

    // ── Glint lifecycle (only animates while visible & attached) ──────────────

    private fun startGlint() {
        if (glintAnimator != null || !animatorsEnabled()) return
        glintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GLINT_CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = null
            addUpdateListener {
                val f = it.animatedValue as Float
                val a = if (f < GLINT_SWEEP_FRACTION) {
                    glintProgress = f / GLINT_SWEEP_FRACTION
                    sin(Math.PI * glintProgress).toFloat()
                } else 0f
                if (a != glintAlpha) {
                    glintAlpha = a
                    invalidate()
                }
            }
            start()
        }
    }

    private fun stopGlint() {
        glintAnimator?.cancel()
        glintAnimator = null
        glintAlpha = 0f
    }

    private fun animatorsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown) startGlint()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopGlint()
        countAnimator?.cancel()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) startGlint() else stopGlint()
    }

    companion object {
        // Slow, in sync with the coin's own ~5.2s glint cadence.
        private const val GLINT_CYCLE_MS = 5200L
        private const val GLINT_SWEEP_FRACTION = 0.18f
    }
}
