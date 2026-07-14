package com.gridee.parking.data.repository

import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.data.model.ParkingSpot
import com.google.gson.JsonElement
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class ParkingRepository {
    
    private val apiService = ApiClient.apiService
    
    suspend fun getParkingLots(): Response<List<ParkingLot>> {
        return apiService.getParkingLots()
    }

    suspend fun getParkingLotsPayload(): Response<JsonElement> {
        return apiService.getParkingLotsPayload()
    }

    suspend fun getParkingLotNames(): Response<List<String>> {
        return apiService.getParkingLotNames()
    }

    suspend fun getParkingLotByName(name: String): Response<ParkingLot> {
        return apiService.getParkingLotByName(name)
    }
    
    suspend fun getParkingSpots(): Response<List<ParkingSpot>> {
        return apiService.getParkingSpots()
    }

    suspend fun getParkingSpotsPayload(): Response<JsonElement> {
        return apiService.getParkingSpotsPayload()
    }

    suspend fun getOperatorParkingSpots(): Response<List<ParkingSpot>> {
        return apiService.getOperatorParkingSpots()
    }

    suspend fun getOperatorParkingSpotsPayload(): Response<JsonElement> {
        return apiService.getOperatorParkingSpotsPayload()
    }
    
    suspend fun getParkingSpotsByLot(lotId: String): Response<List<ParkingSpot>> {
        val primary = runCatching { apiService.getParkingSpotsForLot(lotId) }.getOrNull()
        if (primary?.isSuccessful == true) return primary

        val fallback = runCatching { apiService.getParkingSpotsByLot(lotId) }.getOrNull()
        if (fallback != null) return fallback

        val errorBody = primary?.errorBody() ?: "Unable to load parking spots".toResponseBody(null)
        return Response.error(primary?.code() ?: 500, errorBody)
    }

    suspend fun getParkingSpotsByLotPayload(lotId: String): Response<JsonElement> {
        val primary = runCatching { apiService.getParkingSpotsForLotPayload(lotId) }.getOrNull()
        if (primary?.isSuccessful == true) return primary

        val fallback = runCatching { apiService.getParkingSpotsByLotPayload(lotId) }.getOrNull()
        if (fallback != null) return fallback

        val errorBody = primary?.errorBody() ?: "Unable to load parking spots".toResponseBody(null)
        return Response.error(primary?.code() ?: 500, errorBody)
    }

    suspend fun getParkingSpotById(id: String): Response<ParkingSpot> {
        return apiService.getParkingSpotById(id)
    }

    suspend fun getAvailableSpots(lotId: String, startTime: String, endTime: String): Response<List<ParkingSpot>> {
        val primary = runCatching {
            apiService.getAvailableSpotsForLot(lotId, startTime, endTime)
        }.getOrNull()
        val response = if (primary?.isSuccessful == true) {
            primary
        } else {
            runCatching { apiService.getAvailableSpots(lotId, startTime, endTime) }.getOrNull()
                ?: primary
                ?: return Response.error(500, "Unable to load availability".toResponseBody(null))
        }
        if (!response.isSuccessful) {
            val errorBody = response.errorBody() ?: "Unable to load availability".toResponseBody(null)
            return Response.error(response.code(), errorBody)
        }
        val availability = response.body().orEmpty()
        val spots = availability.map { info ->
            info.spot.copy(available = info.availableCapacity)
        }
        return Response.success(spots)
    }
}
