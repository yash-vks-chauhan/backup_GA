package com.gridee.parking.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BookingPolicyResolverTest {

    @Test
    fun walletPaymentFlagIsDecodedFromBackendPolicyJson() {
        val policy = Gson().fromJson(
            """{"paymentRequired":false,"walletPaymentRequired":true}""",
            ParkingLotBookingPolicy::class.java,
        )

        assertEquals(false, policy.paymentRequired)
        assertEquals(true, policy.walletPaymentRequired)
    }

    @Test
    fun explicitFlexiblePolicyControlsEveryBookingDecision() {
        val resolved = BookingPolicyResolver.resolve(
            ParkingLotBookingPolicy(
                bookingMode = "flexible",
                advanceBookingDays = 7,
                nextDayBookingOpenTime = "06:15",
                dailyBookingEndTime = "23:45",
                fixedTimeSlotsEnabled = false,
                refundPolicy = "no_refund",
                paymentRequired = false,
                requiresVehicleRegistration = false,
                supportsOperatorValidation = false,
                allowOvernightBookings = true,
            )
        )

        assertEquals(ParkingLotBookingPolicy.MODE_FLEXIBLE, resolved.bookingMode)
        assertEquals(7, resolved.advanceBookingDays)
        assertEquals(6 * 60 + 15, resolved.nextDayBookingOpenMinutes)
        assertEquals(23 * 60 + 45, resolved.dailyBookingEndMinutes)
        assertTrue(resolved.usesDynamicTimeSelection)
        assertTrue(resolved.isNoRefund)
        assertFalse(resolved.paymentRequired)
        assertFalse(resolved.walletPaymentRequired)
        assertFalse(resolved.bookingChargeRequired)
        assertFalse(resolved.requiresVehicleRegistration)
        assertFalse(resolved.supportsOperatorValidation)
        assertTrue(resolved.allowOvernightBookings)
    }

    @Test
    fun dailyFixedPolicyKeepsTheExistingPredefinedSlotFlow() {
        val resolved = BookingPolicyResolver.resolve(
            completePolicy()
        )

        assertTrue(resolved.usesFixedDailySlots)
        assertFalse(resolved.usesDynamicTimeSelection)
    }

    @Test
    fun dateWindowAndFutureOpeningUseTheSelectedLotPolicy() {
        val resolved = BookingPolicyResolver.resolve(
            completePolicy(
                bookingMode = ParkingLotBookingPolicy.MODE_FLEXIBLE,
                advanceBookingDays = 2,
                nextDayBookingOpenTime = "18:30",
                dailyBookingEndTime = "23:00",
                fixedTimeSlotsEnabled = false,
            )
        )
        val beforeOpening = calendar(2026, Calendar.SEPTEMBER, 6, 18, 29)
        val atOpening = calendar(2026, Calendar.SEPTEMBER, 6, 18, 30)
        val tomorrow = calendar(2026, Calendar.SEPTEMBER, 7, 10, 0)
        val lastAllowed = calendar(2026, Calendar.SEPTEMBER, 8, 10, 0)
        val outsideWindow = calendar(2026, Calendar.SEPTEMBER, 9, 10, 0)

        assertFalse(resolved.isFutureDateOpen(tomorrow, beforeOpening))
        assertTrue(resolved.isFutureDateOpen(tomorrow, atOpening))
        assertTrue(resolved.isDateWithinAdvanceWindow(lastAllowed, beforeOpening))
        assertFalse(resolved.isDateWithinAdvanceWindow(outsideWindow, beforeOpening))
    }

    @Test
    fun clientPaidModelFallsBackToLotScopedWalletPayment() {
        val resolved = BookingPolicyResolver.resolve(
            completePolicy(
                paymentRequired = null,
                paymentModel = ParkingLotBookingPolicy.PAYMENT_CLIENT_PAID,
            )
        )

        assertFalse(resolved.paymentRequired)
        assertTrue(resolved.walletPaymentRequired)
        assertTrue(resolved.bookingChargeRequired)
    }

    @Test
    fun explicitWalletPaymentMakesBookingPaidWhenGatewayPaymentIsFalse() {
        val resolved = BookingPolicyResolver.resolve(
            completePolicy(
                paymentRequired = false,
                walletPaymentRequired = true,
                paymentModel = ParkingLotBookingPolicy.PAYMENT_CLIENT_PAID,
            )
        )

        assertFalse(resolved.paymentRequired)
        assertTrue(resolved.walletPaymentRequired)
        assertTrue(resolved.bookingChargeRequired)
    }

    @Test
    fun gatewayPaymentTakesPrecedenceOverConflictingWalletFlag() {
        val resolved = BookingPolicyResolver.resolve(
            completePolicy(
                paymentRequired = true,
                walletPaymentRequired = true,
                paymentModel = ParkingLotBookingPolicy.PAYMENT_USER_PAID,
            )
        )

        assertTrue(resolved.paymentRequired)
        assertFalse(resolved.walletPaymentRequired)
        assertTrue(resolved.bookingChargeRequired)
    }

    @Test
    fun parsesBackendTimesDefensively() {
        assertEquals(0, BookingPolicyResolver.parseTime("00:00"))
        assertEquals(17 * 60 + 30, BookingPolicyResolver.parseTime("17:30:00"))
        assertEquals(null, BookingPolicyResolver.parseTime("24:00"))
        assertEquals(null, BookingPolicyResolver.parseTime("bad"))
    }

    @Test
    fun incompletePolicyIsRejectedInsteadOfUsingGlobalTenantDefaults() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BookingPolicyResolver.resolve(
                ParkingLotBookingPolicy(
                    bookingMode = ParkingLotBookingPolicy.MODE_DAILY,
                    fixedTimeSlotsEnabled = true,
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("advanceBookingDays"))
    }

    private fun completePolicy(
        bookingMode: String = ParkingLotBookingPolicy.MODE_DAILY,
        advanceBookingDays: Int = 1,
        nextDayBookingOpenTime: String = "06:00",
        dailyBookingEndTime: String = "20:00",
        fixedTimeSlotsEnabled: Boolean = true,
        refundPolicy: String = ParkingLotBookingPolicy.REFUND_STANDARD,
        paymentRequired: Boolean? = true,
        walletPaymentRequired: Boolean? = null,
        paymentModel: String? = null,
        requiresVehicleRegistration: Boolean = true,
        supportsOperatorValidation: Boolean = true,
    ) = ParkingLotBookingPolicy(
        bookingMode = bookingMode,
        advanceBookingDays = advanceBookingDays,
        nextDayBookingOpenTime = nextDayBookingOpenTime,
        dailyBookingEndTime = dailyBookingEndTime,
        fixedTimeSlotsEnabled = fixedTimeSlotsEnabled,
        refundPolicy = refundPolicy,
        paymentRequired = paymentRequired,
        walletPaymentRequired = walletPaymentRequired,
        paymentModel = paymentModel,
        requiresVehicleRegistration = requiresVehicleRegistration,
        supportsOperatorValidation = supportsOperatorValidation,
    )

    private fun calendar(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
