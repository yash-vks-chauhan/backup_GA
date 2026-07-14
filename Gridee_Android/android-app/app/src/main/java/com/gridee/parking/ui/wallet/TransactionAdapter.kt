package com.gridee.parking.ui.wallet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.data.model.WalletTransaction
import com.gridee.parking.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter : ListAdapter<WalletTransaction, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class TransactionViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(transaction: WalletTransaction) {
            binding.apply {
                tvDescription.text = transaction.description
                tvAmount.text = if (transaction.type == "CREDIT") {
                    "+${transaction.amount}"
                } else {
                    "-${transaction.amount}"
                }
                
                // Set amount color based on transaction type
                tvAmount.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, com.gridee.parking.R.color.text_primary))
                
                // Format timestamp
                tvTimestamp.text = formatTimestamp(transaction.timestamp ?: "")
                
                // tvBalance removed from layout
            }
        }
        
        private fun formatTimestamp(timestamp: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                val date = inputFormat.parse(timestamp)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                timestamp
            }
        }
    }
    
    class TransactionDiffCallback : DiffUtil.ItemCallback<WalletTransaction>() {
        override fun areItemsTheSame(oldItem: WalletTransaction, newItem: WalletTransaction): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: WalletTransaction, newItem: WalletTransaction): Boolean {
            return oldItem == newItem
        }
    }
}
