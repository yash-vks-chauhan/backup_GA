package com.gridee.parking.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
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

        fun bind(spot: ParkingSpot) {
            // Display Logic: Name -> Zone -> Code -> ID
            val displayName = spot.name?.takeIf { it.isNotBlank() }
                ?: spot.zoneName?.takeIf { it.isNotBlank() }
                ?: spot.spotCode?.takeIf { it.isNotBlank() }
                ?: spot.id

            binding.tvSpotName.text = displayName

            // Dynamic Availability Logic
            val available = spot.available ?: 0
            
            // Base setup
            binding.tvSpotAvailability.text = "$available"
            binding.tvSpotAvailability.setTextColor(Color.parseColor("#111827")) // Always Premium Black
            
            // Dynamic Coloring for Label Only (Subtle Professional Look)
            val colorRes = when {
                available == 0 -> "#9CA3AF" // Gray (Full)
                available <= 5 -> "#DC2626" // Red (Critical)
                available <= 15 -> "#D97706" // Amber (Warning)
                else -> "#059669" // Green (Good)
            }
            
            val statusLabel = if (available == 0) "FULL" else "SPOTS AVAILABLE"
            
            try {
                binding.tvLabelSpots.text = statusLabel
                binding.tvLabelSpots.setTextColor(Color.parseColor(colorRes))
            } catch (e: Exception) {
                 binding.tvLabelSpots.setTextColor(Color.parseColor("#059669"))
            }

            binding.root.setOnClickListener { onItemClick(spot) }
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
