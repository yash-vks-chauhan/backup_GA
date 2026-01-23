package com.gridee.parking.data.model

data class UIBooking(
    val id: String,
    val locationName: String,
    val spotName: String = "",
    val vehicleNumber: String,
    val amount: String,
    val status: BookingStatus,
    val spotId: String = "",
    val locationAddress: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val duration: String = "",
    val bookingDate: String = ""
)
