package com.gridee.parking.ui.motion

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Fragment host that intercepts horizontal edge-swipes to switch tabs while letting
 * vertical scrolls and in-content horizontal scrollers (carousels) work normally.
 *
 * Strategy: in onInterceptTouchEvent, only claim the gesture if it starts within the edge
 * band, moves dominantly horizontally past the touch slop, and the listener agrees the
 * swipe is allowed. Otherwise, the event continues to children.
 */
class SwipeableFragmentContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var detector: TabSwipeGestureDetector? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val edgeWidthPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, MotionTokens.EDGE_SWIPE_WIDTH_DP, resources.displayMetrics
    )
    private val screenWidth get() = resources.displayMetrics.widthPixels.toFloat()

    private var downX = 0f
    private var downY = 0f
    private var fromEdge = false
    private var intercepting = false

    fun setSwipeListener(listener: TabSwipeGestureDetector.Listener) {
        detector = TabSwipeGestureDetector(context, listener)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                fromEdge = downX <= edgeWidthPx || downX >= screenWidth - edgeWidthPx
                intercepting = false
                if (fromEdge) detector?.onTouchEvent(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!fromEdge) return false
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!intercepting && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    intercepting = true
                    detector?.onTouchEvent(ev)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                if (intercepting) detector?.onTouchEvent(ev)
                intercepting = false
                fromEdge = false
            }
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Once we've intercepted, drive the detector with the full event stream.
        return detector?.onTouchEvent(event) ?: false
    }
}
