package com.gridee.parking.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.databinding.ItemVehicleProfileBinding

class VehicleAdapter(
    private val onVehicleClick: (String) -> Unit
) : RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    private var vehicles = listOf<String>()

    fun updateVehicles(newVehicles: List<String>) {
        vehicles = newVehicles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val binding = ItemVehicleProfileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VehicleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        holder.bind(vehicles[position])
    }

    override fun getItemCount(): Int = vehicles.size

    inner class VehicleViewHolder(
        private val binding: ItemVehicleProfileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(vehicleNumber: String) {
            binding.tvVehicleNumber.text = vehicleNumber
            binding.tvVehicleType.text = "Personal Vehicle" // Default type
            
            binding.root.setOnClickListener {
                onVehicleClick(vehicleNumber)
            }
        }
    }
}
