package com.gridee.parking.ui.lot

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.databinding.ItemSelectLotBinding

/**
 * Single-selection list of parking lots for [SelectParkingLotActivity]. Rows are
 * quiet identity lines (live dial + name + place). The chosen row is marked with
 * the brand_primary filled check — the bottom nav's active-pill language — and
 * opens like an accordion to reveal its detail (address, live spots) right where
 * the user tapped. Only one row is ever open. The slim dock below the list echoes
 * the name and holds the confirm button.
 *
 * [currentLotId] is the lot the user is already assigned to (change mode) — it keeps
 * a persistent "Current" chip so they can orient themselves.
 */
class SelectLotAdapter(
    private val currentLotId: String?,
    private val onLotSelected: (lot: ParkingLot, row: View) -> Unit
) : ListAdapter<ParkingLot, SelectLotAdapter.LotViewHolder>(DiffCallback()) {

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
        const val CHECK_MS = 220L
        const val EXPAND_MS = 280L
        const val COLLAPSE_MS = 200L
    }

    private var selectedLotId: String? = null

    // Lots whose dial has already swept open; rebinds stay static.
    private val appearedIds = HashSet<String>()

    /** Selection changes rebind only the two affected rows, so nothing else moves. */
    fun setSelectedLotId(lotId: String?) {
        if (selectedLotId == lotId) return
        val previous = selectedLotId
        selectedLotId = lotId
        indexOf(previous)?.let { notifyItemChanged(it, PAYLOAD_SELECTION) }
        indexOf(lotId)?.let { notifyItemChanged(it, PAYLOAD_SELECTION) }
    }

    private fun indexOf(lotId: String?): Int? {
        if (lotId == null) return null
        return currentList.indexOfFirst { it.id == lotId }.takeIf { it >= 0 }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LotViewHolder {
        val binding = ItemSelectLotBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: LotViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelection(getItem(position), animate = true)
        } else {
            holder.bind(getItem(position))
        }
    }

    override fun onViewRecycled(holder: LotViewHolder) {
        holder.cancelAnimations()
        super.onViewRecycled(holder)
    }

    inner class LotViewHolder(
        private val binding: ItemSelectLotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // easeOutCubic — the app's content-reveal curve (matches SkeletonShimmer).
        private val smoothDecelerate = PathInterpolator(0.33f, 1f, 0.68f, 1f)
        private var detailAnimator: ValueAnimator? = null

        init {
            binding.rowLot.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val lot = getItem(position)
                    if (lot.id == selectedLotId) return@setOnClickListener
                    it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    setSelectedLotId(lot.id)
                    onLotSelected(lot, binding.rowLot)
                }
            }
        }

        fun bind(lot: ParkingLot) {
            binding.tvLotName.text = lot.name
            binding.tvLotMeta.text = buildMeta(lot)
            binding.dial.setSpots(
                total = lot.totalSpots,
                available = lot.availableSpots,
                animate = appearedIds.add(lot.id)
            )

            bindDetail(lot)

            // Persistent "Current" chip on the lot the user is already assigned to.
            binding.chipCurrent.visibility =
                if (!currentLotId.isNullOrBlank() && lot.id == currentLotId) View.VISIBLE else View.GONE

            bindSelection(lot, animate = false)
            // Hide the hairline under the last row so the card ends clean.
            binding.divider.visibility =
                if (adapterPosition == itemCount - 1) View.GONE else View.VISIBLE
        }

        /** Detail is only ever seen on the open row; fill it regardless so expanding is instant. */
        private fun bindDetail(lot: ParkingLot) {
            val meta = buildMeta(lot)
            val address = lot.address.takeIf { it.isNotBlank() && it != meta }
            binding.tvAddress.text = address ?: lot.name
            binding.tvAddress.visibility = if (address != null) View.VISIBLE else View.GONE

            if (lot.totalSpots > 0) {
                binding.rowSpotsFree.visibility = View.VISIBLE
                binding.tvSpotsFree.text = when {
                    lot.availableSpots <= 0 -> "No spots free right now"
                    else -> "${lot.availableSpots} of ${lot.totalSpots} spots free right now"
                }
            } else {
                binding.rowSpotsFree.visibility = View.GONE
            }
        }

        /** Check pops on the chosen row and its detail unfolds; the previous row folds shut. */
        fun bindSelection(lot: ParkingLot, animate: Boolean) {
            val selected = lot.id == selectedLotId
            binding.rowLot.isSelected = selected
            bindCheck(selected, animate)
            setDetailExpanded(selected, animate)
        }

        private fun bindCheck(selected: Boolean, animate: Boolean) {
            val check = binding.ivCheck
            check.animate().cancel()

            if (!animate) {
                check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                check.alpha = 1f
                check.scaleX = 1f
                check.scaleY = 1f
                return
            }

            if (selected) {
                check.visibility = View.VISIBLE
                check.alpha = 0f
                check.scaleX = 0.5f
                check.scaleY = 0.5f
                check.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(CHECK_MS)
                    .setInterpolator(smoothDecelerate)
                    .start()
            } else {
                check.animate()
                    .alpha(0f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(CHECK_MS)
                    .setInterpolator(smoothDecelerate)
                    .withEndAction {
                        check.visibility = View.INVISIBLE
                        check.alpha = 1f
                        check.scaleX = 1f
                        check.scaleY = 1f
                    }
                    .start()
            }
        }

        private fun setDetailExpanded(expanded: Boolean, animate: Boolean) {
            val view = binding.detailBlock
            detailAnimator?.cancel()
            detailAnimator = null
            view.animate().cancel()

            if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
                view.visibility = if (expanded) View.VISIBLE else View.GONE
                view.alpha = if (expanded) 1f else 0f
                setHeight(view, ViewGroup.LayoutParams.WRAP_CONTENT)
                return
            }

            if (expanded) {
                view.visibility = View.VISIBLE
                val target = measureFullHeight(view)
                if (target <= 0) {
                    // Not laid out yet — settle without motion rather than guess.
                    setHeight(view, ViewGroup.LayoutParams.WRAP_CONTENT)
                    view.alpha = 1f
                    return
                }
                animateHeight(view, view.height.coerceAtLeast(0), target, hideAtEnd = false)
                view.animate().alpha(1f).setDuration(EXPAND_MS)
                    .setInterpolator(smoothDecelerate).start()
            } else {
                if (view.visibility != View.VISIBLE || view.height <= 0) {
                    view.visibility = View.GONE
                    view.alpha = 0f
                    return
                }
                animateHeight(view, view.height, 0, hideAtEnd = true)
                view.animate().alpha(0f).setDuration(COLLAPSE_MS)
                    .setInterpolator(smoothDecelerate).start()
            }
        }

        private fun animateHeight(view: View, from: Int, to: Int, hideAtEnd: Boolean) {
            detailAnimator = ValueAnimator.ofInt(from, to).apply {
                duration = if (hideAtEnd) COLLAPSE_MS else EXPAND_MS
                interpolator = smoothDecelerate
                addUpdateListener {
                    setHeight(view, it.animatedValue as Int)
                }
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (cancelled) return
                        if (hideAtEnd) view.visibility = View.GONE
                        setHeight(view, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                })
                start()
            }
        }

        /** Height the detail block wants at the row's current width. 0 = not laid out yet. */
        private fun measureFullHeight(view: View): Int {
            val parent = view.parent as? View ?: return 0
            val width = parent.width
            if (width <= 0) return 0
            val params = view.layoutParams
            val previous = params.height
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val height = view.measuredHeight
            params.height = previous
            return height
        }

        private fun setHeight(view: View, height: Int) {
            view.layoutParams = view.layoutParams.apply { this.height = height }
        }

        fun cancelAnimations() {
            detailAnimator?.cancel()
            detailAnimator = null
            binding.detailBlock.animate().cancel()
            binding.ivCheck.animate().cancel()
        }

        private fun buildMeta(lot: ParkingLot): String {
            val place = lot.location?.takeIf { it.isNotBlank() }
            return listOfNotNull(
                lot.organizationName?.takeIf { it.isNotBlank() },
                place
            ).joinToString(" · ").ifBlank { lot.address.ifBlank { lot.name } }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ParkingLot>() {
        override fun areItemsTheSame(oldItem: ParkingLot, newItem: ParkingLot) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ParkingLot, newItem: ParkingLot) =
            oldItem == newItem
    }
}
