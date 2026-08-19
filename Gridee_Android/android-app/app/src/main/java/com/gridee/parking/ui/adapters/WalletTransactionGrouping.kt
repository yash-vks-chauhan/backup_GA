package com.gridee.parking.ui.adapters

import android.content.Context
import com.gridee.parking.R
import java.util.Calendar
import java.text.DateFormatSymbols
import java.util.TimeZone

object WalletTransactionGrouping {

    private val istTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    fun buildGroupedItems(
        context: Context,
        transactions: List<Transaction>,
        maxItems: Int? = null
    ): List<WalletTransactionListItem> {
        if (transactions.isEmpty()) return emptyList()

        val sorted = transactions.sortedByDescending { it.timestamp }
        val limited = maxItems?.let { sorted.take(it) } ?: sorted
        val locale = context.resources.configuration.locales[0]

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
                txnCal.timeInMillis >= todayStart.timeInMillis -> context.getString(R.string.today)
                txnCal.timeInMillis >= yesterdayStart.timeInMillis -> context.getString(R.string.yesterday)
                txnCal.timeInMillis >= thisWeekStart.timeInMillis -> context.getString(R.string.this_week)
                else -> {
                    val month = DateFormatSymbols(locale).months[txnCal.get(Calendar.MONTH)]
                    val year = txnCal.get(Calendar.YEAR)
                    context.getString(R.string.wallet_month_year_format, month, year)
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
