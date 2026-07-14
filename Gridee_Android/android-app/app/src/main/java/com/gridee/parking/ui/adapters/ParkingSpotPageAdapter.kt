package com.gridee.parking.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.ItemParkingPageBinding
import com.gridee.parking.databinding.ItemParkingSpotHomeBinding

class ParkingSpotPageAdapter(
    private val onItemClick: (ParkingSpot) -> Unit
) : ListAdapter<List<ParkingSpot>, ParkingSpotPageAdapter.PageViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemParkingPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PageViewHolder(
        private val binding: ItemParkingPageBinding,
        private val onItemClick: (ParkingSpot) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val activeHolders = mutableListOf<ParkingSpotHomeAdapter.ParkingSpotViewHolder>()
        private var boundSpots: List<ParkingSpot> = emptyList()

        fun bind(spots: List<ParkingSpot>) {
            // Same spots in the same slots (a background refresh) → update the numbers live
            // in place instead of tearing down and re-animating the whole page.
            if (canUpdateInPlace(spots)) {
                spots.forEachIndexed { index, spot -> activeHolders[index].update(spot) }
                boundSpots = spots
                return
            }
            rebuild(spots)
        }

        private fun canUpdateInPlace(spots: List<ParkingSpot>): Boolean {
            if (activeHolders.size != spots.size || boundSpots.size != spots.size) return false
            return spots.indices.all { boundSpots[it].id == spots[it].id }
        }

        private fun rebuild(spots: List<ParkingSpot>) {
            binding.llPageContainer.removeAllViews()
            activeHolders.clear()
            val inflater = LayoutInflater.from(binding.root.context)

            spots.forEachIndexed { index, spot ->
                val spotBinding = ItemParkingSpotHomeBinding.inflate(inflater, binding.llPageContainer, false)
                val holder = ParkingSpotHomeAdapter.ParkingSpotViewHolder(spotBinding, onItemClick)
                holder.bind(spot)
                activeHolders.add(holder)

                val spotView = spotBinding.root
                spotView.alpha = 0f
                spotView.translationY = 50f

                binding.llPageContainer.addView(spotView)

                // Professional staggered entrance animation
                spotView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setStartDelay(index * 60L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            boundSpots = spots
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<List<ParkingSpot>>() {
        override fun areItemsTheSame(oldItem: List<ParkingSpot>, newItem: List<ParkingSpot>): Boolean {
            if (oldItem.isEmpty() || newItem.isEmpty()) return oldItem == newItem
            return oldItem.first().id == newItem.first().id
        }

        override fun areContentsTheSame(oldItem: List<ParkingSpot>, newItem: List<ParkingSpot>): Boolean {
            return oldItem == newItem
        }
    }
}
