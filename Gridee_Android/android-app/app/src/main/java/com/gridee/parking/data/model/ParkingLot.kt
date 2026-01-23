package com.gridee.parking.data.model

data class ParkingLot(
    val id: String,
    val name: String,
    val location: String?,
    val totalSpots: Int,
    val availableSpots: Int,
    val address: String,
    val latitude: Double,
    val longitude: Double
)
