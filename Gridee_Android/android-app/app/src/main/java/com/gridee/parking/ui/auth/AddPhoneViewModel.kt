package com.gridee.parking.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.UpdateUserRequest
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.utils.AuthErrorMapper
import kotlinx.coroutines.launch

class AddPhoneViewModel : ViewModel() {

    private val userRepository = UserRepository()

    private val _state = MutableLiveData<AddPhoneState>()
    val state: LiveData<AddPhoneState> = _state

    private val _validationErrors = MutableLiveData<Map<String, String>>()
    val validationErrors: LiveData<Map<String, String>> = _validationErrors

    fun updatePhone(userId: String?, phoneNumber: String) {
        val normalized = phoneNumber.trim()
        val sanitized = normalized.filter { it.isDigit() }

        val errors = validate(sanitized, userId)
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            return
        }

        _state.value = AddPhoneState.Loading

        viewModelScope.launch {
            try {
                val request = UpdateUserRequest(phone = sanitized)
                val result = userRepository.updateUser(userId.orEmpty(), request)
                if (result) {
                    _state.value = AddPhoneState.Success(sanitized)
                } else {
                    _state.value = AddPhoneState.Error("Save Failed", "Failed to save phone number. Please try again.", isRetryable = true)
                }
            } catch (e: Exception) {
                val error = AuthErrorMapper.fromException(e)
                _state.value = AddPhoneState.Error(error.title, error.message, error.isRetryable)
            }
        }
    }

    fun clearErrors() {
        _validationErrors.value = emptyMap()
    }

    private fun validate(phoneNumber: String, userId: String?): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (userId.isNullOrBlank()) {
            errors["user"] = "Missing user session. Please sign in again."
            return errors
        }

        if (phoneNumber.isBlank()) {
            errors["phone"] = "Please enter a phone number"
            return errors
        }

        if (phoneNumber.length < 10 || phoneNumber.length > 15) {
            errors["phone"] = "Phone number must be 10-15 digits"
        }

        return errors
    }
}

sealed class AddPhoneState {
    object Loading : AddPhoneState()
    data class Success(val phone: String) : AddPhoneState()
    data class Error(
        val title: String,
        val message: String,
        val isRetryable: Boolean = false
    ) : AddPhoneState()
}
