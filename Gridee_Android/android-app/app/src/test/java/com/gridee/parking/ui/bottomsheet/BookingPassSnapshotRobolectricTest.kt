package com.gridee.parking.ui.bottomsheet

import android.os.Bundle
import android.os.Parcel
import com.gridee.parking.ui.adapters.Booking
import com.gridee.parking.ui.adapters.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class BookingPassSnapshotRobolectricTest {

    @Test
    fun `qr pass arguments survive parceling without a serialized booking model`() {
        val sheet = BookingQrPassBottomSheet.newInstance(booking(), previewMode = true)
        val restoredArguments = parcelRoundTrip(requireNotNull(sheet.arguments))
        val restored = BookingPassSnapshot.from(
            restoredArguments.getBundle(ARG_BOOKING_PASS_SNAPSHOT),
        )

        assertEquals(BookingPassSnapshot.from(booking()), restored)
        assertTrue(restoredArguments.getBoolean("preview_mode"))
        assertFalse(restoredArguments.containsKey(LEGACY_ARG_SERIALIZED_BOOKING))
        assertEquals("booking-42", sheet.bookingId)
    }

    @Test
    fun `details arguments preserve every displayed field as primitives`() {
        val sheet = BookingPassDetailsDialog.newInstance(booking())
        val restoredArguments = parcelRoundTrip(requireNotNull(sheet.arguments))
        val restored = BookingPassSnapshot.from(
            restoredArguments.getBundle(ARG_BOOKING_PASS_SNAPSHOT),
        )

        assertEquals("booking-42", restored?.id)
        assertEquals("B-12", restored?.spotName)
        assertEquals("Central Mall", restored?.locationName)
        assertEquals("10:15", restored?.startTime)
        assertEquals("12:45", restored?.endTime)
        assertEquals("₹240", restored?.amount)
        assertEquals(BookingStatus.ACTIVE, restored?.status)
        assertFalse(restoredArguments.containsKey(LEGACY_ARG_SERIALIZED_BOOKING))
    }

    @Test
    fun `deployed serialized argument is migrated once to the primitive schema`() {
        val legacyArguments = parcelRoundTrip(Bundle().apply {
            putSerializable(LEGACY_ARG_SERIALIZED_BOOKING, booking())
        })

        val restored = restoreBookingPassSnapshot(legacyArguments, null)
        migrateBookingPassArguments(legacyArguments, restored)
        val roundTripped = parcelRoundTrip(legacyArguments)

        assertEquals(BookingPassSnapshot.from(booking()), restored)
        assertFalse(roundTripped.containsKey(LEGACY_ARG_SERIALIZED_BOOKING))
        assertEquals(
            restored,
            BookingPassSnapshot.from(roundTripped.getBundle(ARG_BOOKING_PASS_SNAPSHOT)),
        )
    }

    @Test
    fun `saved snapshot wins over stale launch arguments`() {
        val arguments = Bundle().apply {
            putBundle(
                ARG_BOOKING_PASS_SNAPSHOT,
                BookingPassSnapshot.from(booking().copy(status = BookingStatus.PENDING)).toBundle(),
            )
        }
        val savedState = Bundle().apply {
            putBundle(
                STATE_BOOKING_PASS_SNAPSHOT,
                BookingPassSnapshot.from(booking().copy(status = BookingStatus.ACTIVE)).toBundle(),
            )
        }

        assertEquals(
            BookingStatus.ACTIVE,
            restoreBookingPassSnapshot(arguments, parcelRoundTrip(savedState))?.status,
        )
    }

    private fun booking() = Booking(
        id = "booking-42",
        vehicleNumber = "KA01AB1234",
        spotId = "spot-12",
        spotName = "B-12",
        locationName = "Central Mall",
        locationAddress = "MG Road",
        startTime = "10:15",
        endTime = "12:45",
        duration = "2h 30m",
        amount = "₹240",
        status = BookingStatus.ACTIVE,
        bookingDate = "2026-09-05",
    )

    private fun parcelRoundTrip(source: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(source)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(javaClass.classLoader)).also {
                it.classLoader = javaClass.classLoader
            }
        } finally {
            parcel.recycle()
        }
    }
}
