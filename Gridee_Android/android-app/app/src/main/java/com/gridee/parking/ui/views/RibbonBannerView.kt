package com.gridee.parking.ui.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import com.gridee.parking.R
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * A cloth banner suspended from two strings that flutters like a flag in a light
 * breeze. The two top corners are held by the threads (fixed); the body and the
 * free swallowtail edges ripple on an organic compound wave (two beating harmonics
 * plus a slight horizontal skew so crests read as folds). A soft light slides across
 * the fabric as it moves (per-vertex specular sheen), and a blurred shadow is cast
 * below and ripples in sync — so it reads as a real piece of hanging cloth, not a
 * warp effect.
 *
 * The banner never swings side-to-side — only the fabric waves. Tapping (or the
 * entrance) sends a gentle "gust" that briefly deepens the ripple and decays.
 *
 * Everything is drawn by the view itself (cloth + text baked into a 2× bitmap, then
 * warped with a lit mesh), so the lettering waves and catches light with the fabric.
 */
class RibbonBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    private val isDarkTheme = com.gridee.parking.utils.ThemeManager.isDarkMode(context)

    // Geometry
    private val sideInset = dp(24f)
    private val notchDepth = dp(12f)
    private val bannerTop = dp(24f)     // string drop above the cloth
    private val clothHeight = dp(46f)
    private val sag = dp(3f)            // gentle resting droop
    private val margin = dp(12f)        // bitmap padding for edge + blur (covers the wider light-mode blur)
    private val pinY = dp(3f)
    private val pinInsetX = dp(8f)
    private val stringSlack = dp(2.5f)

    // Drop shadow, tuned per theme. Dark mode keeps the original tight, deeper cast,
    // which reads as a soft halo on a dark page. Light mode softens it — lower opacity,
    // a wider blur and a touch more lift — so it falls off cleanly on white instead of
    // stamping a hard, heavy band under the cloth.
    private val shadowAlpha = if (isDarkTheme) 0x59 else 0x24
    private val shadowBlur = if (isDarkTheme) dp(6f) else dp(9f)
    private val shadowDx = if (isDarkTheme) dp(1.5f) else dp(1f)
    private val shadowDy = if (isDarkTheme) dp(4f) else dp(5f)

    // Wave
    private val cols = 22
    private val rows = 7
    private val idleAmp = dp(2.2f)
    private val gustAmp = dp(5f)
    private val gustTau = 0.5
    private val speed1 = 1.9
    private val speed2 = 3.05
    private val k1 = 2.0 * PI * 1.25
    private val k2 = 2.0 * PI * 2.3
    private val skewFactor = 0.5f
    private val ss = 2                  // bitmap supersampling for crisp lettering

    // Colours
    private val gold = 0xFFE0B65C.toInt()
    private val goldDim = 0xFFC6930A.toInt()
    private val textColor = 0xFFF0D592.toInt()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x66E0B65C
    }
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        strokeCap = Paint.Cap.ROUND
        color = 0xD9C2A964.toInt()
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = goldDim }
    private val knotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
    private val meshPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = sp(12f)
        letterSpacing = 0.1f
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_semibold) }
            .getOrNull() ?: Typeface.DEFAULT_BOLD
    }

    private val stringPath = Path()

    private var clothBitmap: Bitmap? = null
    private var shadowBitmap: Bitmap? = null
    private val vcount = (cols + 1) * (rows + 1)
    private val clothVerts = FloatArray(vcount * 2)
    private val shadowVerts = FloatArray(vcount * 2)
    private val meshColors = IntArray(vcount)

    private var startNanos = 0L
    private var gustStartNanos = 0L

    // View-space geometry, set in onSizeChanged
    private var clothLeft = 0f
    private var clothRight = 0f
    private var clothW = 0f
    private var logicalW = 0f
    private var logicalH = 0f

    var bannerText: String = "STEP PARKING · 8 AM – 5 PM"
        set(value) {
            field = value
            if (width > 0) { buildBitmaps(); invalidate() }
        }

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            postOnAnimation(this)
        }
    }

    /** A gentle breeze gust that deepens the flutter briefly, then settles. */
    fun gust() {
        gustStartNanos = System.nanoTime()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clothLeft = sideInset
        clothRight = w - sideInset
        clothW = clothRight - clothLeft
        if (clothW <= 0) return
        logicalW = clothW + 2 * margin
        logicalH = clothHeight + 2 * margin
        buildBitmaps()
    }

    private fun localClothPath(): Path {
        val p = Path()
        val w = clothW
        val h = clothHeight
        val cx = w / 2f
        p.moveTo(0f, 0f)
        p.quadTo(cx, 2f * sag, w, 0f)
        p.lineTo(w - notchDepth, h / 2f + sag * 0.6f)
        p.lineTo(w, h)
        p.quadTo(cx, h + 2f * sag, 0f, h)
        p.lineTo(notchDepth, h / 2f + sag * 0.6f)
        p.close()
        return p
    }

    private fun buildBitmaps() {
        val bmpW = (logicalW * ss).toInt()
        val bmpH = (logicalH * ss).toInt()
        if (bmpW <= 0 || bmpH <= 0) return
        val local = localClothPath()

        // Cloth: gradient body + whisper of antique texture + edge + baked lettering
        val cloth = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val cc = Canvas(cloth)
        cc.scale(ss.toFloat(), ss.toFloat())
        cc.translate(margin, margin)
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, clothHeight,
            intArrayOf(0xFF4A3D24.toInt(), 0xFF2E2415.toInt(), 0xFF1E1710.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        cc.drawPath(local, fillPaint)
        cc.save()
        cc.clipPath(local)
        AppCompatResources.getDrawable(context, R.drawable.bg_diamond_grid)?.let { tex ->
            tex.setBounds(0, 0, clothW.toInt(), clothHeight.toInt())
            tex.alpha = 22
            tex.draw(cc)
        }
        cc.restore()
        drawContent(cc)
        cc.drawPath(local, edgePaint)
        clothBitmap = cloth

        // Shadow: blurred silhouette
        val shadow = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val sc = Canvas(shadow)
        sc.scale(ss.toFloat(), ss.toFloat())
        sc.translate(margin, margin)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadowAlpha shl 24
            maskFilter = BlurMaskFilter(shadowBlur, BlurMaskFilter.Blur.NORMAL)
        }
        sc.drawPath(local, shadowPaint)
        shadowBitmap = shadow
    }

    private fun drawContent(canvas: Canvas) {
        val label = bannerText.uppercase(Locale.getDefault())
        var starSize = dp(11f)
        var chevSize = dp(13f)
        var gap1 = dp(9f)
        var gap2 = dp(7f)

        // Shrink the whole content (text + star + chevron) uniformly if it would overflow
        // the cloth on narrow screens, otherwise the star/chevron spill past the ribbon's
        // tapered ends (visible on compact ~360dp phones).
        val origTextSize = textPaint.textSize
        textPaint.textSize = sp(12f)
        var tw = textPaint.measureText(label)
        var total = starSize + gap1 + tw + gap2 + chevSize
        val safeWidth = clothW - dp(36f)
        if (safeWidth > 0f && total > safeWidth) {
            val scale = safeWidth / total
            starSize *= scale; chevSize *= scale; gap1 *= scale; gap2 *= scale
            textPaint.textSize = sp(12f) * scale
            tw = textPaint.measureText(label)
            total = starSize + gap1 + tw + gap2 + chevSize
        }

        val cx = clothW / 2f
        val cy = clothHeight / 2f + sag * 0.5f
        var x = cx - total / 2f

        AppCompatResources.getDrawable(context, R.drawable.ic_star)?.mutate()?.apply {
            setTint(gold)
            setBounds(x.toInt(), (cy - starSize / 2f).toInt(), (x + starSize).toInt(), (cy + starSize / 2f).toInt())
            draw(canvas)
        }
        x += starSize + gap1

        val fm = textPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, x, baseline, textPaint)
        x += tw + gap2

        AppCompatResources.getDrawable(context, R.drawable.ic_chevron_right)?.mutate()?.apply {
            setTint(0xFFC9A85C.toInt())
            setBounds(x.toInt(), (cy - chevSize / 2f).toInt(), (x + chevSize).toInt(), (cy + chevSize / 2f).toInt())
            draw(canvas)
        }

        textPaint.textSize = origTextSize
    }

    override fun onDraw(canvas: Canvas) {
        val cloth = clothBitmap ?: return
        val shadow = shadowBitmap
        if (startNanos == 0L) startNanos = System.nanoTime()
        val now = System.nanoTime()
        val t = (now - startNanos) / 1e9
        val phase1 = t * speed1
        val phase2 = t * speed2
        val gust = if (gustStartNanos > 0L) {
            (gustAmp * exp(-((now - gustStartNanos) / 1e9) / gustTau)).toFloat()
        } else 0f
        val amp = idleAmp + gust

        val ox = clothLeft - margin
        val oy = bannerTop - margin

        var p = 0   // vertex index
        var c = 0   // float index
        for (j in 0..rows) {
            val gy = logicalH * j / rows
            val cfY = ((gy - margin) / clothHeight).coerceIn(0f, 1f)
            val envY = 0.3f + 0.7f * cfY
            for (i in 0..cols) {
                val gx = logicalW * i / cols
                val cfX = (gx - margin) / clothW
                val envX = if (cfX in 0f..1f) sin(PI * cfX).toFloat() else 0f
                val env = envX * envY

                // Compound wave (two beating harmonics)
                val wv = (sin(phase1 - cfX * k1) + 0.45 * sin(phase2 - cfX * k2 + 0.8)) / 1.45
                val dy = amp * wv.toFloat() * env

                // Horizontal skew from the wave slope → crests read as folds
                val slope = (-k1 * cos(phase1 - cfX * k1) - 0.45 * k2 * cos(phase2 - cfX * k2 + 0.8)) / 1.45
                val dx = amp * skewFactor * (slope / k1).coerceIn(-1.0, 1.0).toFloat() * env

                clothVerts[c] = ox + gx + dx
                clothVerts[c + 1] = oy + gy + dy
                shadowVerts[c] = ox + gx + shadowDx
                shadowVerts[c + 1] = oy + gy + dy + shadowDy
                c += 2

                // Per-vertex lighting: light slides along the wave slope
                val sN = (slope / (k1 * 1.45)).coerceIn(-1.0, 1.0)
                val lit = (0.82 + 0.26 * sN).coerceIn(0.5, 1.08)
                val rC = (lit * 255).coerceAtMost(255.0).toInt()
                val gC = (lit * 247).coerceAtMost(255.0).toInt()
                val bC = (lit * 225).coerceAtMost(255.0).toInt()
                meshColors[p] = (0xFF shl 24) or (rC shl 16) or (gC shl 8) or bC
                p++
            }
        }

        if (shadow != null) {
            canvas.drawBitmapMesh(shadow, cols, rows, shadowVerts, 0, null, 0, meshPaint)
        }
        canvas.drawBitmapMesh(cloth, cols, rows, clothVerts, 0, meshColors, 0, meshPaint)

        drawStrings(canvas)
    }

    private fun drawStrings(canvas: Canvas) {
        val lPin = pinInsetX
        val rPin = width - pinInsetX
        stringPath.reset()
        stringPath.moveTo(lPin, pinY)
        stringPath.quadTo((lPin + clothLeft) / 2f, (pinY + bannerTop) / 2f + stringSlack, clothLeft, bannerTop)
        canvas.drawPath(stringPath, stringPaint)
        stringPath.reset()
        stringPath.moveTo(rPin, pinY)
        stringPath.quadTo((rPin + clothRight) / 2f, (pinY + bannerTop) / 2f + stringSlack, clothRight, bannerTop)
        canvas.drawPath(stringPath, stringPaint)

        canvas.drawCircle(lPin, pinY, dp(2.6f), pinPaint)
        canvas.drawCircle(rPin, pinY, dp(2.6f), pinPaint)
        canvas.drawCircle(clothLeft, bannerTop, dp(2f), knotPaint)
        canvas.drawCircle(clothRight, bannerTop, dp(2f), knotPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startNanos = 0L
        postOnAnimation(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }
}
