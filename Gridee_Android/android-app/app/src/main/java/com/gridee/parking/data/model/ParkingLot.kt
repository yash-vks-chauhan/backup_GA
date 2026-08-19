package com.gridee.parking.data.model

data class ParkingLot(
    val id: String,
    val name: String,
    val location: String?,
    val totalSpots: Int,
    val availableSpots: Int,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    // Multi-tenant / lot metadata returned by the backend. Defaulted so existing
    // positional constructions keep compiling; Gson fills them when present.
    val organizationId: String? = null,
    val organizationName: String? = null,
    val locationId: String? = null,
    val locationName: String? = null,
    // Category of the location: COLLEGE, PUBLIC_PLACE, SOCIETY, CORPORATE, EVENT, CUSTOM.
    val organizationType: String? = null,
    val active: Boolean = true,
    val lotType: String? = null,
    /**
     * Who pays for bookings at this lot — see `ParkingLotBookingPolicy.PAYMENT_*`.
     *
     * Read-only for now: the backend records this on each booking but does **not** branch on it
     * when charging, so the app must not use it to skip the payment step. See
     * [ParkingLotBookingPolicy].
     */
    val paymentModel: String? = null,
    /** Per-lot rules, sent inline by the backend. Null means the lot has no policy override. */
    val bookingPolicy: ParkingLotBookingPolicy? = null
)
