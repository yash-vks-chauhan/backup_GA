package com.gridee.parking.ui.profile

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.User
import com.gridee.parking.data.model.UpdateUserRequest
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.UserProfileCache
import kotlinx.coroutines.launch

class EditProfileViewModel : ViewModel() {

    private val userRepository = UserRepository()

    private val _userProfile = MutableLiveData<User?>()
    val userProfile: LiveData<User?> = _userProfile

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _updateSuccess = MutableLiveData<Boolean>()
    val updateSuccess: LiveData<Boolean> = _updateSuccess

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

    fun updateProfile(context: Context, name: String, email: String, phone: String) {
        val currentUser = _userProfile.value
        if (currentUser == null) {
            _errorMessage.value = "User profile not loaded"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val trimmedName = name.trim()
                val trimmedEmail = email.trim()
                val trimmedPhone = phone.trim()
                val sanitizedPhone = trimmedPhone.filter { it.isDigit() }

                val request = UpdateUserRequest(
                    name = trimmedName.takeIf { it != currentUser.name },
                    email = trimmedEmail.takeIf { it != currentUser.email },
                    phone = sanitizedPhone.takeIf { it.isNotEmpty() && it != currentUser.phone }
                )

                if (request.name == null && request.email == null && request.phone == null) {
                    _errorMessage.value = "No changes made"
                    return@launch
                }

                val userId = currentUser.id?.trim().orEmpty()
                if (userId.isEmpty()) {
                    _errorMessage.value = "User session expired"
                    return@launch
                }

                val result = userRepository.updateUser(userId, request)
                
                if (result) {
                    val updatedUser = currentUser.copy(
                        name = trimmedName,
                        email = trimmedEmail,
                        phone = sanitizedPhone
                    )
                    _userProfile.value = updatedUser
                    AuthSession.updateCachedUserProfile(context, updatedUser)
                    _updateSuccess.value = true
                } else {
                    _errorMessage.value = "Failed to update profile"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error updating profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
