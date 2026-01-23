package com.gridee.parking.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.databinding.ItemTransactionBinding
import com.gridee.parking.databinding.ItemWalletTransactionHeaderBinding

sealed class WalletTransactionListItem {
    data class Header(val title: String) : WalletTransactionListItem()
    data class Item(val transaction: Transaction) : WalletTransactionListItem()
    object Loading : WalletTransactionListItem()
}

class WalletTransactionsAdapter(
    private var items: List<WalletTransactionListItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TRANSACTION = 1
        const val TYPE_LOADING = 2
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is WalletTransactionListItem.Header -> TYPE_HEADER
            is WalletTransactionListItem.Item -> TYPE_TRANSACTION
            is WalletTransactionListItem.Loading -> TYPE_LOADING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemWalletTransactionHeaderBinding.inflate(layoutInflater, parent, false)
                HeaderViewHolder(binding)
            }
            TYPE_LOADING -> {
                val view = layoutInflater.inflate(com.gridee.parking.R.layout.item_loading_footer, parent, false)
                LoadingViewHolder(view)
            }
            else -> {
                val binding = ItemTransactionBinding.inflate(layoutInflater, parent, false)
                TransactionViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is WalletTransactionListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is WalletTransactionListItem.Item -> (holder as TransactionViewHolder).bind(item.transaction)
            is WalletTransactionListItem.Loading -> { /* No binding needed */ }
        }
    }

    fun updateItems(newItems: List<WalletTransactionListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private class HeaderViewHolder(
        private val binding: ItemWalletTransactionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WalletTransactionListItem.Header) {
            binding.tvSectionTitle.text = item.title
        }
    }

    private inner class TransactionViewHolder(
        private val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction) {
            TransactionsAdapter.bindTransactionRow(binding, transaction)
        }
    }

    fun isHeader(position: Int): Boolean {
        return position in 0 until items.size && items[position] is WalletTransactionListItem.Header
    }

    private class LoadingViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view)
}
