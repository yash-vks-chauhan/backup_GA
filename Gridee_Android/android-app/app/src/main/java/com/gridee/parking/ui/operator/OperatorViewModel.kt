package com.gridee.parking.ui.operator

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.model.CheckInMode
import com.gridee.parking.data.model.CheckInRequest
import com.gridee.parking.data.model.ErrorResponse
import com.gridee.parking.data.repository.BookingRepository
import com.google.gson.GsonBuilder
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ViewModel for operator dashboard
 * Handles vehicle check-in/check-out operations for parking lot operators
 */
class OperatorViewModel(
    private val bookingRepository: BookingRepository = BookingRepository()
) : ViewModel() {

    private val _checkInState = MutableLiveData<CheckInState>()
    val checkInState: LiveData<CheckInState> = _checkInState

    private val _checkOutState = MutableLiveData<CheckInState>()
    val checkOutState: LiveData<CheckInState> = _checkOutState
    
    /**
     * Check-in using vehicle number (OPERATOR mode)
     * No bookingId required - backend finds the booking by vehicle number
     */
    fun checkInByVehicleNumber(vehicleNumber: String, parkingSpotId: String? = null) {
        val normalizedVehicle = normalizeVehicleNumber(vehicleNumber)
        val normalizedSpotId = normalizeSpotId(parkingSpotId)
        if (normalizedVehicle.isBlank()) {
            _checkInState.value = CheckInState.Error("Vehicle number cannot be empty")
            return
        }

        _checkInState.value = CheckInState.Loading

        viewModelScope.launch {
            try {
                val request = CheckInRequest(
                    mode = CheckInMode.VEHICLE_NUMBER,
                    vehicleNumber = normalizedVehicle,
                    parkingSpotId = normalizedSpotId
                )

                val response = bookingRepository.operatorCheckIn(request)

                if (response.isSuccessful && response.body() != null) {
                    _checkInState.value = CheckInState.Success(response.body()!!)
                } else {
                    val errorMsg = extractBackendErrorMessage(response)
                        ?: when (response.code()) {
                            404 -> "No booking found for vehicle: $normalizedVehicle"
                            403 -> "Not authorized to perform check-in"
                            400 -> "Invalid request. Check vehicle number format"
                            else -> "Check-in failed: ${response.message()}"
                        }
                    _checkInState.value = CheckInState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _checkInState.value = CheckInState.Error("Network error: ${e.message}")
            }
        }
    }

    /**
     * Check-out using vehicle number (OPERATOR mode)
     * No bookingId required - backend finds the active booking by vehicle number
     */
    fun checkOutByVehicleNumber(vehicleNumber: String, parkingSpotId: String? = null) {
        val normalizedVehicle = normalizeVehicleNumber(vehicleNumber)
        val normalizedSpotId = normalizeSpotId(parkingSpotId)
        if (normalizedVehicle.isBlank()) {
            _checkOutState.value = CheckInState.Error("Vehicle number cannot be empty")
            return
        }

        _checkOutState.value = CheckInState.Loading

        viewModelScope.launch {
            try {
                val request = CheckInRequest(
                    mode = CheckInMode.VEHICLE_NUMBER,
                    vehicleNumber = normalizedVehicle,
                    parkingSpotId = normalizedSpotId
                )

                val response = bookingRepository.operatorCheckOut(request)

                if (response.isSuccessful && response.body() != null) {
                    _checkOutState.value = CheckInState.Success(response.body()!!)
                } else {
                    val errorMsg = extractBackendErrorMessage(response)
                        ?: when (response.code()) {
                            404 -> "No active booking found for vehicle: $normalizedVehicle"
                            403 -> "Not authorized to perform check-out"
                            400 -> "Invalid request. Check vehicle number format"
                            else -> "Check-out failed: ${response.message()}"
                        }
                    _checkOutState.value = CheckInState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _checkOutState.value = CheckInState.Error("Network error: ${e.message}")
            }
        }
    }

    /**
     * Check-in using QR code (alternative method for operators)
     */
    fun checkInByQrCode(qrCode: String, parkingSpotId: String? = null) {
        if (qrCode.isBlank()) {
            _checkInState.value = CheckInState.Error("QR code cannot be empty")
            return
        }

        val normalizedSpotId = normalizeSpotId(parkingSpotId)
        _checkInState.value = CheckInState.Loading

        viewModelScope.launch {
            try {
                val request = CheckInRequest(
                    mode = CheckInMode.QR_CODE,
                    qrCode = qrCode,
                    parkingSpotId = normalizedSpotId
                )

                val response = bookingRepository.operatorCheckIn(request)

                if (response.isSuccessful && response.body() != null) {
                    _checkInState.value = CheckInState.Success(response.body()!!)
                } else {
                    val errorMsg = extractBackendErrorMessage(response)
                        ?: "Check-in failed: ${response.message()}"
                    _checkInState.value = CheckInState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _checkInState.value = CheckInState.Error("Network error: ${e.message}")
            }
        }
    }

    /**
     * Reset check-in state (e.g., after showing success message)
     */
    fun resetCheckInState() {
        _checkInState.value = CheckInState.Idle
    }

    /**
     * Reset check-out state
     */
    fun resetCheckOutState() {
        _checkOutState.value = CheckInState.Idle
    }
    
    private fun normalizeVehicleNumber(raw: String): String {
        return raw.trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9]"), "")
    }

    private fun normalizeSpotId(raw: String?): String? {
        val cleaned = raw?.trim()
        return if (cleaned.isNullOrBlank()) null else cleaned
    }

    private fun <T> extractBackendErrorMessage(response: retrofit2.Response<T>): String? {
        val rawBody = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
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
    
}

/**
 * States for check-in/check-out operations
 */
sealed class CheckInState {
    object Idle : CheckInState()
    object Loading : CheckInState()
    data class Success(val booking: Booking) : CheckInState()
    data class Error(val message: String) : CheckInState()
}
