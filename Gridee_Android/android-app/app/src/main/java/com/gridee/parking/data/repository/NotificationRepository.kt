package com.gridee.parking.data.repository

import com.google.gson.Gson
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.DeviceTokenRegisterRequest
import com.gridee.parking.data.model.DeviceTokenUnregisterRequest
import com.gridee.parking.data.model.ErrorResponse

class NotificationRepository {

    private val apiService = ApiClient.apiService
    private val gson = Gson()

    data class TokenRegistrationResult(
        val success: Boolean,
        val featureDisabled: Boolean = false
    )

    suspend fun registerToken(authHeader: String, request: DeviceTokenRegisterRequest): TokenRegistrationResult {
        val response = apiService.registerNotificationToken(authHeader, request)
        if (response.isSuccessful) return TokenRegistrationResult(success = true)

        val errorCode = runCatching {
            gson.fromJson(response.errorBody()?.string(), ErrorResponse::class.java)?.errorCode
        }.getOrNull()
        return TokenRegistrationResult(
            success = false,
            featureDisabled = response.code() == 503 && errorCode == "FEATURE_DISABLED"
        )
    }

    suspend fun unregisterToken(authHeader: String, request: DeviceTokenUnregisterRequest): Boolean {
        val response = apiService.unregisterNotificationToken(authHeader, request)
        return response.isSuccessful
    }
}
