package com.gridee.parking.ui.discovery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.databinding.ItemParkingLotBinding

class ParkingLotAdapter(
    private val onLotClick: (ParkingLot) -> Unit
) : ListAdapter<ParkingLot, ParkingLotAdapter.ParkingLotViewHolder>(ParkingLotDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingLotViewHolder {
        val binding = ItemParkingLotBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ParkingLotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParkingLotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ParkingLotViewHolder(
        private val binding: ItemParkingLotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLotClick(getItem(position))
                }
            }
        }

        fun bind(lot: ParkingLot) {
            binding.tvLotName.text = lot.name
            binding.tvLotAddress.text = lot.address ?: lot.location
            binding.tvAvailability.text = "${lot.availableSpots}/${lot.totalSpots} spots available"
        }
    }

    private class ParkingLotDiffCallback : DiffUtil.ItemCallback<ParkingLot>() {
        override fun areItemsTheSame(oldItem: ParkingLot, newItem: ParkingLot): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ParkingLot, newItem: ParkingLot): Boolean {
            return oldItem == newItem
        }
    }
}
