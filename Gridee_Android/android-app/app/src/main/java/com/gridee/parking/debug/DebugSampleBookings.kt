package com.gridee.parking.debug

import com.gridee.parking.BuildConfig
import com.gridee.parking.data.model.Booking
import java.util.Date

/**
 * Two hard-coded bookings — one Active, one Booked — so the bookings screen has something to
 * render while its UI is being worked on, without needing an operator to check anybody in.
 *
 * Debug builds only, and inert unless [ENABLED] is on. These never reach the backend: they are
 * appended purely at render time, so the notification, ad-transition and cancel paths all keep
 * working off the real list.
 */
object DebugSampleBookings {

    /** Flip to true to put the five sample states back on the tab. */
    private const val ENABLED = false

    private const val ID_ACTIVE = "debug-sample-active"
    private const val ID_PENDING = "debug-sample-pending"
    private const val ID_OVERDUE = "debug-sample-overdue"
    private const val ID_COMPLETED = "debug-sample-completed"
    private const val ID_CANCELLED = "debug-sample-cancelled"

    val isOn: Boolean
        get() = BuildConfig.DEBUG && ENABLED

    /** True for the fakes, so callers can keep them out of anything that hits the network. */
    fun isSample(bookingId: String?): Boolean =
        bookingId in setOf(ID_ACTIVE, ID_PENDING, ID_OVERDUE, ID_COMPLETED, ID_CANCELLED)

    /**
     * Timestamps are relative to now on every call, so the active card always has a live
     * countdown running and the booked card is always a little way out.
     */
    fun bookings(): List<Booking> {
        if (!isOn) return emptyList()
        val now = System.currentTimeMillis()
        val minute = 60_000L

        val active = Booking(
            id = ID_ACTIVE,
            userId = "debug-user",
            lotId = "debug-lot",
            spotId = "A-14",
            status = "active",
            bookingType = "FLEXIBLE",
            lotName = "SRM University Parking Lot",
            amount = 60.0,
            qrCode = "GRIDEE-DEBUG-ACTIVE",
            vehicleNumber = "TN 09 AB 1234",
            qrCodeScanned = true,
            // Checked in 40 minutes ago, 1h20m still on the clock.
            checkInTime = Date(now - 40 * minute),
            actualCheckInTime = Date(now - 40 * minute),
            checkOutTime = Date(now + 80 * minute),
            createdAt = Date(now - 50 * minute),
        )

        val pending = Booking(
            id = ID_PENDING,
            userId = "debug-user",
            lotId = "debug-lot",
            spotId = "B-07",
            status = "pending",
            bookingType = "FLEXIBLE",
            lotName = "SRM University Parking Lot",
            amount = 90.0,
            qrCode = "GRIDEE-DEBUG-PENDING",
            vehicleNumber = "TN 10 XY 7788",
            // Starts in 45 minutes, runs for three hours.
            checkInTime = Date(now + 45 * minute),
            checkOutTime = Date(now + 225 * minute),
            createdAt = Date(now - 5 * minute),
        )

        val overdue = Booking(
            id = ID_OVERDUE,
            userId = "debug-user",
            lotId = "debug-lot",
            spotId = "C-03",
            status = "active",
            bookingType = "FLEXIBLE",
            lotName = "SRM University Parking Lot",
            amount = 45.0,
            qrCode = "GRIDEE-DEBUG-OVERDUE",
            vehicleNumber = "TN 22 CD 4455",
            qrCodeScanned = true,
            // Checked in 3h ago on a 2h slot: an hour past checkout and still not scanned out.
            checkInTime = Date(now - 180 * minute),
            actualCheckInTime = Date(now - 180 * minute),
            checkOutTime = Date(now - 60 * minute),
            createdAt = Date(now - 200 * minute),
        )

        val completed = Booking(
            id = ID_COMPLETED,
            userId = "debug-user",
            lotId = "debug-lot",
            spotId = "D-11",
            status = "completed",
            lotName = "SRM University Parking Lot",
            amount = 120.0,
            vehicleNumber = "TN 01 EF 9090",
            checkInTime = Date(now - 320 * minute),
            actualCheckInTime = Date(now - 318 * minute),
            checkOutTime = Date(now - 12 * minute),
            actualCheckOutTime = Date(now - 12 * minute),
            createdAt = Date(now - 400 * minute),
        )

        val cancelled = Booking(
            id = ID_CANCELLED,
            userId = "debug-user",
            lotId = "debug-lot",
            spotId = "E-02",
            status = "cancelled",
            lotName = "SRM University Parking Lot",
            amount = 75.0,
            vehicleNumber = "TN 44 GH 1212",
            checkInTime = Date(now + 120 * minute),
            checkOutTime = Date(now + 300 * minute),
            cancelledAt = Date(now - 6 * minute),
            createdAt = Date(now - 90 * minute),
        )

        return listOf(active, pending, overdue, completed, cancelled)
    }
}
