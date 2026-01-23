package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

data class ExtendBookingRequest(
    @SerializedName("newCheckOutTime")
    val newCheckOutTime: String
)

