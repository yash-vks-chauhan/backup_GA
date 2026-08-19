package com.gridee.parking.ui.adapters

import android.animation.ValueAnimator
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Build
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.gridee.parking.R
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
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
        private var nudgeAnimation: SpringAnimation? = null

        // Captured once so the bookable look can be restored when a recycled/updated card
        // flips back from Full to available.
        private val defaultForeground = binding.cardSpot.foreground
        private val defaultElevation = binding.cardSpot.cardElevation

        init {
            binding.root.setOnClickListener { handleTap() }
            binding.btnBook.setOnClickListener { handleTap() }
        }

        /** A full spot is never bookable — refuse the tap instead of opening the sheet. */
        private fun handleTap() {
            val spot = currentSpot ?: return
            if (spot.available <= 0) refuseTap() else onItemClick(spot)
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
            applyBookableState(available > 0)
        }

        /**
         * A full card recedes rather than restyling: neutral status pill, dimmed icon and Park
         * button, no ripple, and flattened so it visually sits behind the bookable cards.
         */
        private fun applyBookableState(bookable: Boolean) {
            binding.layoutAvailability.setBackgroundResource(
                if (bookable) R.drawable.status_soft_active else R.drawable.status_soft_full
            )
            binding.layoutSpotIcon.alpha = if (bookable) 1f else DIMMED_ICON_ALPHA
            binding.btnBook.alpha = if (bookable) 1f else DISABLED_BUTTON_ALPHA
            binding.cardSpot.cardElevation = if (bookable) defaultElevation else 0f
            // Drop the ripple so the card stops advertising itself as actionable, but stay
            // clickable — the tap still needs to reach the refusal feedback.
            binding.cardSpot.foreground = if (bookable) defaultForeground else null

            if (bookable) {
                nudgeAnimation?.cancel()
                binding.cardSpot.translationX = 0f
            }
        }

        /** Tapped a full spot: a damped nudge + haptic tick, then the "Full" pill draws the eye. */
        private fun refuseTap() {
            val card = binding.cardSpot
            card.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.VIRTUAL_KEY
            )

            val density = card.resources.displayMetrics.density
            // Start velocity is derived from the peak travel so the nudge reads the same on
            // every screen density: v = peak * sqrt(stiffness).
            val startVelocity = -NUDGE_PEAK_DP * density * kotlin.math.sqrt(NUDGE_STIFFNESS)

            nudgeAnimation?.cancel()
            nudgeAnimation = SpringAnimation(card, DynamicAnimation.TRANSLATION_X, 0f).apply {
                spring = SpringForce(0f).apply {
                    dampingRatio = 0.38f
                    stiffness = NUDGE_STIFFNESS
                }
                setStartVelocity(startVelocity)
                start()
            }

            pulseAvailabilityPill()
        }

        private fun pulseAvailabilityPill() {
            val pill = binding.layoutAvailability
            pill.animate().cancel()
            // Grow from the left edge so the pill stays anchored under the spot name.
            pill.pivotX = 0f
            pill.pivotY = pill.height / 2f
            pill.scaleX = 1f
            pill.scaleY = 1f
            pill.animate()
                .scaleX(1.06f).scaleY(1.06f)
                .setDuration(120)
                .withEndAction {
                    pill.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
                }
                .start()
        }

        private fun animateAvailability(from: Int, to: Int) {
            // Color settles to the final state immediately (only crosses at the full boundary).
            val accent = accentColor(to)
            binding.tvSpotAvailability.setTextColor(accent)
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(accent)
            applyBookableState(to > 0)

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

        private companion object {
            const val DIMMED_ICON_ALPHA = 0.45f
            const val DISABLED_BUTTON_ALPHA = 0.4f
            const val NUDGE_PEAK_DP = 9f
            const val NUDGE_STIFFNESS = 1400f
        }
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
