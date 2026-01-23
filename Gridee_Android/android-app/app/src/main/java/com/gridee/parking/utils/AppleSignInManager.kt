package com.gridee.parking.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import java.util.Base64
import kotlin.coroutines.resume

class AppleSignInManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AppleSignInManager"
        // Replace these with your actual values from Apple Developer Console
        private const val CLIENT_ID = "com.gridee.parking.signin"
        private const val REDIRECT_URI = "https://your-backend-domain.com/auth/apple/callback"
        private const val APPLE_AUTH_URL = "https://appleid.apple.com/auth/authorize"
        
        // Generate a random state parameter for security
        fun generateState(): String {
            val random = SecureRandom()
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
    
    fun signIn() {
        try {
            val state = generateState()
            
            // Build the Apple Sign-In URL
            val authUrl = Uri.parse(APPLE_AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "name email")
                .appendQueryParameter("response_mode", "form_post")
                .appendQueryParameter("state", state)
                .build()
            
            // Open Custom Tab for Apple Sign-In
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            
            customTabsIntent.launchUrl(context, authUrl)
            
            Log.d(TAG, "Launched Apple Sign In with state: $state")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Apple Sign In", e)
        }
    }
    
    fun handleRedirect(intent: Intent?): AppleSignInResult {
        return try {
            val uri = intent?.data
            if (uri != null) {
                val code = uri.getQueryParameter("code")
                val state = uri.getQueryParameter("state")
                val error = uri.getQueryParameter("error")
                
                when {
                    error != null -> {
                        Log.e(TAG, "Apple Sign In error: $error")
                        AppleSignInResult.Error(error)
                    }
                    code != null && state != null -> {
                        Log.d(TAG, "Apple Sign In successful")
                        AppleSignInResult.Success(code, state)
                    }
                    else -> {
                        Log.e(TAG, "Invalid Apple Sign In response")
                        AppleSignInResult.Error("Invalid response from Apple")
                    }
                }
            } else {
                AppleSignInResult.Cancelled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Apple Sign In redirect", e)
            AppleSignInResult.Error(e.message ?: "Unknown error")
        }
    }
}

sealed class AppleSignInResult {
    data class Success(val authorizationCode: String, val state: String) : AppleSignInResult()
    data class Error(val message: String) : AppleSignInResult()
    object Cancelled : AppleSignInResult()
}
