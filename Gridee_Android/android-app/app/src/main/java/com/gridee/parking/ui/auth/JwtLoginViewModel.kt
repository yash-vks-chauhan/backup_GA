package com.gridee.parking.ui.auth

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.AuthResponse
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.JwtTokenManager
import com.gridee.parking.utils.NotificationTokenManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest

/**
 * ViewModel for JWT-based authentication
 * Uses the /api/auth/login endpoint which returns a JWT token
 */
class JwtLoginViewModel : ViewModel() {
    
    private val userRepository = UserRepository()
    
    private val _authState = MutableLiveData<JwtAuthState>()
    val authState: LiveData<JwtAuthState> = _authState
    
    private val _validationErrors = MutableLiveData<Map<String, String>>()
    val validationErrors: LiveData<Map<String, String>> = _validationErrors
    
    /**
     * Login using JWT authentication endpoint
     */
    fun loginWithJwt(context: Context, emailOrPhone: String, password: String) {
        // Validate input
        val errors = validateInput(emailOrPhone, password)
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            return
        }
        
        val normalizedIdentifier = emailOrPhone.trim().let { input ->
            if (input.contains("@")) input.lowercase() else input
        }

        _authState.value = JwtAuthState.Loading
        
        viewModelScope.launch {
            try {
                // Send plain password; backend verifies with BCrypt
                val response = userRepository.authLogin(normalizedIdentifier, password)
                
                if (response.isSuccessful) {
                    response.body()?.let { authResponse ->
                        // Save JWT token and user info
                        val jwtManager = JwtTokenManager(context)
                        jwtManager.saveAuthToken(
                            token = authResponse.token,
                            userId = authResponse.id,
                            userName = authResponse.name,
                            userRole = authResponse.role
                        )
                        AuthSession.syncLegacyPrefsFromJwt(context)
                        NotificationTokenManager.registerCurrentToken(context)
                        
                        _authState.value = JwtAuthState.Success(authResponse)
                    } ?: run {
                        _authState.value = JwtAuthState.Error("Login successful but no token received")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val backendMessage = extractBackendMessage(errorBody)
                    val errorMessage = when (response.code()) {
                        400, 401 -> backendMessage ?: "Invalid email/phone or password"
                        404 -> backendMessage ?: "User not found"
                        else -> backendMessage ?: "Authentication failed (${response.code()})"
                    }
                    _authState.value = JwtAuthState.Error(errorMessage)
                }
            } catch (e: Exception) {
                _authState.value = JwtAuthState.Error("Network error: ${e.message}")
            }
        }
    }
    
    /**
     * Check if user is already authenticated with valid JWT token
     */
    fun checkAuthentication(context: Context): Boolean {
        val jwtManager = JwtTokenManager(context)
        return jwtManager.isAuthenticated()
    }
    
    /**
     * Logout user by clearing JWT token
     */
    fun logout(context: Context) {
        AuthSession.clearSession(context)
        _authState.value = JwtAuthState.LoggedOut
    }
    
    private fun validateInput(emailOrPhone: String, password: String): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        
        if (emailOrPhone.isBlank()) {
            errors["emailOrPhone"] = "Email or phone is required"
        }
        
        if (password.isBlank()) {
            errors["password"] = "Password is required"
        } else if (password.length < 6) {
            errors["password"] = "Password must be at least 6 characters"
        }
        
        return errors
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun extractBackendMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null

        return try {
            val json = JSONObject(errorBody)
            when {
                json.has("error") -> json.getString("error")
                json.has("message") -> json.getString("message")
                else -> errorBody
            }
        } catch (_: Exception) {
            errorBody
        }
    }
    
}

/**
 * Sealed class representing JWT authentication states
 */
sealed class JwtAuthState {
    object Idle : JwtAuthState()
    object Loading : JwtAuthState()
    data class Success(val authResponse: AuthResponse) : JwtAuthState()
    data class Error(val message: String) : JwtAuthState()
    object LoggedOut : JwtAuthState()
}
