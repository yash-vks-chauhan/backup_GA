package com.gridee.parking.utils

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.config.ApiConfig
import com.gridee.parking.data.api.ApiClient
import kotlinx.coroutines.launch

object DebugHelper {
    
    fun testBackendConnection(context: Context, lifecycleOwner: LifecycleOwner, userId: String = "testuser") {
        lifecycleOwner.lifecycleScope.launch {
            try {
                Toast.makeText(context, "Testing connection to: ${ApiConfig.BASE_URL}", Toast.LENGTH_LONG).show()
                
                // Test wallet endpoint
                val response = ApiClient.apiService.getWalletDetails(userId)
                if (response.isSuccessful) {
                    val wallet = response.body()
                    Toast.makeText(context, "✅ Wallet API working! Balance: ${wallet?.balance ?: "null"}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "❌ Wallet API failed: ${response.code()} - ${response.message()}", Toast.LENGTH_LONG).show()
                }
                
                // Test transactions endpoint
                val transResponse = ApiClient.apiService.getWalletTransactions(userId)
                if (transResponse.isSuccessful) {
                    val transactions = transResponse.body()
                    Toast.makeText(context, "✅ Transactions API working! Count: ${transactions?.size ?: 0}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "❌ Transactions API failed: ${transResponse.code()} - ${transResponse.message()}", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    fun showApiConfig(context: Context) {
        val message = """
            Environment: ${ApiConfig.getEnvironmentInfo()}
            Base URL: ${ApiConfig.BASE_URL}
            Auth Required: ${ApiConfig.REQUIRES_AUTH}
        """.trimIndent()
        
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
