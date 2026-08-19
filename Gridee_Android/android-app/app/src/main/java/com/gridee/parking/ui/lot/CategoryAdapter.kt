package com.gridee.parking.ui.lot

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.databinding.ItemSelectCategoryBinding

/**
 * The place index for [ChooseCategoryActivity]. Each row uses the app's standard
 * anatomy (44dp tile, title, meta, chevron) with a live [com.gridee.parking.ui.views.SpotDialView]
 * as the tile.
 */
class CategoryAdapter(
    private val onCategorySelected: (category: LotCategory) -> Unit
) : ListAdapter<LotCategory, CategoryAdapter.CategoryViewHolder>(DiffCallback()) {

    // Types whose dial has already swept open; rebinds stay static.
    private val appearedTypes = HashSet<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemSelectCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class CategoryViewHolder(
        private val binding: ItemSelectCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.rowCategory.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onCategorySelected(getItem(position))
                }
            }
        }

        fun bind(category: LotCategory, position: Int) {
            binding.tvCategory.text = category.label
            binding.tvMeta.text = buildMeta(category)
            binding.dial.setSpots(
                total = category.totalSpots,
                available = category.availableSpots,
                animate = appearedTypes.add(category.type)
            )
            binding.divider.visibility =
                if (position == itemCount - 1) View.GONE else View.VISIBLE
        }

        private fun buildMeta(category: LotCategory): String {
            val locations = if (category.count == 1) "1 location" else "${category.count} locations"
            return when {
                category.totalSpots <= 0 -> locations
                category.availableSpots <= 0 -> "Full right now · $locations"
                else -> "${category.availableSpots} of ${category.totalSpots} spots free · $locations"
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<LotCategory>() {
        override fun areItemsTheSame(oldItem: LotCategory, newItem: LotCategory) =
            oldItem.type == newItem.type

        override fun areContentsTheSame(oldItem: LotCategory, newItem: LotCategory) =
            oldItem == newItem
    }
}
