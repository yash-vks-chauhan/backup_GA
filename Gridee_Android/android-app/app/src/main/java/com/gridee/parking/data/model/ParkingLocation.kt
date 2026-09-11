package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

/** Physical location inside an organization. A location can contain several parking lots. */
data class ParkingLocation(
    @SerializedName(value = "id", alternate = ["locationId"])
    val id: String = "",
    @SerializedName(value = "organizationId", alternate = ["tenantId"])
    val organizationId: String = "",
    @SerializedName(value = "name", alternate = ["locationName"])
    val name: String = "",
    val address: String? = null,
    val active: Boolean = true,
)

