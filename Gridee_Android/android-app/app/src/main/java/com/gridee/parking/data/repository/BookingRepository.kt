package com.gridee.parking.data.repository

import android.content.Context
import com.gridee.parking.GrideeApplication
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.model.CheckInMode
import com.gridee.parking.data.model.CheckInRequest
import com.gridee.parking.data.model.CreateBookingRequest
import com.gridee.parking.data.model.ErrorResponse
import com.gridee.parking.data.model.QrValidationResult
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
    
    suspend fun getUserBookings(): Result<List<Booking>> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            println("BookingRepository: Loading bookings for userId: '$userId'")
            if (userId.isNullOrEmpty()) {
                println("BookingRepository: User not logged in")
                return@withContext Result.failure(Exception("User not logged in"))
            }
            
            val response = apiService.getUserBookings(userId)
            println("BookingRepository: Get bookings response code: ${response.code()}")
            if (response.isSuccessful) {
                val bookings = response.body() ?: emptyList()
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
            
            val response = apiService.getUserBookingHistory(userId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
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
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
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

            val response = apiService.createBooking(
                userId = userId,
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
                if (errorBody?.contains("Wallet not found") == true) {
                    println("BookingRepository: Wallet not found, attempting to create wallet...")
                    try {
                        // Try to create wallet by topping up with initial amount
                        val walletResponse = apiService.topUpWallet(userId, com.gridee.parking.data.model.TopUpRequest(100.0))
                        if (walletResponse.isSuccessful) {
                            println("BookingRepository: Wallet created successfully, retrying booking...")
                            // Retry the booking
                            val retryResponse = apiService.createBooking(userId, body)
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

                val friendlyMessage = when (response.code()) {
                    409 -> extractBackendErrorMessage(errorBody)
                        ?: "You already have an active or pending booking. Please cancel or complete it first."
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
    
    suspend fun confirmBooking(bookingId: String): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }
            val role = AuthSession.getUserRole(context)
            if (!role.equals("ADMIN", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Admin access required to update booking status"))
            }
            // Update booking status to ACTIVE to mimic confirm
            val response = apiService.updateBookingStatus(userId, bookingId, mapOf("status" to "ACTIVE"))
            if (response.isSuccessful) {
                val booking = response.body()
                if (booking != null) {
                    Result.success(booking)
                } else {
                    Result.failure(Exception("Empty response from server"))
                }
            } else {
                Result.failure(Exception("Failed to confirm booking: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
     * Validate QR code for check-in and return penalty info/message
     */
    suspend fun validateCheckInQr(
        bookingId: String,
        qrCode: String
    ): Result<QrValidationResult> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val response = apiService.getBookingById(userId, bookingId)
            return@withContext if (response.isSuccessful) {
                Result.success(QrValidationResult(true, 0.0, "Booking verified. Ready to check in."))
            } else {
                Result.failure(Exception("Validation failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Perform actual check-in
     */
    suspend fun checkIn(
        bookingId: String,
        qrCode: String
    ): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val request = CheckInRequest(mode = CheckInMode.QR_CODE, qrCode = qrCode)
            val response = apiService.checkInBooking(userId, bookingId, request)

            if (response.isSuccessful) {
                val booking = response.body()
                if (booking != null) {
                    Result.success(booking)
                } else {
                    Result.failure(Exception("Empty response"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Check-in failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validate QR code for check-out and return final charges
     */
    suspend fun validateCheckOutQr(
        bookingId: String,
        qrCode: String
    ): Result<QrValidationResult> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val response = apiService.getPenaltyInfo(userId, bookingId)
            return@withContext if (response.isSuccessful) {
                val penalty = response.body() ?: 0.0
                Result.success(QrValidationResult(true, penalty, "Estimated additional charges: ${'$'}penalty"))
            } else {
                Result.failure(Exception("Validation failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Perform actual check-out
     */
    suspend fun checkOut(
        bookingId: String,
        qrCode: String
    ): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val request = CheckInRequest(mode = CheckInMode.QR_CODE, qrCode = qrCode)
            val response = apiService.checkOutBooking(userId, bookingId, request)

            if (response.isSuccessful) {
                val booking = response.body()
                if (booking != null) {
                    Result.success(booking)
                } else {
                    Result.failure(Exception("Empty response"))
                }
            } else if (response.code() == 402) {
                // Payment required - insufficient funds
                Result.failure(Exception("Insufficient wallet balance to pay penalties"))
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Check-out failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Operator check-in (vehicle number / QR, no bookingId required)
     */
    suspend fun operatorCheckIn(request: CheckInRequest): Response<Booking> {
        return apiService.operatorCheckIn(request)
    }

    /**
     * Operator check-out (vehicle number / QR, no bookingId required)
     */
    suspend fun operatorCheckOut(request: CheckInRequest): Response<Booking> {
        return apiService.operatorCheckOut(request)
    }
    
    /**
     * Get real-time penalty for active booking
     */
    suspend fun getPenaltyInfo(bookingId: String): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val response = apiService.getPenaltyInfo(userId, bookingId)

            if (response.isSuccessful) {
                val penalty = response.body()
                if (penalty != null) {
                    Result.success(penalty)
                } else {
                    Result.failure(Exception("Empty response"))
                }
            } else {
                Result.failure(Exception("Failed to get penalty info"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refresh booking data
     */
    suspend fun refreshBooking(bookingId: String): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val response = apiService.getBookingById(userId, bookingId)

            if (response.isSuccessful) {
                val booking = response.body()
                if (booking != null) {
                    Result.success(booking)
                } else {
                    Result.failure(Exception("Empty response"))
                }
            } else {
                Result.failure(Exception("Failed to refresh booking"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch price breakup for a booking
     */
    suspend fun getPriceBreakup(bookingId: String): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val response = apiService.getBookingPriceBreakup(userId, bookingId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyMap())
            } else {
                Result.failure(Exception("Failed to fetch price breakup: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extend booking checkout time
     */
    suspend fun extendBooking(
        bookingId: String,
        newCheckOutTime: String
    ): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            val request = mapOf("newCheckOutTime" to newCheckOutTime)
            val response = apiService.extendBooking(userId, bookingId, request)

            if (response.isSuccessful) {
                val booking = response.body()
                if (booking != null) {
                    Result.success(booking)
                } else {
                    Result.failure(Exception("Empty response"))
                }
            } else if (response.code() == 402) {
                Result.failure(Exception("Insufficient wallet balance"))
            } else if (response.code() == 409) {
                Result.failure(Exception("Parking spot not available for extended time"))
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Failed to extend booking"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
    
    // Legacy methods for backward compatibility
    suspend fun getUserBookings(userId: String): List<Booking>? {
        return try {
            val response = apiService.getUserBookings(userId)
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun getUserBookingHistory(userId: String): List<Booking>? {
        return try {
            val response = apiService.getUserBookingHistory(userId)
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
