package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for JWT-based authentication
 * Used by POST /api/auth/login endpoint
 */
data class AuthResponse(
    @SerializedName("token")
    val token: String,
    
    @SerializedName("tokenType")
    val tokenType: String? = "Bearer",
    
    @SerializedName("user")
    val user: UserResponseDto,
    
    @SerializedName("message")
    val message: String? = null
) {
    // Convenience properties for backward compatibility
    val id: String get() = user.id
    val name: String get() = user.name
    val role: String get() = user.role
    val email: String get() = user.email
    val phone: String get() = user.phone
}

/**
 * User data returned in authentication response
 */
data class UserResponseDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("phone")
    val phone: String,
    
    @SerializedName("vehicleNumbers")
    val vehicleNumbers: List<String>? = emptyList(),
    
    @SerializedName("firstUser")
    val firstUser: Boolean? = false,
    
    @SerializedName("walletCoins")
    val walletCoins: Int? = 0,
    
    @SerializedName("createdAt")
    val createdAt: String? = null,
    
    @SerializedName("updatedAt")
    val updatedAt: String? = null,
    
    @SerializedName("role")
    val role: String,
    
    @SerializedName("parkingLotId")
    val parkingLotId: String? = null,
    
    @SerializedName("parkingLotName")
    val parkingLotName: String? = null,
    
    @SerializedName("active")
    val active: Boolean? = true
)

/**
 * Request model for JWT-based authentication
 */
data class AuthRequest(
    @SerializedName("email")
    val email: String,
    
    @SerializedName("password")
    val password: String
)

