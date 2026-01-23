package com.gridee.parking.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.repository.ParkingRepository
import kotlinx.coroutines.launch

data class Location(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

class ParkingDiscoveryViewModel : ViewModel() {
    
    private val parkingRepository = ParkingRepository()
    
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
    private val _maxDistance = MutableLiveData<Double>()
    private val _selectedAmenities = MutableLiveData<List<String>>()
    private val _availableOnly = MutableLiveData<Boolean>()

    // When a screen explicitly loads spots for a lot, don't let background "loadParkingData()"
    // overwrite the spot list (can otherwise revert UI back to empty).
    private var lockSpotUpdates: Boolean = false
    
    init {
        loadParkingData()
    }
    
    fun searchParking(query: String) {
        _searchQuery.value = query
        _isLoading.value = true
        
        // Filter existing data based on search query
        filterParkingSpots()
    }
    
    fun loadParkingData() {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                println("DEBUG ParkingDiscoveryViewModel.loadParkingData: Starting data load")
                
                // Load parking lots
                val lotsResponse = parkingRepository.getParkingLots()
                if (!lotsResponse.isSuccessful) { 
                    println("DEBUG ParkingDiscoveryViewModel.loadParkingData: Lots API failed - ${lotsResponse.code()}")
                    _isLoading.value = false
                    return@launch 
                }
                val lots = lotsResponse.body() ?: emptyList()
                println("DEBUG ParkingDiscoveryViewModel.loadParkingData: Loaded ${lots.size} parking lots")

                // NO FILTERING - show ALL lots
                val filteredLots = lots
                
                // Aggregate spots per lot
                val allSpots = mutableListOf<ParkingSpot>()
                
                for (lot in filteredLots) {
                    println("DEBUG ParkingDiscoveryViewModel.loadParkingData: Fetching spots for lot: id=${lot.id}, name=${lot.name}")
                    val spots = fetchSpotsForLot(lot.id)
                    println("DEBUG ParkingDiscoveryViewModel.loadParkingData: Got ${spots.size} spots for lot ${lot.name}")
                    allSpots.addAll(spots)
                }
                
                println("DEBUG ParkingDiscoveryViewModel.loadParkingData: Total spots aggregated=${allSpots.size}")
                
                _parkingLots.value = filteredLots
                if (!lockSpotUpdates) {
                    _parkingSpots.value = allSpots
                    println("DEBUG ParkingDiscoveryViewModel.loadParkingData: Updated _parkingSpots LiveData with ${allSpots.size} spots")
                }
                _isLoading.value = false
            } catch (e: Exception) {
                println("DEBUG ParkingDiscoveryViewModel.loadParkingData: Exception - ${e.message}")
                e.printStackTrace()
                _isLoading.value = false
            }
        }
    }
    
    fun loadParkingSpotsForLot(lotId: String, lotName: String? = null) {
        _isLoading.value = true
        lockSpotUpdates = true
        
        viewModelScope.launch {
            try {
                val spots = fetchSpotsForLot(lotId)
                println("DEBUG ParkingDiscoveryViewModel.loadParkingSpotsForLot: Fetched spots for lotId='$lotId', lotName='$lotName', size=${spots.size}")
                _parkingSpots.value = spots
                _isLoading.value = false
            } catch (e: Exception) {
                println("DEBUG ParkingDiscoveryViewModel.loadParkingSpotsForLot: Exception - ${e.message}")
                _isLoading.value = false
            }
        }
    }
    
    fun getCurrentLocation() {
        _isLoading.value = true
        
        // TODO: Implement actual location detection
        // Mock current location (Chennai, near SRM University)
        _currentLocation.value = Location(
            latitude = 12.8231,
            longitude = 80.0414,
            address = "Kattankulathur, SRM Nagar, Chennai"
        )
        
        loadParkingData()
    }
    
    private fun filterParkingSpots() {
        val allSpots = _parkingSpots.value ?: emptyList()
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
        
        println("DEBUG ParkingDiscoveryViewModel.filterParkingSpots: Filtered from ${allSpots.size} to ${filteredSpots.size} spots")
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

    private suspend fun fetchSpotsForLot(lotId: String): List<ParkingSpot> {
        if (lotId.isBlank()) return emptyList()

        return try {
            val resp = parkingRepository.getParkingSpotsByLot(lotId)
            if (!resp.isSuccessful) return emptyList()
            resp.body() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
