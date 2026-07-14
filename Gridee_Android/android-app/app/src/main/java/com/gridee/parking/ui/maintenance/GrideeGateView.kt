package com.gridee.parking.ui.maintenance

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.gridee.parking.R
import kotlin.math.min
import kotlin.math.sin

/**
 * A precisely-drawn **parking boom-gate** — the maintenance hero for a parking app.
 *
 * It behaves like a real mechanism, which is where the premium feel comes from:
 *
 *  • At rest the arm is **lowered** (the universal "you can't enter yet" signal),
 *    a hinged **counterweight** balances the short end, and a gold warning **lamp**
 *    breathes on the post — alive without a spinner.
 *  • On a retry, [beginAttempt] makes the arm **strain partway up** (the gate
 *    testing its latch) while the backend is checked.
 *  • If still down, [failAttempt] **drops it back closed** with a small settle —
 *    the gate visibly re-locks.
 *  • If maintenance clears, [raise] swings it **fully open** — the gate opening
 *    *is* the transition into the app (the caller fades the hero over the lift,
 *    so the long arm never clips the frame).
 *
 * Fully self-drawn (no Lottie), crisp at any density, themed for light/dark, with
 * a single gold accent. Idle motion runs only while attached + visible and honours
 * the system "remove animations" setting.
 */
class GrideeGateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    // ── Palette ──────────────────────────────────────────────────────────────
    private val structureColor = ContextCompat.getColor(context, R.color.text_primary)
    private val gold = Color.parseColor("#DAA520")
    private val goldBright = Color.parseColor("#E7C463")

    // ── Paints ───────────────────────────────────────────────────────────────
    private val structurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = structureColor }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = structureColor }
    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = goldBright }
    private val lampGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Geometry (set in onSizeChanged) ──────────────────────────────────────
    private var pivotX = 0f
    private var pivotY = 0f
    private var groundY = 0f
    private var armLen = 0f
    private var armThick = 0f
    private var counterLen = 0f
    private var postHalf = 0f
    private var hubR = 0f
    private var lampX = 0f
    private var lampY = 0f
    private var lampR = 0f
    private val armRect = RectF()
    private val counterRect = RectF()
    private val weightRect = RectF()
    private val armClip = Path()

    // ── Motion state ───────────────────────────────────────────────────────────
    private var armAngle = 0f       // 0 = lowered, negative = lifting
    private var lamp = 1f           // resolved lamp intensity 0..1
    private var lampActive = false  // solid (during a retry) vs. breathing (at rest)
    private var settled = false     // terminal "opened" latch
    private var openAngle = -58f    // fully-raised angle, clamped to fit the frame
    private var lampAnim: ValueAnimator? = null
    private var armAnim: ValueAnimator? = null

    private companion object {
        const val ANGLE_REST = 0f
        const val ANGLE_STRAIN = -13f   // partial "testing the latch" lift
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        postHalf = 3f * density
        armThick = 9f * density
        hubR = 6.5f * density
        lampR = 5f * density

        // Post stands left-of-centre, low in the view; the arm reaches across the
        // lane. A low pivot + capped length leaves headroom for the lift.
        pivotX = w * 0.22f
        pivotY = h * 0.66f
        groundY = h * 0.94f
        armLen = min(w * 0.60f, 205f * density)
        counterLen = armLen * 0.15f

        lampX = pivotX
        lampY = pivotY - 15f * density

        lampGlowPaint.shader = RadialGradient(
            lampX, lampY, lampR * 3.6f,
            intArrayOf((0x80 shl 24) or (goldBright and 0x00FFFFFF), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        stripePaint.strokeWidth = armThick * 0.5f

        // Clamp the open angle so the lifted tip (plus its round cap) always lands
        // just inside the view's top edge — no clipping, at any screen size.
        val ar = armThick / 2f
        val headroom = (pivotY - (8f * density + ar)).coerceAtLeast(0f)
        // Cap leaves room for the overshoot peak so even the bounce stays in-frame.
        val maxSin = (headroom / (armLen + ar)).coerceIn(0f, 0.82f) // ~55°
        openAngle = -Math.toDegrees(Math.asin(maxSin.toDouble())).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        if (armLen <= 0f) return

        val r = postHalf
        // Ground plate + post.
        val plateHalf = postHalf * 3.2f
        canvas.drawRoundRect(
            pivotX - plateHalf, groundY - 1.5f * density,
            pivotX + plateHalf, groundY + 1.5f * density, r, r, structurePaint
        )
        canvas.drawRoundRect(pivotX - postHalf, pivotY, pivotX + postHalf, groundY, r, r, structurePaint)

        // ── Arm + counterweight rotate together about the pivot ───────────────
        canvas.save()
        canvas.rotate(armAngle, pivotX, pivotY)

        val ar = armThick / 2f

        // Counterweight (short end, balances the boom).
        val ct = armThick * 0.8f
        counterRect.set(pivotX - counterLen, pivotY - ct / 2f, pivotX, pivotY + ct / 2f)
        canvas.drawRoundRect(counterRect, ct / 2f, ct / 2f, structurePaint)
        val wW = 7f * density
        val wH = armThick * 1.7f
        weightRect.set(
            pivotX - counterLen - wW, pivotY - wH / 2f,
            pivotX - counterLen + wW * 0.2f, pivotY + wH / 2f
        )
        canvas.drawRoundRect(weightRect, 2f * density, 2f * density, structurePaint)

        // Main arm.
        armRect.set(pivotX, pivotY - ar, pivotX + armLen, pivotY + ar)
        canvas.drawRoundRect(armRect, ar, ar, structurePaint)

        // Gold hazard stripes confined to the business end — one restrained accent.
        armClip.reset()
        armClip.addRoundRect(armRect, ar, ar, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(armClip)
        val step = armThick * 1.35f
        var x = pivotX + armLen * 0.64f
        while (x < pivotX + armLen + armThick) {
            canvas.drawLine(x, pivotY - armThick, x - armThick * 1.5f, pivotY + armThick, stripePaint)
            x += step
        }
        canvas.restore()
        canvas.restore()

        // ── Hinge hub (covers the arm root for a clean pivot) ─────────────────
        canvas.drawCircle(pivotX, pivotY, hubR, hubPaint)

        // ── Warning lamp — the alive-at-rest beacon ───────────────────────────
        val lampScale = 0.85f + 0.15f * lamp
        lampGlowPaint.alpha = (255 * (0.22f + 0.58f * lamp)).toInt().coerceIn(0, 255)
        canvas.drawCircle(lampX, lampY, lampR * 2.8f * lampScale, lampGlowPaint)
        lampPaint.alpha = (255 * (0.55f + 0.45f * lamp)).toInt().coerceIn(0, 255)
        canvas.drawCircle(lampX, lampY, lampR * lampScale, lampPaint)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Strain partway up while a retry is checked. */
    fun beginAttempt() {
        if (settled) return
        setLampActive(true)
        animateArmTo(floatArrayOf(armAngle, ANGLE_STRAIN), 360L, DecelerateInterpolator())
    }

    /** Drop back closed with a small settle — the gate re-locks. */
    fun failAttempt() {
        if (settled) return
        setLampActive(false)
        // Overshoot a hair past closed, then settle — a soft "clunk".
        animateArmTo(floatArrayOf(armAngle, 3f, ANGLE_REST), 460L, DecelerateInterpolator())
    }

    /** Swing fully open; [onEnd] fires once raised. The caller proceeds from there. */
    fun raise(onEnd: () -> Unit) {
        if (settled) { onEnd(); return }
        settled = true
        setLampActive(true)
        if (!animatorsEnabled()) {
            armAngle = openAngle
            invalidate()
            onEnd()
            return
        }
        animateArmTo(floatArrayOf(armAngle, openAngle), 720L, OvershootInterpolator(0.5f)) {
            onEnd()
        }
    }

    private fun animateArmTo(
        values: FloatArray,
        durationMs: Long,
        interp: android.view.animation.Interpolator,
        onEnd: (() -> Unit)? = null
    ) {
        armAnim?.cancel()
        if (!animatorsEnabled()) {
            armAngle = values.last()
            invalidate()
            onEnd?.invoke()
            return
        }
        armAnim = ValueAnimator.ofFloat(*values).apply {
            duration = durationMs
            interpolator = interp
            addUpdateListener {
                armAngle = it.animatedValue as Float
                invalidate()
            }
            if (onEnd != null) addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
    }

    // ── Lamp ────────────────────────────────────────────────────────────────────

    private fun setLampActive(active: Boolean) {
        lampActive = active
        if (active) {
            stopLamp()
            lamp = 1f
            invalidate()
        } else {
            startLamp()
        }
    }

    private fun startLamp() {
        if (lampActive || settled) return
        if (!animatorsEnabled()) { lamp = 1f; invalidate(); return }
        if (lampAnim != null) return
        lampAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = null
            addUpdateListener {
                val phase = it.animatedValue as Float
                // Slow, smooth breathe — a calm beacon, not a strobe.
                lamp = (0.5f + 0.5f * sin(phase * 2.0 * Math.PI).toFloat()).coerceIn(0f, 1f)
                invalidate()
            }
            start()
        }
    }

    private fun stopLamp() {
        lampAnim?.cancel(); lampAnim = null
    }

    private fun animatorsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown) startLamp()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLamp()
        armAnim?.cancel()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) startLamp() else stopLamp()
    }
}
