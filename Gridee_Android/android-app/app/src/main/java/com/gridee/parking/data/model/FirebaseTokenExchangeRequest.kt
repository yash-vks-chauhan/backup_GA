package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

data class FirebaseTokenExchangeRequest(
    @SerializedName("idToken")
    val idToken: String
)
