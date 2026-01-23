package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName
import java.util.Date

data class User(
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("name")
    val name: String = "",
    
    @SerializedName("email")
    val email: String = "",
    
    @SerializedName("phone")
    val phone: String = "",
    
    @SerializedName("vehicleNumbers")
    val vehicleNumbers: List<String> = emptyList(),
    
    @SerializedName("defaultVehicle")
    val defaultVehicle: String? = null,
    
    @SerializedName("firstUser")
    val firstUser: Boolean = true,
    
    @SerializedName("walletCoins")
    val walletCoins: Int = 0,
    
    @SerializedName("role")
    val role: String? = null,
    
    @SerializedName("parkingLotId")
    val parkingLotId: String? = null,
    
    @SerializedName("parkingLotName")
    val parkingLotName: String? = null
    
    // Removed createdAt and passwordHash to avoid serialization issues
)

data class UserRegistration(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("phone")
    val phone: String,
    
    @SerializedName("password")  // Backend expects "password", not "passwordHash"
    val passwordHash: String,
    
    @SerializedName("parkingLotName")
    val parkingLotName: String?,
    
    @SerializedName("vehicleNumbers")
    val vehicleNumbers: List<String> = emptyList()
)
