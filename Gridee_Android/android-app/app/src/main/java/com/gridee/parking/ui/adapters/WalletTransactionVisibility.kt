package com.gridee.parking.ui.adapters

import com.gridee.parking.data.model.WalletTransaction
import java.util.Locale

/**
 * Decides which backend transactions belong in a user-facing list.
 *
 * The backend writes a `WALLET_TOP_UP` row with status `pending` the moment an order is
 * created — before the user has paid anything. If the user then cancels, abandons checkout, or
 * the payment fails, that row survives with status `pending` or `failed`. The list UI colours
 * every top-up as a credit regardless of status, so those rows rendered as money received that
 * the user never actually paid, while the wallet balance (correctly) did not include it.
 *
 * A gateway top-up is only money once the backend says `completed`. Anything else is an attempt,
 * not a transaction, and is kept out of the list.
 */
object WalletTransactionVisibility {

    private const val STATUS_COMPLETED = "completed"
    private const val STATUS_PENDING = "pending"
    private const val STATUS_FAILED = "failed"

    /** Types whose money only exists once a payment gateway has settled it. */
    private val GATEWAY_FUNDED_TYPES = setOf("WALLET_TOP_UP")

    fun isListable(transaction: WalletTransaction): Boolean =
        isListable(transaction.type, transaction.status)

    fun isListable(backendType: String?, status: String?): Boolean {
        val type = backendType?.trim()?.uppercase(Locale.ROOT)
        if (type !in GATEWAY_FUNDED_TYPES) return true

        return when (normalize(status)) {
            // Unsettled or dead attempts never happened as far as the user's money is concerned.
            STATUS_PENDING, STATUS_FAILED -> false
            // `completed`, and legacy rows written before the status field existed.
            else -> true
        }
    }

    fun isFailed(status: String?): Boolean = normalize(status) == STATUS_FAILED

    fun isPending(status: String?): Boolean = normalize(status) == STATUS_PENDING

    fun isCompleted(status: String?): Boolean = normalize(status) == STATUS_COMPLETED

    private fun normalize(status: String?): String? =
        status?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
}
