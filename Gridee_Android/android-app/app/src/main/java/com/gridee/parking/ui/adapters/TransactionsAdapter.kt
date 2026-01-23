package com.gridee.parking.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.databinding.ItemTransactionBinding
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: Double,
    val description: String,
    val timestamp: Date,
    val balanceAfter: Double,
    val paymentMethod: String? = null,
    val reference: String? = null,
    val status: String? = null
)

enum class TransactionType {
    TOP_UP,
    PARKING_PAYMENT,
    REFUND,
    BONUS
}

class TransactionsAdapter(
    private var transactions: List<Transaction>
) : RecyclerView.Adapter<TransactionsAdapter.TransactionViewHolder>() {

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction) {
            bindTransactionRow(binding, transaction)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    fun updateTransactions(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }

    companion object {
        fun bindTransactionRow(binding: ItemTransactionBinding, transaction: Transaction) {
            val context = binding.root.context

            binding.tvDescription.text = transaction.description

            // Timestamp (auto-updating "time ago")
            binding.tvTimestamp.setReferenceTime(transaction.timestamp.time)
            binding.tvTimestamp.visibility = android.view.View.VISIBLE

            // Timestamp (auto-updating "time ago")
            binding.tvTimestamp.setReferenceTime(transaction.timestamp.time)
            binding.tvTimestamp.visibility = android.view.View.VISIBLE

            val statusLower = transaction.status
                ?.trim()
                ?.lowercase(Locale.getDefault())
                .orEmpty()
            val statusIndicatesFailure = statusLower == "failed" || statusLower == "cancelled" || statusLower == "canceled"
            val amountPositive = transaction.amount >= 0
            
            // Format Amount
            val absoluteAmount = abs(transaction.amount)
            val formattedAmount = String.format(
                Locale.getDefault(),
                "%.2f",
                absoluteAmount
            )

            val amountText = if (statusIndicatesFailure) {
                "₹$formattedAmount"
            } else {
                if (amountPositive) "+₹$formattedAmount" else "-₹$formattedAmount"
            }
            binding.tvAmount.text = amountText

            // Configure Status Pill (Amount Background & Color)
            when {
                statusIndicatesFailure -> {
                    // Failed: Red Pill, Red Text
                    binding.layoutStatusPill.setBackgroundResource(R.drawable.status_soft_debit)
                    binding.tvAmount.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    binding.viewStatusDot.visibility = android.view.View.GONE
                }
                transaction.type == TransactionType.BONUS -> {
                    // Bonus: Pending Style (Amber Pill, Amber Text)
                    binding.layoutStatusPill.setBackgroundResource(R.drawable.status_soft_pending)
                    binding.tvAmount.setTextColor(android.graphics.Color.parseColor("#D97706"))
                    binding.viewStatusDot.visibility = android.view.View.GONE
                }
                transaction.type == TransactionType.PARKING_PAYMENT -> {
                    // Debit: Red Pill, Red Text
                    binding.layoutStatusPill.setBackgroundResource(R.drawable.status_soft_debit)
                    binding.tvAmount.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    binding.viewStatusDot.visibility = android.view.View.GONE 
                }
                transaction.type == TransactionType.REFUND -> {
                    // Refund: Green Pill, Green Text
                    binding.layoutStatusPill.setBackgroundResource(R.drawable.status_soft_active)
                    binding.tvAmount.setTextColor(android.graphics.Color.parseColor("#059669"))
                    binding.viewStatusDot.visibility = android.view.View.GONE
                }
                transaction.type == TransactionType.TOP_UP -> {
                    // Credit: Green Pill, Green Text
                    binding.layoutStatusPill.setBackgroundResource(R.drawable.status_soft_active)
                    binding.tvAmount.setTextColor(android.graphics.Color.parseColor("#059669"))
                    binding.viewStatusDot.visibility = android.view.View.GONE
                }
                else -> {
                    // Default: Grey Pill, Dark Grey Text
                    binding.layoutStatusPill.setBackgroundResource(R.drawable.status_soft_completed)
                    binding.tvAmount.setTextColor(android.graphics.Color.parseColor("#374151"))
                    binding.viewStatusDot.visibility = android.view.View.GONE
                }
            }

            val iconRes = when {
                statusIndicatesFailure -> R.drawable.ic_wallet_transaction_failed
                transaction.type == TransactionType.BONUS -> R.drawable.ic_transaction_reward
                transaction.type == TransactionType.TOP_UP -> R.drawable.ic_wallet_topup_success
                transaction.type == TransactionType.PARKING_PAYMENT -> R.drawable.ic_wallet_booking_charge
                transaction.type == TransactionType.REFUND -> R.drawable.ic_wallet_topup_success
                else -> R.drawable.ic_wallet_topup_success
            }
            binding.ivTransactionIcon.setImageResource(iconRes)

            // Keep all wallet icons neutral (grey) for a consistent UI.
            binding.ivTransactionIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.text_secondary)
            

        }
    }
}
