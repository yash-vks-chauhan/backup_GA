package com.gridee.parking.ui.lot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gridee.parking.R

/**
 * Skeleton rows for the lot-picker pages (place index + lot list), following the
 * app's shared idiom: populate an adaptive number of ghost rows, then let
 * [com.gridee.parking.ui.views.SkeletonShimmer] drive the breath pulse and the
 * staggered rise-in reveal. Widths cycle per row so the block reads organic.
 */
object LotSkeleton {

    private val TITLE_WIDTHS = intArrayOf(150, 118, 138, 104, 132)
    private val META_WIDTHS = intArrayOf(96, 78, 118, 70, 104)

    fun populate(container: ViewGroup, rowCount: Int) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        val density = container.resources.displayMetrics.density
        for (i in 0 until rowCount) {
            val row = inflater.inflate(R.layout.item_select_skeleton, container, false)
            setWidth(row.findViewById(R.id.skeletonTitle), TITLE_WIDTHS[i % TITLE_WIDTHS.size], density)
            setWidth(row.findViewById(R.id.skeletonMeta), META_WIDTHS[i % META_WIDTHS.size], density)
            container.addView(row)
        }
    }

    private fun setWidth(view: View, dp: Int, density: Float) {
        view.layoutParams = view.layoutParams.apply { width = (dp * density).toInt() }
    }
}
