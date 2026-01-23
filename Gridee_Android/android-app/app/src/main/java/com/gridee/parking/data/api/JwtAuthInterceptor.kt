package com.gridee.parking.data.api

import android.content.Context
import com.gridee.parking.utils.JwtTokenManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that automatically adds JWT token to API requests
 * Add this to your OkHttpClient to enable JWT authentication
 */
class JwtAuthInterceptor(private val context: Context) : Interceptor {
    
    private val jwtTokenManager by lazy { JwtTokenManager(context) }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Check if request already has Authorization header
        if (originalRequest.header("Authorization") != null) {
            // Request already has auth header, proceed as is
            return chain.proceed(originalRequest)
        }
        
        // Check if endpoint requires JWT authentication
        val path = originalRequest.url.encodedPath
        if (shouldAddJwtToken(path)) {
            // Get JWT token
            val token = jwtTokenManager.getBearerToken()
            
            if (token != null) {
                // Add JWT token to request
                val authenticatedRequest = originalRequest.newBuilder()
                    .addHeader("Authorization", token)
                    .build()
                
                println("JwtAuthInterceptor: Added JWT token to request: $path")
                return chain.proceed(authenticatedRequest)
            } else {
                println("JwtAuthInterceptor: No valid JWT token found for request: $path")
            }
        }
        
        // Proceed with original request if no JWT token needed or available
        return chain.proceed(originalRequest)
    }
    
    /**
     * Determine if endpoint requires JWT authentication
     * Add more paths here as needed
     */
    private fun shouldAddJwtToken(path: String): Boolean {
        // List of paths that don't require JWT token
        val publicPaths = listOf(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/google",
            "/api/auth/firebase/exchange",
            "/api/users/login",
            "/api/users/social-signin",
            "/api/otp/generate",
            "/api/otp/validate"
            // /api/oauth2/user requires JWT token to show authenticated user info
        )
        
        // Check if path is public (doesn't require authentication)
        val isPublicPath = publicPaths.any { publicPath ->
            path.contains(publicPath)
        }
        
        // Return true if path is not public (requires authentication)
        return !isPublicPath
    }
}
