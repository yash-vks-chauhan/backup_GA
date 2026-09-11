package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

/** Organization returned by the multi-tenant parking discovery API. */
data class ParkingOrganization(
    @SerializedName(value = "id", alternate = ["organizationId"])
    val id: String = "",
    @SerializedName(value = "name", alternate = ["organizationName"])
    val name: String = "",
    @SerializedName(value = "type", alternate = ["organizationType"])
    val type: String? = null,
    val description: String? = null,
    val active: Boolean = true,
)

