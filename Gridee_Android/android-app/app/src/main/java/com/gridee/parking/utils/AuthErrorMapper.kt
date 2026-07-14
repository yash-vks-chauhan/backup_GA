package com.gridee.parking.utils

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.gson.Gson
import com.gridee.parking.data.model.ErrorResponse
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps all auth-related errors to user-friendly messages.
 * Never exposes raw exception messages, HTTP codes, or technical details.
 */
object AuthErrorMapper {

    data class UserFacingError(
        val title: String,
        val message: String,
        val isRetryable: Boolean = false
    )

    // ── Network / connectivity ──────────────────────────────────────────

    fun fromException(e: Exception): UserFacingError {
        return when (e) {
            is UnknownHostException,
            is ConnectException,
            is SocketException,
            is SSLException ->
                UserFacingError(
                    title = "No Connection",
                    message = "Please check your internet and try again.",
                    isRetryable = true
                )

            is SocketTimeoutException ->
                UserFacingError(
                    title = "Request Timed Out",
                    message = "The server took too long to respond. Please try again.",
                    isRetryable = true
                )

            is FirebaseNetworkException ->
                UserFacingError(
                    title = "No Connection",
                    message = "Please check your internet and try again.",
                    isRetryable = true
                )

            is FirebaseTooManyRequestsException ->
                UserFacingError(
                    title = "Too Many Attempts",
                    message = "Please wait a moment before trying again."
                )

            is FirebaseAuthInvalidUserException ->
                UserFacingError(
                    title = "Account Not Found",
                    message = "No account found with this email."
                )

            is FirebaseAuthInvalidCredentialsException ->
                UserFacingError(
                    title = "Incorrect Credentials",
                    message = "The email or password you entered is incorrect."
                )

            is FirebaseAuthUserCollisionException ->
                UserFacingError(
                    title = "Account Exists",
                    message = "An account already exists with this email."
                )

            is FirebaseAuthWeakPasswordException ->
                UserFacingError(
                    title = "Weak Password",
                    message = "Please choose a stronger password (at least 6 characters)."
                )

            else -> {
                // Check if the cause is a network-level exception
                val cause = e.cause
                if (cause is UnknownHostException ||
                    cause is ConnectException ||
                    cause is SocketException ||
                    cause is SSLException
                ) {
                    UserFacingError(
                        title = "No Connection",
                        message = "Please check your internet and try again.",
                        isRetryable = true
                    )
                } else if (cause is SocketTimeoutException) {
                    UserFacingError(
                        title = "Request Timed Out",
                        message = "The server took too long to respond. Please try again.",
                        isRetryable = true
                    )
                } else {
                    UserFacingError(
                        title = "Something Went Wrong",
                        message = "Please try again.",
                        isRetryable = true
                    )
                }
            }
        }
    }

    // ── HTTP response codes ─────────────────────────────────────────────

    fun fromHttpCode(code: Int, errorBody: String? = null): UserFacingError {
        // Try to extract a backend message first
        val backendError = parseBackendError(errorBody)
        val backendMessage = backendError?.message?.takeIf { it.isNotBlank() }

        return when (code) {
            400 -> UserFacingError(
                title = "Invalid Request",
                message = backendMessage ?: "Please check your details and try again."
            )
            401 -> UserFacingError(
                title = "Incorrect Credentials",
                message = "The email or password you entered is incorrect."
            )
            403 -> UserFacingError(
                title = "Access Denied",
                message = "You don't have permission for this action."
            )
            404 -> UserFacingError(
                title = "Incorrect Credentials",
                message = "The email or password you entered is incorrect."
            )
            409 -> UserFacingError(
                title = "Account Exists",
                message = backendMessage ?: "An account already exists with this email."
            )
            429 -> UserFacingError(
                title = "Too Many Attempts",
                message = "Please wait a moment before trying again."
            )
            503 -> when (backendError?.errorCode) {
                "FEATURE_DISABLED" -> UserFacingError(
                    title = "Feature Unavailable",
                    message = backendMessage ?: "This feature is temporarily unavailable."
                )
                "MAINTENANCE_MODE" -> UserFacingError(
                    title = "Maintenance",
                    message = backendMessage ?: "Gridee is temporarily unavailable. Please try again later.",
                    isRetryable = true
                )
                else -> UserFacingError(
                    title = "Server Issue",
                    message = backendMessage ?: "We're having trouble right now. Please try again shortly.",
                    isRetryable = true
                )
            }
            in 500..599 -> UserFacingError(
                title = "Server Issue",
                message = "We're having trouble right now. Please try again shortly.",
                isRetryable = true
            )
            else -> UserFacingError(
                title = "Something Went Wrong",
                message = "Please try again.",
                isRetryable = true
            )
        }
    }

    // ── Google Sign-In specific ─────────────────────────────────────────

    fun fromGoogleSignInCode(statusCode: Int): UserFacingError {
        return when (statusCode) {
            7 -> UserFacingError(
                title = "No Connection",
                message = "Please check your internet and try again.",
                isRetryable = true
            )
            10 -> UserFacingError(
                title = "Configuration Error",
                message = "Google sign-in is temporarily unavailable. Please try again later.",
                isRetryable = true
            )
            12500 -> UserFacingError(
                title = "Sign-In Failed",
                message = "Google sign-in failed. Please try again.",
                isRetryable = true
            )
            else -> UserFacingError(
                title = "Sign-In Failed",
                message = "Google sign-in failed. Please try again.",
                isRetryable = true
            )
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun parseBackendMessage(errorBody: String?): String? {
        return parseBackendError(errorBody)?.message?.takeIf { it.isNotBlank() }
    }

    private fun parseBackendError(errorBody: String?): ErrorResponse? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            Gson().fromJson(errorBody, ErrorResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
