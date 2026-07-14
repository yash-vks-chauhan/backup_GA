package com.gridee.parking.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.res.ResourcesCompat
import com.gridee.parking.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The home header's reward affordance — a minted Gridee coin set in a laurel
 * medallion. Reads as "award / reward" at a glance, no text needed.
 *
 * Replaces the old perpetually-looping Lottie. Nothing spins idly; every bit of
 * motion means something:
 *
 *  • The **coin** sits still and catches a periodic specular **glint**, like
 *    light raking across real gold.
 *  • A thin gold **laurel wreath** frames the coin — the universal symbol of an
 *    award — so the user understands it's a reward without a label.
 *  • The **crown star** is the daily signal: bright and twinkling when today's
 *    reward is waiting (via [setRewardAvailable]), dim once it's been opened.
 *  • On tap, [playTapFlip] does a single 3D coin-flip — rotation as the payoff
 *    for interacting, not idle decoration.
 *
 * All self-drawn, so it stays crisp at any density and themes cleanly against
 * the dark obsidian hero header in both light and dark mode.
 */
class RewardCoinView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Palette (gold-on-obsidian; reads in both themes) ───────────────────
    private val goldHighlight = Color.parseColor("#FFE9A8")
    private val goldMid       = Color.parseColor("#E3B64A")
    private val goldDeep      = Color.parseColor("#B8860B")
    private val rimColor      = Color.parseColor("#8A6410")
    private val glyphColor    = Color.parseColor("#6E4E07")
    private val glyphEmboss   = Color.parseColor("#80FFF3C9")
    private val engraveShadow = Color.parseColor("#7A5A0E")

    // ── Paints ─────────────────────────────────────────────────────────────
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_bold) }
            .getOrNull() ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val glyphHighlightPaint = Paint(glyphPaint).apply { color = glyphEmboss }

    private val laurelLeafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8C25A") }
    private val laurelShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = engraveShadow }
    private val laurelStemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#C39A2E")
    }

    private val starFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4CE5E") }
    private val starShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = engraveShadow }
    private val starRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#A77E12")
    }
    private val starGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFDF0") }
    private val sparkleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Geometry (set in onSizeChanged) ────────────────────────────────────
    private var cx = 0f
    private var cy = 0f
    private var medallionOuter = 0f
    private var coinRadius = 0f
    private var stemRadius = 0f
    private var leafLen = 0f
    private var leafWid = 0f
    private var starCx = 0f
    private var starCy = 0f
    private var starOuter = 0f
    private var starInner = 0f
    private var sparkleCx = 0f
    private var sparkleCy = 0f
    private var sparkleBase = 0f
    private val coinPath = Path()
    private val arcRect = RectF()
    private val stemPath = Path()
    private val starPath = Path()
    private val leafPath = Path()   // a single pointed laurel leaf, centred at origin
    // Right-branch leaf transforms: [x, y, angleDeg] repeated. Left is mirrored.
    private var leaves = FloatArray(0)

    private val density = resources.displayMetrics.density

    // ── Glint state ─────────────────────────────────────────────────────────
    private var glintAlpha = 0f          // 0..1 envelope for the current sweep
    private var glintProgress = 0f       // 0..1 position of the band across the face
    private var sparkleAlpha = 0f        // 0..1 envelope for the periodic shine ping
    private var glintAnimator: ValueAnimator? = null

    // ── Daily reward state (drives the crown star) ──────────────────────────
    private var rewardAvailable = false
    private var starActive = 0f          // 0 claimed (dim) .. 1 available (bright)
    private var starAnimator: ValueAnimator? = null

    // ── First-launch "mint reveal" intro ────────────────────────────────────
    private var revealActive = false
    private var revealAnimator: ValueAnimator? = null

    init {
        // Deep camera distance so the tap-flip reads as a real 3D coin toss.
        cameraDistance = 8000 * density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        cx = w / 2f
        cy = h / 2f
        // Leave a hair of room so the star/leaves never clip the bounds.
        medallionOuter = min(w, h) / 2f - 1.5f * density
        coinRadius = medallionOuter * 0.68f
        stemRadius = medallionOuter * 0.86f

        val ringWidth = medallionOuter - coinRadius
        leafLen = ringWidth * 0.92f
        leafWid = leafLen * 0.5f

        // A single pointed leaf (almond/lens), centred at the origin along x.
        leafPath.reset()
        val hl = leafLen / 2f
        val hw = leafWid / 2f
        leafPath.moveTo(-hl, 0f)
        leafPath.quadTo(0f, -hw, hl, 0f)
        leafPath.quadTo(0f, hw, -hl, 0f)
        leafPath.close()

        rimPaint.strokeWidth = coinRadius * 0.055f
        highlightPaint.strokeWidth = coinRadius * 0.05f
        laurelStemPaint.strokeWidth = 1.4f * density
        starRimPaint.strokeWidth = 1f * density
        glyphPaint.textSize = coinRadius * 1.02f
        glyphHighlightPaint.textSize = glyphPaint.textSize

        coinPath.reset()
        coinPath.addCircle(cx, cy, coinRadius, Path.Direction.CW)

        // Minted-metal fill: light source upper-left.
        bodyPaint.shader = RadialGradient(
            cx - coinRadius * 0.35f, cy - coinRadius * 0.35f, coinRadius * 1.25f,
            intArrayOf(goldHighlight, goldMid, goldDeep),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )

        // Soft contact shadow under the coin for depth on the dark header.
        shadowPaint.shader = RadialGradient(
            cx, cy + coinRadius * 0.16f, coinRadius * 1.08f,
            intArrayOf(Color.parseColor("#55000000"), Color.TRANSPARENT),
            floatArrayOf(0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        // Crown star sits in the gap at the top of the wreath.
        starCx = cx
        starCy = cy - medallionOuter * 0.82f
        starOuter = medallionOuter * 0.21f
        starInner = starOuter * 0.44f
        starGlowPaint.shader = RadialGradient(
            starCx, starCy, starOuter * 2.4f,
            intArrayOf(Color.parseColor("#FFE27A"), Color.TRANSPARENT),
            floatArrayOf(0.25f, 1f),
            Shader.TileMode.CLAMP
        )

        // Shine ping rides the star's upper-right facet (safely inside bounds).
        val tip = Math.toRadians(-18.0)
        sparkleCx = starCx + starOuter * cos(tip).toFloat()
        sparkleCy = starCy + starOuter * sin(tip).toFloat()
        sparkleBase = starOuter * 0.85f
        sparkleGlowPaint.shader = RadialGradient(
            sparkleCx, sparkleCy, sparkleBase * 1.6f,
            intArrayOf(Color.parseColor("#FFFDF0"), Color.TRANSPARENT),
            floatArrayOf(0.15f, 1f),
            Shader.TileMode.CLAMP
        )

        buildLaurelLeaves()
        buildStemPath()
    }

    /** Pre-compute the right branch's leaf positions; left is drawn mirrored. */
    private fun buildLaurelLeaves() {
        // Right branch lives in the right hemisphere (angle 0° = 3 o'clock, 90° =
        // bottom). The lowest leaf sits just right of the bottom so it nearly meets
        // its mirror — the wreath closes at the bottom, open only at the crown.
        val firstDeg = 85f   // lowest leaf, hugs the bottom-centre
        val lastDeg = -58f   // highest leaf, flanks the crown star (302°)
        val count = 6
        leaves = FloatArray(count * 3)
        for (i in 0 until count) {
            val t = i / (count - 1f)
            val aDeg = firstDeg + (lastDeg - firstDeg) * t
            val aRad = Math.toRadians(aDeg.toDouble())
            leaves[i * 3] = cx + stemRadius * cos(aRad).toFloat()
            leaves[i * 3 + 1] = cy + stemRadius * sin(aRad).toFloat()
            // Tilt each leaf so it fans up-and-outward along the branch.
            leaves[i * 3 + 2] = aDeg - 60f
        }
    }

    private fun buildStemPath() {
        // Both stems originate at the bottom-centre (90°) and arc up each side, so
        // the mirrored pair meets at a single point — the tied base of the wreath.
        stemPath.reset()
        arcRect.set(cx - stemRadius, cy - stemRadius, cx + stemRadius, cy + stemRadius)
        stemPath.addArc(arcRect, 90f, -150f)
    }

    override fun onDraw(canvas: Canvas) {
        if (coinRadius <= 0f) return

        // 1. Laurel wreath behind the coin (right branch + mirrored left).
        drawLaurelBranch(canvas)
        canvas.save()
        canvas.scale(-1f, 1f, cx, cy)
        drawLaurelBranch(canvas)
        canvas.restore()

        // 2. Contact shadow + coin body
        canvas.drawCircle(cx, cy + coinRadius * 0.05f, coinRadius, shadowPaint)
        canvas.drawCircle(cx, cy, coinRadius, bodyPaint)

        // 3. Rim
        rimPaint.color = rimColor
        canvas.drawCircle(cx, cy, coinRadius - rimPaint.strokeWidth / 2f, rimPaint)

        // 4. Bevel highlight — bright arc catching light at the top-left
        arcRect.set(
            cx - coinRadius * 0.78f, cy - coinRadius * 0.78f,
            cx + coinRadius * 0.78f, cy + coinRadius * 0.78f
        )
        highlightPaint.color = Color.parseColor("#B3FFF6D8")
        canvas.drawArc(arcRect, 150f, 120f, false, highlightPaint)

        // 5. Glyph "G" — engraved (light highlight behind, deep gold on top)
        val baseline = cy - (glyphPaint.descent() + glyphPaint.ascent()) / 2f
        canvas.drawText("G", cx, baseline - 1f * density, glyphHighlightPaint)
        glyphPaint.color = glyphColor
        canvas.drawText("G", cx, baseline, glyphPaint)

        // 6. Light glint — clipped to the coin face
        if (glintAlpha > 0.01f) drawGlint(canvas)

        // 7. Crown star (daily reward signal) + its shine ping
        drawStar(canvas)
        if (sparkleAlpha > 0.01f) drawSparkle(canvas)
    }

    private fun drawLaurelBranch(canvas: Canvas) {
        // Spine
        canvas.drawPath(stemPath, laurelStemPaint)

        // Leaves: engraved (deep shadow copy) then gold, pointed at both ends.
        var i = 0
        while (i < leaves.size) {
            val lx = leaves[i]
            val ly = leaves[i + 1]
            val deg = leaves[i + 2]
            canvas.save()
            canvas.translate(lx, ly)
            canvas.rotate(deg)
            canvas.save()
            canvas.translate(0f, 0.7f * density)
            canvas.drawPath(leafPath, laurelShadowPaint)
            canvas.restore()
            canvas.drawPath(leafPath, laurelLeafPaint)
            canvas.restore()
            i += 3
        }
    }

    private fun drawGlint(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(coinPath)
        canvas.rotate(-22f, cx, cy)

        val travel = coinRadius * 2.4f
        val bandX = cx - travel / 2f + travel * glintProgress
        val bandW = coinRadius * 0.55f
        val left = bandX - bandW / 2f
        val right = bandX + bandW / 2f
        val whiteA = (0x66 * glintAlpha).toInt().coerceIn(0, 0xFF)
        val whiteCenter = (whiteA shl 24) or 0x00FFFFFF

        glintPaint.shader = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(Color.TRANSPARENT, whiteCenter, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(left, cy - coinRadius * 1.4f, right, cy + coinRadius * 1.4f, glintPaint)
        canvas.restore()
    }

    private fun drawStar(canvas: Canvas) {
        // Light catches the star as the coin glints → it twinkles in sync.
        val twinkle = glintAlpha
        val scale = 1f + 0.10f * starActive * twinkle
        val outer = starOuter * scale
        val inner = starInner * scale

        if (starActive > 0.01f) {
            starGlowPaint.alpha = (255 * starActive * (0.30f + 0.45f * twinkle)).toInt().coerceIn(0, 255)
            canvas.drawCircle(starCx, starCy, outer * 2.2f, starGlowPaint)
        }

        // Engraved shadow copy
        buildStar(starCx, starCy + 0.6f * density, outer, inner)
        canvas.drawPath(starPath, starShadowPaint)

        // Face — dim when claimed, bright when available
        buildStar(starCx, starCy, outer, inner)
        starFacePaint.alpha = (255 * (0.5f + 0.5f * starActive)).toInt().coerceIn(0, 255)
        canvas.drawPath(starPath, starFacePaint)
        canvas.drawPath(starPath, starRimPaint)
    }

    private fun drawSparkle(canvas: Canvas) {
        val a = (sparkleAlpha * starActive).coerceIn(0f, 1f)
        if (a <= 0.01f) return
        val r = sparkleBase * sparkleAlpha

        sparkleGlowPaint.alpha = (170 * a).toInt().coerceIn(0, 255)
        canvas.drawCircle(sparkleCx, sparkleCy, r * 1.5f, sparkleGlowPaint)

        // Four-point lens shine — long thin cross, like light off a facet.
        buildStar(sparkleCx, sparkleCy, r, r * 0.14f, points = 4)
        sparklePaint.alpha = (255 * a).toInt().coerceIn(0, 255)
        canvas.drawPath(starPath, sparklePaint)
        canvas.drawCircle(sparkleCx, sparkleCy, r * 0.16f, sparklePaint)
    }

    private fun buildStar(scx: Float, scy: Float, outer: Float, inner: Float, points: Int = 5) {
        starPath.reset()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.toRadians((-90f + i * 360f / (points * 2)).toDouble())
            val x = scx + r * cos(a).toFloat()
            val y = scy + r * sin(a).toFloat()
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Light up the crown star when today's reward is waiting; dim it once opened. */
    fun setRewardAvailable(available: Boolean) {
        if (available == rewardAvailable) return
        rewardAvailable = available
        // Announce state to screen readers, not just "Daily reward".
        contentDescription = if (available) "Daily reward available" else "Daily reward"

        starAnimator?.cancel()
        val target = if (available) 1f else 0f
        starAnimator = ValueAnimator.ofFloat(starActive, target).apply {
            duration = if (available) 480L else 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                starActive = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** A single 3D coin-flip that lands face-up, with a crisp tick on landing. */
    fun playTapFlip() {
        if (!animatorsEnabled()) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            return
        }
        val flip = ObjectAnimator.ofFloat(this, View.ROTATION_Y, 0f, 360f).apply {
            interpolator = DecelerateInterpolator(1.4f)
        }
        val pop = ObjectAnimator.ofPropertyValuesHolder(
            this,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.9f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f, 1f)
        )
        AnimatorSet().apply {
            playTogether(flip, pop)
            duration = 480L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // The coin "thunks" down face-up.
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    rotationY = 0f
                }
            })
            start()
        }
    }

    /**
     * One-time first-launch reveal — a small piece of art, shown once:
     * the medallion eases into place, catches one slow "mint" glint as if being
     * struck, and the crown star sparkles to finish. Then it rests, forever calm.
     */
    fun playReveal() {
        if (!animatorsEnabled()) return
        stopGlint()                 // the reveal drives the light itself
        revealActive = true

        // 1) A clear self-flip + pop — an unmistakable "look at me" beat that
        //    also hints the coin is tappable (same gesture as a tap).
        val flip = ObjectAnimator.ofFloat(this, View.ROTATION_Y, 0f, 360f).apply {
            interpolator = DecelerateInterpolator(1.5f)
        }
        val pop = ObjectAnimator.ofPropertyValuesHolder(
            this,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.14f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.14f, 1f)
        )
        AnimatorSet().apply {
            playTogether(flip, pop)
            duration = 640L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { rotationY = 0f }
            })
            start()
        }
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

        // 2) A slow luxurious glint as the flip lands, then a crown-star sparkle.
        revealAnimator?.cancel()
        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L
            startDelay = 380L       // glint after the flip lands face-up
            addUpdateListener {
                val p = it.animatedValue as Float
                if (p < 0.5f) {
                    glintProgress = p / 0.5f
                    glintAlpha = sin(Math.PI * glintProgress).toFloat()
                } else {
                    glintAlpha = 0f
                }
                sparkleAlpha = if (p in 0.55f..0.82f) {
                    sin(Math.PI * ((p - 0.55f) / 0.27f)).toFloat()
                } else 0f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    revealActive = false
                    glintAlpha = 0f
                    sparkleAlpha = 0f
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (isShown) startGlint()   // hand back to the idle shimmer
                    invalidate()
                }
            })
            start()
        }
    }

    /**
     * A single light-rake glint + crown-star sparkle, no flip — the payoff beat
     * for when the medallion *lands* somewhere (e.g. flies into the reward sheet
     * and settles). The coin has already tumbled in transit, so this adds only
     * the "struck gold" shimmer, then hands light back to the idle glint.
     */
    fun shimmerOnce() {
        if (!animatorsEnabled()) return
        stopGlint()
        revealAnimator?.cancel()
        revealActive = true
        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            addUpdateListener {
                val p = it.animatedValue as Float
                if (p < 0.55f) {
                    glintProgress = p / 0.55f
                    glintAlpha = sin(Math.PI * glintProgress).toFloat()
                } else {
                    glintAlpha = 0f
                }
                sparkleAlpha = if (p in 0.50f..0.85f) {
                    sin(Math.PI * ((p - 0.50f) / 0.35f)).toFloat()
                } else 0f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    revealActive = false
                    glintAlpha = 0f
                    sparkleAlpha = 0f
                    if (isShown) startGlint()
                    invalidate()
                }
            })
            start()
        }
    }

    /** Respect the system "remove animations" accessibility setting. */
    private fun animatorsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    // ── Glint lifecycle (only animates while visible & attached) ─────────────

    private fun startGlint() {
        if (glintAnimator != null) return
        if (!animatorsEnabled()) return  // static medallion when animations are off
        glintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GLINT_CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = null // linear cycle clock
            addUpdateListener {
                val f = it.animatedValue as Float

                // Coin glint — sweeps across the face early in each cycle.
                val newGlint = if (f < GLINT_SWEEP_FRACTION) {
                    glintProgress = f / GLINT_SWEEP_FRACTION
                    sin(Math.PI * glintProgress).toFloat()
                } else 0f

                // Shine ping — fires mid-cycle (between sweeps) only when a
                // reward is waiting, so the glimmer also means "claim me".
                val newSparkle = if (starActive > 0.01f && f >= SPARKLE_START && f <= SPARKLE_END) {
                    val p = (f - SPARKLE_START) / (SPARKLE_END - SPARKLE_START)
                    sin(Math.PI * p).toFloat()
                } else 0f

                if (newGlint != glintAlpha || newSparkle != sparkleAlpha) {
                    glintAlpha = newGlint
                    sparkleAlpha = newSparkle
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
        sparkleAlpha = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown) startGlint()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopGlint()
        starAnimator?.cancel()
        revealAnimator?.cancel()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            if (!revealActive) startGlint()   // reveal drives its own light
        } else {
            stopGlint()
        }
    }

    companion object {
        private const val GLINT_CYCLE_MS = 5200L
        // Fraction of each cycle spent actively sweeping (~830ms of 5.2s).
        private const val GLINT_SWEEP_FRACTION = 0.16f
        // Shine ping window — mid-cycle, between coin sweeps (~0.52→0.66 ≈ 730ms).
        private const val SPARKLE_START = 0.52f
        private const val SPARKLE_END = 0.66f
    }
}
