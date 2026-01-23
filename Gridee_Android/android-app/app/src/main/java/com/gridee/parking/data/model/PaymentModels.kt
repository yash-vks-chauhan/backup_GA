package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

data class PaymentInitiateRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("amount") val amount: Double
)

data class PaymentInitiateResponse(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("keyId") val keyId: String? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("amount") val amount: Double? = null
)

data class PaymentCallbackRequest(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("paymentId") val paymentId: String,
    @SerializedName("signature") val signature: String? = null,
    @SerializedName("razorpay_signature") val razorpaySignature: String? = null,
    @SerializedName("success") val success: Boolean,
    @SerializedName("userId") val userId: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("status") val status: String? = null
)

data class PaymentCallbackResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null
)

data class TopUpRequest(
    @SerializedName("amount") val amount: Double
)

data class TopUpResponse(
    @SerializedName("orderId") val orderId: String? = null,
    @SerializedName("keyId") val keyId: String? = null,
    @SerializedName("balance") val balance: Double? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("amount") val amount: Double? = null
)
