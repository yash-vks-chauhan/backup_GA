package com.gridee.parking.ui.adapters

import android.content.Context
import com.gridee.parking.R
import java.util.Locale

/**
 * Converts stable backend transaction codes into localized, user-facing text.
 *
 * Backend descriptions are deliberately used only to classify legacy records. They are
 * not rendered because they are server-authored English and therefore do not follow the
 * app locale.
 */
object WalletTransactionText {

    fun resolve(
        context: Context,
        backendType: String?,
        transactionType: TransactionType,
        isReward: Boolean,
        isBookingRelated: Boolean,
        status: String?
    ): String {
        val normalizedBackendType = backendType?.trim()?.uppercase(Locale.ROOT)
        val labelRes = when (normalizedBackendType) {
            "BOOKING_FEE" -> R.string.booking_fee
            "BOOKING_REFUND" -> R.string.transaction_booking_refund
            "WALLET_TOP_UP" -> R.string.wallet_top_up
            "AD_TOP_UP" -> R.string.transaction_ad_top_up
            "WELCOME_BONUS" -> R.string.transaction_welcome_bonus
            "DAILY_WALLET_RESET" -> R.string.transaction_daily_wallet_credit
            "REFUND" -> R.string.transaction_refund
            "PENALTY_FEE" -> R.string.transaction_penalty_fee
            "LATE_CHECK_IN_PENALTY" -> R.string.transaction_late_check_in_penalty
            "LATE_CHECK_OUT_PENALTY" -> R.string.transaction_late_check_out_penalty
            else -> when {
                isReward || transactionType == TransactionType.BONUS ->
                    R.string.transaction_reward_added
                transactionType == TransactionType.TOP_UP -> R.string.wallet_top_up
                transactionType == TransactionType.PARKING_PAYMENT ->
                    R.string.transaction_booking_charge
                transactionType == TransactionType.REFUND && isBookingRelated ->
                    R.string.transaction_booking_refund
                else -> R.string.transaction_refund
            }
        }

        val label = context.getString(labelRes)
        return when (status?.trim()?.lowercase(Locale.ROOT)) {
            "failed" -> context.getString(R.string.transaction_failed_format, label)
            "cancelled", "canceled" ->
                context.getString(R.string.transaction_cancelled_format, label)
            else -> label
        }
    }
}
