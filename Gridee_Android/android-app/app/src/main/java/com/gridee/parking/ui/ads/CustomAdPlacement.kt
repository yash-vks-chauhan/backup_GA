package com.gridee.parking.ui.ads

/**
 * Placement identifiers sent to `GET /api/custom-ads/active`.
 *
 * The client never enumerates what the backend may serve — a placement is just a string, so a
 * new screen only needs a new constant here plus the banner view dropped into its layout.
 */
object CustomAdPlacement {
    const val HOME = "HOME"
    const val BOOKING_QR_PASS = "BOOKING_QR_PASS"
}
