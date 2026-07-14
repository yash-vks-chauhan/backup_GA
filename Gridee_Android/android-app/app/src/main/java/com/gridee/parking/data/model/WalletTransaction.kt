package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

data class WalletTransaction(
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("type")
    val type: String? = null, // "CREDIT", "DEBIT"

    @SerializedName("status")
    val status: String? = null, // e.g., "success", "failed", "cancelled"
    
    @SerializedName("amount")
    val amount: Double? = null,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("timestamp")
    val timestamp: String? = null,

    @SerializedName("bookingId")
    val bookingId: String? = null,

    @SerializedName("lotId")
    val lotId: String? = null,

    @SerializedName("lotName")
    val lotName: String? = null,

    @SerializedName("spotId")
    val spotId: String? = null,

    @SerializedName("referenceId")
    val referenceId: String? = null,

    @SerializedName("currency")
    val currency: String? = null,

    @SerializedName("method")
    val method: String? = null,

    @SerializedName("gateway")
    val gateway: String? = null,

    @SerializedName("failureReason")
    val failureReason: String? = null,
    
    @SerializedName("balanceAfter")
    val balanceAfter: Double? = null
)

data class WalletDetails(
    @SerializedName("balance")
    val balance: Double? = null,
    
    @SerializedName("transactions")
    val transactions: List<WalletTransaction>? = null
)
