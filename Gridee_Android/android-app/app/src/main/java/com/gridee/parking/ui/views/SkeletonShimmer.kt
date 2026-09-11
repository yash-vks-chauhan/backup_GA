package com.gridee.parking.ui.views

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.ui.motion.AnimatorSettingsCompat

/**
 * The app's shared transaction-list loading skeleton.
 *
 * - [populate] fills a container with an adaptive number of skeleton rows/headers
 *   (5 on the wallet tab, a screenful on the full history page).
 * - [start] drives the "breath" pulse — one animator fading every placeholder
 *   between 0.55 and 1.0 alpha in unison (~1.4s cycle). Calm and ambient, matching
 *   the parking bottom sheet.
 * - [revealStagger] / [revealView] replace the skeleton with real content via a
 *   staggered "settle-in": rows rise a few dp into place one after another. No
 *   muddy crossfade — the skeleton is removed instantly and the incoming motion
 *   carries the reveal.
 *
 * All motion is skipped when the system has animations disabled (Developer Options
 * animation scale 0 / Accessibility "Remove animations").
 */
object SkeletonShimmer {

    const val PILL_TAG = "skeleton_pill"

    private const val BREATH_LOW = 0.55f
    private const val BREATH_HIGH = 1.0f
    private const val BREATH_HALF_CYCLE_MS = 700L

    private const val ROWS_PER_HEADER = 4
    private const val REVEAL_RISE_DP = 12f
    private const val REVEAL_DURATION_MS = 400L
    private const val REVEAL_STAGGER_MS = 34L
    private const val REVEAL_MAX_STAGGER_STEPS = 6

    // Per-row placeholder widths (dp), cycled so the block reads organic, not robotic.
    private val TITLE_WIDTHS = intArrayOf(150, 118, 138, 104, 132, 126, 112, 144)
    private val SUBTITLE_WIDTHS = intArrayOf(78, 92, 70, 86, 74, 88, 82, 66)
    private val AMOUNT_WIDTHS = intArrayOf(64, 72, 60, 68, 66, 74, 62, 70)
    private val HEADER_WIDTHS = intArrayOf(72, 92, 84, 100)

    // easeOutCubic — smooth, gentle deceleration with a long settle. Reads calmer
    // than the snappier Material emphasized curve for a content reveal.
    private fun smoothDecelerate() = PathInterpolator(0.33f, 1f, 0.68f, 1f)

    /**
     * Inflate [rowCount] skeleton rows into [container]. When [includeHeaders] is
     * true a date-header pill is inserted every few rows (matches the grouped list);
     * pass false for the pagination footer, which is just a couple of ghost rows.
     */
    fun populate(container: ViewGroup, rowCount: Int, includeHeaders: Boolean = true) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        val density = container.resources.displayMetrics.density

        var headerIndex = 0
        var rowsUnderHeader = ROWS_PER_HEADER
        for (i in 0 until rowCount) {
            if (includeHeaders && rowsUnderHeader >= ROWS_PER_HEADER) {
                val header = inflater.inflate(R.layout.item_transaction_skeleton_header, container, false)
                setWidth(header, HEADER_WIDTHS[headerIndex % HEADER_WIDTHS.size], density)
                container.addView(header)
                headerIndex++
                rowsUnderHeader = 0
            }
            val row = inflater.inflate(R.layout.item_transaction_skeleton, container, false)
            setWidth(row.findViewById(R.id.skeleton_title), TITLE_WIDTHS[i % TITLE_WIDTHS.size], density)
            setWidth(row.findViewById(R.id.skeleton_subtitle), SUBTITLE_WIDTHS[i % SUBTITLE_WIDTHS.size], density)
            setWidth(row.findViewById(R.id.skeleton_amount), AMOUNT_WIDTHS[i % AMOUNT_WIDTHS.size], density)
            container.addView(row)
            rowsUnderHeader++
        }
    }

    private fun setWidth(view: View, dp: Int, density: Float) {
        view.layoutParams = view.layoutParams.apply { width = (dp * density).toInt() }
    }

    /**
     * Start the breath pulse over every tagged pill under [root]. Returns the
     * animator so the caller can cancel it in onDestroyView / onDestroy, or null
     * when there's nothing to animate / motion is disabled.
     */
    fun start(root: View): ValueAnimator? {
        val pills = collectPills(root)
        if (pills.isEmpty()) return null

        if (!AnimatorSettingsCompat.areEnabled(root.context)) {
            pills.forEach { it.alpha = BREATH_HIGH }
            return null
        }

        return ValueAnimator.ofFloat(BREATH_LOW, BREATH_HIGH).apply {
            duration = BREATH_HALF_CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val a = anim.animatedValue as Float
                pills.forEach { it.alpha = a }
            }
            start()
        }
    }

    /**
     * Reveal a freshly-populated list by rising each currently-attached child into
     * place with a small stagger. Call right after the adapter's data is set; the
     * pre-draw hook animates whatever children ended up laid out.
     */
    fun revealStagger(recyclerView: RecyclerView) {
        if (!AnimatorSettingsCompat.areEnabled(recyclerView.context)) return
        recyclerView.doOnPreDraw {
            val rise = REVEAL_RISE_DP * recyclerView.resources.displayMetrics.density
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                val step = i.coerceAtMost(REVEAL_MAX_STAGGER_STEPS)
                child.translationY = rise
                child.alpha = 0f
                child.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setStartDelay(step * REVEAL_STAGGER_MS)
                    .setDuration(REVEAL_DURATION_MS)
                    .setInterpolator(smoothDecelerate())
                    .withLayer()
                    .withEndAction {
                        child.translationY = 0f
                        child.alpha = 1f
                    }
                    .start()
            }
        }
    }

    /** Reveal a single view (e.g. an empty state) with the same rise-in motion. */
    fun revealView(view: View) {
        if (!AnimatorSettingsCompat.areEnabled(view.context)) return
        val rise = REVEAL_RISE_DP * view.resources.displayMetrics.density
        view.translationY = rise
        view.alpha = 0f
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(REVEAL_DURATION_MS)
            .setInterpolator(smoothDecelerate())
            .withLayer()
            .withEndAction {
                view.translationY = 0f
                view.alpha = 1f
            }
            .start()
    }

    private fun collectPills(view: View, sink: MutableList<View> = mutableListOf()): List<View> {
        if (view.tag == PILL_TAG) sink.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectPills(view.getChildAt(i), sink)
            }
        }
        return sink
    }
}
