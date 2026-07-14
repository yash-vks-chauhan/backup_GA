package com.gridee.parking.ui.adapters

import android.animation.ValueAnimator
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.gridee.parking.R
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.ItemParkingSpotHomeBinding

class ParkingSpotHomeAdapter(
    private val onItemClick: (ParkingSpot) -> Unit
) :
    ListAdapter<ParkingSpot, ParkingSpotHomeAdapter.ParkingSpotViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingSpotViewHolder {
        val binding = ItemParkingSpotHomeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ParkingSpotViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ParkingSpotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ParkingSpotViewHolder(
        private val binding: ItemParkingSpotHomeBinding,
        private val onItemClick: (ParkingSpot) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentSpot: ParkingSpot? = null
        private var availabilityAnimator: ValueAnimator? = null

        init {
            binding.root.setOnClickListener { currentSpot?.let(onItemClick) }
            binding.btnBook.setOnClickListener { currentSpot?.let(onItemClick) }
        }

        /** Full bind used when the card is first inflated. No number animation. */
        fun bind(spot: ParkingSpot) {
            availabilityAnimator?.cancel()
            currentSpot = spot
            binding.tvSpotName.text = resolveName(spot)
            applyAvailability(spot.available)
        }

        /**
         * Live in-place update for background refreshes: animate the availability number
         * ticking to its new value instead of re-inflating the card.
         */
        fun update(spot: ParkingSpot) {
            val previous = currentSpot?.available ?: spot.available
            currentSpot = spot
            binding.tvSpotName.text = resolveName(spot)

            if (previous == spot.available) {
                applyAvailability(spot.available)
            } else {
                animateAvailability(previous, spot.available)
            }
        }

        private fun resolveName(spot: ParkingSpot): String =
            spot.name?.takeIf { it.isNotBlank() }
                ?: spot.zoneName?.takeIf { it.isNotBlank() }
                ?: spot.spotCode?.takeIf { it.isNotBlank() }
                ?: spot.id

        private fun applyAvailability(available: Int) {
            binding.tvSpotAvailability.text = availabilityText(available)
            val accent = accentColor(available)
            binding.tvSpotAvailability.setTextColor(accent)
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(accent)
        }

        private fun animateAvailability(from: Int, to: Int) {
            // Color settles to the final state immediately (only crosses at the full boundary).
            val accent = accentColor(to)
            binding.tvSpotAvailability.setTextColor(accent)
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(accent)

            availabilityAnimator?.cancel()
            availabilityAnimator = ValueAnimator.ofInt(from, to).apply {
                duration = 450L
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    binding.tvSpotAvailability.text = availabilityText(anim.animatedValue as Int)
                }
                start()
            }
            pulseDot()
        }

        private fun pulseDot() {
            binding.viewStatusDot.animate().cancel()
            binding.viewStatusDot.scaleX = 1f
            binding.viewStatusDot.scaleY = 1f
            binding.viewStatusDot.animate()
                .scaleX(1.6f).scaleY(1.6f)
                .setDuration(180)
                .withEndAction {
                    binding.viewStatusDot.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
                }
                .start()
        }

        private fun availabilityText(available: Int): String =
            if (available <= 0) "Full" else "$available Available"

        private fun accentColor(available: Int): Int =
            ContextCompat.getColor(
                itemView.context,
                if (available <= 0) R.color.parking_spot_unavailable else R.color.parking_spot_available
            )
    }

    private class DiffCallback : DiffUtil.ItemCallback<ParkingSpot>() {
        override fun areItemsTheSame(oldItem: ParkingSpot, newItem: ParkingSpot): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ParkingSpot, newItem: ParkingSpot): Boolean {
            return oldItem == newItem
        }
    }
}
