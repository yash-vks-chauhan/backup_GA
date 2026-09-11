package com.gridee.parking.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.gridee.parking.R

/**
 * The gutter of the bookings stage rail: a lead line, a stop marker, a tail line.
 *
 * Drawn rather than assembled from three background drawables because the three pieces have
 * to agree on one vertical axis and on where the marker sits — and because a *vertical* dashed
 * line has no honest shape-drawable spelling (`<shape android:shape="line">` is horizontal, and
 * rotating it fights the layout pass).
 *
 * Everything the rail says about state it says here:
 *   travelled -> [Marker.DONE] + [Line.SOLID]
 *   here      -> [Marker.CURRENT] + its halo
 *   ahead     -> [Marker.FUTURE] + [Line.DASHED]
 *   skipped   -> [Marker.SKIPPED], a ring struck through, for a stop that will never happen
 */
class StageRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class Marker { DONE, CURRENT, FUTURE, SKIPPED }
    enum class Line { NONE, SOLID, DASHED }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    private val railInk = ContextCompat.getColor(context, R.color.stage_rail_ink)
    private val pageInk = ContextCompat.getColor(context, R.color.background_primary)

    /** Distance from the top of this view to the marker's centre — set to the row's optical centre. */
    var markerCenterY: Float = dp(19f)
        set(value) { field = value; invalidate() }

    var marker: Marker = Marker.FUTURE
        set(value) { field = value; invalidate() }

    var leadLine: Line = Line.NONE
        set(value) { field = value; invalidate() }

    var tailLine: Line = Line.NONE
        set(value) { field = value; invalidate() }

    /** Fill for [Marker.CURRENT]; the halo is derived from it. */
    var accentColor: Int = railInk
        set(value) { field = value; invalidate() }

    var haloColor: Int = 0
        set(value) { field = value; invalidate() }

    private val dashEffect = DashPathEffect(floatArrayOf(dp(2.5f), dp(2.5f)), 0f)

    init {
        // DashPathEffect is only reliably hardware-accelerated from P; a software layer on a
        // view this small costs nothing and renders identically everywhere.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = markerCenterY
        val radius = when (marker) {
            Marker.CURRENT -> dp(5.5f)
            Marker.DONE -> dp(4.5f)
            else -> dp(4f)
        }
        val gap = dp(3f)

        drawSegment(canvas, cx, 0f, cy - radius - gap, leadLine)
        drawSegment(canvas, cx, cy + radius + gap, height.toFloat(), tailLine)

        when (marker) {
            Marker.CURRENT -> {
                if (haloColor != 0) {
                    fillPaint.color = haloColor
                    canvas.drawCircle(cx, cy, radius + dp(4f), fillPaint)
                }
                fillPaint.color = accentColor
                canvas.drawCircle(cx, cy, radius, fillPaint)
            }
            Marker.DONE -> {
                fillPaint.color = railInk
                canvas.drawCircle(cx, cy, radius, fillPaint)
            }
            Marker.FUTURE -> {
                strokePaint.color = railInk
                strokePaint.strokeWidth = dp(1.25f)
                fillPaint.color = pageInk
                canvas.drawCircle(cx, cy, radius, fillPaint)
                canvas.drawCircle(cx, cy, radius - dp(0.625f), strokePaint)
            }
            Marker.SKIPPED -> {
                strokePaint.color = railInk
                strokePaint.strokeWidth = dp(1.25f)
                fillPaint.color = pageInk
                canvas.drawCircle(cx, cy, radius, fillPaint)
                canvas.drawCircle(cx, cy, radius - dp(0.625f), strokePaint)
                // Struck through: the stop existed and was passed over.
                canvas.drawLine(cx - radius - dp(2f), cy, cx + radius + dp(2f), cy, strokePaint)
            }
        }
    }

    private fun drawSegment(canvas: Canvas, cx: Float, top: Float, bottom: Float, style: Line) {
        if (style == Line.NONE || bottom <= top) return
        linePaint.color = railInk
        linePaint.pathEffect = if (style == Line.DASHED) dashEffect else null
        canvas.drawLine(cx, top, cx, bottom, linePaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
