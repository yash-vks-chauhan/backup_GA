package com.gridee.parking.debug

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.gridee.parking.ui.adapters.Booking
import com.gridee.parking.ui.adapters.BookingStatus
import com.gridee.parking.ui.bottomsheet.BookingQrPassBottomSheet
import com.gridee.parking.utils.AdConsentManager

/** Debug-only entry point for visually checking the QR pass without creating a real booking. */
class BookingQrPassPreviewActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply {
            id = android.R.id.content
            setBackgroundColor(android.graphics.Color.WHITE)
        })

        supportFragmentManager.setFragmentResultListener(
            BookingQrPassBottomSheet.RESULT_KEY_DISMISSED,
            this
        ) { _, _ -> finish() }

        if (savedInstanceState == null) {
            AdConsentManager.gatherConsent(this) { showPreview() }
        }
    }

    private fun showPreview() {
        if (isFinishing || supportFragmentManager.isStateSaved) return
        if (supportFragmentManager.findFragmentByTag(BookingQrPassBottomSheet.TAG) == null) {
            val now = System.currentTimeMillis()
            BookingQrPassBottomSheet.newInstance(
                booking = Booking(
                    id = "QA-BOOKING-2026-000184",
                    vehicleNumber = "TN 07 AB 4321",
                    spotId = "A-12",
                    spotName = "Level 1 · A-12",
                    locationName = "SRM Main Parking",
                    locationAddress = "Kattankulathur",
                    startTime = "08:51 PM",
                    endTime = "10:51 PM",
                    duration = "2h",
                    amount = "₹80.00",
                    status = BookingStatus.ACTIVE,
                    bookingDate = "Today",
                    checkInTimestamp = now - 36L * 60_000L,
                    checkOutTimestamp = now + 84L * 60_000L
                ),
                previewMode = true
            ).show(supportFragmentManager, BookingQrPassBottomSheet.TAG)
        }
    }
}
