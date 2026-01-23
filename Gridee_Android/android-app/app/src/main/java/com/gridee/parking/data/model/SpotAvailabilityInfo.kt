package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

data class SpotAvailabilityInfo(
    @SerializedName("spot")
    val spot: ParkingSpot,
    @SerializedName("availableCapacity")
    val availableCapacity: Int,
    @SerializedName(value = "available", alternate = ["isAvailable"])
    val isAvailable: Boolean,
    @SerializedName("bookedCount")
    val bookedCount: Int
)
