package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

data class DeviceTokenRegisterRequest(
    @SerializedName("token") val token: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("deviceId") val deviceId: String?,
    @SerializedName("appVersion") val appVersion: String?
)

data class DeviceTokenUnregisterRequest(
    @SerializedName("token") val token: String
)
