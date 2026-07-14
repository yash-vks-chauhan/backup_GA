package com.gridee.parking.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.User
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.utils.AuthErrorMapper
import com.gridee.parking.utils.VehicleNumberValidator
import kotlinx.coroutines.launch

class AddVehicleViewModel : ViewModel() {

    private val userRepository = UserRepository()

    private val _state = MutableLiveData<AddVehicleState>()
    val state: LiveData<AddVehicleState> = _state

    private val _validationErrors = MutableLiveData<Map<String, String>>()
    val validationErrors: LiveData<Map<String, String>> = _validationErrors

    fun addVehicle(userId: String?, vehicleNumber: String) {
        val normalized = VehicleNumberValidator.normalize(vehicleNumber)

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
                    _state.value = AddVehicleState.Error("Profile Load Failed", "Unable to load your profile. Please try again.", isRetryable = true)
                    return@launch
                }

                if (VehicleNumberValidator.containsEquivalent(user.vehicleNumbers, normalized)) {
                    _state.value = AddVehicleState.Error("Duplicate Vehicle", "This vehicle number is already added.")
                    return@launch
                }

                val updatedVehicles = user.vehicleNumbers.toMutableList()
                updatedVehicles.add(normalized)

                val updatedUser = user.copy(vehicleNumbers = updatedVehicles)
                val result = userRepository.updateUser(updatedUser)

                if (result) {
                    _state.value = AddVehicleState.Success(updatedUser)
                } else {
                    _state.value = AddVehicleState.Error("Save Failed", "Failed to save vehicle. Please try again.", isRetryable = true)
                }
            } catch (e: Exception) {
                val error = AuthErrorMapper.fromException(e)
                _state.value = AddVehicleState.Error(error.title, error.message, error.isRetryable)
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

        val vehicleError = VehicleNumberValidator.getError(vehicleNumber)
        if (vehicleError != null) {
            errors["vehicle"] = vehicleError
        }

        return errors
    }
}

sealed class AddVehicleState {
    object Loading : AddVehicleState()
    data class Success(val user: User) : AddVehicleState()
    data class Error(
        val title: String,
        val message: String,
        val isRetryable: Boolean = false
    ) : AddVehicleState()
}
