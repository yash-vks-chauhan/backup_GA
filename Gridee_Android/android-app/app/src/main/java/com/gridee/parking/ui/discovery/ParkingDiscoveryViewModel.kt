package com.gridee.parking.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.gridee.parking.GrideeApplication
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.model.BookingPolicyResolver
import com.gridee.parking.data.model.ResolvedBookingPolicy
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.utils.ParkingSpotSchedulePolicy
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class Location(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

class ParkingDiscoveryViewModel : ViewModel() {
    
    private val parkingRepository = GrideeApplication.instance.repositories.parkingRepository
    
    private val _parkingSpots = MutableLiveData<List<ParkingSpot>>()
    val parkingSpots: LiveData<List<ParkingSpot>> = _parkingSpots
    
    private val _parkingLots = MutableLiveData<List<ParkingLot>>()
    val parkingLots: LiveData<List<ParkingLot>> = _parkingLots
    
    private val _currentLocation = MutableLiveData<Location>()
    val currentLocation: LiveData<Location> = _currentLocation
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _searchQuery = MutableLiveData<String>()
    val searchQuery: LiveData<String> = _searchQuery
    
    // Filter options
    private val _maxPrice = MutableLiveData<Double>()
    private val _maxDistance = MutableLiveData<Double?>()
    private val _selectedAmenities = MutableLiveData<List<String>>()
    private val _availableOnly = MutableLiveData<Boolean>()

    // When a screen explicitly loads spots for a lot, don't let background "loadParkingData()"
    // overwrite the spot list (can otherwise revert UI back to empty).
    private var lockSpotUpdates: Boolean = false
    private var parkingLoadJob: Job? = null
    private var selectedLotPolicy: ResolvedBookingPolicy? = null
    private var selectedLotPolicyId: String? = null
    
    init {
        loadParkingData()
    }
    
    fun searchParking(query: String) {
        _searchQuery.value = query
        _isLoading.value = true
        
        // Filter existing data based on search query
        filterParkingSpots()
    }
    
    fun loadParkingData(forceRefresh: Boolean = false) {
        if (parkingLoadJob?.isActive == true) return
        _isLoading.value = true

        parkingLoadJob = viewModelScope.launch {
            try {
                val app = GrideeApplication.instance
                val selectedLotId = AuthSession.getParkingLotId(app)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                if (selectedLotId == null) {
                    _parkingLots.value = emptyList()
                    if (!lockSpotUpdates) _parkingSpots.value = emptyList()
                    return@launch
                }

                val policyResponse = parkingRepository.getBookingPolicy(selectedLotId, forceRefresh)
                val policyBody = policyResponse.body()
                if (!policyResponse.isSuccessful || policyBody == null) {
                    selectedLotPolicy = null
                    selectedLotPolicyId = null
                    _parkingLots.value = emptyList()
                    if (!lockSpotUpdates) _parkingSpots.value = emptyList()
                    return@launch
                }
                val policy = BookingPolicyResolver.resolve(policyBody)
                selectedLotPolicy = policy
                selectedLotPolicyId = selectedLotId

                val lotsResponse = parkingRepository.getParkingLots(
                    organizationId = AuthSession.getOrganizationId(app),
                    locationId = AuthSession.getLocationId(app),
                    forceRefresh = forceRefresh,
                )
                if (!lotsResponse.isSuccessful) { 
                    return@launch 
                }
                val filteredLots = lotsResponse.body().orEmpty().filter { it.id == selectedLotId }
                
                // Inventory is tenant-scoped. Fetch only the user's selected/assigned lot; the
                // former all-lots loop generated an N+1 request burst merely by opening discovery.
                val allSpots = fetchSpotsForLot(selectedLotId, forceRefresh)
                
                val visibleSpots = ParkingSpotSchedulePolicy.filterVisibleSpots(
                    allSpots,
                    policy = policy,
                )
                
                _parkingLots.value = filteredLots
                if (!lockSpotUpdates) {
                    _parkingSpots.value = visibleSpots
                }
            } catch (e: Exception) {
            } finally {
                _isLoading.value = false
                parkingLoadJob = null
            }
        }
    }
    
    fun loadParkingSpotsForLot(lotId: String, lotName: String? = null) {
        _isLoading.value = true
        lockSpotUpdates = true
        
        viewModelScope.launch {
            try {
                val policyResponse = parkingRepository.getBookingPolicy(lotId)
                val policyBody = policyResponse.body()
                if (!policyResponse.isSuccessful || policyBody == null) {
                    selectedLotPolicy = null
                    selectedLotPolicyId = null
                    _parkingSpots.value = emptyList()
                    return@launch
                }
                val policy = BookingPolicyResolver.resolve(policyBody)
                selectedLotPolicy = policy
                selectedLotPolicyId = lotId.trim()
                val spots = fetchSpotsForLot(lotId)
                val visibleSpots = ParkingSpotSchedulePolicy.filterVisibleSpots(
                    spots,
                    policy = policy,
                )
                _parkingSpots.value = visibleSpots
            } catch (e: Exception) {
                _parkingSpots.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun getCurrentLocation() {
        _parkingLots.value?.firstOrNull()?.let { selectedLot ->
            _currentLocation.value = Location(
                latitude = selectedLot.latitude,
                longitude = selectedLot.longitude,
                address = selectedLot.address,
            )
        }
    }
    
    private fun filterParkingSpots() {
        val policy = selectedLotPolicy.takeIf { selectedLotPolicyId != null }
        val allSpots = ParkingSpotSchedulePolicy.filterVisibleSpots(
            _parkingSpots.value ?: emptyList(),
            policy = policy,
        )
        val query = _searchQuery.value?.lowercase() ?: ""
        
        val filteredSpots = allSpots.filter { spot ->
            val matchesQuery = query.isEmpty() || 
                (spot.name?.lowercase()?.contains(query) == true) ||
                (spot.zoneName?.lowercase()?.contains(query) == true) ||
                (spot.spotCode?.lowercase()?.contains(query) == true) ||
                spot.status.lowercase().contains(query)
            
            // TEMPORARILY DISABLED: availability filter
            // Re-enable this after confirming spots are visible
            /*
            val matchesAvailability = _availableOnly.value?.let { availableOnly -> 
                if (availableOnly) spot.available > 0 else true 
            } ?: true
            
            matchesQuery && matchesAvailability
            */
            
            // TEMPORARY: Only filter by query, show all spots regardless of availability
            matchesQuery
        }
        
        _parkingSpots.value = filteredSpots
        _isLoading.value = false
    }
    
    fun applyFilters(
        maxDistance: Double?,
        selectedAmenities: List<String>,
        availableOnly: Boolean
    ) {
        _maxDistance.value = maxDistance
        _selectedAmenities.value = selectedAmenities
        _availableOnly.value = availableOnly
        
        filterParkingSpots()
    }

    private suspend fun fetchSpotsForLot(
        lotId: String,
        forceRefresh: Boolean = false,
    ): List<ParkingSpot> {
        if (lotId.isBlank()) return emptyList()

        return try {
            val resp = parkingRepository.getParkingSpotsByLot(lotId, forceRefresh)
            if (!resp.isSuccessful) return emptyList()
            resp.body().orEmpty().mapNotNull { spot ->
                when (spot.lotId.trim()) {
                    "" -> spot.copy(lotId = lotId)
                    lotId -> spot
                    else -> null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
