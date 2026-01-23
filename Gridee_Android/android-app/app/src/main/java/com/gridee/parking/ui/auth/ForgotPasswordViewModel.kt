package com.gridee.parking.ui.auth

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException

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
                    _state.value = ForgotPasswordState.Error(getErrorMessage(task.exception))
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

    private fun getErrorMessage(exception: Exception?): String {
        return when (exception) {
            is FirebaseAuthInvalidUserException -> "No account found with this email"
            is FirebaseTooManyRequestsException -> "Too many attempts. Try again later"
            else -> exception?.message ?: "Failed to send reset email"
        }
    }
}

sealed class ForgotPasswordState {
    object Loading : ForgotPasswordState()
    object Success : ForgotPasswordState()
    data class Error(val message: String) : ForgotPasswordState()
}
