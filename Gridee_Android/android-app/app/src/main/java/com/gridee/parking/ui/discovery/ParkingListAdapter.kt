package com.gridee.parking.ui.discovery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.ItemParkingSpotBinding

class ParkingListAdapter(
    private val onItemClick: (ParkingSpot) -> Unit
) : ListAdapter<ParkingSpot, ParkingListAdapter.ParkingSpotViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingSpotViewHolder {
        val binding = ItemParkingSpotBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ParkingSpotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParkingSpotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ParkingSpotViewHolder(
        private val binding: ItemParkingSpotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(parkingSpot: ParkingSpot) {
            binding.apply {
                tvName.text = parkingSpot.name ?: parkingSpot.zoneName ?: "Unknown Spot"
                tvAddress.text = "Lot ID: ${parkingSpot.lotId}"
                tvPrice.text = "Available: ${parkingSpot.available}/${parkingSpot.capacity}"
                tvDistance.text = parkingSpot.status.uppercase()
                
                // Set rating to a default value since it's not in API
                ratingBar.rating = 4.0f
                tvRating.text = "4.0 (${parkingSpot.capacity})"
                
                // Availability
                if (parkingSpot.available > 0) {
                    tvAvailability.text = "${parkingSpot.available} spots available"
                    tvAvailability.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.primary_green)
                    )
                    cardAvailability.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.context, R.color.primary_green_light)
                    )
                } else {
                    tvAvailability.text = "Full"
                    tvAvailability.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.error)
                    )
                    cardAvailability.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.context, android.R.color.transparent)
                    )
                }
                
                // Hide amenities for now since they're not in the API response
                setupAmenities(emptyList())
                
                // Click listener
                root.setOnClickListener {
                    onItemClick(parkingSpot)
                }
            }
        }
        
        private fun setupAmenities(amenities: List<String>) {
            binding.apply {
                // Hide all amenity icons first
                icCovered.visibility = android.view.View.GONE
                icSecurity.visibility = android.view.View.GONE
                icEvCharging.visibility = android.view.View.GONE
                icCctv.visibility = android.view.View.GONE
                
                // Show relevant amenity icons
                amenities.forEach { amenity ->
                    when (amenity) {
                        "covered" -> icCovered.visibility = android.view.View.VISIBLE
                        "security" -> icSecurity.visibility = android.view.View.VISIBLE
                        "ev_charging" -> icEvCharging.visibility = android.view.View.VISIBLE
                        "cctv" -> icCctv.visibility = android.view.View.VISIBLE
                    }
                }
            }
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
