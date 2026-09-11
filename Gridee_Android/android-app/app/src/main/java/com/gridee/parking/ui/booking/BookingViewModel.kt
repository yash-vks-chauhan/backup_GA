package com.gridee.parking.ui.booking

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.model.BookingPolicyResolver
import com.gridee.parking.data.model.ResolvedBookingPolicy
import com.gridee.parking.data.repository.BookingMutationRefreshCoordinator
import com.gridee.parking.data.repository.BookingRepository
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.data.repository.WalletRepository
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.model.Vehicle
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.ParkingSpotSchedulePolicy
import com.gridee.parking.utils.VehicleNumberValidator
import com.gridee.parking.ui.wallet.WalletRefreshSource
import kotlinx.coroutines.launch
import java.util.*
import java.text.SimpleDateFormat
import kotlin.math.ceil

data class BookingDetails(
    val id: String,
    val parkingSpotId: String,
    val parkingSpotName: String,
    val startTime: Date,
    val endTime: Date,
    val duration: String,
    val pricePerHour: Double,
    val totalPrice: Double,
    val selectedSpot: String?,
    val status: BookingStatus,
    val createdAt: Date,
    val paymentMethod: String?,
    val transactionId: String?
)

enum class BookingStatus {
    PENDING,
    CONFIRMED,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    EXPIRED
}

class BookingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bookingRepository = BookingRepository(application)
    private val parkingRepository = com.gridee.parking.data.repository.ParkingRepository()
    private val walletRepository = WalletRepository(application)
    
    private val _startTime = MutableLiveData<Date>()
    val startTime: LiveData<Date> = _startTime
    
    private val _endTime = MutableLiveData<Date>()
    val endTime: LiveData<Date> = _endTime
    
    private val _selectedSpot = MutableLiveData<String?>()
    val selectedSpot: LiveData<String?> = _selectedSpot
    
    private val _totalPrice = MutableLiveData<Double>()
    val totalPrice: LiveData<Double> = _totalPrice
    
    private val _duration = MutableLiveData<String>()
    val duration: LiveData<String> = _duration
    
    private val _parkingSpot = MutableLiveData<ParkingSpot>()
    val parkingSpot: LiveData<ParkingSpot> = _parkingSpot
    
    // Backend integration
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _bookingCreated = MutableLiveData<Booking?>()
    val bookingCreated: LiveData<Booking?> = _bookingCreated
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _vehicleNumber = MutableLiveData<String>()
    val vehicleNumber: LiveData<String> = _vehicleNumber
    
    private val _selectedVehicle = MutableLiveData<Vehicle?>()
    val selectedVehicle: LiveData<Vehicle?> = _selectedVehicle
    
    private val _userVehicles = MutableLiveData<List<Vehicle>>()
    val userVehicles: LiveData<List<Vehicle>> = _userVehicles
    private var userVehicleLoadGeneration = 0L
    
    private val _bookings = MutableLiveData<List<BookingDetails>>()
    val bookings: LiveData<List<BookingDetails>> = _bookings
    
    private val _activeBookings = MutableLiveData<List<BookingDetails>>()
    val activeBookings: LiveData<List<BookingDetails>> = _activeBookings
    
    private val _bookingHistory = MutableLiveData<List<BookingDetails>>()
    val bookingHistory: LiveData<List<BookingDetails>> = _bookingHistory
    
    private val _walletBalance = MutableLiveData<Double>()
    val walletBalance: LiveData<Double> = _walletBalance

    private val _bookingPolicy = MutableLiveData<ResolvedBookingPolicy?>()
    val bookingPolicy: LiveData<ResolvedBookingPolicy?> = _bookingPolicy

    private val _policyLoading = MutableLiveData(false)
    val policyLoading: LiveData<Boolean> = _policyLoading

    private val _policyError = MutableLiveData<String?>()
    val policyError: LiveData<String?> = _policyError
    private var bookingPolicyLotId: String? = null
    
    init {
        // No dummy data; bookings will be populated from backend when needed
        loadWalletBalance()
    }
    
    fun setStartTime(time: Date) {
        _startTime.value = time
    }
    
    fun setEndTime(time: Date) {
        _endTime.value = time
    }
    
    fun setSelectedSpot(spot: String?) {
        _selectedSpot.value = spot
    }
    
    fun setParkingSpot(spot: ParkingSpot) {
        _parkingSpot.value = spot
    }

    fun loadBookingPolicy(
        lotId: String,
        forceRefresh: Boolean = false,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val safeLotId = lotId.trim()
        if (safeLotId.isEmpty()) {
            _bookingPolicy.value = null
            _policyError.value = "A parking lot must be selected before booking."
            onComplete?.invoke(false)
            return
        }
        _policyLoading.value = true
        _policyError.value = null
        viewModelScope.launch {
            val loaded = try {
                val response = parkingRepository.getBookingPolicy(safeLotId, forceRefresh)
                if (response.isSuccessful && response.body() != null) {
                    _bookingPolicy.value = BookingPolicyResolver.resolve(requireNotNull(response.body()))
                    bookingPolicyLotId = safeLotId
                    true
                } else {
                    _bookingPolicy.value = null
                    bookingPolicyLotId = null
                    _policyError.value = "Couldn't load this parking lot's booking rules (${response.code()})."
                    false
                }
            } catch (_: Exception) {
                _bookingPolicy.value = null
                bookingPolicyLotId = null
                _policyError.value = "Couldn't load this parking lot's booking rules. Check your connection and try again."
                false
            }
            _policyLoading.value = false
            onComplete?.invoke(loaded)
        }
    }
    
    fun loadParkingSpotById(spotId: String, onResult: (ParkingSpot?) -> Unit) {
        viewModelScope.launch {
            try {
                val spotResponse = parkingRepository.getParkingSpotById(spotId)
                if (spotResponse.isSuccessful) onResult(spotResponse.body()) else onResult(null)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }
    
    fun loadParkingSpotsForLot(lotId: String, lotName: String? = null, onResult: (List<ParkingSpot>) -> Unit) {
        viewModelScope.launch {
            try {
                val safeLotId = lotId.trim()
                if (safeLotId.isEmpty()) {
                    onResult(emptyList())
                    return@launch
                }
                val policy = _bookingPolicy.value
                if (bookingPolicyLotId != safeLotId || policy == null) {
                    onResult(emptyList())
                    return@launch
                }

                val spotsResponse = parkingRepository.getParkingSpotsByLot(safeLotId)
                if (spotsResponse.isSuccessful) {
                    val spots = spotsResponse.body().orEmpty().mapNotNull { spot ->
                        when (spot.lotId.trim()) {
                            "" -> spot.copy(lotId = safeLotId)
                            safeLotId -> spot
                            else -> null
                        }
                    }
                    val visibleSpots = ParkingSpotSchedulePolicy.filterVisibleSpots(
                        spots,
                        policy = policy,
                    )
                    onResult(visibleSpots)
                } else {
                    onResult(emptyList())
                }
            } catch (e: Exception) {
                onResult(emptyList())
            }
        }
    }
    
    fun setVehicleNumber(vehicleNumber: String) {
        _vehicleNumber.value = vehicleNumber
    }
    
    fun setSelectedVehicle(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
        _vehicleNumber.value = vehicle.number
    }

    /**
     * Clears a selection that can no longer be verified against the refreshed profile.
     *
     * A restored booking draft must never silently fall through to a different vehicle: the
     * caller will ask the user to choose again when the saved plate no longer exists.
     */
    fun clearSelectedVehicle() {
        _selectedVehicle.value = null
        _vehicleNumber.value = ""
    }
    
    fun loadUserVehicles(autoSelectFirst: Boolean = true) {
        val loadGeneration = ++userVehicleLoadGeneration
        // Get user ID from SharedPreferences
        val sharedPref = getApplication<Application>().getSharedPreferences("gridee_prefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getString("user_id", null)
        
        if (userId != null) {
            loadUserVehiclesFromProfile(userId, autoSelectFirst, loadGeneration)
        } else {
            // No user logged in, show empty list
            if (loadGeneration == userVehicleLoadGeneration) {
                _userVehicles.value = emptyList()
            }
        }
    }
    
    private fun loadUserVehiclesFromProfile(
        userId: String,
        autoSelectFirst: Boolean,
        loadGeneration: Long,
    ) {
        viewModelScope.launch {
            try {
                val userRepository = UserRepository()
                val user = userRepository.getUserById(userId)
                if (loadGeneration != userVehicleLoadGeneration) return@launch
                
                if (user != null) {
                    
                    if (user.vehicleNumbers.isNotEmpty()) {
                        val vehicles = user.vehicleNumbers.mapIndexed { index, vehicleNumber ->
                            Vehicle(
                                id = "user_vehicle_$index",
                                number = vehicleNumber,
                                type = "Car", // Default type since we only store numbers
                                brand = "User",
                                model = "Vehicle",
                                isDefault = index == 0 // First vehicle is default
                            )
                        }
                        _userVehicles.value = vehicles
                        
                        // Fresh booking sheets retain the convenient default. Restored drafts pass
                        // false until their saved vehicle identity has been checked against this
                        // exact refreshed list, preventing an invalid saved plate from becoming the
                        // first/default plate without the user noticing.
                        if (autoSelectFirst && vehicles.isNotEmpty() && _selectedVehicle.value == null) {
                            setSelectedVehicle(vehicles.first())
                        }
                    } else {
                        // User has no vehicles, show empty list
                        _userVehicles.value = emptyList()
                    }
                } else {
                    // User not found, show empty list
                    _userVehicles.value = emptyList()
                }
            } catch (e: Exception) {
                // On error, show empty list
                if (loadGeneration == userVehicleLoadGeneration) {
                    _userVehicles.value = emptyList()
                }
            }
        }
    }
    
    // Removed mock vehicle generator
    
    // Backend integration - Create actual booking
    fun createBackendBooking() {
        if (_isLoading.value == true) {
            return
        }

        val start = _startTime.value
        val end = _endTime.value
        val spot = _parkingSpot.value
        val vehicle = _vehicleNumber.value
        val policy = _bookingPolicy.value
        
        if (policy == null) {
            _errorMessage.value = "Parking rules are still loading. Please try again."
            return
        }

        if (start == null || end == null || spot == null ||
            (policy.requiresVehicleRegistration && vehicle.isNullOrEmpty())
        ) {
            _errorMessage.value = "Please fill all required fields"
            return
        }
        if (spot.lotId.trim() != bookingPolicyLotId) {
            _errorMessage.value = "The selected spot does not belong to the policy's parking lot."
            return
        }
        if (!end.after(start)) {
            _errorMessage.value = "Checkout must be after check-in."
            return
        }
        if (policy.usesDynamicTimeSelection) {
            val now = Calendar.getInstance()
            val startCalendar = Calendar.getInstance().apply { time = start }
            val endCalendar = Calendar.getInstance().apply { time = end }
            val sameDay = startCalendar.get(Calendar.YEAR) == endCalendar.get(Calendar.YEAR) &&
                startCalendar.get(Calendar.DAY_OF_YEAR) == endCalendar.get(Calendar.DAY_OF_YEAR)
            val error = when {
                start.before(now.time) -> "Start time cannot be in the past."
                !policy.isDateWithinAdvanceWindow(startCalendar, now) ->
                    "The selected date is outside this lot's advance-booking window."
                !policy.isFutureDateOpen(startCalendar, now) ->
                    "Future-date booking is not open yet for this lot."
                !policy.isDateWithinAdvanceWindow(endCalendar, now) ->
                    "The checkout date is outside this lot's advance-booking window."
                !policy.allowOvernightBookings && !sameDay ->
                    "This parking lot does not allow overnight bookings."
                endCalendar.after(policy.endOfBookingDay(endCalendar)) ->
                    "Checkout is later than this lot's daily booking end time."
                else -> null
            }
            if (error != null) {
                _errorMessage.value = error
                return
            }
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                if (policy.usesFixedDailySlots && !ParkingSpotSchedulePolicy.canBookNow(spot, policy = policy)) {
                    _errorMessage.value = ParkingSpotSchedulePolicy.bookingRestrictionMessage(spot, policy = policy)
                        ?: "This parking spot cannot be booked right now."
                    _bookingCreated.value = null
                    return@launch
                }
                if (policy.usesFixedDailySlots && !ParkingSpotSchedulePolicy.isStartTimeAllowed(spot, start, policy)) {
                    _errorMessage.value = ParkingSpotSchedulePolicy.startTimeRestrictionMessage(spot)
                        ?: "Selected start time is not allowed for this parking spot."
                    _bookingCreated.value = null
                    return@launch
                }

                bookingRepository.startBooking(
                    spotId = spot.id,
                    lotId = spot.lotId, // Use the correct lot ID from the spot
                    checkInTime = start,
                    checkOutTime = end,
                    vehicleNumber = vehicle?.takeIf(String::isNotBlank)
                ).fold(
                    onSuccess = { booking ->
                        BookingMutationRefreshCoordinator.refreshAfterSuccess(
                            context = getApplication<Application>(),
                            parkingLotId = booking.lotId,
                            bookingId = booking.id,
                            statusHint = booking.status,
                            walletSource = WalletRefreshSource.BOOKING_CREATE,
                            cachesAlreadyInvalidated = true,
                        )
                        _bookingCreated.value = booking
                        _errorMessage.value = null
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message ?: "Failed to create booking"
                        _bookingCreated.value = null
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error creating booking: ${e.message}"
                _bookingCreated.value = null
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
    
    fun calculatePricing() {
        val start = _startTime.value
        val end = _endTime.value
        val spot = _parkingSpot.value
        
        if (start != null && end != null && spot != null) {
            val durationMillis = end.time - start.time
            val durationHours = durationMillis / (1000.0 * 60 * 60)

            // Round up to nearest hour for pricing
            val billingHours = ceil(durationHours)
            val pricePerHour = spot.bookingRate
            val price = if (pricePerHour > 0.0) billingHours * pricePerHour else 0.0

            _totalPrice.value = price
            _duration.value = formatDuration(durationMillis)
        }
    }
    
    private fun formatDuration(durationMillis: Long): String {
        val hours = (durationMillis / (1000 * 60 * 60)).toInt()
        val minutes = ((durationMillis % (1000 * 60 * 60)) / (1000 * 60)).toInt()
        
        return when {
            hours == 0 -> "${minutes}m"
            minutes == 0 -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }
    
    fun createBooking(): BookingDetails? {
        val start = _startTime.value
        val end = _endTime.value
        val spot = _parkingSpot.value
        val price = _totalPrice.value
        val durationStr = _duration.value
        val selectedSpotStr = _selectedSpot.value
        
        if (start != null && end != null && spot != null && price != null && durationStr != null) {
            return BookingDetails(
                id = generateBookingId(),
                parkingSpotId = spot.id,
                parkingSpotName = spot.name ?: spot.zoneName ?: "Unknown Spot",
                startTime = start,
                endTime = end,
                duration = durationStr,
                pricePerHour = spot.bookingRate,
                totalPrice = price,
                selectedSpot = selectedSpotStr,
                status = BookingStatus.PENDING,
                createdAt = Date(),
                paymentMethod = null,
                transactionId = null
            )
        }
        return null
    }
    
    private fun generateBookingId(): String {
        return "BK${System.currentTimeMillis()}"
    }
    
    // Removed mock bookings; rely on backend data only
    
    fun extendBooking(bookingId: String, additionalHours: Int) {
        // TODO: Implement booking extension
    }
    
    fun cancelBooking(bookingId: String) {
        // TODO: Implement booking cancellation
    }
    
    fun modifyBooking(bookingId: String, newStartTime: Date, newEndTime: Date) {
        // TODO: Implement booking modification
    }
    
    fun loadWalletBalance() {
        viewModelScope.launch {
            try {
                walletRepository.getWalletDetails().fold(
                    onSuccess = { details ->
                        val balance = details.balance ?: 0.0
                        _walletBalance.value = balance
                    },
                    onFailure = { exception ->
                        _walletBalance.value = 0.0
                    }
                )
            } catch (e: Exception) {
                _walletBalance.value = 0.0
            }
        }
    }
    
    fun addVehicleToProfile(vehicleNumber: String, onResult: (Boolean) -> Unit) {
        val sharedPref = getApplication<Application>().getSharedPreferences("gridee_prefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getString("user_id", null)
        val normalizedVehicleNumber = VehicleNumberValidator.normalize(vehicleNumber)
        
        
        if (userId == null) {
            onResult(false)
            return
        }
        
        viewModelScope.launch {
            try {
                val userRepository = UserRepository()
                val user = userRepository.getUserById(userId)
                
                if (user != null) {
                    
                    // Check if vehicle already exists
                    if (VehicleNumberValidator.containsEquivalent(user.vehicleNumbers, normalizedVehicleNumber)) {
                        onResult(false)
                        return@launch
                    }
                    
                    val updatedVehicles = user.vehicleNumbers.toMutableList()
                    updatedVehicles.add(normalizedVehicleNumber)
                    
                    val updatedUser = user.copy(vehicleNumbers = updatedVehicles)
                    val result = userRepository.updateUser(updatedUser)
                    
                    if (result) {
                        AuthSession.updateCachedUserProfile(getApplication(), updatedUser)
                    }
                    onResult(result)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    companion object
    {
    }
}
