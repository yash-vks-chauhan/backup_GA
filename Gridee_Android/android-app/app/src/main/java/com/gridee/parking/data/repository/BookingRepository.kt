package com.gridee.parking.data.repository

import android.content.Context
import com.gridee.parking.GrideeApplication
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.model.CheckInRequest
import com.gridee.parking.data.model.CreateBookingRequest
import com.gridee.parking.data.model.ErrorResponse
import com.gridee.parking.data.model.BookingPayloadParser
import com.google.gson.GsonBuilder
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class BookingRepository(
    private val context: Context = GrideeApplication.instance.applicationContext
) {

    private val apiService = ApiClient.apiService
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

    private suspend fun createBookingWithFallback(
        userId: String,
        lotId: String?,
        request: CreateBookingRequest
    ): Response<Booking> {
        val scopedLotId = normalizeId(lotId)
        if (scopedLotId != null) {
            val scopedResponse = runCatching {
                apiService.createBookingForLot(scopedLotId, userId, request)
            }.getOrNull()
            if (scopedResponse?.isSuccessful == true) {
                return scopedResponse
            }
            println("BookingRepository: Lot-scoped create failed, falling back to legacy create")
        }
        return apiService.createBooking(userId, request)
    }

    suspend fun getUserBookings(): Result<List<Booking>> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            println("BookingRepository: Loading bookings for userId: '$userId'")
            if (userId.isNullOrEmpty()) {
                println("BookingRepository: User not logged in")
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val parkingLotId = getParkingLotId()
            val response = if (!parkingLotId.isNullOrBlank()) {
                val scopedResponse = runCatching {
                    apiService.getUserBookingsForLot(parkingLotId, userId)
                }.getOrNull()
                when {
                    scopedResponse?.isSuccessful == true || scopedResponse?.code() == 404 -> scopedResponse
                    else -> apiService.getUserBookings(userId)
                }
            } else {
                apiService.getUserBookings(userId)
            }
            println("BookingRepository: Get bookings response code: ${response.code()}")
            if (response.isSuccessful) {
                val bookings = BookingPayloadParser.parseBookings(response.body())
                println("BookingRepository: Found ${bookings.size} bookings")
                bookings.forEach { booking ->
                    println("BookingRepository: Booking ID: ${booking.id}, Status: ${booking.status}, Spot: ${booking.spotId}")
                }
                Result.success(bookings)
            } else if (response.code() == 404) {
                println("BookingRepository: No bookings found (404), returning empty list")
                Result.success(emptyList())
            } else {
                val errorBody = response.errorBody()?.string()
                println("BookingRepository: Get bookings error: $errorBody")
                Result.failure(Exception("Failed to load bookings: ${response.message()}"))
            }
        } catch (e: Exception) {
            println("BookingRepository: Exception loading bookings: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getUserBookingHistory(): Result<List<Booking>> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val parkingLotId = getParkingLotId()
            val response = if (!parkingLotId.isNullOrBlank()) {
                val scopedResponse = runCatching {
                    apiService.getUserBookingHistoryForLot(parkingLotId, userId)
                }.getOrNull()
                when {
                    scopedResponse?.isSuccessful == true || scopedResponse?.code() == 404 -> scopedResponse
                    else -> apiService.getUserBookingHistory(userId)
                }
            } else {
                apiService.getUserBookingHistory(userId)
            }
            if (response.isSuccessful) {
                Result.success(BookingPayloadParser.parseBookings(response.body()))
            } else if (response.code() == 404) {
                println("BookingRepository: No booking history found (404), returning empty list")
                Result.success(emptyList())
            } else {
                Result.failure(Exception("Failed to load booking history: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startBooking(
        spotId: String,
        lotId: String,
        checkInTime: Date,
        checkOutTime: Date,
        vehicleNumber: String
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

            val checkInTimeStr = dateFormatter.format(checkInTime)
            val checkOutTimeStr = dateFormatter.format(checkOutTime)

            println("BookingRepository: Creating booking with userId: $userId")
            println("BookingRepository: spotId: $spotId, lotId: $lotId")
            println("BookingRepository: checkInTime: $checkInTimeStr")
            println("BookingRepository: checkOutTime: $checkOutTimeStr")
            println("BookingRepository: vehicleNumber: $vehicleNumber")

            val body = CreateBookingRequest(
                spotId = spotId,
                lotId = lotId,
                checkInTime = checkInTimeStr,
                checkOutTime = checkOutTimeStr,
                vehicleNumber = vehicleNumber
            )

            val response = createBookingWithFallback(
                userId = userId,
                lotId = lotId,
                request = body
            )

            println("BookingRepository: API response code: ${response.code()}")
            println("BookingRepository: API response message: ${response.message()}")

            if (response.isSuccessful) {
                val booking = response.body()
                if (booking != null) {
                    println("BookingRepository: Booking created successfully: ${booking.id}")
                    Result.success(booking)
                } else {
                    println("BookingRepository: Empty response from server")
                    Result.failure(Exception("Empty response from server"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("BookingRepository: API error body: $errorBody")

                // Check if it's a wallet error and try to create wallet
                if (errorBody?.contains("Wallet not found") == true && RemoteConfigManager.isWalletEnabled()) {
                    println("BookingRepository: Wallet not found, attempting to create wallet...")
                    try {
                        // Try to create wallet by topping up with initial amount
                        val walletResponse = apiService.topUpWallet(userId, com.gridee.parking.data.model.TopUpRequest(100.0))
                        if (walletResponse.isSuccessful) {
                            println("BookingRepository: Wallet created successfully, retrying booking...")
                            // Retry the booking
                            val retryResponse = createBookingWithFallback(userId, lotId, body)
                            if (retryResponse.isSuccessful) {
                                val booking = retryResponse.body()
                                if (booking != null) {
                                    println("BookingRepository: Booking created successfully after wallet creation: ${booking.id}")
                                    return@withContext Result.success(booking)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("BookingRepository: Failed to create wallet: ${e.message}")
                    }
                }

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
        } catch (e: Exception) {
            println("BookingRepository: Exception occurred: ${e.message}")
            e.printStackTrace()
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

            val response = apiService.cancelBooking(userId, bookingId)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to cancel booking: ${response.message()}"))
            }
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
        parkingLotId: String? = request.parkingLotId
    ): Response<Booking> {
        val scopedLotId = normalizeId(parkingLotId) ?: normalizeId(request.parkingLotId)
        val scopedRequest = if (scopedLotId != null && request.parkingLotId != scopedLotId) {
            request.copy(parkingLotId = scopedLotId)
        } else {
            request
        }

        var scopedResponse: Response<Booking>? = null
        if (scopedLotId != null) {
            scopedResponse = runCatching {
                apiService.operatorCheckInForLot(scopedLotId, scopedRequest)
            }.getOrNull()
            if (scopedResponse?.isSuccessful == true) return scopedResponse
        }

        val fallbackResponse = runCatching { apiService.operatorCheckIn(scopedRequest) }.getOrNull()
        return fallbackResponse ?: scopedResponse ?: apiService.operatorCheckIn(scopedRequest)
    }

    /**
     * Operator check-out (vehicle number / QR, no bookingId required)
     */
    suspend fun operatorCheckOut(
        request: CheckInRequest,
        parkingLotId: String? = request.parkingLotId
    ): Response<Booking> {
        val scopedLotId = normalizeId(parkingLotId) ?: normalizeId(request.parkingLotId)
        val scopedRequest = if (scopedLotId != null && request.parkingLotId != scopedLotId) {
            request.copy(parkingLotId = scopedLotId)
        } else {
            request
        }

        var scopedResponse: Response<Booking>? = null
        if (scopedLotId != null) {
            scopedResponse = runCatching {
                apiService.operatorCheckOutForLot(scopedLotId, scopedRequest)
            }.getOrNull()
            if (scopedResponse?.isSuccessful == true) return scopedResponse
        }

        val fallbackResponse = runCatching { apiService.operatorCheckOut(scopedRequest) }.getOrNull()
        return fallbackResponse ?: scopedResponse ?: apiService.operatorCheckOut(scopedRequest)
    }

    private fun getUserId(): String? {
        // Legacy storage
        val sharedPref = context.getSharedPreferences("gridee_prefs", Context.MODE_PRIVATE)
        val legacyId = sharedPref.getString("user_id", null)
        if (!legacyId.isNullOrBlank()) return legacyId

        // JWT-based storage fallback
        return try {
            com.gridee.parking.utils.JwtTokenManager(context).getUserId()
        } catch (_: Exception) {
            null
        }
    }

    private fun getParkingLotId(): String? {
        return normalizeId(AuthSession.getParkingLotId(context))
    }

    private fun normalizeId(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }
}
