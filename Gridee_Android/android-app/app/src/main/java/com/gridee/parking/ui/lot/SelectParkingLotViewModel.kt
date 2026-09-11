package com.gridee.parking.ui.lot

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.data.model.ParkingLocation
import com.gridee.parking.data.model.ParkingOrganization
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.launch

/**
 * Backs [SelectParkingLotActivity]: loads the available (active) parking lots and
 * persists the user's choice to the backend + local session.
 */
class SelectParkingLotViewModel : ViewModel() {

    private val parkingRepository = ParkingRepository()
    private val userRepository = UserRepository()

    private val _lots = MutableLiveData<List<ParkingLot>>(emptyList())
    val lots: LiveData<List<ParkingLot>> = _lots

    private val _organizations = MutableLiveData<List<ParkingOrganization>>(emptyList())
    val organizations: LiveData<List<ParkingOrganization>> = _organizations

    private val _locations = MutableLiveData<List<ParkingLocation>>(emptyList())
    val locations: LiveData<List<ParkingLocation>> = _locations

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _loadError = MutableLiveData<String?>()
    val loadError: LiveData<String?> = _loadError

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState
    private var lastManualLoadElapsedMs = Long.MIN_VALUE

    fun loadOrganizations(manualRefresh: Boolean = false) {
        if (_loading.value == true) return
        _loading.value = true
        _loadError.value = null
        viewModelScope.launch {
            try {
                val response = parkingRepository.getOrganizations(forceRefresh = manualRefresh)
                if (response.isSuccessful) {
                    val active = response.body().orEmpty().filter { it.active && it.id.isNotBlank() }
                    _organizations.value = active
                    _loadError.value = if (active.isEmpty()) "No organizations are available yet." else null
                } else {
                    _loadError.value = "Couldn't load organizations (${response.code()}). Please try again."
                }
            } catch (_: Exception) {
                _loadError.value = "Couldn't load organizations. Check your connection and try again."
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadLocations(organizationId: String, manualRefresh: Boolean = false) {
        val safeOrganizationId = organizationId.trim()
        if (safeOrganizationId.isEmpty() || _loading.value == true) return
        _loading.value = true
        _loadError.value = null
        _locations.value = emptyList()
        viewModelScope.launch {
            try {
                val response = parkingRepository.getLocations(
                    safeOrganizationId,
                    forceRefresh = manualRefresh,
                )
                if (response.isSuccessful) {
                    val active = response.body().orEmpty().mapNotNull { location ->
                        if (!location.active || location.id.isBlank()) return@mapNotNull null
                        when (location.organizationId.trim()) {
                            "" -> location.copy(organizationId = safeOrganizationId)
                            safeOrganizationId -> location
                            else -> null
                        }
                    }
                    _locations.value = active
                    _loadError.value = if (active.isEmpty()) "No locations are available for this organization." else null
                } else {
                    _loadError.value = "Couldn't load locations (${response.code()}). Please try again."
                }
            } catch (_: Exception) {
                _loadError.value = "Couldn't load locations. Check your connection and try again."
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadLots(
        organizationType: String? = null,
        organizationId: String? = null,
        locationId: String? = null,
        manualRefresh: Boolean = false,
    ) {
        if (_loading.value == true) return
        if (manualRefresh) {
            val now = SystemClock.elapsedRealtime()
            if (lastManualLoadElapsedMs != Long.MIN_VALUE &&
                now - lastManualLoadElapsedMs < MANUAL_REFRESH_COOLDOWN_MS
            ) {
                return
            }
            lastManualLoadElapsedMs = now
        }
        _loading.value = true
        _loadError.value = null
        viewModelScope.launch {
            try {
                val response = parkingRepository.getParkingLots(
                    organizationType = organizationType,
                    organizationId = organizationId,
                    locationId = locationId,
                    forceRefresh = manualRefresh,
                )
                if (response.isSuccessful) {
                    val active = response.body().orEmpty().mapNotNull { lot ->
                        if (!lot.active || lot.id.isBlank()) return@mapNotNull null
                        val returnedOrganizationId = lot.organizationId?.trim().orEmpty()
                        val returnedLocationId = lot.locationId?.trim().orEmpty()
                        if (!organizationId.isNullOrBlank() &&
                            returnedOrganizationId.isNotEmpty() &&
                            returnedOrganizationId != organizationId
                        ) return@mapNotNull null
                        if (!locationId.isNullOrBlank() &&
                            returnedLocationId.isNotEmpty() &&
                            returnedLocationId != locationId
                        ) return@mapNotNull null
                        lot.copy(
                            organizationId = returnedOrganizationId.ifEmpty { organizationId },
                            locationId = returnedLocationId.ifEmpty { locationId },
                        )
                    }
                    _lots.value = active
                    _loadError.value =
                        if (active.isEmpty()) "No parking lots are available yet." else null
                } else {
                    _loadError.value = "Couldn't load parking lots (${response.code()}). Please try again."
                }
            } catch (_: Exception) {
                _loadError.value = "Couldn't load parking lots. Check your connection and try again."
            } finally {
                _loading.value = false
            }
        }
    }

    fun assignLot(context: Context, lot: ParkingLot) {
        if (_saveState.value == SaveState.Saving) return
        val appContext = context.applicationContext
        val userId = AuthSession.getUserId(appContext)
        if (userId.isNullOrBlank()) {
            _saveState.value = SaveState.Error("Your session expired. Please log in again.")
            return
        }
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                val response = userRepository.assignParkingLot(userId, lot.id, lot.name)
                if (response.isSuccessful) {
                    // Persist locally so lot-scoped screens pick it up immediately.
                    AuthSession.saveParkingLot(
                        appContext,
                        lot.id,
                        lot.name,
                        organizationId = lot.organizationId,
                        locationId = lot.locationId
                    )
                    _saveState.value = SaveState.Success(lot)
                } else {
                    _saveState.value = SaveState.Error(
                        "Couldn't save your parking lot (${response.code()}). Please try again."
                    )
                }
            } catch (_: Exception) {
                _saveState.value = SaveState.Error(
                    "Couldn't save your parking lot. Check your connection and try again."
                )
            }
        }
    }

    /** Reset after an error so the Save button can be tried again. */
    fun clearSaveError() {
        if (_saveState.value is SaveState.Error) _saveState.value = SaveState.Idle
    }

    sealed class SaveState {
        object Idle : SaveState()
        object Saving : SaveState()
        data class Success(val lot: ParkingLot) : SaveState()
        data class Error(val message: String) : SaveState()
    }

    private companion object {
        const val MANUAL_REFRESH_COOLDOWN_MS = 15_000L
    }
}
