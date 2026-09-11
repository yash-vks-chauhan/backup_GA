package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

/**
 * Per-lot booking rules from `GET /api/parking-lots/{lotId}/booking-policy`, and inline on
 * [ParkingLot.bookingPolicy].
 *
 * Fields stay nullable at the transport boundary so malformed/older responses can be decoded
 * without crashing Gson. The booking-policy endpoint is expected to return the effective values;
 * [BookingPolicyResolver] rejects an incomplete policy instead of silently applying another
 * tenant's timing or payment assumptions.
 */
data class ParkingLotBookingPolicy(
    /** [MODE_DAILY] or [MODE_FLEXIBLE]. */
    @SerializedName("bookingMode") val bookingMode: String? = null,
    @SerializedName("advanceBookingDays") val advanceBookingDays: Int? = null,
    /** "HH:mm" — when tomorrow's slots open for booking. */
    @SerializedName("nextDayBookingOpenTime") val nextDayBookingOpenTime: String? = null,
    /** "HH:mm" — after this, same-day booking closes. */
    @SerializedName("dailyBookingEndTime") val dailyBookingEndTime: String? = null,
    @SerializedName("fixedTimeSlotsEnabled") val fixedTimeSlotsEnabled: Boolean? = null,
    @SerializedName("allowOvernightBookings") val allowOvernightBookings: Boolean? = null,
    /** [REFUND_STANDARD] or [REFUND_NONE]. */
    @SerializedName("refundPolicy") val refundPolicy: String? = null,
    @SerializedName("welcomeBonusAmount") val welcomeBonusAmount: Double? = null,
    /** True when the backend requires a separately verified payment-gateway order. */
    @SerializedName("paymentRequired") val paymentRequired: Boolean? = null,
    /** True when booking deducts Gridee Coins from this lot's scoped wallet. */
    @SerializedName("walletPaymentRequired") val walletPaymentRequired: Boolean? = null,
    /** When false the lot allows entry without a prior booking at all. */
    @SerializedName("bookingRequired") val bookingRequired: Boolean? = null,
    @SerializedName("requiresVehicleRegistration") val requiresVehicleRegistration: Boolean? = null,
    @SerializedName("requiresUserVerification") val requiresUserVerification: Boolean? = null,
    @SerializedName("requiresResidentApproval") val requiresResidentApproval: Boolean? = null,
    @SerializedName("allowWalkIn") val allowWalkIn: Boolean? = null,
    @SerializedName("allowAdvanceBooking") val allowAdvanceBooking: Boolean? = null,
    @SerializedName("supportsANPR") val supportsANPR: Boolean? = null,
    @SerializedName("supportsQRCode") val supportsQRCode: Boolean? = null,
    @SerializedName("supportsOperatorValidation") val supportsOperatorValidation: Boolean? = null,
    @SerializedName("penaltyEnabled") val penaltyEnabled: Boolean? = null,
    /** Backend payment model; resolved together with the separate gateway and wallet flags. */
    @SerializedName("paymentModel") val paymentModel: String? = null,
    @SerializedName("pricingType") val pricingType: String? = null,
    @SerializedName("validationMode") val validationMode: String? = null,
    @SerializedName("accessTypes") val accessTypes: List<String>? = null
) {
    companion object {
        const val MODE_DAILY = "DAILY"
        const val MODE_FLEXIBLE = "FLEXIBLE"

        const val REFUND_STANDARD = "STANDARD"
        const val REFUND_NONE = "NO_REFUND"

        // Mirrors the backend `PaymentModel` enum. USER_PAID defaults to gateway payment, while
        // CLIENT_PAID defaults to a debit from the user's lot-scoped Gridee Coin wallet.
        const val PAYMENT_USER_PAID = "USER_PAID"
        const val PAYMENT_CLIENT_PAID = "CLIENT_PAID"
        const val PAYMENT_SOCIETY_PAID = "SOCIETY_PAID"
        const val PAYMENT_FREE_FOR_USERS = "FREE_FOR_USERS"
        const val PAYMENT_POSTPAID_CLIENT_BILLING = "POSTPAID_CLIENT_BILLING"

        /** True when the backend defaults this model to direct gateway payment. */
        fun requiresGatewayPayment(paymentModel: String?): Boolean =
            paymentModel?.trim()?.uppercase() == PAYMENT_USER_PAID

        /** True when the backend defaults this model to a Gridee Coin wallet debit. */
        fun requiresWalletPayment(paymentModel: String?): Boolean =
            paymentModel?.trim()?.uppercase() == PAYMENT_CLIENT_PAID

        const val ACCESS_FREE_BOOKING = "FREE_BOOKING"
        const val ACCESS_PAID_BOOKING = "PAID_BOOKING"
        const val ACCESS_RESIDENT_ACCESS = "RESIDENT_ACCESS"
        const val ACCESS_VISITOR_ACCESS = "VISITOR_ACCESS"
        const val ACCESS_STAFF_ACCESS = "STAFF_ACCESS"
        const val ACCESS_WALK_IN_ACCESS = "WALK_IN_ACCESS"
        const val ACCESS_SUBSCRIPTION_ACCESS = "SUBSCRIPTION_ACCESS"
    }
}
