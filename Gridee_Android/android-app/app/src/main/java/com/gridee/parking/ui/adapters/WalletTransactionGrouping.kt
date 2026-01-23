package com.gridee.parking.ui.adapters

import java.util.Calendar
import java.util.TimeZone

object WalletTransactionGrouping {

    private val istTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    fun buildGroupedItems(
        transactions: List<Transaction>,
        maxItems: Int? = null
    ): List<WalletTransactionListItem> {
        if (transactions.isEmpty()) return emptyList()

        val sorted = transactions.sortedByDescending { it.timestamp }
        val limited = maxItems?.let { sorted.take(it) } ?: sorted

        val groupedItems = mutableListOf<WalletTransactionListItem>()
        
        // Use a linked map to preserve order: Section Title -> List of Transactions
        val groups = LinkedHashMap<String, MutableList<Transaction>>()

        val now = Calendar.getInstance(istTimeZone)
        val todayStart = getStartOfDay(now)
        
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStart = getStartOfDay(yesterday)
        
        val thisWeekStart = (now.clone() as Calendar).apply { 
            add(Calendar.DAY_OF_YEAR, -7) 
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        limited.forEach { transaction ->
            val txnCal = Calendar.getInstance(istTimeZone).apply { time = transaction.timestamp }
            
            val sectionTitle = when {
                txnCal.timeInMillis >= todayStart.timeInMillis -> "Today"
                txnCal.timeInMillis >= yesterdayStart.timeInMillis -> "Yesterday"
                txnCal.timeInMillis >= thisWeekStart.timeInMillis -> "This Week"
                else -> {
                    // Format as "Month Year", e.g., "December 2025"
                    val month = txnCal.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault())
                    val year = txnCal.get(Calendar.YEAR)
                    "$month $year"
                }
            }
            
            groups.getOrPut(sectionTitle) { mutableListOf() }.add(transaction)
        }

        groups.forEach { (title, txns) ->
            if (txns.isNotEmpty()) {
                groupedItems.add(WalletTransactionListItem.Header(title))
                txns.forEach { txn ->
                    groupedItems.add(WalletTransactionListItem.Item(txn))
                }
            }
        }

        return groupedItems
    }

    private fun getStartOfDay(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
