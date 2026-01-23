package com.gridee.parking.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject

/**
 * Utility class for managing JWT tokens
 * Handles storage, retrieval, and validation of JWT authentication tokens
 */
class JwtTokenManager(context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "gridee_auth_prefs"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
        
        // Fallback expiry time (used only if JWT doesn't contain an "exp" claim)
        private const val TOKEN_EXPIRY_TIME = 24 * 60 * 60 * 1000L
    }
    
    /**
     * Save JWT token and user information
     */
    fun saveAuthToken(token: String, userId: String, userName: String, userRole: String) {
        prefs.edit().apply {
            putString(KEY_JWT_TOKEN, token)
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            putString(KEY_USER_ROLE, userRole)
            putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }
    
    /**
     * Get stored JWT token
     * Returns null if token doesn't exist or has expired
     */
    fun getAuthToken(): String? {
        val token = prefs.getString(KEY_JWT_TOKEN, null)
        val timestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0)
        
        // Check if token exists and hasn't expired
        return if (token != null && !isTokenExpired(token, timestamp)) {
            token
        } else {
            // Clear expired token
            clearAuthToken()
            null
        }
    }
    
    /**
     * Get bearer token format (used in Authorization header)
     */
    fun getBearerToken(): String? {
        val token = getAuthToken()
        return if (token != null) "Bearer $token" else null
    }
    
    /**
     * Get stored user ID
     */
    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }
    
    /**
     * Get stored user name
     */
    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }
    
    /**
     * Get stored user role
     */
    fun getUserRole(): String? {
        return prefs.getString(KEY_USER_ROLE, null)
    }
    
    /**
     * Check if user is authenticated (has valid token)
     */
    fun isAuthenticated(): Boolean {
        return getAuthToken() != null
    }
    
    /**
     * Check if token has expired
     */
    private fun isTokenExpired(token: String, timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()

        // Prefer actual JWT expiry if present.
        val jwtExpiry = getJwtExpiryMillis(token)
        if (jwtExpiry != null) {
            return currentTime >= jwtExpiry
        }

        // Fallback to local timestamp if "exp" is missing/unparseable.
        return (currentTime - timestamp) > TOKEN_EXPIRY_TIME
    }

    /**
     * Extract JWT "exp" claim (epoch seconds) and convert to epoch millis.
     * Returns null if token is malformed or claim is missing.
     */
    private fun getJwtExpiryMillis(token: String): Long? {
        return try {
            val parts = token.split('.')
            if (parts.size < 2) return null

            val payloadJson = String(decodeBase64Url(parts[1]) ?: return null, Charsets.UTF_8)
            val payload = JSONObject(payloadJson)
            if (!payload.has("exp")) return null

            val expSeconds = payload.optLong("exp", -1L)
            if (expSeconds <= 0L) return null
            expSeconds * 1000L
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBase64Url(value: String): ByteArray? {
        return try {
            // JWT uses Base64 URL-safe encoding without padding. Add padding if required.
            val padded = when (value.length % 4) {
                2 -> "$value=="
                3 -> "$value="
                else -> value
            }
            Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Clear all authentication data (logout)
     */
    fun clearAuthToken() {
        prefs.edit().apply {
            remove(KEY_JWT_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USER_NAME)
            remove(KEY_USER_ROLE)
            remove(KEY_TOKEN_TIMESTAMP)
            apply()
        }
    }
    
    /**
     * Get all auth data as a map (for debugging)
     */
    fun getAuthData(): Map<String, String?> {
        return mapOf(
            "token" to getAuthToken(),
            "userId" to getUserId(),
            "userName" to getUserName(),
            "userRole" to getUserRole(),
            "isAuthenticated" to isAuthenticated().toString()
        )
    }
}
