package com.gridee.parking.ui.bookings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.repository.BookingRepository
import kotlinx.coroutines.launch
import java.util.*

class BookingsViewModel(application: Application) : AndroidViewModel(application) {

    private val bookingRepository = BookingRepository(application)

    private val _bookings = MutableLiveData<List<Booking>>()
    val bookings: LiveData<List<Booking>> = _bookings

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // For booking creation
    private val _bookingCreated = MutableLiveData<Booking?>()
    val bookingCreated: LiveData<Booking?> = _bookingCreated

    // ========== NEW QR CODE STATE ==========
    private val _selectedBooking = MutableLiveData<Booking?>()
    val selectedBooking: LiveData<Booking?> = _selectedBooking

    private val _penalty = MutableLiveData<Double>()
    val penalty: LiveData<Double> = _penalty

    private val _qrValidation = MutableLiveData<com.gridee.parking.data.model.QrValidationResult?>()
    val qrValidation: LiveData<com.gridee.parking.data.model.QrValidationResult?> = _qrValidation

    private val _checkInSuccess = MutableLiveData<Boolean?>()
    val checkInSuccess: LiveData<Boolean?> = _checkInSuccess

    private val _checkOutSuccess = MutableLiveData<Boolean?>()
    val checkOutSuccess: LiveData<Boolean?> = _checkOutSuccess

    fun loadUserBookings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.getUserBookings().fold(
                    onSuccess = { bookingList ->
                        println("BookingsViewModel: Received ${bookingList.size} bookings from repository")
                        for (i in bookingList.indices) {
                            println("BookingsViewModel: Booking $i - ID: ${bookingList[i].id}, Status: ${bookingList[i].status}")
                        }
                        _bookings.value = bookingList
                        _errorMessage.value = null
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                        _bookings.value = emptyList()
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load bookings: ${e.message}"
                _bookings.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createBooking(
        spotId: String,
        lotId: String,
        checkInTime: Date,
        checkOutTime: Date,
        vehicleNumber: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.startBooking(
                    spotId = spotId,
                    lotId = lotId,
                    checkInTime = checkInTime,
                    checkOutTime = checkOutTime,
                    vehicleNumber = vehicleNumber
                ).fold(
                    onSuccess = { booking ->
                        _bookingCreated.value = booking
                        _errorMessage.value = null
                        // Refresh bookings list
                        loadUserBookings()
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                        _bookingCreated.value = null
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create booking: ${e.message}"
                _bookingCreated.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun confirmBooking(bookingId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.confirmBooking(bookingId).fold(
                    onSuccess = { booking ->
                        _errorMessage.value = null
                        // Refresh bookings list
                        loadUserBookings()
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to confirm booking: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.cancelBooking(bookingId).fold(
                    onSuccess = {
                        _errorMessage.value = null
                        // Refresh bookings list
                        loadUserBookings()
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to cancel booking: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearBookingCreated() {
        _bookingCreated.value = null
    }

    // Legacy method for backward compatibility
    fun loadUserBookings(userId: String) {
        loadUserBookings()
    }

    // ========== NEW QR METHODS ==========
    fun selectBooking(booking: Booking) {
        _selectedBooking.value = booking
    }

    fun refreshBooking(bookingId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.refreshBooking(bookingId).fold(
                    onSuccess = { booking ->
                        _selectedBooking.value = booking
                        _errorMessage.value = null
                        // Also refresh list
                        loadUserBookings()
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh booking: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadPenaltyInfo(bookingId: String) {
        viewModelScope.launch {
            try {
                bookingRepository.getPenaltyInfo(bookingId).fold(
                    onSuccess = { value -> _penalty.value = value },
                    onFailure = { _penalty.value = 0.0 }
                )
            } catch (e: Exception) {
                _penalty.value = 0.0
            }
        }
    }

    fun validateCheckInQr(bookingId: String, qrCode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.validateCheckInQr(bookingId, qrCode).fold(
                    onSuccess = { v ->
                        _qrValidation.value = v
                        _errorMessage.value = null
                    },
                    onFailure = { ex ->
                        _errorMessage.value = ex.message
                        _qrValidation.value = null
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "QR validation failed: ${e.message}"
                _qrValidation.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkIn(bookingId: String, qrCode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.checkIn(bookingId, qrCode).fold(
                    onSuccess = { booking ->
                        _selectedBooking.value = booking
                        _checkInSuccess.value = true
                        _errorMessage.value = null
                        loadUserBookings()
                    },
                    onFailure = { ex ->
                        _errorMessage.value = ex.message
                        _checkInSuccess.value = false
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Check-in failed: ${e.message}"
                _checkInSuccess.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun validateCheckOutQr(bookingId: String, qrCode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.validateCheckOutQr(bookingId, qrCode).fold(
                    onSuccess = { v ->
                        _qrValidation.value = v
                        _errorMessage.value = null
                    },
                    onFailure = { ex ->
                        _errorMessage.value = ex.message
                        _qrValidation.value = null
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "QR validation failed: ${e.message}"
                _qrValidation.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkOut(bookingId: String, qrCode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.checkOut(bookingId, qrCode).fold(
                    onSuccess = { booking ->
                        _selectedBooking.value = booking
                        _checkOutSuccess.value = true
                        _errorMessage.value = null
                        loadUserBookings()
                    },
                    onFailure = { ex ->
                        _errorMessage.value = ex.message
                        _checkOutSuccess.value = false
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Check-out failed: ${e.message}"
                _checkOutSuccess.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearQrValidation() { _qrValidation.value = null }
    fun clearCheckInSuccess() { _checkInSuccess.value = null }
    fun clearCheckOutSuccess() { _checkOutSuccess.value = null }

    // Extend booking to a new checkout time
    fun extendBooking(bookingId: String, newCheckOutTime: Date) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault()).format(newCheckOutTime)
                bookingRepository.extendBooking(bookingId, iso).fold(
                    onSuccess = { booking ->
                        _selectedBooking.value = booking
                        _errorMessage.value = null
                        loadUserBookings()
                    },
                    onFailure = { ex ->
                        _errorMessage.value = ex.message
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to extend booking: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
