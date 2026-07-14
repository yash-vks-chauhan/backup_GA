package com.gridee.parking.ui.profile

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.User
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.UserProfileCache
import com.gridee.parking.utils.VehicleNumberValidator
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val userRepository = UserRepository()

    private val _userProfile = MutableLiveData<User?>()
    val userProfile: LiveData<User?> = _userProfile

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _logoutSuccess = MutableLiveData<Boolean>()
    val logoutSuccess: LiveData<Boolean> = _logoutSuccess

    fun loadUserProfile(context: Context, userId: String) {
        viewModelScope.launch {
            val cachedUser = UserProfileCache.get(context, userId)
            if (cachedUser != null) {
                _userProfile.value = cachedUser
            }

            _isLoading.value = true
            try {
                val user = userRepository.getUserById(userId)
                if (user != null) {
                    _userProfile.value = user
                    AuthSession.updateCachedUserProfile(context, user)
                } else if (cachedUser == null) {
                    _errorMessage.value = "Failed to load profile"
                }
            } catch (e: Exception) {
                if (cachedUser == null) {
                    _errorMessage.value = "Failed to load profile: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addVehicle(vehicleNumber: String) {
        val currentUser = _userProfile.value
        if (currentUser == null) {
            _errorMessage.value = "User profile not loaded. Please refresh and try again."
            return
        }

        val normalizedVehicleNumber = VehicleNumberValidator.normalize(vehicleNumber)
        val validationError = VehicleNumberValidator.getError(normalizedVehicleNumber)
        if (validationError != null) {
            _errorMessage.value = validationError
            return
        }

        if (VehicleNumberValidator.containsEquivalent(currentUser.vehicleNumbers, normalizedVehicleNumber)) {
            _errorMessage.value = "Vehicle number already exists"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedVehicles = currentUser.vehicleNumbers.toMutableList()
                updatedVehicles.add(normalizedVehicleNumber)
                
                val updatedUser = currentUser.copy(vehicleNumbers = updatedVehicles)
                val result = userRepository.updateUser(updatedUser)
                
                if (result) {
                    _userProfile.value = updatedUser
                } else {
                    _errorMessage.value = "Failed to update on server. Please check your connection."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun editVehicle(oldVehicleNumber: String, newVehicleNumber: String) {
        val currentUser = _userProfile.value
        if (currentUser == null) {
            _errorMessage.value = "User profile not loaded. Please refresh and try again."
            return
        }

        val normalizedOldVehicleNumber = VehicleNumberValidator.normalize(oldVehicleNumber)
        val normalizedNewVehicleNumber = VehicleNumberValidator.normalize(newVehicleNumber)
        val validationError = VehicleNumberValidator.getError(normalizedNewVehicleNumber)
        if (validationError != null) {
            _errorMessage.value = validationError
            return
        }

        if (
            normalizedNewVehicleNumber != normalizedOldVehicleNumber &&
            VehicleNumberValidator.containsEquivalent(currentUser.vehicleNumbers, normalizedNewVehicleNumber)
        ) {
            _errorMessage.value = "Vehicle number already exists"
            return
        }

        if (oldVehicleNumber == normalizedNewVehicleNumber) {
            _errorMessage.value = "No changes made"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedVehicles = currentUser.vehicleNumbers.toMutableList()
                val index = updatedVehicles.indexOfFirst {
                    it == oldVehicleNumber || VehicleNumberValidator.areEquivalent(it, oldVehicleNumber)
                }
                if (index != -1) {
                    updatedVehicles[index] = normalizedNewVehicleNumber

                    val updatedDefaultVehicle = currentUser.defaultVehicle
                        ?.takeIf { VehicleNumberValidator.areEquivalent(it, oldVehicleNumber) }
                        ?.let { normalizedNewVehicleNumber }
                        ?: currentUser.defaultVehicle

                    val updatedUser = currentUser.copy(
                        vehicleNumbers = updatedVehicles,
                        defaultVehicle = updatedDefaultVehicle
                    )
                    val result = userRepository.updateUser(updatedUser)
                    
                    if (result) {
                        _userProfile.value = updatedUser
                    } else {
                        _errorMessage.value = "Failed to update on server. Please check your connection."
                    }
                } else {
                    _errorMessage.value = "Vehicle not found in list"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeVehicle(vehicleNumber: String) {
        val currentUser = _userProfile.value
        if (currentUser == null) {
            _errorMessage.value = "User profile not loaded"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedVehicles = currentUser.vehicleNumbers.toMutableList()
                updatedVehicles.removeAll { VehicleNumberValidator.areEquivalent(it, vehicleNumber) }

                val updatedDefaultVehicle = currentUser.defaultVehicle
                    ?.takeUnless { VehicleNumberValidator.areEquivalent(it, vehicleNumber) }

                val updatedUser = currentUser.copy(
                    vehicleNumbers = updatedVehicles,
                    defaultVehicle = updatedDefaultVehicle
                )
                val result = userRepository.updateUser(updatedUser)
                
                if (result) {
                    _userProfile.value = updatedUser
                } else {
                    _errorMessage.value = "Failed to remove vehicle"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error removing vehicle: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                // Perform any cleanup operations if needed
                // For now, just signal successful logout
                _logoutSuccess.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Error during logout: ${e.message}"
            }
        }
    }

    fun setDefaultVehicle(vehicleNumber: String) {
        val currentUser = _userProfile.value
        if (currentUser == null) {
            _errorMessage.value = "User profile not loaded"
            return
        }

        val resolvedVehicleNumber = currentUser.vehicleNumbers.firstOrNull {
            VehicleNumberValidator.areEquivalent(it, vehicleNumber)
        }

        if (resolvedVehicleNumber == null) {
            _errorMessage.value = "Vehicle not found in your list"
            return
        }

        val currentDefaultVehicle = currentUser.defaultVehicle
        if (
            currentDefaultVehicle != null &&
            VehicleNumberValidator.areEquivalent(currentDefaultVehicle, resolvedVehicleNumber)
        ) {
            _errorMessage.value = "$resolvedVehicleNumber is already your default vehicle"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedUser = currentUser.copy(defaultVehicle = resolvedVehicleNumber)
                val result = userRepository.updateUser(updatedUser)
                
                if (result) {
                    _userProfile.value = updatedUser
                    // Setting default vehicle successfully
                } else {
                    _errorMessage.value = "Failed to set default vehicle"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error setting default vehicle: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
