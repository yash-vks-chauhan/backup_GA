package com.gridee.parking.ui.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import com.gridee.parking.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The home wordmark, doing double duty as the Instagram entry point. At rest it reads
 * "Gridee" with a small Instagram glyph — the quiet, constant cue that the mark is
 * tappable and where it leads. On a slow cycle the letters come apart into a soft swarm
 * of colour-carrying grains that lift, curl, and reassemble into "Follow @_gridee_" in the
 * Instagram colours, then gather back into "Gridee" by the same law played in reverse.
 *
 * The motion obeys one physics in both directions — ease along the path, with a gentle
 * buoyant arc and soft turbulence that vanish exactly at the endpoints — so the switch
 * reads as one coherent material reflowing rather than two separate effects. Crisp art
 * bookends each flight; grains settle precisely onto the glyphs.
 *
 * Both states are sampled once into point clouds carrying each grain's real colour;
 * ~300 grains are paired source→dest, sorted by x so the mark visibly reflows. Tapping
 * casts a brief spark burst and then opens the profile, so the tap is rewarded and clearly
 * causal. Honours the system "remove animations" setting via a static fallback, and idles
 * without redrawing during the resting hold. Theme contrast via [setBrandColor].
 */
class BrandFollowSwitcher @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaledDensity

    private val brandText = "Gridee"
    private val followText = "Follow @_gridee_"

    // Cycle timeline (ms): a long, stable brand rest, then dissolve → reform → gather back.
    // Grains depart and arrive on staggered schedules within each morph, so the switch
    // reads as fluid rather than a snap.
    private val brandHold = 6500.0
    private val morph = 1650.0
    private val followHold = 3000.0
    private val cycle = brandHold + morph + followHold + morph

    private val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = sp(36f)
        letterSpacing = -0.03f
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_bold) }
            .getOrNull() ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val followPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        letterSpacing = -0.01f
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_semibold) }
            .getOrNull() ?: Typeface.DEFAULT_BOLD
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glyphPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val igColors = intArrayOf(
        0xFFFEDA75.toInt(), // warm yellow
        0xFFFA7E1E.toInt(), // orange
        0xFFD62976.toInt(), // magenta
        0xFF962FBF.toInt(), // purple
        0xFF4F5BD5.toInt()  // indigo
    )
    private var igGradient: LinearGradient? = null
    private val igMatrix = Matrix()
    private var igSpan = 0f

    // Resting Instagram glyph (the meaning cue), baked once with the IG gradient.
    private var glyphBmp: Bitmap? = null
    private val glyphSize = dp(22f)
    private var glyphX = 0f
    private var glyphY = 0f

    // Particle clouds (paired by index): brand[i] <-> follow[i], each carrying its colour.
    private var count = 0
    private var bX = FloatArray(0); private var bY = FloatArray(0); private var bCol = IntArray(0)
    private var fX = FloatArray(0); private var fY = FloatArray(0); private var fCol = IntArray(0)
    private var amp = FloatArray(0)     // turbulence radius
    private var phase = FloatArray(0)   // turbulence phase
    private var arc = FloatArray(0)     // buoyant lift at mid-flight
    private var delay = FloatArray(0)   // when this grain begins to move (0..~0.3 of morph)
    private var dur = FloatArray(0)     // how long its flight lasts (fraction of morph)
    private var baseR = dp(1.5f)
    private var brandWidth = 0f

    // Tap spark burst
    private val sparkN = 40
    private val sparkAngle = FloatArray(sparkN)
    private val sparkSpeed = FloatArray(sparkN)
    private val sparkCol = IntArray(sparkN)
    private val burstDur = 480.0
    private var burstStart = 0L

    private var brandColor = 0xFFFFFFFF.toInt()
    private var startNanos = 0L
    private var reduced = false

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            scheduleNext()
        }
    }

    /**
     * Drive the next frame — but during the static brand rest, sleep until the morph is
     * due instead of redrawing an unchanging word every frame. An active burst always
     * forces per-frame animation.
     */
    private fun scheduleNext() {
        if (reduced) return
        if (burstStart != 0L) { postOnAnimation(ticker); return }
        val pos = ((System.nanoTime() - startNanos) / 1e6) % cycle
        if (pos < brandHold) {
            postDelayed(ticker, (brandHold - pos).toLong().coerceAtLeast(16L))
        } else {
            postOnAnimation(ticker)
        }
    }

    init {
        val r = Random(7)
        for (i in 0 until sparkN) {
            sparkAngle[i] = r.nextFloat() * 2f * PI.toFloat()
            sparkSpeed[i] = dp(16f) + r.nextFloat() * dp(34f)
            sparkCol[i] = igColors[r.nextInt(igColors.size)]
        }
    }

    /** Adapt the resting wordmark to the hero theme (white on dark, ink on light). */
    fun setBrandColor(color: Int) {
        brandColor = color
        brandPaint.color = color
        if (width > 0 && height > 0) buildParticles(width, height) // recolour the grains
        invalidate()
    }

    /** Cast a spark burst from the word — call on tap, just before opening Instagram. */
    fun castBurst() {
        if (reduced) return
        burstStart = System.nanoTime()
        removeCallbacks(ticker)   // wake the loop if it's idling through the brand rest
        postOnAnimation(ticker)
    }

    private fun reducedMotion(): Boolean = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        // Fit the follow line to the available width.
        var size = sp(21f)
        followPaint.textSize = size
        val measured = followPaint.measureText(followText)
        if (measured > w) {
            size *= w / measured
            followPaint.textSize = size
        }

        val followWidth = followPaint.measureText(followText).coerceAtLeast(dp(1f))
        // Map the full Instagram sweep across the whole word so amber → magenta → purple →
        // indigo are all present at once; MIRROR lets the gentle shimmer overrun the ends
        // cleanly without ever sliding the warm colours out of view.
        igSpan = followWidth
        igGradient = LinearGradient(0f, 0f, igSpan, 0f, igColors, null, Shader.TileMode.MIRROR)
        followPaint.shader = igGradient

        // Resting Instagram glyph: legible (~22dp), set just after the wordmark, centred.
        brandWidth = brandPaint.measureText(brandText)
        glyphX = brandWidth + dp(11f)
        glyphY = h / 2f - glyphSize / 2f
        buildGlyph()

        buildParticles(w, h)
    }

    /** Bake the Instagram glyph once, tinted with the IG gradient (alpha preserved). */
    private fun buildGlyph() {
        val dr = AppCompatResources.getDrawable(context, R.drawable.ic_instagram)?.mutate() ?: return
        val s = glyphSize.toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        dr.setBounds(0, 0, s, s)
        dr.setTint(0xFFFFFFFF.toInt())
        dr.draw(c)
        val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, s.toFloat(), s.toFloat(), 0f, igColors, null, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), gp)
        glyphBmp = bmp
    }

    /** Sample the resting mark and the follow line into colour-carrying point clouds. */
    private fun buildParticles(w: Int, h: Int) {
        val brandPts = sampleRendered(w, h) { c -> drawRestingMark(c, 255) }
        val followPts = sampleRendered(w, h) { c ->
            igMatrix.reset(); igGradient?.setLocalMatrix(igMatrix)
            followPaint.alpha = 255
            c.drawText(followText, 0f, baselineFor(followPaint, 0f), followPaint)
        }
        if (brandPts.isEmpty() || followPts.isEmpty()) return

        count = min(300, min(brandPts.size / 2, followPts.size / 2))
        if (count <= 0) return

        val b = pick(brandPts, count).sortedBy { it.x }
        val f = pick(followPts, count).sortedBy { it.x }

        bX = FloatArray(count); bY = FloatArray(count); bCol = IntArray(count)
        fX = FloatArray(count); fY = FloatArray(count); fCol = IntArray(count)
        amp = FloatArray(count); phase = FloatArray(count); arc = FloatArray(count)
        delay = FloatArray(count); dur = FloatArray(count)

        val rnd = Random(42)
        for (i in 0 until count) {
            bX[i] = b[i].x; bY[i] = b[i].y; bCol[i] = b[i].c
            fX[i] = f[i].x; fY[i] = f[i].y; fCol[i] = f[i].c
            // Softened ranges: a shimmer-dissolve, not an explosion.
            amp[i] = dp(2f) + rnd.nextFloat() * dp(5f)
            phase[i] = rnd.nextFloat() * (2f * PI.toFloat())
            arc[i] = dp(1.5f) + rnd.nextFloat() * dp(5f)
            // Staggered schedule: each grain leaves at its own moment and flies for its
            // own span, always finishing by the end of the morph (delay + dur ≤ 1).
            delay[i] = rnd.nextFloat() * 0.30f
            dur[i] = (1f - delay[i]) * (0.66f + rnd.nextFloat() * 0.34f)
        }
    }

    private class Grain(val x: Float, val y: Float, val c: Int)

    private fun sampleRendered(w: Int, h: Int, render: (Canvas) -> Unit): List<Grain> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        render(Canvas(bmp))
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()

        val step = (dp(2.4f)).toInt().coerceAtLeast(2)
        val out = ArrayList<Grain>(2048)
        var y = 0
        while (y < h) {
            var x = 0
            val row = y * w
            while (x < w) {
                val px = pixels[row + x]
                if ((px ushr 24) > 120) out.add(Grain(x.toFloat(), y.toFloat(), (0xFF shl 24) or (px and 0x00FFFFFF)))
                x += step
            }
            y += step
        }
        return out
    }

    /** Evenly thin a list down to exactly [n] grains. */
    private fun pick(list: List<Grain>, n: Int): List<Grain> {
        if (list.size <= n) return list
        val out = ArrayList<Grain>(n)
        val stride = list.size.toFloat() / n
        var acc = 0f
        repeat(n) { out.add(list[min(acc.toInt(), list.size - 1)]); acc += stride }
        return out
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        val ar = Color.red(a); val ag = Color.green(a); val ab = Color.blue(a)
        return Color.rgb(
            (ar + (Color.red(b) - ar) * u).toInt(),
            (ag + (Color.green(b) - ag) * u).toInt(),
            (ab + (Color.blue(b) - ab) * u).toInt()
        )
    }

    /** Smootherstep — buttery acceleration and deceleration. */
    private fun ease(t: Double): Float {
        val x = t.coerceIn(0.0, 1.0)
        return (x * x * x * (x * (x * 6 - 15) + 10)).toFloat()
    }

    private fun baselineFor(p: Paint, offsetY: Float): Float {
        val fm = p.fontMetrics
        return height / 2f - (fm.ascent + fm.descent) / 2f + offsetY
    }

    /** "Gridee" + the Instagram glyph — the resting mark. */
    private fun drawRestingMark(canvas: Canvas, alpha: Int) {
        brandPaint.color = brandColor
        brandPaint.alpha = alpha
        canvas.drawText(brandText, 0f, baselineFor(brandPaint, 0f), brandPaint)
        brandPaint.alpha = 255

        glyphBmp?.let {
            glyphPaint.alpha = alpha
            canvas.drawBitmap(it, glyphX, glyphY, glyphPaint)
        }
    }

    private fun drawWholeFollow(canvas: Canvas, alpha: Int, elapsedMs: Double) {
        // A slow, low-amplitude shimmer (≤8% of the word) — the colours breathe without the
        // warm amber/orange end ever scrolling off the word.
        val shimmer = ((sin(elapsedMs / 1400.0) * 0.5 + 0.5).toFloat()) * igSpan * 0.08f
        igMatrix.setTranslate(-shimmer, 0f)
        igGradient?.setLocalMatrix(igMatrix)
        followPaint.alpha = alpha
        canvas.drawText(followText, 0f, baselineFor(followPaint, 0f), followPaint)
        followPaint.alpha = 255
    }

    /**
     * The switch. The grains ease along their path with a gentle buoyant arc and soft
     * turbulence, both of which vanish exactly at the endpoints so the cloud settles onto
     * the art. The return runs the very same law with source and destination swapped — one
     * coherent material reflowing, not two different effects. Crisp art bookends each flight.
     */
    private fun drawDissolve(canvas: Canvas, m: Float, forward: Boolean) {
        if (count == 0) return

        // Crisp art bookends; particles always cover the shape in between (undeparted
        // grains rest on the source, arrived grains rest on the destination).
        val srcCrisp = (1f - m / 0.18f).coerceIn(0f, 1f)
        val dstCrisp = ((m - 0.82f) / 0.18f).coerceIn(0f, 1f)
        val pAlpha = when {
            m < 0.16f -> m / 0.16f
            m > 0.84f -> (1f - m) / 0.16f
            else -> 1f
        }.coerceIn(0f, 1f)

        if (forward) {
            if (srcCrisp > 0f) drawRestingMark(canvas, (srcCrisp * 255).toInt())
            if (dstCrisp > 0f) drawWholeFollow(canvas, (dstCrisp * 255).toInt(), 0.0)
        } else {
            if (srcCrisp > 0f) drawWholeFollow(canvas, (srcCrisp * 255).toInt(), 0.0)
            if (dstCrisp > 0f) drawRestingMark(canvas, (dstCrisp * 255).toInt())
        }

        val coreA = pAlpha * 235f
        val glowA = (pAlpha * 52f).toInt()
        for (i in 0 until count) {
            // Per-grain local time: leaves at delay[i], flies for dur[i]. This stagger is
            // what makes the cloud feel alive rather than a single synchronized swap.
            val lr = ((m - delay[i]) / dur[i]).coerceIn(0f, 1f)
            val me = ease(lr.toDouble())
            val bump = sin(PI * lr).toFloat()
            val curl = lr * 2.4f   // a modest curl, not a chaotic swirl

            val sx: Float; val sy: Float; val tx: Float; val ty: Float; val cStart: Int; val cEnd: Int
            if (forward) {
                sx = bX[i]; sy = bY[i]; tx = fX[i]; ty = fY[i]; cStart = bCol[i]; cEnd = fCol[i]
            } else {
                sx = fX[i]; sy = fY[i]; tx = bX[i]; ty = bY[i]; cStart = fCol[i]; cEnd = bCol[i]
            }

            // One law in both directions: ease along the path, with the arc and turbulence
            // fading out at the ends (bump → 0) so grains land exactly on the glyphs.
            val tb = amp[i] * bump
            val ex = sx + (tx - sx) * me + tb * cos(phase[i] + curl)
            val ey = sy + (ty - sy) * me + tb * 0.6f * sin(phase[i] * 1.3f + curl) - arc[i] * bump

            val col = lerpColor(cStart, cEnd, me)
            val r = baseR * (0.75f + 0.55f * bump)
            val tw = 0.85f + 0.15f * sin(phase[i] * 2f + curl * 2f)   // gentle catch-the-light shimmer

            particlePaint.color = col
            particlePaint.alpha = glowA
            canvas.drawCircle(ex, ey, r * 2.0f, particlePaint)
            particlePaint.alpha = (coreA * tw).toInt().coerceIn(0, 255)
            canvas.drawCircle(ex, ey, r, particlePaint)
        }
    }

    private fun drawBurst(canvas: Canvas) {
        if (burstStart == 0L) return
        val e = (System.nanoTime() - burstStart) / 1e6
        if (e > burstDur) { burstStart = 0L; return }
        val bp = (e / burstDur).toFloat()
        val ej = ease(bp.toDouble())
        val cx = brandWidth / 2f
        val cy = height / 2f
        val a = (1f - bp) * 235f
        for (i in 0 until sparkN) {
            val d = ej * sparkSpeed[i]
            val x = cx + cos(sparkAngle[i]) * d
            val y = cy + sin(sparkAngle[i]) * d - dp(7f) * bp
            particlePaint.color = sparkCol[i]
            particlePaint.alpha = (a * 0.35f).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, dp(2.6f) * (1f - bp * 0.5f), particlePaint)
            particlePaint.alpha = a.toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, dp(1.3f) * (1f - bp * 0.4f), particlePaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (reduced) {
            drawRestingMark(canvas, 255)
            return
        }
        if (startNanos == 0L) startNanos = System.nanoTime()
        val elapsedMs = (System.nanoTime() - startNanos) / 1e6
        val pos = elapsedMs % cycle

        when {
            pos < brandHold -> drawRestingMark(canvas, 255)
            pos < brandHold + morph -> {
                val mm = ((pos - brandHold) / morph).toFloat()
                drawDissolve(canvas, mm, forward = true)
            }
            pos < brandHold + morph + followHold ->
                drawWholeFollow(canvas, 255, elapsedMs)
            else -> {
                val mm = ((pos - brandHold - morph - followHold) / morph).toFloat()
                drawDissolve(canvas, mm, forward = false)
            }
        }

        drawBurst(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reduced = reducedMotion()
        if (reduced) { invalidate(); return }
        // Entrance: let the brand read first, then begin the cycle naturally.
        startNanos = System.nanoTime() - ((brandHold - 2200.0) * 1e6).toLong()
        removeCallbacks(ticker)
        postOnAnimation(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }
}
