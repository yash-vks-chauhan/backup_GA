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

            val signText = if (statusIndicatesFailure) {
                ""
            } else {
                if (amountPositive) "+" else "-"
            }
            binding.tvSign.text = signText
            binding.tvSign.visibility = if (signText.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

            binding.tvAmount.text = formattedAmount

            // Configure Status Pill (Amount Background & Color)
            val ctx = context
            val textColorRes = when {
                statusIndicatesFailure -> R.color.status_text_debit
                transaction.type == TransactionType.BONUS -> R.color.status_text_pending
                transaction.type == TransactionType.PARKING_PAYMENT -> R.color.status_text_debit
                transaction.type == TransactionType.REFUND -> R.color.status_text_credit
                transaction.type == TransactionType.TOP_UP -> R.color.status_text_credit
                else -> R.color.status_text_neutral
            }

            val bgRes = when {
                statusIndicatesFailure -> R.drawable.status_soft_debit
                transaction.type == TransactionType.BONUS -> R.drawable.status_soft_pending
                transaction.type == TransactionType.PARKING_PAYMENT -> R.drawable.status_soft_debit
                transaction.type == TransactionType.REFUND -> R.drawable.status_soft_active
                transaction.type == TransactionType.TOP_UP -> R.drawable.status_soft_active
                else -> R.drawable.status_soft_completed
            }

            binding.layoutStatusPill.setBackgroundResource(bgRes)
            val colorResolved = androidx.core.content.ContextCompat.getColor(ctx, textColorRes)
            binding.tvAmount.setTextColor(colorResolved)
            binding.tvSign.setTextColor(colorResolved)
            binding.tvCoinBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(colorResolved)
            binding.viewStatusDot.visibility = android.view.View.GONE

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
            
            // Transaction cards should not trigger notifications on click.
        }
    }
}
