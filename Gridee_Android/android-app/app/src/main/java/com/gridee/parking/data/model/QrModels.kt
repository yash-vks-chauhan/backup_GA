package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

data class QrValidationResult(
    @SerializedName("valid")
    val valid: Boolean,

    @SerializedName("penalty")
    val penalty: Double,

    @SerializedName("message")
    val message: String
)

data class QrCodeRequest(
    @SerializedName("qrCode")
    val qrCode: String
)

