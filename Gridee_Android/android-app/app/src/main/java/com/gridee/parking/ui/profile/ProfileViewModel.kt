package com.gridee.parking.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.User
import com.gridee.parking.data.repository.UserRepository
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

    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = userRepository.getUserById(userId)
                _userProfile.value = user
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load profile: ${e.message}"
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

        // Basic validation - just check if not empty and reasonable length
        if (vehicleNumber.isBlank()) {
            _errorMessage.value = "Please enter a vehicle number"
            return
        }
        
        if (vehicleNumber.length < 4 || vehicleNumber.length > 15) {
            _errorMessage.value = "Vehicle number should be between 4-15 characters"
            return
        }

        // Check if vehicle already exists
        if (currentUser.vehicleNumbers.contains(vehicleNumber)) {
            _errorMessage.value = "Vehicle number already exists"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedVehicles = currentUser.vehicleNumbers.toMutableList()
                updatedVehicles.add(vehicleNumber)
                
                val updatedUser = currentUser.copy(vehicleNumbers = updatedVehicles)
                val result = userRepository.updateUser(updatedUser)
                
                if (result) {
                    _userProfile.value = updatedUser
                    _errorMessage.value = "Vehicle added successfully"
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

        // Basic validation
        if (newVehicleNumber.isBlank()) {
            _errorMessage.value = "Please enter a vehicle number"
            return
        }
        
        if (newVehicleNumber.length < 4 || newVehicleNumber.length > 15) {
            _errorMessage.value = "Vehicle number should be between 4-15 characters"
            return
        }

        // Check if new vehicle number already exists (and it's not the same as old one)
        if (newVehicleNumber != oldVehicleNumber && currentUser.vehicleNumbers.contains(newVehicleNumber)) {
            _errorMessage.value = "Vehicle number already exists"
            return
        }

        // If same vehicle number, no need to update
        if (oldVehicleNumber == newVehicleNumber) {
            _errorMessage.value = "No changes made"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedVehicles = currentUser.vehicleNumbers.toMutableList()
                val index = updatedVehicles.indexOf(oldVehicleNumber)
                if (index != -1) {
                    updatedVehicles[index] = newVehicleNumber
                    
                    val updatedUser = currentUser.copy(vehicleNumbers = updatedVehicles)
                    val result = userRepository.updateUser(updatedUser)
                    
                    if (result) {
                        _userProfile.value = updatedUser
                        _errorMessage.value = "Vehicle updated successfully"
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
                updatedVehicles.remove(vehicleNumber)
                
                val updatedUser = currentUser.copy(vehicleNumbers = updatedVehicles)
                val result = userRepository.updateUser(updatedUser)
                
                if (result) {
                    _userProfile.value = updatedUser
                    _errorMessage.value = "Vehicle removed successfully"
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

        // Check if vehicle exists in user's vehicle list
        if (!currentUser.vehicleNumbers.contains(vehicleNumber)) {
            _errorMessage.value = "Vehicle not found in your list"
            return
        }

        // If already default, no need to update
        if (currentUser.defaultVehicle == vehicleNumber) {
            _errorMessage.value = "$vehicleNumber is already your default vehicle"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedUser = currentUser.copy(defaultVehicle = vehicleNumber)
                val result = userRepository.updateUser(updatedUser)
                
                if (result) {
                    _userProfile.value = updatedUser
                    _errorMessage.value = "Default vehicle set to $vehicleNumber"
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

    private fun isValidVehicleNumber(vehicleNumber: String): Boolean {
        // Indian vehicle number format: XX00XX0000 (e.g., MH01AB1234)
        val regex = "^[A-Z]{2}[0-9]{1,2}[A-Z]{1,2}[0-9]{4}$".toRegex()
        return regex.matches(vehicleNumber)
    }
}
