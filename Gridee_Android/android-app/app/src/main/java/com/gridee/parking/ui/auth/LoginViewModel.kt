package com.gridee.parking.ui.auth

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.gson.Gson
import com.gridee.parking.data.model.AuthResponse
import com.gridee.parking.data.model.ErrorResponse
import com.gridee.parking.data.model.UpdateUserRequest
import com.gridee.parking.data.model.User
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.JwtTokenManager
import com.gridee.parking.utils.NotificationTokenManager
import com.gridee.parking.utils.PendingProfileUpdate
import com.gridee.parking.utils.PendingProfileUpdateStore
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    
    private val userRepository = UserRepository()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    
    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage
    
    private val _validationErrors = MutableLiveData<Map<String, String>>()
    val validationErrors: LiveData<Map<String, String>> = _validationErrors
    
    fun loginUser(context: Context, emailOrPhone: String, password: String) {
        // Validate input
        val errors = validateInput(emailOrPhone, password)
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            return
        }
        
        val normalizedInput = emailOrPhone.trim()
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val normalized = normalizedInput.let { if (it.contains("@")) it.lowercase() else it }
            try {
                val jwtResponse = userRepository.authLogin(normalized, password)

                if (jwtResponse.isSuccessful) {
                    jwtResponse.body()?.let { auth ->
                        handleAuthSuccess(context, auth)
                    } ?: run {
                        _loginState.value = LoginState.Error("Login successful but no token received")
                    }
                    return@launch
                }

                val backendMessage = parseErrorMessage(jwtResponse.code(), jwtResponse.errorBody()?.string())
                if (isEmailAddress(normalizedInput)) {
                    loginWithFirebase(context, normalized.lowercase(), password)
                } else {
                    _loginState.value = LoginState.Error(backendMessage)
                }
            } catch (e: Exception) {
                if (isEmailAddress(normalizedInput)) {
                    loginWithFirebase(context, normalized.lowercase(), password)
                } else {
                    _loginState.value = LoginState.Error("Network error: ${e.message}")
                }
            }
        }
    }

    fun resendVerificationEmail() {
        val user = firebaseAuth.currentUser
        if (user == null) {
            _statusMessage.value = "Please sign in again to resend verification email"
            return
        }

        user.sendEmailVerification()
            .addOnSuccessListener {
                _statusMessage.value = "Verification email sent"
            }
            .addOnFailureListener { e ->
                _statusMessage.value = "Failed to send verification email: ${e.message}"
            }
    }
    
    private fun getDefaultErrorMessage(code: Int): String {
        return when (code) {
            400, 401, 404 -> "Invalid email/phone or password"
            500 -> "Server error. Please try again later"
            else -> "Login failed. Please check your connection"
        }
    }
    
    private fun validateInput(emailOrPhone: String, password: String): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        
        if (emailOrPhone.isBlank()) {
            errors["emailPhone"] = "Email or phone number is required"
        }
        
        if (password.isBlank()) {
            errors["password"] = "Password is required"
        }
        
        return errors
    }

    private fun loginWithFirebase(context: Context, email: String, password: String) {
        _loginState.value = LoginState.Loading

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    val exception = task.exception
                    _loginState.value = LoginState.Error(getFirebaseLoginError(exception))
                    return@addOnCompleteListener
                }

                val user = firebaseAuth.currentUser
                if (user == null) {
                    _loginState.value = LoginState.Error("Login failed. Please try again")
                    return@addOnCompleteListener
                }

                user.reload()
                    .addOnCompleteListener { reloadTask ->
                        if (!reloadTask.isSuccessful) {
                            _loginState.value = LoginState.Error("Unable to check email verification status")
                            return@addOnCompleteListener
                        }

                        if (!user.isEmailVerified) {
                            _loginState.value = LoginState.VerificationRequired(user.email ?: email)
                            return@addOnCompleteListener
                        }

                        user.getIdToken(true)
                            .addOnCompleteListener { tokenTask ->
                                if (!tokenTask.isSuccessful) {
                                    _loginState.value = LoginState.Error("Failed to obtain verification token")
                                    return@addOnCompleteListener
                                }

                                val idToken = tokenTask.result?.token
                                if (idToken.isNullOrBlank()) {
                                    _loginState.value = LoginState.Error("Missing verification token")
                                    return@addOnCompleteListener
                                }

                                exchangeFirebaseToken(context, idToken)
                            }
                    }
            }
    }

    private fun exchangeFirebaseToken(context: Context, idToken: String) {
        viewModelScope.launch {
            try {
                val response = userRepository.exchangeFirebaseToken(idToken)
                if (response.isSuccessful) {
                    response.body()?.let { auth ->
                        handleAuthSuccess(context, auth)
                    } ?: run {
                        _loginState.value = LoginState.Error("Login successful but no token received")
                    }
                } else {
                    _loginState.value = LoginState.Error(parseErrorMessage(response.code(), response.errorBody()?.string()))
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Network error: ${e.message}")
            }
        }
    }

    private suspend fun handleAuthSuccess(context: Context, auth: AuthResponse) {
        // Persist JWT for subsequent authenticated calls
        val jwtManager = JwtTokenManager(context)
        jwtManager.saveAuthToken(
            token = auth.token,
            userId = auth.id,
            userName = auth.name,
            userRole = auth.role
        )
        AuthSession.syncLegacyPrefsFromJwt(context)
        NotificationTokenManager.registerCurrentToken(context)

        val appliedUpdate = applyPendingProfileUpdate(context, auth.id, auth.email)

        // Build a User object from the response
        val user = User(
            id = auth.id,
            name = appliedUpdate?.name?.takeIf { it.isNotBlank() } ?: auth.name,
            email = auth.email,
            phone = appliedUpdate?.phone?.takeIf { it.isNotBlank() } ?: auth.phone,
            vehicleNumbers = auth.user.vehicleNumbers ?: emptyList(),
            role = auth.role,
            parkingLotId = auth.user.parkingLotId,
            parkingLotName = appliedUpdate?.parkingLotName ?: auth.user.parkingLotName
        )
        _loginState.value = LoginState.Success(user)
    }

    private suspend fun applyPendingProfileUpdate(
        context: Context,
        userId: String,
        userEmail: String
    ): PendingProfileUpdate? {
        val store = PendingProfileUpdateStore(context)
        val pending = store.get() ?: return null
        if (!pending.email.equals(userEmail, ignoreCase = true)) {
            return null
        }

        val request = UpdateUserRequest(
            name = pending.name.takeIf { it.isNotBlank() },
            email = null,
            phone = pending.phone.takeIf { it.isNotBlank() },
            vehicleNumbers = null,
            parkingLotName = pending.parkingLotName
        )

        val success = userRepository.updateUser(userId, request)
        if (success) {
            store.clear()
            return pending
        }
        return null
    }

    private fun parseErrorMessage(code: Int, errorBody: String?): String {
        return try {
            if (!errorBody.isNullOrBlank()) {
                val errorResponse = Gson().fromJson(errorBody, ErrorResponse::class.java)
                errorResponse.message ?: getDefaultErrorMessage(code)
            } else {
                getDefaultErrorMessage(code)
            }
        } catch (e: Exception) {
            getDefaultErrorMessage(code)
        }
    }

    private fun getFirebaseLoginError(exception: Exception?): String {
        return when (exception) {
            is FirebaseAuthInvalidUserException -> "No account found with this email"
            is FirebaseAuthInvalidCredentialsException -> "Invalid email or password"
            is FirebaseTooManyRequestsException -> "Too many attempts. Try again later"
            else -> exception?.message ?: "Login failed. Please try again"
        }
    }

    private fun isEmailAddress(input: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(input).matches()
    }
    
    fun clearErrors() {
        _validationErrors.value = emptyMap()
    }
    
    fun handleGoogleSignInSuccess(context: Context, account: GoogleSignInAccount) {
        android.util.Log.d("LoginViewModel", ">>> handleGoogleSignInSuccess called")
        android.util.Log.d("LoginViewModel", "  - Email: ${account.email}")
        android.util.Log.d("LoginViewModel", "  - Name: ${account.displayName}")
        android.util.Log.d("LoginViewModel", "  - ID Token length: ${account.idToken?.length}")
        
        _loginState.value = LoginState.Loading
        
        viewModelScope.launch {
            try {
                android.util.Log.d("LoginViewModel", "Sending Google sign-in request to backend...")
                android.util.Log.d("LoginViewModel", "  - Endpoint: POST /api/users/social-signin")
                android.util.Log.d("LoginViewModel", "  - idToken: ${account.idToken?.take(30)}...")
                android.util.Log.d("LoginViewModel", "  - email: ${account.email}")
                android.util.Log.d("LoginViewModel", "  - name: ${account.displayName}")
                android.util.Log.d("LoginViewModel", "  - profilePicture: ${account.photoUrl}")
                
                // Send Google account data to your backend for verification
                val response = userRepository.googleSignIn(
                    idToken = account.idToken ?: "",
                    email = account.email ?: "",
                    name = account.displayName ?: "",
                    profilePicture = account.photoUrl?.toString()
                )
                
                android.util.Log.d("LoginViewModel", "Backend response received:")
                android.util.Log.d("LoginViewModel", "  - HTTP Code: ${response.code()}")
                android.util.Log.d("LoginViewModel", "  - is Successful: ${response.isSuccessful}")
                android.util.Log.d("LoginViewModel", "  - Message: ${response.message()}")
                
                if (response.isSuccessful) {
                    android.util.Log.d("LoginViewModel", "✅ Google sign-in backend SUCCESS")
                    response.body()?.let { auth ->
                        android.util.Log.d("LoginViewModel", "Auth response body:")
                        android.util.Log.d("LoginViewModel", "  - token: ${auth.token.take(30)}...")
                        android.util.Log.d("LoginViewModel", "  - userId: ${auth.id}")
                        android.util.Log.d("LoginViewModel", "  - userName: ${auth.name}")
                        android.util.Log.d("LoginViewModel", "  - userRole: ${auth.role}")
                        
                        // Save JWT token and user info
                        val jwtManager = JwtTokenManager(context)
                        jwtManager.saveAuthToken(
                            token = auth.token,
                            userId = auth.id,
                            userName = auth.name,
                            userRole = auth.role
                        )
                        AuthSession.syncLegacyPrefsFromJwt(context)
                        NotificationTokenManager.registerCurrentToken(context)
                        android.util.Log.d("LoginViewModel", "JWT token saved to preferences")
                        
                        // Build a User object from the response
                        val user = User(
                            id = auth.id,
                            name = auth.name,
                            email = auth.email,
                            phone = auth.phone,
                            vehicleNumbers = auth.user.vehicleNumbers ?: emptyList(),
                            role = auth.role
                        )
                        android.util.Log.d("LoginViewModel", "User object created, setting Success state")
                        _loginState.value = LoginState.Success(user)
                    } ?: run {
                        android.util.Log.e("LoginViewModel", "❌ Response body is NULL")
                        _loginState.value = LoginState.Error("Sign in successful but no token received")
                    }
                } else {
                    android.util.Log.e("LoginViewModel", "❌ Google sign-in backend FAILED")
                    
                    // Parse error response from backend
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("LoginViewModel", "Error body: $errorBody")
                    
                    val errorMessage = try {
                        if (errorBody != null) {
                            val errorResponse = Gson().fromJson(errorBody, ErrorResponse::class.java)
                            android.util.Log.e("LoginViewModel", "Parsed error: ${errorResponse.message}")
                            errorResponse.message ?: "Google Sign In failed. Please try again"
                        } else {
                            "Google Sign In failed. Please try again"
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LoginViewModel", "Failed to parse error: ${e.message}")
                        "Google Sign In failed. Please try again"
                    }
                    android.util.Log.e("LoginViewModel", "Final error message: $errorMessage")
                    _loginState.value = LoginState.Error(errorMessage)
                }
            } catch (e: Exception) {
                android.util.Log.e("LoginViewModel", "❌ EXCEPTION during Google sign-in", e)
                android.util.Log.e("LoginViewModel", "  - Exception type: ${e.javaClass.simpleName}")
                android.util.Log.e("LoginViewModel", "  - Message: ${e.message}")
                android.util.Log.e("LoginViewModel", "  - Stack trace: ", e)
                _loginState.value = LoginState.Error("Network error: ${e.message}")
            }
        }
    }
    
    
    fun handleSignInError(message: String) {
        _loginState.value = LoginState.Error(message)
    }
    
    
    @Deprecated("Use handleGoogleSignInSuccess instead")
    fun signInWithGoogle() {
        // TODO: Implement Google Sign In
        _loginState.value = LoginState.Error("Google Sign In not implemented yet")
    }
}

sealed class LoginState {
    object Loading : LoginState()
    data class Success(val user: User) : LoginState()
    data class VerificationRequired(val email: String) : LoginState()
    data class Error(val message: String) : LoginState()
}
