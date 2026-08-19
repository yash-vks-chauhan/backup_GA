package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

/**
 * Per-lot booking rules from `GET /api/parking-lots/{lotId}/booking-policy`, and inline on
 * [ParkingLot.bookingPolicy].
 *
 * **Every field is nullable on purpose.** The backend models these as boxed types, where `null`
 * means "this lot has no override — use the global [AppRemoteConfig] value". Do not give these
 * non-null defaults: doing so would silently apply one lot's rules to every lot, which is exactly
 * the bug this type exists to fix. Read them through a resolver that falls back to global config.
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
    /** When false the lot is free to book — the wallet/checkout step must be skipped entirely. */
    @SerializedName("paymentRequired") val paymentRequired: Boolean? = null,
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
    /** Who pays. One of the `PAYMENT_*` constants; only [PAYMENT_USER_PAID] charges the user. */
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

        // Mirrors the backend `PaymentModel` enum. Note that only USER_PAID charges the end
        // user's wallet — under the other four the user must not be asked to pay.
        const val PAYMENT_USER_PAID = "USER_PAID"
        const val PAYMENT_CLIENT_PAID = "CLIENT_PAID"
        const val PAYMENT_SOCIETY_PAID = "SOCIETY_PAID"
        const val PAYMENT_FREE_FOR_USERS = "FREE_FOR_USERS"
        const val PAYMENT_POSTPAID_CLIENT_BILLING = "POSTPAID_CLIENT_BILLING"

        /** True when the end user's wallet is the payer. */
        fun chargesEndUser(paymentModel: String?): Boolean =
            paymentModel?.trim()?.uppercase() == PAYMENT_USER_PAID

        const val ACCESS_FREE_BOOKING = "FREE_BOOKING"
        const val ACCESS_PAID_BOOKING = "PAID_BOOKING"
        const val ACCESS_RESIDENT_ACCESS = "RESIDENT_ACCESS"
        const val ACCESS_VISITOR_ACCESS = "VISITOR_ACCESS"
        const val ACCESS_STAFF_ACCESS = "STAFF_ACCESS"
        const val ACCESS_WALK_IN_ACCESS = "WALK_IN_ACCESS"
        const val ACCESS_SUBSCRIPTION_ACCESS = "SUBSCRIPTION_ACCESS"
    }
}
