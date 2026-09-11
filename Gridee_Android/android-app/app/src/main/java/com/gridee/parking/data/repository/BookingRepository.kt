package com.gridee.parking.data.repository

import android.content.Context
import com.gridee.parking.GrideeApplication
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.api.ApiService
import com.gridee.parking.data.api.ScannerNetworkTraceTag
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.model.CheckInRequest
import com.gridee.parking.data.model.CreateBookingRequest
import com.gridee.parking.data.model.ErrorResponse
import com.gridee.parking.data.model.BookingPayloadParser
import com.gridee.parking.data.repository.cache.TtlSingleFlightCache
import com.google.gson.GsonBuilder
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class BookingRepository(
    context: Context = GrideeApplication.instance.applicationContext,
    private val apiService: ApiService = ApiClient.apiService,
) {

    private val context = context.applicationContext
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

    private suspend fun createBookingOnce(
        userId: String,
        lotId: String?,
        request: CreateBookingRequest
    ): Response<Booking> {
        val scopedLotId = normalizeId(lotId)
        return if (scopedLotId != null) {
            apiService.createBookingForLot(scopedLotId, userId, request)
        } else {
            apiService.createBooking(userId, request)
        }
    }

    suspend fun getUserBookings(forceRefresh: Boolean = false): Result<List<Booking>> =
        withContext(Dispatchers.IO) {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }
            val lotId = getParkingLotId()
            val key = bookingCacheKey(userId, lotId)
            activeBookingsCache.getOrLoad(
                key = key,
                ttlMillis = ACTIVE_BOOKINGS_TTL_MILLIS,
                forceRefresh = forceRefresh,
                isCacheable = { it.isSuccess },
            ) {
                loadBookings(userId, lotId, history = false)
            }
        }

    suspend fun getUserBookingHistory(forceRefresh: Boolean = false): Result<List<Booking>> =
        withContext(Dispatchers.IO) {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }
            val lotId = getParkingLotId()
            val key = bookingCacheKey(userId, lotId)
            bookingHistoryCache.getOrLoad(
                key = key,
                ttlMillis = BOOKING_HISTORY_TTL_MILLIS,
                forceRefresh = forceRefresh,
                isCacheable = { it.isSuccess },
            ) {
                loadBookings(userId, lotId, history = true)
            }
        }

    /**
     * Global history is used only when resolving a booking that is no longer in the currently
     * selected lot. It has its own cached `lot=all` key, so details screens do not bypass the
     * repository or repeatedly fetch the user history endpoint.
     */
    suspend fun getGlobalUserBookingHistory(
        forceRefresh: Boolean = false,
    ): Result<List<Booking>> = withContext(Dispatchers.IO) {
        val userId = getUserId()
        if (userId.isNullOrEmpty()) {
            return@withContext Result.failure(Exception("User not logged in"))
        }
        val key = bookingCacheKey(userId, lotId = null)
        bookingHistoryCache.getOrLoad(
            key = key,
            ttlMillis = BOOKING_HISTORY_TTL_MILLIS,
            forceRefresh = forceRefresh,
            isCacheable = { it.isSuccess },
        ) {
            loadBookings(userId, lotId = null, history = true)
        }
    }

    private suspend fun loadBookings(
        userId: String,
        lotId: String?,
        history: Boolean,
    ): Result<List<Booking>> {
        return try {
            val response = if (lotId != null) {
                val scoped = if (history) {
                    apiService.getUserBookingHistoryForLot(lotId, userId)
                } else {
                    apiService.getUserBookingsForLot(lotId, userId)
                }
                if (scoped.isSuccessful || !LegacyGetFallbackPolicy.shouldFallback(scoped.code())) {
                    scoped
                } else if (history) {
                    apiService.getUserBookingHistory(userId)
                } else {
                    apiService.getUserBookings(userId)
                }
            } else if (history) {
                apiService.getUserBookingHistory(userId)
            } else {
                apiService.getUserBookings(userId)
            }

            when {
                response.isSuccessful -> Result.success(BookingPayloadParser.parseBookings(response.body()))
                response.code() == 404 -> Result.success(emptyList())
                else -> Result.failure(
                    Exception("Failed to load ${if (history) "booking history" else "bookings"} (HTTP ${response.code()})")
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    suspend fun startBooking(
        spotId: String,
        lotId: String,
        checkInTime: Date,
        checkOutTime: Date,
        vehicleNumber: String?
    ): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            RemoteConfigManager.loadCached(context)
            if (!RemoteConfigManager.isBookingEnabled()) {
                return@withContext Result.failure(Exception("Booking is temporarily unavailable."))
            }

            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val bookingConfig = RemoteConfigManager.currentConfig.booking
            val durationMinutes = ((checkOutTime.time - checkInTime.time) / 60000L).toInt()
            if (durationMinutes < bookingConfig.minBookingDurationMinutes) {
                return@withContext Result.failure(
                    Exception("Minimum booking duration is ${bookingConfig.minBookingDurationMinutes} minutes.")
                )
            }
            if (durationMinutes > bookingConfig.maxBookingDurationHours * 60) {
                return@withContext Result.failure(
                    Exception("Maximum booking duration is ${bookingConfig.maxBookingDurationHours} hours.")
                )
            }

            val (checkInTimeStr, checkOutTimeStr) = synchronized(dateFormatter) {
                dateFormatter.format(checkInTime) to dateFormatter.format(checkOutTime)
            }


            val body = CreateBookingRequest(
                spotId = spotId,
                lotId = lotId,
                checkInTime = checkInTimeStr,
                checkOutTime = checkOutTimeStr,
                vehicleNumber = vehicleNumber
            )

            val response = createBookingOnce(
                userId = userId,
                lotId = lotId,
                request = body
            )


            if (response.isSuccessful) {
                val booking = response.body()
                if (booking != null) {
                    invalidateAfterBookingMutation(
                        userId = userId,
                        lotId = booking.lotId.ifBlank { lotId },
                        includeHistory = false,
                    )
                    Result.success(booking)
                } else {
                    Result.failure(Exception("Empty response from server"))
                }
            } else {
                val errorBody = response.errorBody()?.string()

                val errorCode = extractBackendErrorCode(errorBody)
                val friendlyMessage = when {
                    response.code() == 503 && errorCode == "FEATURE_DISABLED" ->
                        extractBackendErrorMessage(errorBody) ?: "Booking is temporarily unavailable."
                    response.code() == 503 && errorCode == "MAINTENANCE_MODE" ->
                        extractBackendErrorMessage(errorBody) ?: "Gridee is temporarily unavailable. Please try again later."
                    response.code() == 402 ->
                        extractBackendErrorMessage(errorBody) ?: "Insufficient wallet balance."
                    response.code() == 409 && !RemoteConfigManager.isFeatureEnabled("multipleBookings") ->
                        extractBackendErrorMessage(errorBody) ?: "Only one active or pending booking is allowed right now."
                    response.code() == 409 ->
                        extractBackendErrorMessage(errorBody)
                            ?: "You can have up to ${RemoteConfigManager.currentConfig.booking.maxConcurrentBookingsPerUser} active or pending bookings at a time. Complete or cancel one before booking again."
                    else -> extractBackendErrorMessage(errorBody)
                        ?: "Failed to create booking (HTTP ${response.code()})"
                }
                Result.failure(Exception(friendlyMessage))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractBackendErrorCode(rawBody: String?): String? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            val gson = GsonBuilder().setLenient().create()
            gson.fromJson(rawBody, ErrorResponse::class.java)?.errorCode
        } catch (_: Exception) {
            null
        }
    }

    suspend fun cancelBooking(bookingId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val cacheKey = bookingCacheKey(userId, getParkingLotId())
            val cachedLotId = activeBookingsCache.peek(cacheKey)
                ?.getOrNull()
                ?.firstOrNull { it.id == bookingId }
                ?.lotId
                ?.takeIf(String::isNotBlank)
            val response = apiService.cancelBooking(userId, bookingId)
            if (response.isSuccessful) {
                invalidateAfterBookingMutation(
                    userId = userId,
                    lotId = cachedLotId ?: getParkingLotId(),
                    includeHistory = true,
                )
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to cancel booking: ${response.message()}"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractBackendErrorMessage(rawBody: String?): String? {
        if (rawBody.isNullOrBlank()) return null

        return try {
            val gson = GsonBuilder().setLenient().create()
            val error = gson.fromJson(rawBody, ErrorResponse::class.java)
            val validationErrors = error?.validationErrors
            when {
                !error?.message.isNullOrBlank() -> error.message
                !error?.error.isNullOrBlank() -> error.error
                !validationErrors.isNullOrEmpty() -> validationErrors.values.joinToString("\n")
                else -> rawBody
            }
        } catch (_: Exception) {
            rawBody
        }
    }

    // ========== NEW QR METHODS ==========

    /**
     * Operator check-in (vehicle number / QR, no bookingId required)
     */
    suspend fun operatorCheckIn(
        request: CheckInRequest,
        parkingLotId: String? = request.parkingLotId,
        networkTraceId: String? = null,
    ): Response<Booking> {
        val scopedLotId = normalizeId(parkingLotId) ?: normalizeId(request.parkingLotId)
        val scopedRequest = if (scopedLotId != null && request.parkingLotId != scopedLotId) {
            request.copy(parkingLotId = scopedLotId)
        } else {
            request
        }

        val traceTag = ScannerNetworkTraceTag.create(networkTraceId)
        val response = if (scopedLotId != null) {
            apiService.operatorCheckInForLot(scopedLotId, scopedRequest, traceTag)
        } else {
            apiService.operatorCheckIn(scopedRequest, traceTag)
        }
        response.body()?.takeIf { response.isSuccessful }?.let(::invalidateAfterOperatorMutation)
        return response
    }

    /**
     * Operator check-out (vehicle number / QR, no bookingId required)
     */
    suspend fun operatorCheckOut(
        request: CheckInRequest,
        parkingLotId: String? = request.parkingLotId,
        networkTraceId: String? = null,
    ): Response<Booking> {
        val scopedLotId = normalizeId(parkingLotId) ?: normalizeId(request.parkingLotId)
        val scopedRequest = if (scopedLotId != null && request.parkingLotId != scopedLotId) {
            request.copy(parkingLotId = scopedLotId)
        } else {
            request
        }

        val traceTag = ScannerNetworkTraceTag.create(networkTraceId)
        val response = if (scopedLotId != null) {
            apiService.operatorCheckOutForLot(scopedLotId, scopedRequest, traceTag)
        } else {
            apiService.operatorCheckOut(scopedRequest, traceTag)
        }
        response.body()?.takeIf { response.isSuccessful }?.let(::invalidateAfterOperatorMutation)
        return response
    }

    private fun invalidateAfterOperatorMutation(booking: Booking) {
        invalidateAfterBookingMutation(
            userId = booking.userId,
            lotId = booking.lotId,
            includeHistory = true,
        )
    }

    private fun invalidateAfterBookingMutation(
        userId: String,
        lotId: String?,
        includeHistory: Boolean,
    ) {
        invalidateUserBookings(userId, lotId, includeHistory)
        WalletRepository.invalidateWallet(userId)
        normalizeId(lotId)?.let(ParkingRepository::invalidateLotSpots)
    }

    private fun getUserId(): String? {
        return normalizeId(AuthSession.getUserId(context))
    }

    private fun getParkingLotId(): String? {
        return normalizeId(AuthSession.getParkingLotId(context))
    }

    private fun bookingCacheKey(userId: String, lotId: String?): String {
        val role = AuthSession.getUserRole(context)?.trim()?.uppercase() ?: "USER"
        return "user=${normalizeKey(userId)}|role=$role|lot=${normalizeKey(lotId) ?: "all"}"
    }

    private fun normalizeId(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val ACTIVE_BOOKINGS_TTL_MILLIS = 20 * 1000L
        const val BOOKING_HISTORY_TTL_MILLIS = 5 * 60 * 1000L

        private val activeBookingsCache =
            TtlSingleFlightCache<String, Result<List<Booking>>>(128)
        private val bookingHistoryCache =
            TtlSingleFlightCache<String, Result<List<Booking>>>(128)

        @JvmStatic
        fun invalidateUserBookings(
            userId: String,
            lotId: String? = null,
            includeHistory: Boolean = true,
        ) {
            val userMarker = "user=${normalizeKey(userId)}|"
            val normalizedLotId = normalizeKey(lotId)
            val matches: (String) -> Boolean = { key ->
                key.startsWith(userMarker) && (
                    normalizedLotId == null ||
                        key.endsWith("|lot=$normalizedLotId") ||
                        key.endsWith("|lot=all")
                    )
            }
            activeBookingsCache.invalidateWhere(matches)
            if (includeHistory) bookingHistoryCache.invalidateWhere(matches)
        }

        @JvmStatic
        fun invalidateUserBookingHistory(userId: String, lotId: String? = null) {
            val userMarker = "user=${normalizeKey(userId)}|"
            val normalizedLotId = normalizeKey(lotId)
            bookingHistoryCache.invalidateWhere { key ->
                key.startsWith(userMarker) && (
                    normalizedLotId == null ||
                        key.endsWith("|lot=$normalizedLotId") ||
                        key.endsWith("|lot=all")
                    )
            }
        }

        @JvmStatic
        fun clearReadCache() {
            activeBookingsCache.clear()
            bookingHistoryCache.clear()
        }

        private fun normalizeKey(value: String?): String? =
            value?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
    }
}
