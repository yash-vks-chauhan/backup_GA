package com.gridee.parking.config

/**
 * API Configuration for backend server
 * Configure your backend server URL here
 */
object ApiConfig {
    // Backend Server Configuration
    // Note: Base URL must NOT include "/api" because paths in ApiService already start with "api/..."
    
    // ✅ Production (custom domain)
    // API base: https://www.gridee.in/api
    const val BASE_URL = "https://www.gridee.in/"
    
    // Alternative backend URLs (uncomment to use)
    // const val BASE_URL = "http://10.0.2.2:8080/"  // Android emulator (local backend)
    // const val BASE_URL = "http://localhost:8080/"  // Via ADB reverse (adb reverse tcp:8080 tcp:8080)
    // const val BASE_URL = "http://192.168.1.100:8080/"  // Physical device (your machine IP)
    
    // Authentication is handled via JWT tokens (no basic auth required)
    val REQUIRES_AUTH = false
    
    /**
     * Quick switch methods for easy configuration changes
     */
    fun getAuthHeader(): String? {
        return null  // JWT tokens are handled by JwtAuthInterceptor
    }
    
    // Convenience helper
    fun isSSLRequired(): Boolean = BASE_URL.startsWith("https://")
    
    fun getEnvironmentInfo(): String = "GRIDEE.IN"
}
