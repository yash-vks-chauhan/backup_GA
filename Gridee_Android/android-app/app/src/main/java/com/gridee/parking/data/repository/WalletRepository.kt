package com.gridee.parking.data.repository

import android.content.Context
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.WalletDetails
import com.gridee.parking.data.model.WalletTransaction
import com.gridee.parking.utils.AuthSession

class WalletRepository(private val context: Context) {
    
    private val apiService = ApiClient.apiService
    private val sharedPreferences = context.getSharedPreferences("gridee_prefs", Context.MODE_PRIVATE)
    
    suspend fun getWalletDetails(): Result<WalletDetails> {
        return try {
            val userId = AuthSession.getUserId(context)
            if (userId == null) {
                println("WalletRepository: No user ID found")
                return Result.failure(Exception("User not logged in"))
            }
            
            println("WalletRepository: Getting wallet details for user: $userId")
            val response = apiService.getWalletDetails(userId)
            
            if (response.isSuccessful) {
                val walletDetails = response.body()
                if (walletDetails != null) {
                    println("WalletRepository: Wallet details received - Balance: ${walletDetails.balance}, Transactions: ${walletDetails.transactions?.size ?: 0}")
                    Result.success(walletDetails)
                } else {
                    println("WalletRepository: Empty wallet details response")
                    Result.failure(Exception("Empty response"))
                }
            } else {
                println("WalletRepository: Failed to get wallet details - ${response.code()}: ${response.message()}")
                Result.failure(Exception("Failed to get wallet details: ${response.message()}"))
            }
        } catch (e: Exception) {
            println("WalletRepository: Exception getting wallet details: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getWalletTransactions(): Result<List<WalletTransaction>> {
        return try {
            val userId = AuthSession.getUserId(context)
            if (userId == null) {
                println("WalletRepository: No user ID found for transactions")
                return Result.failure(Exception("User not logged in"))
            }
            
            println("WalletRepository: Getting wallet transactions for user: $userId")
            val response = apiService.getWalletTransactions(userId)
            
            if (response.isSuccessful) {
                val transactions = response.body() ?: emptyList()
                println("WalletRepository: Received ${transactions.size} transactions")
                Result.success(transactions)
            } else {
                println("WalletRepository: Failed to get transactions - ${response.code()}: ${response.message()}")
                Result.failure(Exception("Failed to get transactions: ${response.message()}"))
            }
        } catch (e: Exception) {
            println("WalletRepository: Exception getting transactions: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun topUpWallet(amount: Double): Result<Map<String, Any>> {
        return try {
            val userId = AuthSession.getUserId(context)
            if (userId == null) {
                println("WalletRepository: No user ID found for topup")
                return Result.failure(Exception("User not logged in"))
            }
            
            println("WalletRepository: Topping up wallet for user: $userId, amount: $amount")
            val request = com.gridee.parking.data.model.TopUpRequest(amount)
            val response = apiService.topUpWallet(userId, request)
            
            if (response.isSuccessful) {
                val result = response.body()
                val mapResult: Map<String, Any> = if (result != null && result.balance != null) {
                    mapOf("balance" to result.balance)
                } else {
                    emptyMap()
                }
                println("WalletRepository: Topup successful: $mapResult")
                Result.success(mapResult)
            } else {
                println("WalletRepository: Topup failed - ${response.code()}: ${response.message()}")
                Result.failure(Exception("Topup failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            println("WalletRepository: Exception during topup: ${e.message}")
            Result.failure(e)
        }
    }
}
