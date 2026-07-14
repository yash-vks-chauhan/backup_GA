package com.gridee.parking.ui.motion

import android.content.Context
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Detects a horizontal edge-swipe to switch tabs. Drag must start within EDGE_SWIPE_WIDTH_DP
 * of the screen edge so it doesn't fight horizontal scrollers (carousels, swipeable cards)
 * that live inside fragments. Streams progress to the listener at 1:1 with the finger; the
 * activity decides when to commit or cancel based on release velocity + drag fraction.
 */
class TabSwipeGestureDetector(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        /** Return true if a swipe in this direction is allowed (e.g., next tab exists). */
        fun canSwipe(forward: Boolean): Boolean
        fun onSwipeBegin(forward: Boolean)
        fun onSwipeProgress(progress: Float)
        fun onSwipeRelease(forward: Boolean, progress: Float, velocityPxPerSec: Float)
        fun onSwipeCancelled()
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val edgeWidthPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, MotionTokens.EDGE_SWIPE_WIDTH_DP, context.resources.displayMetrics
    )
    private val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()

    private var velocityTracker: VelocityTracker? = null
    private var tracking = false
    private var rejected = false
    private var startX = 0f
    private var startY = 0f
    private var forward = false
    private var pointerId = -1

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rejected = false
                tracking = false
                startX = event.x
                startY = event.y
                pointerId = event.getPointerId(0)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                // Only consider starts that begin within an edge band.
                val fromLeftEdge = startX <= edgeWidthPx
                val fromRightEdge = startX >= screenWidth - edgeWidthPx
                if (!fromLeftEdge && !fromRightEdge) {
                    rejected = true
                    releaseTracker()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (rejected) return false
                velocityTracker?.addMovement(event)
                val dx = event.x - startX
                val dy = event.y - startY

                if (!tracking) {
                    // Decide direction once we cross slop. If vertical motion dominates,
                    // reject so vertical scrollers keep working.
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                        rejected = true
                        releaseTracker()
                        return false
                    }
                    if (abs(dx) > touchSlop) {
                        // Drag rightward (positive dx) from left edge = back to previous tab (forward=false)
                        // Drag leftward (negative dx) from right edge = next tab (forward=true)
                        forward = dx < 0f
                        if (!listener.canSwipe(forward)) {
                            rejected = true
                            releaseTracker()
                            return false
                        }
                        tracking = true
                        listener.onSwipeBegin(forward)
                    }
                }

                if (tracking) {
                    val progressRaw = abs(dx) / screenWidth
                    val progress = rubberBand(progressRaw)
                    listener.onSwipeProgress(progress.coerceIn(0f, 1f))
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (tracking) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.getXVelocity(pointerId) ?: 0f
                    val dx = event.x - startX
                    val progress = (abs(dx) / screenWidth).coerceIn(0f, 1f)
                    // Velocity sign aligns with direction: forward=true (drag left) means vx < 0
                    val signedVelocity = if (forward) -vx else vx
                    listener.onSwipeRelease(forward, progress, signedVelocity)
                }
                tracking = false
                rejected = false
                releaseTracker()
                return false
            }
        }
        return tracking
    }

    private fun rubberBand(progress: Float): Float {
        // Slight resistance after 0.95 to feel anchored; doesn't matter much within [0..1]
        return if (progress < 0.95f) progress else 0.95f + (progress - 0.95f) * 0.5f
    }

    private fun releaseTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
