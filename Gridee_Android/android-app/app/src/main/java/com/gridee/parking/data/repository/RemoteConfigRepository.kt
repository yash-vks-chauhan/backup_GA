package com.gridee.parking.data.repository

import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.AppRemoteConfig

class RemoteConfigRepository {

    private val apiService = ApiClient.apiService

    suspend fun fetchAppConfig(): AppRemoteConfig? {
        return try {
            val response = apiService.getAppConfig()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) body.data else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
