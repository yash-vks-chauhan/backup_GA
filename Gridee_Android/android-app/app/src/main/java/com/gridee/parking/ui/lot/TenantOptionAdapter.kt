package com.gridee.parking.ui.lot

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.databinding.ItemSelectCategoryBinding

data class TenantOption(
    val id: String,
    val title: String,
    val subtitle: String,
)

/** Shared organization/location row used by the mandatory tenant selection cascade. */
class TenantOptionAdapter(
    private val onSelected: (TenantOption) -> Unit,
) : ListAdapter<TenantOption, TenantOptionAdapter.OptionViewHolder>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder =
        OptionViewHolder(
            ItemSelectCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class OptionViewHolder(
        private val binding: ItemSelectCategoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.rowCategory.setOnClickListener { view ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSelected(getItem(position))
                }
            }
        }

        fun bind(option: TenantOption, position: Int) {
            binding.tvCategory.text = option.title
            binding.tvMeta.text = option.subtitle
            // Tenant endpoints do not promise aggregate spot counts. Keep the familiar dial as a
            // neutral identity mark instead of fabricating availability before a lot is selected.
            binding.dial.setSpots(total = 0, available = 0, animate = false)
            binding.divider.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE
        }
    }

    private class Diff : DiffUtil.ItemCallback<TenantOption>() {
        override fun areItemsTheSame(oldItem: TenantOption, newItem: TenantOption) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TenantOption, newItem: TenantOption) =
            oldItem == newItem
    }
}
