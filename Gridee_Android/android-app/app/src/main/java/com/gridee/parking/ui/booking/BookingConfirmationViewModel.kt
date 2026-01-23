package com.gridee.parking.ui.booking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
class BookingConfirmationViewModel : ViewModel() {

    private val _bookingDetails = MutableLiveData<BookingConfirmationDetails>()
    val bookingDetails: LiveData<BookingConfirmationDetails> = _bookingDetails

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun setBookingDetails(details: BookingConfirmationDetails) {
        _bookingDetails.value = details
        _isLoading.value = false
    }
}

data class BookingConfirmationDetails(
    val bookingId: String,
    val transactionId: String,
    val parkingSpotName: String,
    val parkingAddress: String,
    val selectedSpot: String?,
    val vehicleNumber: String?,
    val startTime: Long,
    val endTime: Long,
    val totalAmount: Double,
    val paymentMethodDisplay: String,
    val paymentStatus: String,
    val timestamp: Long
)
