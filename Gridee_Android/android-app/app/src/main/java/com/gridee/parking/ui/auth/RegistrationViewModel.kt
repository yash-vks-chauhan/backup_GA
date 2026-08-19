package com.gridee.parking.ui.auth

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.model.User
import com.gridee.parking.utils.AuthErrorMapper
import com.gridee.parking.utils.PendingProfileUpdate
import com.gridee.parking.utils.PendingProfileUpdateStore

class RegistrationViewModel : ViewModel() {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _registrationState = MutableLiveData<RegistrationState>()
    val registrationState: LiveData<RegistrationState> = _registrationState

    private val _validationErrors = MutableLiveData<Map<String, String>>()
    val validationErrors: LiveData<Map<String, String>> = _validationErrors

    fun registerUser(
        context: Context,
        name: String,
        email: String,
        phone: String,
        password: String
    ) {
        RemoteConfigManager.loadCached(context)
        if (!RemoteConfigManager.isEmailSignInEnabled()) {
            _registrationState.value = RegistrationState.Error(
                "Feature Unavailable",
                "Email registration is temporarily unavailable."
            )
            return
        }

        val sanitizedPhone = phone.filter { it.isDigit() }
        // Validate input
        val errors = validateInput(name, email, sanitizedPhone, password)
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            return
        }

        _registrationState.value = RegistrationState.Loading

        firebaseAuth.createUserWithEmailAndPassword(email.trim().lowercase(), password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    val exception = task.exception
                    val error = if (exception != null) AuthErrorMapper.fromException(exception) else
                        AuthErrorMapper.UserFacingError("Registration Failed", "Please try again.", isRetryable = true)
                    _registrationState.value = RegistrationState.Error(error.title, error.message, error.isRetryable)
                    return@addOnCompleteListener
                }

                val user = firebaseAuth.currentUser
                if (user == null) {
                    _registrationState.value = RegistrationState.Error("Registration Failed", "Please try again.", isRetryable = true)
                    return@addOnCompleteListener
                }

                val profileRequest = UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()

                user.updateProfile(profileRequest)
                    .addOnCompleteListener {
                        user.sendEmailVerification()
                            .addOnCompleteListener { verifyTask ->
                                if (!verifyTask.isSuccessful) {
                                    _registrationState.value = RegistrationState.Error(
                                        "Verification Email Failed",
                                        "We couldn't send a verification email. Please try again.",
                                        isRetryable = true
                                    )
                                    return@addOnCompleteListener
                                }

                                // Parking lot is chosen after sign-in via the selection gate,
                                // so it is intentionally not part of the pending profile here.
                                PendingProfileUpdateStore(context).save(
                                    PendingProfileUpdate(
                                        email = email.trim().lowercase(),
                                        name = name.trim(),
                                        phone = sanitizedPhone,
                                        parkingLotName = null
                                    )
                                )

                                _registrationState.value = RegistrationState.VerificationSent(user.email ?: email.trim())
                            }
                    }
            }
    }

    private fun validateInput(
        name: String,
        email: String,
        phone: String,
        password: String
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (name.isBlank()) {
            errors["name"] = "Name is required"
        }

        if (email.isBlank()) {
            errors["email"] = "Email is required"
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errors["email"] = "Invalid email format"
        }

        if (phone.isBlank()) {
            errors["phone"] = "Phone number is required"
        } else if (phone.length < 10) {
            errors["phone"] = "Phone number must be at least 10 digits"
        }

        if (password.isBlank()) {
            errors["password"] = "Password is required"
        } else if (password.length < 6) {
            errors["password"] = "Password must be at least 6 characters"
        }

        return errors
    }

    fun clearErrors() {
        _validationErrors.value = emptyMap()
    }
}

sealed class RegistrationState {
    object Loading : RegistrationState()
    data class Success(val user: User) : RegistrationState()
    data class VerificationSent(val email: String) : RegistrationState()
    data class Error(
        val title: String,
        val message: String,
        val isRetryable: Boolean = false
    ) : RegistrationState()
}
