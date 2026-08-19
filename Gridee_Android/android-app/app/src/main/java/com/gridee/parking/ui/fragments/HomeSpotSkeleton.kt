package com.gridee.parking.ui.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gridee.parking.R

/**
 * Ghost cards for the home parking-spot list, following the app's shared skeleton
 * idiom: populate a container with placeholder cards, then let
 * [com.gridee.parking.ui.views.SkeletonShimmer] drive the breath pulse.
 *
 * The list is a horizontal pager, so the caller populates exactly one page's worth
 * of cards — a page is precisely what the user is waiting to see, and the block
 * then occupies the height the real page will. Widths cycle per card so it reads
 * organic rather than stamped.
 */
object HomeSpotSkeleton {

    private val NAME_WIDTHS = intArrayOf(138, 96, 124, 108)
    private val AVAILABILITY_WIDTHS = intArrayOf(96, 82, 104, 88)

    fun populate(container: ViewGroup, cardCount: Int) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        val density = container.resources.displayMetrics.density
        for (i in 0 until cardCount) {
            val card = inflater.inflate(R.layout.item_parking_spot_skeleton, container, false)
            setWidth(
                card.findViewById(R.id.skeleton_spot_name),
                NAME_WIDTHS[i % NAME_WIDTHS.size],
                density
            )
            setWidth(
                card.findViewById(R.id.skeleton_spot_availability),
                AVAILABILITY_WIDTHS[i % AVAILABILITY_WIDTHS.size],
                density
            )
            container.addView(card)
        }
    }

    private fun setWidth(view: View, dp: Int, density: Float) {
        view.layoutParams = view.layoutParams.apply { width = (dp * density).toInt() }
    }
}
