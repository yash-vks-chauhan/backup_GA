package com.gridee.parking.data.model

import java.util.Calendar
import java.util.Locale

/**
 * Non-null, UI-safe form of a lot policy.
 *
 * Nullable transport fields are validated in one place so screens cannot accidentally invent
 * global rules. An incomplete effective policy is rejected and the calling screen fails closed.
 */
data class ResolvedBookingPolicy(
    val bookingMode: String,
    val advanceBookingDays: Int,
    val nextDayBookingOpenMinutes: Int?,
    val dailyBookingEndMinutes: Int,
    val fixedTimeSlotsEnabled: Boolean,
    val refundPolicy: String,
    /** A separately verified payment-gateway order is required. */
    val paymentRequired: Boolean,
    /** The booking amount is deducted from the lot-scoped Gridee Coin wallet. */
    val walletPaymentRequired: Boolean,
    val requiresVehicleRegistration: Boolean,
    val supportsOperatorValidation: Boolean,
    val allowOvernightBookings: Boolean,
) {
    val isFlexible: Boolean
        get() = bookingMode == ParkingLotBookingPolicy.MODE_FLEXIBLE

    val usesDynamicTimeSelection: Boolean
        get() = isFlexible || !fixedTimeSlotsEnabled

    val usesFixedDailySlots: Boolean
        get() = !isFlexible && fixedTimeSlotsEnabled

    val isNoRefund: Boolean
        get() = refundPolicy == ParkingLotBookingPolicy.REFUND_NONE

    /** True for both gateway-paid and Gridee Coin wallet-paid bookings. */
    val bookingChargeRequired: Boolean
        get() = paymentRequired || walletPaymentRequired

    fun isDateWithinAdvanceWindow(date: Calendar, now: Calendar = Calendar.getInstance()): Boolean {
        val requested = startOfDay(date)
        val today = startOfDay(now)
        val latest = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, advanceBookingDays) }
        return !requested.before(today) && !requested.after(latest)
    }

    /** Future dates are exposed only after this lot's configured opening time. */
    fun isFutureDateOpen(date: Calendar, now: Calendar = Calendar.getInstance()): Boolean {
        val requested = startOfDay(date)
        val today = startOfDay(now)
        if (!requested.after(today)) return true
        val openAt = nextDayBookingOpenMinutes ?: return true
        return minutesOfDay(now) >= openAt
    }

    fun endOfBookingDay(date: Calendar): Calendar = (date.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, dailyBookingEndMinutes / 60)
        set(Calendar.MINUTE, dailyBookingEndMinutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    companion object {
        fun minutesOfDay(calendar: Calendar): Int =
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        private fun startOfDay(value: Calendar): Calendar = (value.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}

object BookingPolicyResolver {
    fun resolve(
        policy: ParkingLotBookingPolicy,
        lot: ParkingLot? = null,
    ): ResolvedBookingPolicy {
        val mode = policy.bookingMode.normalizedEnum()
            ?.takeIf { it == ParkingLotBookingPolicy.MODE_DAILY || it == ParkingLotBookingPolicy.MODE_FLEXIBLE }
            ?: invalid("bookingMode")
        val advanceBookingDays = policy.advanceBookingDays
            ?.takeIf { it >= 0 }
            ?: invalid("advanceBookingDays")
        val nextDayBookingOpenMinutes = parseTime(policy.nextDayBookingOpenTime)
            ?: invalid("nextDayBookingOpenTime")
        val dailyBookingEndMinutes = parseTime(policy.dailyBookingEndTime)
            ?: invalid("dailyBookingEndTime")
        val fixedSlots = policy.fixedTimeSlotsEnabled ?: invalid("fixedTimeSlotsEnabled")
        val refundPolicy = policy.refundPolicy.normalizedEnum()
            ?.takeIf { it == ParkingLotBookingPolicy.REFUND_STANDARD || it == ParkingLotBookingPolicy.REFUND_NONE }
            ?: invalid("refundPolicy")
        val paymentModel = policy.paymentModel.normalizedEnum() ?: lot?.paymentModel.normalizedEnum()
        val paymentRequired = policy.paymentRequired
            ?: paymentModel?.let(ParkingLotBookingPolicy::requiresGatewayPayment)
            ?: invalid("paymentRequired")
        // Match ParkingLotPolicyService: gateway payment takes precedence, and CLIENT_PAID
        // policies fall back to the lot-scoped Gridee Coin wallet for older/inlined responses.
        val walletPaymentRequired = !paymentRequired && (
            policy.walletPaymentRequired
                ?: ParkingLotBookingPolicy.requiresWalletPayment(paymentModel)
            )

        return ResolvedBookingPolicy(
            bookingMode = mode,
            advanceBookingDays = advanceBookingDays,
            nextDayBookingOpenMinutes = nextDayBookingOpenMinutes,
            dailyBookingEndMinutes = dailyBookingEndMinutes,
            fixedTimeSlotsEnabled = fixedSlots,
            refundPolicy = refundPolicy,
            paymentRequired = paymentRequired,
            walletPaymentRequired = walletPaymentRequired,
            requiresVehicleRegistration = policy.requiresVehicleRegistration
                ?: invalid("requiresVehicleRegistration"),
            supportsOperatorValidation = policy.supportsOperatorValidation
                ?: invalid("supportsOperatorValidation"),
            allowOvernightBookings = policy.allowOvernightBookings ?: false,
        )
    }

    fun parseTime(value: String?): Int? {
        val parts = value?.trim()?.split(':') ?: return null
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].take(2).toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun formatTime(minutes: Int): String {
        val normalized = minutes.coerceIn(0, 23 * 60 + 59)
        val hour24 = normalized / 60
        val minute = normalized % 60
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (val value = hour24 % 12) { 0 -> 12; else -> value }
        return String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, suffix)
    }

    private fun String?.normalizedEnum(): String? =
        this?.trim()?.uppercase(Locale.ROOT)?.takeIf(String::isNotEmpty)

    private fun invalid(field: String): Nothing =
        throw IllegalArgumentException("Incomplete booking policy: $field")
}
