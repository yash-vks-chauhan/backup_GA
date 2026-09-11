package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

/**
 * Wallet top-up request for `POST /api/payments/initiate`.
 *
 * Parking/tenant context is included when the app knows it. It remains optional because the
 * authenticated backend can resolve the user's authoritative default context; a stale or missing
 * local preference must not make the wallet screen unable to add money.
 */
data class PaymentInitiateRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("parkingLotId") val parkingLotId: String? = null,
    @SerializedName("organizationId") val organizationId: String? = null,
    @SerializedName("locationId") val locationId: String? = null
)

enum class PaymentEnvironment { SANDBOX, PRODUCTION }

/**
 * Order created by the backend against Cashfree.
 *
 * The mobile app deliberately models only the four fields in the frontend contract. Cashfree
 * credentials never belong in this response or in the Android app.
 */
data class PaymentInitiateResponse(
    @SerializedName("orderId") val orderId: String? = null,
    @SerializedName("paymentSessionId") val paymentSessionId: String? = null,
    @SerializedName("environment") val environment: String? = null,
    @SerializedName("gateway") val gateway: String? = null
) {
    val normalizedOrderId: String? get() = orderId?.trim()?.takeIf { it.isNotEmpty() }

    val normalizedPaymentSessionId: String?
        get() = paymentSessionId?.trim()?.takeIf { it.isNotEmpty() }

    val paymentEnvironment: PaymentEnvironment?
        get() = runCatching {
            PaymentEnvironment.valueOf(environment?.trim()?.uppercase().orEmpty())
        }.getOrNull()

    val isCashfree: Boolean
        get() = gateway?.trim()?.equals(CASHFREE_GATEWAY, ignoreCase = true) == true

    /** Unknown or missing environment/gateway values fail closed instead of opening in sandbox. */
    val isLaunchable: Boolean
        get() = normalizedOrderId != null &&
            normalizedPaymentSessionId != null &&
            paymentEnvironment != null &&
            isCashfree

    private companion object {
        const val CASHFREE_GATEWAY = "CASHFREE"
    }
}

/**
 * Result of `GET /api/payments/status/{orderId}`.
 *
 * This is the only thing that decides whether a top-up succeeded. The app never infers credit
 * from the checkout SDK's own callback. Even [walletCredited] is informational: the Android UI
 * unlocks its credited state only when [status] is exactly `PAID`.
 */
data class PaymentStatusResponse(
    @SerializedName("orderId") val orderId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("gateway") val gateway: String? = null,
    @SerializedName("gatewayPaymentId") val gatewayPaymentId: String? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("walletCredited") val walletCredited: Boolean = false,
    @SerializedName("organizationId") val organizationId: String? = null,
    @SerializedName("organizationName") val organizationName: String? = null,
    @SerializedName("locationId") val locationId: String? = null,
    @SerializedName("locationName") val locationName: String? = null,
    @SerializedName("parkingLotId") val parkingLotId: String? = null,
    @SerializedName("parkingLotName") val parkingLotName: String? = null
) {
    /** Only the exact backend PAID status allows the frontend to show wallet credit. */
    val isPaid: Boolean
        get() = status?.trim()?.equals(PAID_STATUS, ignoreCase = true) == true

    val isPending: Boolean
        get() = status?.trim()?.equals(PENDING_STATUS, ignoreCase = true) == true

    private companion object {
        const val PAID_STATUS = "PAID"
        const val PENDING_STATUS = "PENDING"
    }
}

data class TopUpRequest(
    @SerializedName("amount") val amount: Double
)

data class TopUpResponse(
    @SerializedName("orderId") val orderId: String? = null,
    @SerializedName("balance") val balance: Double? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("amount") val amount: Double? = null
)
