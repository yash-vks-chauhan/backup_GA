package com.gridee.parking.ui.lot

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.ParkingLot
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

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _loadError = MutableLiveData<String?>()
    val loadError: LiveData<String?> = _loadError

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    fun loadLots(organizationType: String? = null) {
        _loading.value = true
        _loadError.value = null
        viewModelScope.launch {
            try {
                val response = parkingRepository.getParkingLots(organizationType)
                if (response.isSuccessful) {
                    val active = (response.body() ?: emptyList()).filter { it.active }
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
}
