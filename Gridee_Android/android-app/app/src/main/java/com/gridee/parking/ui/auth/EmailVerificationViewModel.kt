package com.gridee.parking.ui.auth

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class EmailVerificationViewModel : ViewModel() {

    private val userRepository = UserRepository()

    private val _state = MutableLiveData<EmailVerificationState>()
    val state: LiveData<EmailVerificationState> = _state

    fun exchangeFirebaseToken(context: Context, idToken: String) {
        _state.value = EmailVerificationState.Loading

        viewModelScope.launch {
            try {
                val response = userRepository.exchangeFirebaseToken(idToken)
                if (response.isSuccessful) {
                    response.body()?.let { auth ->
                        val user = handleAuthSuccess(context, auth)
                        _state.value = EmailVerificationState.Success(user)
                    } ?: run {
                        _state.value = EmailVerificationState.Error("Login successful but no token received")
                    }
                } else {
                    _state.value = EmailVerificationState.Error(parseErrorMessage(response.code(), response.errorBody()?.string()))
                }
            } catch (e: Exception) {
                _state.value = EmailVerificationState.Error("Network error: ${e.message}")
            }
        }
    }

    private suspend fun handleAuthSuccess(context: Context, auth: AuthResponse): User {
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

        return User(
            id = auth.id,
            name = appliedUpdate?.name?.takeIf { it.isNotBlank() } ?: auth.name,
            email = auth.email,
            phone = appliedUpdate?.phone?.takeIf { it.isNotBlank() } ?: auth.phone,
            vehicleNumbers = auth.user.vehicleNumbers ?: emptyList(),
            role = auth.role,
            parkingLotId = auth.user.parkingLotId,
            parkingLotName = appliedUpdate?.parkingLotName ?: auth.user.parkingLotName
        )
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

    private fun getDefaultErrorMessage(code: Int): String {
        return when (code) {
            400, 401, 404 -> "Invalid credentials"
            500 -> "Server error. Please try again later"
            else -> "Login failed. Please check your connection"
        }
    }
}

sealed class EmailVerificationState {
    object Loading : EmailVerificationState()
    data class Success(val user: User) : EmailVerificationState()
    data class Error(val message: String) : EmailVerificationState()
}
