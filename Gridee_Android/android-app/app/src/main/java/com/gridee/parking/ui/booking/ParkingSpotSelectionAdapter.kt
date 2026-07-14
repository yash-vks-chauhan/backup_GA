package com.gridee.parking.ui.booking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.ItemSpotSelectionBinding

class ParkingSpotSelectionAdapter(
    private val onItemClick: (ParkingSpot) -> Unit,
    private val allowUnavailableSelection: Boolean = false
) : ListAdapter<ParkingSpot, ParkingSpotSelectionAdapter.ParkingSpotViewHolder>(ParkingSpotDiffCallback()) {

    private var selectedSpotId: String? = null

    fun setSelectedSpot(spotId: String?) {
        val oldSelectedId = selectedSpotId
        selectedSpotId = spotId
        
        println("ParkingSpotAdapter: Setting selected spot from '$oldSelectedId' to '$spotId'")
        
        if (oldSelectedId != null) {
            val oldIndex = currentList.indexOfFirst { it.id == oldSelectedId }
            println("ParkingSpotAdapter: Old selection index: $oldIndex")
            if (oldIndex >= 0) notifyItemChanged(oldIndex)
        }
        if (spotId != null) {
            val newIndex = currentList.indexOfFirst { it.id == spotId }
            println("ParkingSpotAdapter: New selection index: $newIndex")
            if (newIndex >= 0) notifyItemChanged(newIndex)
        } else {
            println("ParkingSpotAdapter: Clearing selection")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingSpotViewHolder {
        val binding = ItemSpotSelectionBinding.inflate(
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
        private val binding: ItemSpotSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(parkingSpot: ParkingSpot) {
            val context = itemView.context
            binding.apply {
                val displayName = parkingSpot.name
                    ?: parkingSpot.zoneName
                    ?: parkingSpot.spotCode
                    ?: parkingSpot.id
                    ?: "Unknown Spot"
                tvSpotName.text = displayName
                tvAvailability.text = "${parkingSpot.available}/${parkingSpot.capacity} available"
                
                val isSelected = parkingSpot.id == selectedSpotId
                val hasAvailability = parkingSpot.available > 0
                val isSelectable = allowUnavailableSelection || hasAvailability

                // Availability Colors
                if (hasAvailability) {
                    tvAvailability.setTextColor(ContextCompat.getColor(context, R.color.parking_spot_available))
                    layoutAvailability.background = ContextCompat.getDrawable(context, R.drawable.status_soft_active)
                    viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, R.color.parking_spot_available))
                } else {
                    tvAvailability.setTextColor(ContextCompat.getColor(context, R.color.error))
                    layoutAvailability.background = ContextCompat.getDrawable(context, R.drawable.rounded_background_error)
                    viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, R.color.error))
                }

                // Radio Button
                val radioButton = binding.rbSelectSpot
                radioButton.alpha = if (isSelectable) 1.0f else 0.3f
                radioButton.isChecked = isSelected

                root.isEnabled = isSelectable
                root.alpha = if (isSelectable) 1.0f else 0.6f
                
                if (isSelectable) {
                    root.setOnClickListener {
                        setSelectedSpot(parkingSpot.id)
                        onItemClick(parkingSpot)
                    }
                } else {
                    root.setOnClickListener(null)
                }
            }
        }
    }
}

class ParkingSpotDiffCallback : DiffUtil.ItemCallback<ParkingSpot>() {
    override fun areItemsTheSame(oldItem: ParkingSpot, newItem: ParkingSpot): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ParkingSpot, newItem: ParkingSpot): Boolean {
        return oldItem == newItem
    }
}
