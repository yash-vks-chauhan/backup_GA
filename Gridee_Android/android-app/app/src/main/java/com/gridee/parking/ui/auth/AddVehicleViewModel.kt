package com.gridee.parking.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.User
import com.gridee.parking.data.repository.UserRepository
import kotlinx.coroutines.launch
import java.util.Locale

class AddVehicleViewModel : ViewModel() {

    private val userRepository = UserRepository()

    private val _state = MutableLiveData<AddVehicleState>()
    val state: LiveData<AddVehicleState> = _state

    private val _validationErrors = MutableLiveData<Map<String, String>>()
    val validationErrors: LiveData<Map<String, String>> = _validationErrors

    fun addVehicle(userId: String?, vehicleNumber: String) {
        val normalized = vehicleNumber.trim().uppercase(Locale.ROOT)

        val errors = validate(normalized, userId)
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            return
        }

        _state.value = AddVehicleState.Loading

        viewModelScope.launch {
            try {
                val user = userRepository.getUserById(userId.orEmpty())
                if (user == null) {
                    _state.value = AddVehicleState.Error("Unable to load your profile. Please try again.")
                    return@launch
                }

                if (user.vehicleNumbers.contains(normalized)) {
                    _state.value = AddVehicleState.Error("Vehicle number already exists")
                    return@launch
                }

                val updatedVehicles = user.vehicleNumbers.toMutableList()
                updatedVehicles.add(normalized)

                val updatedUser = user.copy(vehicleNumbers = updatedVehicles)
                val result = userRepository.updateUser(updatedUser)

                if (result) {
                    _state.value = AddVehicleState.Success(updatedUser)
                } else {
                    _state.value = AddVehicleState.Error("Failed to save vehicle. Please try again.")
                }
            } catch (e: Exception) {
                _state.value = AddVehicleState.Error("Network error: ${e.message}")
            }
        }
    }

    fun clearErrors() {
        _validationErrors.value = emptyMap()
    }

    private fun validate(vehicleNumber: String, userId: String?): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (userId.isNullOrBlank()) {
            errors["user"] = "Missing user session. Please sign in again."
            return errors
        }

        if (vehicleNumber.isBlank()) {
            errors["vehicle"] = "Please enter a vehicle number"
            return errors
        }

        if (vehicleNumber.length < 6 || vehicleNumber.length > 15) {
            errors["vehicle"] = "Vehicle number should be 6-15 characters"
        }

        return errors
    }
}

sealed class AddVehicleState {
    object Loading : AddVehicleState()
    data class Success(val user: User) : AddVehicleState()
    data class Error(val message: String) : AddVehicleState()
}
