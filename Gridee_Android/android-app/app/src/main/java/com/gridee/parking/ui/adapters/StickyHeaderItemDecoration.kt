package com.gridee.parking.ui.adapters

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class StickyHeaderItemDecoration(
    private val adapter: WalletTransactionsAdapter
) : RecyclerView.ItemDecoration() {

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        val topChild = parent.getChildAt(0) ?: return
        val topChildPosition = parent.getChildAdapterPosition(topChild)
        
        if (topChildPosition == RecyclerView.NO_POSITION) return

        val headerPosition = findHeaderPosition(topChildPosition)
        if (headerPosition == -1) return

        // If the header is the top child and it's visible (top >= 0), don't draw the sticky version yet.
        // This prevents the "double header" visual glitch and allows the header to scroll naturally until it hits the top.
        if (headerPosition == topChildPosition && topChild.top >= 0) {
            return
        }

        // Create or update the header view
        val headerView = getHeaderView(parent, headerPosition)
        
        // Measure and layout the header view
        fixLayoutSize(parent, headerView)

        val contactPoint = headerView.bottom
        val childInContact = getChildInContact(parent, contactPoint)

        var translationY = 0f
        if (childInContact != null) {
            val childAdapterPosition = parent.getChildAdapterPosition(childInContact)
            if (childAdapterPosition != RecyclerView.NO_POSITION && adapter.isHeader(childAdapterPosition)) {
                // Determine layout direction for correct translation if needed, but Y is simple
                // If the next header is hitting the current sticky header, push it up
                translationY = (childInContact.top - headerView.height).toFloat()
            }
        }
        
        // Don't draw if pushed completely off screen
        if (translationY + headerView.height <= 0) return

        c.save()
        // Account for parent's padding (especially since clipToPadding might be false)
        c.translate(parent.paddingLeft.toFloat(), translationY)
        headerView.draw(c)
        c.restore()
    }

    private fun findHeaderPosition(fromPosition: Int): Int {
        var position = fromPosition
        while (position >= 0) {
            if (adapter.isHeader(position)) return position
            position--
        }
        return -1
    }

    private fun getHeaderView(parent: RecyclerView, position: Int): View {
        val viewType = adapter.getItemViewType(position)
        val holder = adapter.onCreateViewHolder(parent, viewType)
        adapter.onBindViewHolder(holder, position)
        return holder.itemView
    }

    private fun fixLayoutSize(parent: ViewGroup, view: View) {
        // Specs for measuring
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        // Ensure layout params exist
        val layoutParams = view.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        view.layoutParams = layoutParams

        // Measure
        val childWidthSpec = ViewGroup.getChildMeasureSpec(
            widthSpec,
            parent.paddingLeft + parent.paddingRight,
            layoutParams.width
        )
        val childHeightSpec = ViewGroup.getChildMeasureSpec(
            heightSpec,
            parent.paddingTop + parent.paddingBottom,
            layoutParams.height
        )

        view.measure(childWidthSpec, childHeightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun getChildInContact(parent: RecyclerView, contactPoint: Int): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            // If the child is crossing the contact point (bottom line of header)
            if (child.bottom > contactPoint && child.top <= contactPoint) {
                return child
            }
        }
        return null
    }
}
