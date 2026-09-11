package com.gridee.parking.data.repository

import android.content.Context
import com.gridee.parking.GrideeApplication
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.api.ApiService
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.data.model.ParkingLotBookingPolicy
import com.gridee.parking.data.model.ParkingLocation
import com.gridee.parking.data.model.ParkingOrganization
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.repository.cache.TtlSingleFlightCache
import com.gridee.parking.utils.AuthSession
import com.google.gson.JsonElement
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class ParkingRepository(
    context: Context? = defaultApplicationContext(),
    private val apiService: ApiService = ApiClient.apiService,
) {

    private val context = context?.applicationContext

    suspend fun getParkingLots(
        organizationType: String? = null,
        organizationId: String? = null,
        locationId: String? = null,
        forceRefresh: Boolean = false,
    ): Response<List<ParkingLot>> {
        val key = "lots:list|role=${role()}|type=${normalize(organizationType) ?: "all"}" +
            "|org=${normalize(organizationId) ?: "all"}|location=${normalize(locationId) ?: "all"}"
        return parkingLotsCache.getOrLoad(
            key = key,
            ttlMillis = PARKING_LOTS_TTL_MILLIS,
            forceRefresh = forceRefresh,
            isCacheable = Response<List<ParkingLot>>::isSuccessful,
        ) { apiService.getParkingLotsByType(organizationType, organizationId, locationId) }
    }

    suspend fun getOrganizations(forceRefresh: Boolean = false): Response<List<ParkingOrganization>> {
        val key = "organizations:list|role=${role()}"
        return organizationsCache.getOrLoad(
            key,
            PARKING_LOTS_TTL_MILLIS,
            forceRefresh,
            Response<List<ParkingOrganization>>::isSuccessful,
        ) { apiService.getOrganizations() }
    }

    suspend fun getLocations(
        organizationId: String,
        forceRefresh: Boolean = false,
    ): Response<List<ParkingLocation>> {
        val normalizedOrganizationId = normalize(organizationId).orEmpty()
        val key = "locations:list|role=${role()}|org=$normalizedOrganizationId"
        return locationsCache.getOrLoad(
            key,
            PARKING_LOTS_TTL_MILLIS,
            forceRefresh,
            Response<List<ParkingLocation>>::isSuccessful,
        ) { apiService.getLocations(normalizedOrganizationId) }
    }

    suspend fun getBookingPolicy(
        lotId: String,
        forceRefresh: Boolean = false,
    ): Response<ParkingLotBookingPolicy> {
        val normalizedLotId = normalize(lotId).orEmpty()
        val key = "policy:lot|role=${role()}|lot=$normalizedLotId"
        return bookingPolicyCache.getOrLoad(
            key,
            BOOKING_POLICY_TTL_MILLIS,
            forceRefresh,
            Response<ParkingLotBookingPolicy>::isSuccessful,
        ) { apiService.getLotBookingPolicy(normalizedLotId) }
    }

    suspend fun getParkingLotsPayload(forceRefresh: Boolean = false): Response<JsonElement> {
        val key = "lots:payload|role=${role()}"
        return jsonCache.getOrLoad(key, PARKING_LOTS_TTL_MILLIS, forceRefresh, Response<JsonElement>::isSuccessful) {
            apiService.getParkingLotsPayload()
        }
    }

    suspend fun getParkingLotNames(forceRefresh: Boolean = false): Response<List<String>> {
        val key = "lots:names|role=${role()}"
        return stringListCache.getOrLoad(key, PARKING_LOTS_TTL_MILLIS, forceRefresh, Response<List<String>>::isSuccessful) {
            apiService.getParkingLotNames()
        }
    }

    suspend fun getParkingLotByName(name: String, forceRefresh: Boolean = false): Response<ParkingLot> {
        val key = "lots:name|role=${role()}|name=${normalize(name).orEmpty()}"
        return parkingLotCache.getOrLoad(key, PARKING_LOTS_TTL_MILLIS, forceRefresh, Response<ParkingLot>::isSuccessful) {
            apiService.getParkingLotByName(name)
        }
    }

    suspend fun getParkingSpots(forceRefresh: Boolean = false): Response<List<ParkingSpot>> {
        val key = "spots:all|scope=${sessionScope("USER")}"
        return parkingSpotsCache.getOrLoad(key, PARKING_SPOTS_TTL_MILLIS, forceRefresh, Response<List<ParkingSpot>>::isSuccessful) {
            apiService.getParkingSpots()
        }
    }

    suspend fun getParkingSpotsPayload(forceRefresh: Boolean = false): Response<JsonElement> {
        val key = "spots:all-payload|scope=${sessionScope("USER")}"
        return jsonCache.getOrLoad(key, PARKING_SPOTS_TTL_MILLIS, forceRefresh, Response<JsonElement>::isSuccessful) {
            apiService.getParkingSpotsPayload()
        }
    }

    suspend fun getOperatorParkingSpots(forceRefresh: Boolean = false): Response<List<ParkingSpot>> {
        val key = "spots:operator-all|scope=${sessionScope("OPERATOR")}"
        return parkingSpotsCache.getOrLoad(key, PARKING_SPOTS_TTL_MILLIS, forceRefresh, Response<List<ParkingSpot>>::isSuccessful) {
            apiService.getOperatorParkingSpots()
        }
    }

    suspend fun getOperatorParkingSpotsPayload(forceRefresh: Boolean = false): Response<JsonElement> {
        val key = "spots:operator-all-payload|scope=${sessionScope("OPERATOR")}"
        return jsonCache.getOrLoad(key, PARKING_SPOTS_TTL_MILLIS, forceRefresh, Response<JsonElement>::isSuccessful) {
            apiService.getOperatorParkingSpotsPayload()
        }
    }

    suspend fun getOperatorParkingSpotsForLotPayload(
        lotId: String,
        forceRefresh: Boolean = false,
    ): Response<JsonElement> {
        val normalizedLotId = normalize(lotId).orEmpty()
        val key = "spots:operator-lot-payload|scope=${sessionScope("OPERATOR")}|lot=$normalizedLotId|"
        return jsonCache.getOrLoad(key, PARKING_SPOTS_TTL_MILLIS, forceRefresh, Response<JsonElement>::isSuccessful) {
            apiService.getOperatorParkingSpotsForLotPayload(lotId)
        }
    }

    suspend fun getParkingSpotsByLot(
        lotId: String,
        forceRefresh: Boolean = false,
    ): Response<List<ParkingSpot>> {
        val normalizedLotId = normalize(lotId).orEmpty()
        val key = "spots:lot|scope=${sessionScope("USER")}|lot=$normalizedLotId|"
        return parkingSpotsCache.getOrLoad(key, PARKING_SPOTS_TTL_MILLIS, forceRefresh, Response<List<ParkingSpot>>::isSuccessful) {
            val primary = apiService.getParkingSpotsForLot(lotId)
            if (primary.isSuccessful || !LegacyGetFallbackPolicy.shouldFallback(primary.code())) {
                primary
            } else {
                apiService.getParkingSpotsByLot(lotId)
            }
        }
    }

    suspend fun getParkingSpotsByLotPayload(
        lotId: String,
        forceRefresh: Boolean = false,
    ): Response<JsonElement> {
        val normalizedLotId = normalize(lotId).orEmpty()
        val key = "spots:lot-payload|scope=${sessionScope("USER")}|lot=$normalizedLotId|"
        return jsonCache.getOrLoad(key, PARKING_SPOTS_TTL_MILLIS, forceRefresh, Response<JsonElement>::isSuccessful) {
            val primary = apiService.getParkingSpotsForLotPayload(lotId)
            if (primary.isSuccessful || !LegacyGetFallbackPolicy.shouldFallback(primary.code())) {
                primary
            } else {
                apiService.getParkingSpotsByLotPayload(lotId)
            }
        }
    }

    suspend fun getParkingSpotById(id: String, forceRefresh: Boolean = false): Response<ParkingSpot> {
        val key = "spots:id|scope=${sessionScope("USER")}|spot=${normalize(id).orEmpty()}"
        return parkingSpotCache.getOrLoad(key, PARKING_SPOTS_TTL_MILLIS, forceRefresh, Response<ParkingSpot>::isSuccessful) {
            apiService.getParkingSpotById(id)
        }
    }

    suspend fun getAvailableSpots(
        lotId: String,
        startTime: String,
        endTime: String,
        forceRefresh: Boolean = false,
    ): Response<List<ParkingSpot>> {
        val normalizedLotId = normalize(lotId).orEmpty()
        val key = "spots:available|scope=${sessionScope("USER")}|lot=$normalizedLotId|start=${startTime.trim()}|end=${endTime.trim()}"
        return parkingSpotsCache.getOrLoad(key, PARKING_SPOTS_TTL_MILLIS, forceRefresh, Response<List<ParkingSpot>>::isSuccessful) {
            val primary = apiService.getAvailableSpotsForLot(lotId, startTime, endTime)
            val availabilityResponse = if (
                primary.isSuccessful || !LegacyGetFallbackPolicy.shouldFallback(primary.code())
            ) {
                primary
            } else {
                apiService.getAvailableSpots(lotId, startTime, endTime)
            }

            if (!availabilityResponse.isSuccessful) {
                val errorBody = availabilityResponse.errorBody()
                    ?: "Unable to load availability".toResponseBody(null)
                Response.error(availabilityResponse.code(), errorBody)
            } else {
                val spots = availabilityResponse.body().orEmpty().map { info ->
                    info.spot.copy(available = info.availableCapacity)
                }
                Response.success(spots)
            }
        }
    }

    private fun sessionScope(roleHint: String): String {
        val appContext = context ?: return "anonymous:$roleHint"
        val userId = normalize(AuthSession.getUserId(appContext)) ?: "anonymous"
        return "$userId:${role(roleHint)}"
    }

    private fun role(fallback: String = "PUBLIC"): String {
        return context?.let(AuthSession::getUserRole)?.trim()?.uppercase() ?: fallback
    }

    companion object {
        const val PARKING_LOTS_TTL_MILLIS = 5 * 60 * 1000L
        const val PARKING_SPOTS_TTL_MILLIS = 30 * 1000L
        const val BOOKING_POLICY_TTL_MILLIS = 5 * 60 * 1000L

        private val organizationsCache =
            TtlSingleFlightCache<String, Response<List<ParkingOrganization>>>(32)
        private val locationsCache =
            TtlSingleFlightCache<String, Response<List<ParkingLocation>>>(64)
        private val parkingLotsCache = TtlSingleFlightCache<String, Response<List<ParkingLot>>>(64)
        private val bookingPolicyCache =
            TtlSingleFlightCache<String, Response<ParkingLotBookingPolicy>>(128)
        private val parkingLotCache = TtlSingleFlightCache<String, Response<ParkingLot>>(64)
        private val stringListCache = TtlSingleFlightCache<String, Response<List<String>>>(32)
        private val parkingSpotsCache = TtlSingleFlightCache<String, Response<List<ParkingSpot>>>(256)
        private val parkingSpotCache = TtlSingleFlightCache<String, Response<ParkingSpot>>(128)
        private val jsonCache = TtlSingleFlightCache<String, Response<JsonElement>>(256)

        @JvmStatic
        fun invalidateLotSpots(lotId: String) {
            val lotMarker = "|lot=${normalize(lotId).orEmpty()}|"
            parkingSpotsCache.invalidateWhere { key ->
                key.contains(lotMarker) || key.startsWith("spots:all|") || key.startsWith("spots:operator-all|")
            }
            parkingSpotCache.clear()
            jsonCache.invalidateWhere { key ->
                (key.startsWith("spots:") && key.contains(lotMarker)) ||
                    key.startsWith("spots:all-") || key.startsWith("spots:operator-all-")
            }
        }

        @JvmStatic
        fun invalidateParkingLots(organizationType: String? = null) {
            val normalizedType = normalize(organizationType)
            if (normalizedType == null) {
                parkingLotsCache.clear()
                parkingLotCache.clear()
                stringListCache.clear()
                jsonCache.invalidateWhere { it.startsWith("lots:") }
            } else {
                val typeMarker = "|type=$normalizedType"
                parkingLotsCache.invalidateWhere { it.contains(typeMarker) }
            }
        }

        @JvmStatic
        fun clearReadCache() {
            organizationsCache.clear()
            locationsCache.clear()
            parkingLotsCache.clear()
            bookingPolicyCache.clear()
            parkingLotCache.clear()
            stringListCache.clear()
            parkingSpotsCache.clear()
            parkingSpotCache.clear()
            jsonCache.clear()
        }

        private fun defaultApplicationContext(): Context? =
            runCatching { GrideeApplication.instance.applicationContext }.getOrNull()

        private fun normalize(value: String?): String? =
            value?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
    }
}
