package com.gridee.parking.data.repository

import android.content.Context
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.api.ApiService
import com.gridee.parking.data.model.TopUpRequest
import com.gridee.parking.data.model.WalletDetails
import com.gridee.parking.data.model.WalletTransaction
import com.gridee.parking.data.repository.cache.TtlSingleFlightCache
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.CancellationException

class WalletRepository(
    context: Context,
    private val apiService: ApiService = ApiClient.apiService,
) {

    private val context = context.applicationContext

    suspend fun getWalletDetails(forceRefresh: Boolean = false): Result<WalletDetails> {
        RemoteConfigManager.loadCached(context)
        if (!RemoteConfigManager.isWalletEnabled()) {
            return Result.failure(Exception("Wallet is temporarily unavailable."))
        }

        val userId = AuthSession.getUserId(context)
            ?: return Result.failure(Exception("User not logged in"))
        val key = walletKey(userId)

        return walletDetailsCache.getOrLoad(
            key = key,
            ttlMillis = WALLET_BALANCE_TTL_MILLIS,
            forceRefresh = forceRefresh,
            isCacheable = { it.isSuccess },
        ) {
            try {
                val response = apiService.getWalletDetails(userId)
                if (response.isSuccessful) {
                    response.body()?.let { Result.success(it) }
                        ?: Result.failure(Exception("Empty wallet response"))
                } else {
                    Result.failure(Exception("Failed to get wallet details (HTTP ${response.code()})"))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
    }

    suspend fun getWalletTransactions(
        forceRefresh: Boolean = false,
        page: Int = 0,
        size: Int = DEFAULT_TRANSACTION_PAGE_SIZE,
        sort: List<String> = DEFAULT_TRANSACTION_SORT,
    ): Result<List<WalletTransaction>> {
        RemoteConfigManager.loadCached(context)
        if (!RemoteConfigManager.isWalletEnabled()) {
            return Result.failure(Exception("Wallet is temporarily unavailable."))
        }

        val userId = AuthSession.getUserId(context)
            ?: return Result.failure(Exception("User not logged in"))
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_TRANSACTION_PAGE_SIZE)
        val safeSort = sort.ifEmpty { DEFAULT_TRANSACTION_SORT }
        val key =
            "${walletKey(userId)}|page=$safePage|size=$safeSize|sort=${safeSort.joinToString(",")}"

        return walletTransactionsCache.getOrLoad(
            key = key,
            ttlMillis = WALLET_TRANSACTIONS_TTL_MILLIS,
            forceRefresh = forceRefresh,
            isCacheable = { it.isSuccess },
        ) {
            try {
                val response = apiService.getWalletTransactions(
                    userId = userId,
                    page = safePage,
                    size = safeSize,
                    sort = safeSort,
                )
                if (response.isSuccessful) {
                    Result.success(response.body()?.content.orEmpty())
                } else {
                    Result.failure(Exception("Failed to get wallet transactions (HTTP ${response.code()})"))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
    }

    /** Mutations are never cached or retried. A success invalidates both wallet read caches once. */
    suspend fun topUpWallet(amount: Double): Result<Map<String, Any>> {
        RemoteConfigManager.loadCached(context)
        if (!RemoteConfigManager.isWalletEnabled()) {
            return Result.failure(Exception("Wallet top-up is temporarily unavailable."))
        }

        val userId = AuthSession.getUserId(context)
            ?: return Result.failure(Exception("User not logged in"))

        return try {
            val response = apiService.topUpWallet(userId, TopUpRequest(amount))
            if (response.isSuccessful) {
                invalidateWallet(userId)
                val result = response.body()
                val mapResult: Map<String, Any> = result?.balance?.let { mapOf("balance" to it) }.orEmpty()
                Result.success(mapResult)
            } else {
                Result.failure(Exception("Top-up failed (HTTP ${response.code()})"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private fun walletKey(userId: String): String {
        val role = AuthSession.getUserRole(context)?.trim()?.uppercase() ?: "USER"
        return "user=${normalize(userId)}|role=$role"
    }

    companion object {
        const val WALLET_BALANCE_TTL_MILLIS = 30 * 1000L
        const val WALLET_TRANSACTIONS_TTL_MILLIS = 5 * 60 * 1000L
        const val DEFAULT_TRANSACTION_PAGE_SIZE = 200
        const val MAX_TRANSACTION_PAGE_SIZE = 1000
        private val DEFAULT_TRANSACTION_SORT = listOf("timestamp", "desc")

        private val walletDetailsCache = TtlSingleFlightCache<String, Result<WalletDetails>>(128)
        private val walletTransactionsCache =
            TtlSingleFlightCache<String, Result<List<WalletTransaction>>>(128)

        @JvmStatic
        fun invalidateWallet(userId: String) {
            val marker = "user=${normalize(userId)}|"
            walletDetailsCache.invalidateWhere { it.startsWith(marker) }
            walletTransactionsCache.invalidateWhere { it.startsWith(marker) }
        }

        @JvmStatic
        fun clearReadCache() {
            walletDetailsCache.clear()
            walletTransactionsCache.clear()
        }

        private fun normalize(value: String): String = value.trim().lowercase()
    }
}
