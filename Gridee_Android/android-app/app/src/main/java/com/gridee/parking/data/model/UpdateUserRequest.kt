package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

data class UpdateUserRequest(
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("phone")
    val phone: String? = null,

    @SerializedName("password")
    val password: String? = null,

    @SerializedName("vehicleNumbers")
    val vehicleNumbers: List<String>? = null,

    @SerializedName("parkingLotName")
    val parkingLotName: String? = null
)

