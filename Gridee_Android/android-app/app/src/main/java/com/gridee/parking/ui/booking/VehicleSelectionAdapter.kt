package com.gridee.parking.ui.booking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.data.model.Vehicle
import com.gridee.parking.databinding.ItemVehicleSelectionBinding

class VehicleSelectionAdapter(
    private val vehicles: List<Vehicle>,
    private val onVehicleSelected: (Vehicle) -> Unit
) : RecyclerView.Adapter<VehicleSelectionAdapter.VehicleViewHolder>() {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val binding = ItemVehicleSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VehicleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        holder.bind(vehicles[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = vehicles.size

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousPosition)
        notifyItemChanged(selectedPosition)
    }

    fun getSelectedVehicle(): Vehicle? {
        return if (selectedPosition >= 0) vehicles[selectedPosition] else null
    }

    inner class VehicleViewHolder(
        private val binding: ItemVehicleSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(vehicle: Vehicle, isSelected: Boolean) {
            binding.apply {
                tvVehicleNumber.text = vehicle.number
                rbSelectVehicle.isChecked = isSelected

                root.setOnClickListener {
                    setSelectedPosition(adapterPosition)
                    onVehicleSelected(vehicle)
                }

                rbSelectVehicle.setOnClickListener {
                    setSelectedPosition(adapterPosition)
                    onVehicleSelected(vehicle)
                }
            }
        }
    }
}
