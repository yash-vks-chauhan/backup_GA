package com.gridee.parking.data.repository

import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.DeviceTokenRegisterRequest
import com.gridee.parking.data.model.DeviceTokenUnregisterRequest

class NotificationRepository {

    private val apiService = ApiClient.apiService

    suspend fun registerToken(authHeader: String, request: DeviceTokenRegisterRequest): Boolean {
        val response = apiService.registerNotificationToken(authHeader, request)
        return response.isSuccessful
    }

    suspend fun unregisterToken(authHeader: String, request: DeviceTokenUnregisterRequest): Boolean {
        val response = apiService.unregisterNotificationToken(authHeader, request)
        return response.isSuccessful
    }
}
