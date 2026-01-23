package com.gridee.parking.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.databinding.ItemAvailabilitySummaryBinding

data class AvailabilitySummary(
    val name: String,
    val available: Int,
    val total: Int
)

class AvailabilitySummaryAdapter :
    ListAdapter<AvailabilitySummary, AvailabilitySummaryAdapter.AvailabilityViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvailabilityViewHolder {
        val binding = ItemAvailabilitySummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AvailabilityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AvailabilityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AvailabilityViewHolder(
        private val binding: ItemAvailabilitySummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AvailabilitySummary) {
            binding.tvSpotName.text = item.name
            binding.tvSpotCount.text = "${item.available} / ${item.total} available"
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AvailabilitySummary>() {
        override fun areItemsTheSame(oldItem: AvailabilitySummary, newItem: AvailabilitySummary): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: AvailabilitySummary, newItem: AvailabilitySummary): Boolean {
            return oldItem == newItem
        }
    }
}
