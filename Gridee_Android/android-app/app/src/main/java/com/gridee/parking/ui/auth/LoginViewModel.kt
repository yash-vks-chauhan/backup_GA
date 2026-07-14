package com.gridee.parking.ui.auth

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.gridee.parking.BuildConfig
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.model.AuthResponse
import com.gridee.parking.data.model.UpdateUserRequest
import com.gridee.parking.data.model.User
import com.gridee.parking.data.model.UserResponseDto
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.utils.AuthErrorMapper
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
        RemoteConfigManager.loadCached(context)
        if (!RemoteConfigManager.isEmailSignInEnabled()) {
            _loginState.value = LoginState.Error(
                "Feature Unavailable",
                "Email sign-in is temporarily unavailable."
            )
            return
        }

        // Validate input
        val errors = validateInput(emailOrPhone, password)
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            return
        }
        
        val normalizedInput = emailOrPhone.trim()

        if (tryLocalDebugLogin(context, normalizedInput, password)) {
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val normalized = normalizedInput.let { if (it.contains("@")) it.lowercase() else it }
            try {
                val jwtResponse = userRepository.authLogin(normalized, password)

                if (jwtResponse.isSuccessful) {
                    jwtResponse.body()?.let { auth ->
                        handleAuthSuccess(context, auth)
                    } ?: run {
                        _loginState.value = LoginState.Error("Something Went Wrong", "Please try again.", isRetryable = true)
                    }
                    return@launch
                }

                val errorBody = jwtResponse.errorBody()?.string()
                if (isEmailAddress(normalizedInput) && shouldTryFirebaseFallback(jwtResponse.code())) {
                    loginWithFirebase(context, normalized.lowercase(), password)
                } else {
                    val error = AuthErrorMapper.fromHttpCode(jwtResponse.code(), errorBody)
                    _loginState.value = LoginState.Error(error.title, error.message, error.isRetryable)
                }
            } catch (e: Exception) {
                val error = AuthErrorMapper.fromException(e)
                _loginState.value = LoginState.Error(error.title, error.message, error.isRetryable)
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
            .addOnFailureListener {
                _statusMessage.value = "Could not send verification email. Please try again."
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
            .addOnCompleteListener signInListener@{ task ->
                if (!task.isSuccessful) {
                    val exception = task.exception
                    val error = if (exception != null) AuthErrorMapper.fromException(exception) else
                        AuthErrorMapper.UserFacingError("Sign-In Failed", "Please try again.", isRetryable = true)
                    _loginState.value = LoginState.Error(error.title, error.message, error.isRetryable)
                    return@signInListener
                }

                val user = firebaseAuth.currentUser
                if (user == null) {
                    _loginState.value = LoginState.Error("Sign-In Failed", "Please try again.", isRetryable = true)
                    return@signInListener
                }

                user.reload()
                    .addOnCompleteListener reloadListener@{ reloadTask ->
                        if (!reloadTask.isSuccessful) {
                            _loginState.value = LoginState.Error("Verification Check Failed", "Unable to check email verification status. Please try again.", isRetryable = true)
                            return@reloadListener
                        }

                        if (!user.isEmailVerified) {
                            _loginState.value = LoginState.VerificationRequired(user.email ?: email)
                            return@reloadListener
                        }

                        user.getIdToken(true)
                            .addOnCompleteListener tokenListener@{ tokenTask ->
                                if (!tokenTask.isSuccessful) {
                                    _loginState.value = LoginState.Error("Sign-In Failed", "Could not complete sign-in. Please try again.", isRetryable = true)
                                    return@tokenListener
                                }

                                val idToken = tokenTask.result?.token
                                if (idToken.isNullOrBlank()) {
                                    _loginState.value = LoginState.Error("Sign-In Failed", "Could not complete sign-in. Please try again.", isRetryable = true)
                                    return@tokenListener
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
                        _loginState.value = LoginState.Error("Something Went Wrong", "Please try again.", isRetryable = true)
                    }
                } else {
                    val error = AuthErrorMapper.fromHttpCode(response.code(), response.errorBody()?.string())
                    _loginState.value = LoginState.Error(error.title, error.message, error.isRetryable)
                }
            } catch (e: Exception) {
                val error = AuthErrorMapper.fromException(e)
                _loginState.value = LoginState.Error(error.title, error.message, error.isRetryable)
            }
        }
    }

    private suspend fun handleAuthSuccess(context: Context, auth: AuthResponse) {
        val token = auth.token?.trim()?.takeIf { it.isNotEmpty() }
        if (token == null) {
            _loginState.value = LoginState.Error(
                "Additional Verification Required",
                if (auth.mfaRequired == true) {
                    "This account needs additional verification before mobile sign-in can continue."
                } else {
                    "The server did not return a sign-in token. Please try again."
                },
                isRetryable = true
            )
            return
        }

        // Persist JWT for subsequent authenticated calls
        val jwtManager = JwtTokenManager(context)
        jwtManager.saveAuthToken(
            token = token,
            userId = auth.id,
            userName = auth.name,
            userRole = auth.role
        )
        AuthSession.syncLegacyPrefsFromJwt(context)
        RemoteConfigManager.refresh(context)
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
        AuthSession.updateCachedUserProfile(context, user)
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


    private fun isEmailAddress(input: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(input).matches()
    }

    private fun shouldTryFirebaseFallback(httpCode: Int): Boolean {
        return httpCode == 401 || httpCode == 404
    }

    private fun tryLocalDebugLogin(context: Context, emailOrPhone: String, password: String): Boolean {
        if (!BuildConfig.DEBUG || !isLocalDebugCredential(emailOrPhone, password)) {
            return false
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            handleAuthSuccess(context, buildLocalDebugAuthResponse())
        }
        return true
    }

    private fun isLocalDebugCredential(emailOrPhone: String, password: String): Boolean {
        return emailOrPhone.equals(LOCAL_DEBUG_USERNAME, ignoreCase = true) &&
            password == LOCAL_DEBUG_PASSWORD
    }

    private fun buildLocalDebugAuthResponse(): AuthResponse {
        return AuthResponse(
            token = "local-debug-token-${System.currentTimeMillis()}",
            tokenType = "Bearer",
            profileComplete = true,
            requiresProfileCompletion = false,
            isNewUser = false,
            user = UserResponseDto(
                id = "local-debug-user",
                name = "Debug User",
                email = "debug@gridee.local",
                phone = "9999999999",
                vehicleNumbers = listOf("KA01AB1234"),
                firstUser = false,
                walletCoins = 0,
                role = "USER",
                active = true
            ),
            message = "Signed in locally"
        )
    }

    companion object {
        private const val LOCAL_DEBUG_USERNAME = "user"
        private const val LOCAL_DEBUG_PASSWORD = "password"
    }
    
    fun clearErrors() {
        _validationErrors.value = emptyMap()
    }
    
    fun handleGoogleSignInSuccess(context: Context, account: GoogleSignInAccount) {
        RemoteConfigManager.loadCached(context)
        if (!RemoteConfigManager.isGoogleSignInEnabled()) {
            _loginState.value = LoginState.Error(
                "Feature Unavailable",
                "Google sign-in is temporarily unavailable."
            )
            return
        }

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
                        val token = auth.token?.trim()?.takeIf { it.isNotEmpty() }
                        if (token == null) {
                            _loginState.value = LoginState.Error(
                                "Additional Verification Required",
                                if (auth.mfaRequired == true) {
                                    "This account needs additional verification before mobile sign-in can continue."
                                } else {
                                    "The server did not return a sign-in token. Please try again."
                                },
                                isRetryable = true
                            )
                            return@launch
                        }
                        android.util.Log.d("LoginViewModel", "Auth response body:")
                        android.util.Log.d("LoginViewModel", "  - token: ${token.take(30)}...")
                        android.util.Log.d("LoginViewModel", "  - userId: ${auth.id}")
                        android.util.Log.d("LoginViewModel", "  - userName: ${auth.name}")
                        android.util.Log.d("LoginViewModel", "  - userRole: ${auth.role}")
                        
                        // Save JWT token and user info
                        val jwtManager = JwtTokenManager(context)
                        jwtManager.saveAuthToken(
                            token = token,
                            userId = auth.id,
                            userName = auth.name,
                            userRole = auth.role
                        )
                        AuthSession.syncLegacyPrefsFromJwt(context)
                        RemoteConfigManager.refresh(context)
                        NotificationTokenManager.registerCurrentToken(context)
                        android.util.Log.d("LoginViewModel", "JWT token saved to preferences")
                        
                        // Build a User object from the response
                        val user = User(
                            id = auth.id,
                            name = auth.name,
                            email = auth.email,
                            phone = auth.phone,
                            vehicleNumbers = auth.user.vehicleNumbers ?: emptyList(),
                            role = auth.role,
                            parkingLotId = auth.user.parkingLotId,
                            parkingLotName = auth.user.parkingLotName
                        )
                        AuthSession.updateCachedUserProfile(context, user)
                        android.util.Log.d("LoginViewModel", "User object created, setting Success state")
                        _loginState.value = LoginState.Success(user)
                    } ?: run {
                        android.util.Log.e("LoginViewModel", "❌ Response body is NULL")
                        _loginState.value = LoginState.Error("Something Went Wrong", "Please try again.", isRetryable = true)
                    }
                } else {
                    android.util.Log.e("LoginViewModel", "❌ Google sign-in backend FAILED")
                    
                    // Parse error response from backend
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("LoginViewModel", "Error body: $errorBody")
                    
                    val error = AuthErrorMapper.fromHttpCode(response.code(), errorBody)
                    android.util.Log.e("LoginViewModel", "Final error: ${error.title} - ${error.message}")
                    _loginState.value = LoginState.Error(error.title, error.message, error.isRetryable)
                }
            } catch (e: Exception) {
                android.util.Log.e("LoginViewModel", "❌ EXCEPTION during Google sign-in", e)
                android.util.Log.e("LoginViewModel", "  - Exception type: ${e.javaClass.simpleName}")
                android.util.Log.e("LoginViewModel", "  - Message: ${e.message}")
                android.util.Log.e("LoginViewModel", "  - Stack trace: ", e)
                val error = AuthErrorMapper.fromException(e)
                _loginState.value = LoginState.Error(error.title, error.message, error.isRetryable)
            }
        }
    }


    fun handleSignInError(message: String) {
        _loginState.value = LoginState.Error("Sign-In Failed", message, isRetryable = true)
    }
    
    
    @Deprecated("Use handleGoogleSignInSuccess instead")
    fun signInWithGoogle() {
        _loginState.value = LoginState.Error("Sign-In Failed", "Google Sign-In not available.", isRetryable = true)
    }
}

sealed class LoginState {
    object Loading : LoginState()
    data class Success(val user: User) : LoginState()
    data class VerificationRequired(val email: String) : LoginState()
    data class Error(
        val title: String,
        val message: String,
        val isRetryable: Boolean = false
    ) : LoginState()
}
