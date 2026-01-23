package com.gridee.parking.data.repository

import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.data.model.ParkingSpot
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class ParkingRepository {
    
    private val apiService = ApiClient.apiService
    
    suspend fun getParkingLots(): Response<List<ParkingLot>> {
        return apiService.getParkingLots()
    }

    suspend fun getParkingLotNames(): Response<List<String>> {
        return apiService.getParkingLotNames()
    }
    
    suspend fun getParkingSpots(): Response<List<ParkingSpot>> {
        return apiService.getParkingSpots()
    }
    
    suspend fun getParkingSpotsByLot(lotId: String): Response<List<ParkingSpot>> {
        return apiService.getParkingSpotsByLot(lotId)
    }

    suspend fun getParkingSpotById(id: String): Response<ParkingSpot> {
        return apiService.getParkingSpotById(id)
    }

    suspend fun getAvailableSpots(lotId: String, startTime: String, endTime: String): Response<List<ParkingSpot>> {
        val response = apiService.getAvailableSpots(lotId, startTime, endTime)
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
