package com.gridee.parking.ui.bottomsheet

import android.os.Bundle
import com.gridee.parking.ui.adapters.Booking
import com.gridee.parking.ui.adapters.BookingStatus

/**
 * Version-tolerant, process-restorable input shared by the booking pass dialogs.
 *
 * Fragment arguments must outlive the in-memory UI model and may be unparcelled by a newer app
 * build. Keeping only platform primitive values here avoids Java-serialization coupling to the
 * mutable adapter model's generated serialVersionUID.
 */
internal data class BookingPassSnapshot(
    val id: String,
    val spotName: String,
    val locationName: String,
    val startTime: String,
    val endTime: String,
    val amount: String,
    val status: BookingStatus,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
        putString(KEY_ID, id)
        putString(KEY_SPOT_NAME, spotName)
        putString(KEY_LOCATION_NAME, locationName)
        putString(KEY_START_TIME, startTime)
        putString(KEY_END_TIME, endTime)
        putString(KEY_AMOUNT, amount)
        putString(KEY_STATUS, status.name)
    }

    companion object {
        private const val CURRENT_SCHEMA_VERSION = 1
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_ID = "id"
        private const val KEY_SPOT_NAME = "spot_name"
        private const val KEY_LOCATION_NAME = "location_name"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_END_TIME = "end_time"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_STATUS = "status"

        fun from(booking: Booking): BookingPassSnapshot = BookingPassSnapshot(
            id = booking.id,
            spotName = booking.spotName,
            locationName = booking.locationName,
            startTime = booking.startTime,
            endTime = booking.endTime,
            amount = booking.amount,
            status = booking.status,
        )

        fun from(bundle: Bundle?): BookingPassSnapshot? {
            bundle ?: return null
            val version = bundle.getInt(KEY_SCHEMA_VERSION, 0)
            if (version !in 1..CURRENT_SCHEMA_VERSION) return null
            return BookingPassSnapshot(
                id = bundle.getString(KEY_ID).orEmpty(),
                spotName = bundle.getString(KEY_SPOT_NAME).orEmpty(),
                locationName = bundle.getString(KEY_LOCATION_NAME).orEmpty(),
                startTime = bundle.getString(KEY_START_TIME).orEmpty(),
                endTime = bundle.getString(KEY_END_TIME).orEmpty(),
                amount = bundle.getString(KEY_AMOUNT).orEmpty(),
                status = runCatching {
                    BookingStatus.valueOf(bundle.getString(KEY_STATUS).orEmpty())
                }.getOrDefault(BookingStatus.PENDING),
            )
        }
    }
}

internal const val ARG_BOOKING_PASS_SNAPSHOT = "booking_pass.snapshot"
internal const val LEGACY_ARG_SERIALIZED_BOOKING = "booking"
internal const val STATE_BOOKING_PASS_SNAPSHOT = "booking_pass.saved_snapshot"

/** Reads new primitive state first, while allowing one safe migration from the deployed format. */
internal fun restoreBookingPassSnapshot(
    arguments: Bundle?,
    savedInstanceState: Bundle?,
): BookingPassSnapshot? {
    BookingPassSnapshot.from(
        runCatching { savedInstanceState?.getBundle(STATE_BOOKING_PASS_SNAPSHOT) }.getOrNull(),
    )?.let { return it }
    BookingPassSnapshot.from(
        runCatching { arguments?.getBundle(ARG_BOOKING_PASS_SNAPSHOT) }.getOrNull(),
    )?.let { return it }

    @Suppress("DEPRECATION")
    return runCatching {
        (arguments?.getSerializable(LEGACY_ARG_SERIALIZED_BOOKING) as? Booking)
            ?.let(BookingPassSnapshot::from)
    }.getOrNull()
}

/** Rewrites a successfully read legacy argument so the next FragmentManager save is primitive. */
internal fun migrateBookingPassArguments(arguments: Bundle?, snapshot: BookingPassSnapshot?) {
    if (arguments == null || snapshot == null) return
    runCatching {
        arguments.putBundle(ARG_BOOKING_PASS_SNAPSHOT, snapshot.toBundle())
        arguments.remove(LEGACY_ARG_SERIALIZED_BOOKING)
    }
}
