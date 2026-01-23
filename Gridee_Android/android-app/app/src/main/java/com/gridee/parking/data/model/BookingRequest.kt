package com.gridee.parking.data.model

data class BookingRequest(
    val spotId: String,
    val lotId: String,
    val checkInTime: String,
    val checkOutTime: String,
    val vehicleNumber: String
)
