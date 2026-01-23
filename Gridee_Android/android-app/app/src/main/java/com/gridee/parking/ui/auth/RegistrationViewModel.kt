package com.gridee.parking.ui.auth

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.gridee.parking.data.model.User
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.utils.PendingProfileUpdate
import com.gridee.parking.utils.PendingProfileUpdateStore
import kotlinx.coroutines.launch

class RegistrationViewModel : ViewModel() {
    
    private val parkingRepository = ParkingRepository()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    
    private val _registrationState = MutableLiveData<RegistrationState>()
    val registrationState: LiveData<RegistrationState> = _registrationState
    
    private val _validationErrors = MutableLiveData<Map<String, String>>()
    val validationErrors: LiveData<Map<String, String>> = _validationErrors

    private val _parkingLotNames = MutableLiveData<List<String>>()
    val parkingLotNames: LiveData<List<String>> = _parkingLotNames

    private val _parkingLotLoading = MutableLiveData<Boolean>()
    val parkingLotLoading: LiveData<Boolean> = _parkingLotLoading

    private val _parkingLotError = MutableLiveData<String?>()
    val parkingLotError: LiveData<String?> = _parkingLotError
    
    fun registerUser(
        context: Context,
        name: String,
        email: String,
        phone: String,
        password: String,
        parkingLotName: String
    ) {
        val normalizedParkingLot = parkingLotName.trim().ifBlank { null }
        val sanitizedPhone = phone.filter { it.isDigit() }
        // Validate input
        val errors = validateInput(name, email, sanitizedPhone, password, normalizedParkingLot)
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            return
        }
        
        _registrationState.value = RegistrationState.Loading
        
        firebaseAuth.createUserWithEmailAndPassword(email.trim().lowercase(), password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    _registrationState.value = RegistrationState.Error(getFirebaseRegisterError(task.exception))
                    return@addOnCompleteListener
                }

                val user = firebaseAuth.currentUser
                if (user == null) {
                    _registrationState.value = RegistrationState.Error("Registration failed. Please try again")
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
                                        "Failed to send verification email: ${verifyTask.exception?.message}"
                                    )
                                    return@addOnCompleteListener
                                }

                                PendingProfileUpdateStore(context).save(
                                    PendingProfileUpdate(
                                        email = email.trim().lowercase(),
                                        name = name.trim(),
                                        phone = sanitizedPhone,
                                        parkingLotName = normalizedParkingLot
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
        password: String,
        parkingLotName: String?
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

    private fun getFirebaseRegisterError(exception: Exception?): String {
        return when (exception) {
            is FirebaseAuthUserCollisionException -> "An account already exists with this email"
            is FirebaseAuthWeakPasswordException -> "Password is too weak"
            is FirebaseTooManyRequestsException -> "Too many attempts. Try again later"
            else -> exception?.message ?: "Registration failed. Please try again"
        }
    }

    fun loadParkingLotNames(forceRefresh: Boolean = false) {
        if (!forceRefresh && !_parkingLotNames.value.isNullOrEmpty()) {
            return
        }

        _parkingLotLoading.value = true

        viewModelScope.launch {
            try {
                val response = parkingRepository.getParkingLotNames()
                if (response.isSuccessful) {
                    val names = response.body().orEmpty()
                    _parkingLotNames.value = names
                    _parkingLotError.value = if (names.isEmpty()) "No parking lots available yet" else null
                } else {
                    val error = runCatching { response.errorBody()?.string() }.getOrNull()
                    _parkingLotError.value = error ?: "Unable to load parking lots"
                }
            } catch (e: Exception) {
                _parkingLotError.value = "Unable to load parking lots: ${e.message}"
            } finally {
                _parkingLotLoading.value = false
            }
        }
    }
    
    fun clearErrors() {
        _validationErrors.value = emptyMap()
    }
}

sealed class RegistrationState {
    object Loading : RegistrationState()
    data class Success(val user: User) : RegistrationState()
    data class VerificationSent(val email: String) : RegistrationState()
    data class Error(val message: String) : RegistrationState()
}
