package com.gridee.parking.ui.utils

import android.util.DisplayMetrics
import android.view.View
import android.view.animation.PathInterpolator
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max

/**
 * A SnapHelper that mimics iOS-style behavior:
 * 1. Snaps to the CENTER (matching the 3D visual focus).
 * 2. Uses "Heavy" physics (slower viscosity) for a premium feel.
 */
class IOSSnapHelper : LinearSnapHelper() {

    private var context: android.content.Context? = null

    override fun attachToRecyclerView(recyclerView: RecyclerView?) {
        this.context = recyclerView?.context
        super.attachToRecyclerView(recyclerView)
    }

    override fun createScroller(layoutManager: RecyclerView.LayoutManager): RecyclerView.SmoothScroller? {
        if (layoutManager !is RecyclerView.SmoothScroller.ScrollVectorProvider) {
            return null
        }
        val ctx = context ?: return null
        return object : LinearSmoothScroller(ctx) {
            
            override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
                val snapDistances = calculateDistanceToFinalSnap(layoutManager, targetView) ?: return
                val dx = snapDistances[0]
                val dy = snapDistances[1]
                val time = calculateTimeForDeceleration(max(abs(dx), abs(dy)))
                
                if (time > 0) {
                    action.update(dx, dy, time, SNAP_INTERPOLATOR)
                }
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                // Slower than default (25f) to give it "weight"
                return MILLISECONDS_PER_INCH / displayMetrics.densityDpi
            }

            override fun calculateTimeForDeceleration(dx: Int): Int {
                val time = super.calculateTimeForDeceleration(dx)
                return time.coerceIn(MIN_SCROLL_DURATION, MAX_SCROLL_DURATION)
            }
        }
    }

    companion object {
        private val SNAP_INTERPOLATOR = PathInterpolator(0.25f, 0.46f, 0.45f, 0.94f) // refined iOS "Quart-out" curve
        private const val MILLISECONDS_PER_INCH = 40f // "Fluid" premium feel (was 60f "heavy")
        private const val MIN_SCROLL_DURATION = 100
        private const val MAX_SCROLL_DURATION = 900 // Allow longer, luxurious glides
    }
}
