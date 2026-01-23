package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

data class CreateBookingRequest(
    @SerializedName("spotId") val spotId: String,
    @SerializedName("lotId") val lotId: String,
    @SerializedName("checkInTime") val checkInTime: String,
    @SerializedName("checkOutTime") val checkOutTime: String,
    @SerializedName("vehicleNumber") val vehicleNumber: String
)

