package com.gridee.parking.ui.booking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.ItemParkingSpotSelectionBinding

class ParkingSpotSelectionAdapter(
    private val onItemClick: (ParkingSpot) -> Unit,
    private val allowUnavailableSelection: Boolean = false
) : ListAdapter<ParkingSpot, ParkingSpotSelectionAdapter.ParkingSpotViewHolder>(ParkingSpotDiffCallback()) {

    private var selectedSpotId: String? = null

    fun setSelectedSpot(spotId: String?) {
        val oldSelectedId = selectedSpotId
        selectedSpotId = spotId
        
        println("ParkingSpotAdapter: Setting selected spot from '$oldSelectedId' to '$spotId'")
        
        // Notify changes for old and new selected items
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
        val binding = ItemParkingSpotSelectionBinding.inflate(
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
        private val binding: ItemParkingSpotSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(parkingSpot: ParkingSpot) {
            println("DEBUG ParkingSpotSelectionAdapter.bind: id=${parkingSpot.id}, name=${parkingSpot.name}, available=${parkingSpot.available}, capacity=${parkingSpot.capacity}")
            
            binding.apply {
                val displayName = parkingSpot.name
                    ?: parkingSpot.zoneName
                    ?: parkingSpot.spotCode
                    ?: parkingSpot.id
                    ?: "Unknown Spot"
                tvSpotName.text = displayName
                tvSpotDetails.text = "Spot ID: ${parkingSpot.spotCode ?: parkingSpot.id}"
                tvAvailability.text = "${parkingSpot.available}/${parkingSpot.capacity} available"
                tvStatus.text = parkingSpot.status.uppercase()
                
                val isSelected = parkingSpot.id == selectedSpotId
                println("ParkingSpotAdapter: Binding spot ${parkingSpot.id}, selected: $isSelected (selectedSpotId: $selectedSpotId)")
                
                val hasAvailability = parkingSpot.available > 0
                val isSelectable = allowUnavailableSelection || hasAvailability

                // Set availability colors and selection state
                if (hasAvailability) {
                    tvAvailability.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.primary_green)
                    )
                    tvStatus.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.primary_green)
                    )
                } else {
                    tvAvailability.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.error)
                    )
                    tvStatus.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.error)
                    )
                }

                // Set radio button state
                val radioButton = binding.ivRadioButton
                radioButton.alpha = if (isSelectable) 1.0f else 0.3f
                if (isSelected) {
                    radioButton.setImageResource(R.drawable.ic_radio_button_checked)
                    println("ParkingSpotAdapter: Applied radio checked to ${parkingSpot.id}")
                } else {
                    radioButton.setImageResource(R.drawable.ic_radio_button_unchecked)
                }

                // Background + enabled state
                cardSpot.setCardBackgroundColor(
                    ContextCompat.getColor(
                        itemView.context,
                        if (isSelectable) android.R.color.white else R.color.light_gray
                    )
                )
                cardSpot.cardElevation = if (isSelectable) 2f else 1f
                root.isEnabled = isSelectable
                root.alpha = if (isSelectable) 1.0f else 0.6f
                
                // Click listener (only if selectable)
                if (isSelectable) {
                    root.setOnClickListener {
                        // Set new selection
                        setSelectedSpot(parkingSpot.id)
                        
                        // Notify the callback
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
