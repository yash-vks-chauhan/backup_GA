package com.gridee.parking.ui.auth

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.gridee.parking.utils.AuthErrorMapper

class ForgotPasswordViewModel : ViewModel() {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _state = MutableLiveData<ForgotPasswordState>()
    val state: LiveData<ForgotPasswordState> = _state

    private val _validationErrors = MutableLiveData<Map<String, String>>()
    val validationErrors: LiveData<Map<String, String>> = _validationErrors

    fun sendResetEmail(email: String) {
        val errors = validate(email)
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            return
        }

        _state.value = ForgotPasswordState.Loading

        firebaseAuth.sendPasswordResetEmail(email.trim().lowercase())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _state.value = ForgotPasswordState.Success
                } else {
                    val exception = task.exception
                    val error = if (exception != null) AuthErrorMapper.fromException(exception) else
                        AuthErrorMapper.UserFacingError("Reset Failed", "Failed to send reset email. Please try again.", isRetryable = true)
                    _state.value = ForgotPasswordState.Error(error.title, error.message, error.isRetryable)
                }
            }
    }

    fun clearErrors() {
        _validationErrors.value = emptyMap()
    }

    private fun validate(email: String): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (email.isBlank()) {
            errors["email"] = "Email is required"
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errors["email"] = "Invalid email format"
        }
        return errors
    }

}

sealed class ForgotPasswordState {
    object Loading : ForgotPasswordState()
    object Success : ForgotPasswordState()
    data class Error(
        val title: String,
        val message: String,
        val isRetryable: Boolean = false
    ) : ForgotPasswordState()
}
