package com.gridee.parking.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-sheet confetti view — gold particles burst outward from a spawn point,
 * drift down with realistic gravity, and dissolve into a soft fog at the bottom.
 */
class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var sway: Float,            // horizontal wobble amplitude
        var swaySpeed: Float,       // wobble frequency
        var swayPhase: Float,       // random phase offset
        var width: Float,
        var height: Float,
        var color: Int,
        var baseAlpha: Int = 255,
        var alpha: Int = 255,
        var isCircle: Boolean = false
    )

    /** Called at key moments during the burst so the host can fire haptics. */
    var onHapticPulse: (() -> Unit)? = null

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var animator: ValueAnimator? = null
    private var frameCount = 0

    // Bottom fog — particles fade out in this zone
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var fogShader: LinearGradient? = null
    private var lastFogWidth = 0
    private var lastFogHeight = 0

    private val goldColors = intArrayOf(
        Color.parseColor("#FFD700"), // Gold
        Color.parseColor("#FFC107"), // Amber
        Color.parseColor("#FFBF00"), // Dark gold
        Color.parseColor("#FFE066"), // Light gold
        Color.parseColor("#DAA520"), // Goldenrod
        Color.parseColor("#F5D063"), // Soft gold
        Color.parseColor("#FFF1B8"), // Pale gold
        Color.parseColor("#FFFFFF"), // White sparkle (rare)
    )

    /**
     * Spawn confetti from the top-center area of this view.
     *
     * @param particleCount  total particles
     * @param durationMs     how long the physics simulation runs
     * @param originYFraction vertical spawn point as fraction of view height (0.15 = near top)
     */
    fun burst(
        particleCount: Int = 60,
        durationMs: Long = 3200,
        originYFraction: Float = 0.28f
    ) {
        particles.clear()
        frameCount = 0

        val cx = width / 2f
        val cy = height * originYFraction

        // Wider horizontal spread for full-sheet coverage
        val spreadX = width * 0.45f

        for (i in 0 until particleCount) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            // Higher initial speed → wider burst radius
            val speed = 3f + Random.nextFloat() * 9f
            val isWhite = i < 4
            val colorIndex = if (isWhite) goldColors.size - 1 else Random.nextInt(goldColors.size - 1)

            // Vary sizes — mix of tiny sparkles and larger confetti
            val sizeCategory = Random.nextFloat()
            val w: Float
            val h: Float
            val isCircle: Boolean
            when {
                sizeCategory < 0.25f -> {
                    // Tiny sparkle dot
                    w = 2f + Random.nextFloat() * 2f
                    h = w
                    isCircle = true
                }
                sizeCategory < 0.6f -> {
                    // Small rectangle
                    w = 3f + Random.nextFloat() * 4f
                    h = 4f + Random.nextFloat() * 7f
                    isCircle = false
                }
                else -> {
                    // Larger confetti piece
                    w = 4f + Random.nextFloat() * 5f
                    h = 6f + Random.nextFloat() * 10f
                    isCircle = false
                }
            }

            particles.add(
                Particle(
                    x = cx + (Random.nextFloat() - 0.5f) * spreadX * 0.3f,
                    y = cy + (Random.nextFloat() - 0.5f) * 30f,
                    vx = cos(angle) * speed * 1.3f, // Extra horizontal push for width
                    vy = sin(angle) * speed - 3f,    // Strong upward bias initially
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 8f,
                    sway = 0.3f + Random.nextFloat() * 0.8f,
                    swaySpeed = 0.02f + Random.nextFloat() * 0.04f,
                    swayPhase = Random.nextFloat() * Math.PI.toFloat() * 2f,
                    width = w,
                    height = h,
                    color = goldColors[colorIndex],
                    isCircle = isCircle
                )
            )
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator() // Physics handles easing
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                updateParticles(progress)
                invalidate()

                // Fire haptic pulses at key moments during the cascade
                frameCount++
                if (frameCount == 3 || frameCount == 8 || frameCount == 15) {
                    onHapticPulse?.invoke()
                }
            }
            start()
        }
    }

    private fun updateParticles(progress: Float) {
        val gravity = 0.08f    // Gentle pull — particles float, not plummet
        val drag = 0.985f      // Very low air resistance → long graceful drift
        val viewHeight = height.toFloat()

        // Bottom fade zone: last 25% of the sheet height
        val fogTop = viewHeight * 0.75f

        for (p in particles) {
            // Physics step
            p.vy += gravity
            p.vx *= drag
            p.vy *= drag

            // Horizontal sway (leaf-like drift)
            val swayOffset = sin((frameCount * p.swaySpeed + p.swayPhase).toDouble()).toFloat() * p.sway
            p.x += p.vx + swayOffset
            p.y += p.vy

            p.rotation += p.rotationSpeed
            // Slow down rotation over time for settling feel
            p.rotationSpeed *= 0.997f

            // Alpha: combine time-based fade + bottom fog zone fade
            val timeFade = when {
                progress < 0.15f -> (progress / 0.15f)  // Fade IN during first 15%
                progress > 0.7f -> 1f - ((progress - 0.7f) / 0.3f) // Fade out last 30%
                else -> 1f
            }

            // Bottom fog fade — smoothly dissolve as particles enter the fog zone
            val positionFade = if (p.y > fogTop && viewHeight > fogTop) {
                val fogProgress = (p.y - fogTop) / (viewHeight - fogTop)
                (1f - fogProgress).coerceIn(0f, 1f)
            } else {
                1f
            }

            p.alpha = (p.baseAlpha * timeFade * positionFade).toInt().coerceIn(0, 255)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (particles.isEmpty()) return

        val density = resources.displayMetrics.density

        // Draw particles
        for (p in particles) {
            if (p.alpha <= 0) continue

            paint.color = p.color
            paint.alpha = p.alpha

            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rotation)

            val w = p.width * density
            val h = p.height * density

            if (p.isCircle) {
                canvas.drawCircle(0f, 0f, w / 2f, paint)
            } else {
                rect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
                canvas.drawRoundRect(rect, 1.5f * density, 1.5f * density, paint)
            }

            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
        particles.clear()
    }
}
