package com.gridee.parking.data.repository

import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.AuthRequest
import com.gridee.parking.data.model.AuthResponse
import com.gridee.parking.data.model.FirebaseTokenExchangeRequest
import com.gridee.parking.data.model.UpdateUserRequest
import com.gridee.parking.data.model.User
import com.gridee.parking.data.model.UserRegistration
import retrofit2.Response

class UserRepository {
    
    private val apiService = ApiClient.apiService
    
    suspend fun registerUser(userRegistration: UserRegistration): Response<AuthResponse> {
        return apiService.registerUser(userRegistration)
    }
    
    /**
     * JWT-based authentication using /api/auth/login endpoint
     * Returns AuthResponse with JWT token and user info
     */
    suspend fun authLogin(email: String, password: String): Response<AuthResponse> {
        val request = AuthRequest(email = email, password = password)
        return apiService.authLogin(request)
    }

    /**
     * Exchange Firebase ID token for backend JWT
     */
    suspend fun exchangeFirebaseToken(idToken: String): Response<AuthResponse> {
        val request = FirebaseTokenExchangeRequest(idToken = idToken)
        return apiService.exchangeFirebaseToken(request)
    }
    
    /**
     * Legacy login using /api/users/login endpoint
     * Returns User object without JWT token
     */
    suspend fun loginUser(email: String, password: String): Response<User> {
        val credentials = mapOf(
            "email" to email,
            "password" to password
        )
        return apiService.loginUser(credentials)
    }
    
    suspend fun getUserById(userId: String): User? {
        return try {
            val response = apiService.getUserById(userId)
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun updateUser(user: User): Boolean {
        return try {
            val userId = user.id?.trim().orEmpty()
            if (userId.isEmpty()) {
                println("UserRepository: Update failed - missing user id")
                return false
            }

            val request = UpdateUserRequest(
                name = user.name.trim().takeIf { it.isNotEmpty() },
                email = user.email.trim().takeIf { it.isNotEmpty() },
                phone = user.phone.trim().takeIf { it.isNotEmpty() },
                // Preserve explicit clears: an empty list means "remove all vehicles",
                // while null means "leave vehicle numbers unchanged".
                vehicleNumbers = user.vehicleNumbers,
                parkingLotName = user.parkingLotName?.trim()?.takeIf { it.isNotEmpty() }
            )

            updateUser(userId, request)
        } catch (e: Exception) {
            println("UserRepository: Update exception: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun updateUser(userId: String, request: UpdateUserRequest): Boolean {
        return try {
            val response = apiService.updateUser(userId, request)
            println("UserRepository: Update response - success: ${response.isSuccessful}, code: ${response.code()}")
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                println("UserRepository: Update failed - error body: $errorBody")
            }
            response.isSuccessful
        } catch (e: Exception) {
            println("UserRepository: Update exception: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    suspend fun googleSignIn(
        idToken: String,
        email: String,
        name: String,
        profilePicture: String?
    ): Response<AuthResponse> {
        // Backend expects 'credential' key for the ID token
        val googleData = mapOf(
            "credential" to idToken
        )
        return apiService.googleSignIn(googleData)
    }
    
    /**
     * Fetch current authenticated user info from OAuth2 (or Basic) context
     */
    suspend fun getOAuth2User(): Response<Map<String, Any>> {
        return apiService.getOAuth2User()
    }
}
