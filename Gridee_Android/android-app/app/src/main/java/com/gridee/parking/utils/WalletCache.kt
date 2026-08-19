package com.gridee.parking.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gridee.parking.data.model.WalletTransaction

/**
 * Last-known wallet snapshot (balance + recent transactions), persisted per user so
 * the wallet tab can paint instantly on open and refresh in the background
 * (stale-while-revalidate). Mirrors [UserProfileCache]. Cleared on logout via
 * [AuthSession.clearSession].
 */
object WalletCache {

    private const val PREFS_NAME = "gridee_wallet_cache"
    private const val KEY_USER_ID = "cached_user_id"
    private const val KEY_BALANCE_BITS = "cached_balance_bits"
    private const val KEY_TXNS_JSON = "cached_transactions_json"
    private const val KEY_UPDATED_AT = "cached_updated_at"

    // Enough for the recent list with headroom; keeps the prefs blob small.
    private const val MAX_TXNS = 50
    // Beyond this the snapshot is too stale to show even briefly; fall back to a load.
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

    private val gson = Gson()
    private val txnListType = object : TypeToken<List<WalletTransaction>>() {}.type

    data class Snapshot(val balance: Double, val transactions: List<WalletTransaction>)

    fun get(context: Context, userId: String): Snapshot? {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isEmpty()) return null

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUserId = prefs.getString(KEY_USER_ID, null)?.trim()
        if (cachedUserId.isNullOrEmpty() || cachedUserId != normalizedUserId) return null

        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        if (updatedAt <= 0L || System.currentTimeMillis() - updatedAt > MAX_AGE_MS) return null

        val json = prefs.getString(KEY_TXNS_JSON, null) ?: return null
        val transactions = runCatching {
            gson.fromJson<List<WalletTransaction>>(json, txnListType)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

        val balance = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_BALANCE_BITS, 0L))
        return Snapshot(balance, transactions)
    }

    fun save(context: Context, userId: String, balance: Double, transactions: List<WalletTransaction>) {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isEmpty()) return

        val trimmed = transactions.take(MAX_TXNS)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, normalizedUserId)
            .putLong(KEY_BALANCE_BITS, java.lang.Double.doubleToRawLongBits(balance))
            .putString(KEY_TXNS_JSON, gson.toJson(trimmed))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
