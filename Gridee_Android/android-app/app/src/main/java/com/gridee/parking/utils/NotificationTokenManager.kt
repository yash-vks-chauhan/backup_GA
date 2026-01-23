package com.gridee.parking.utils

import android.content.Context
import android.provider.Settings
import com.google.firebase.messaging.FirebaseMessaging
import com.gridee.parking.BuildConfig
import com.gridee.parking.data.model.DeviceTokenRegisterRequest
import com.gridee.parking.data.model.DeviceTokenUnregisterRequest
import com.gridee.parking.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object NotificationTokenManager {

    private const val PREFS_NAME = "gridee_push_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val PLATFORM = "ANDROID"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = NotificationRepository()

    fun registerCurrentToken(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> registerToken(context, token) }
    }

    fun registerToken(context: Context, token: String) {
        val appContext = context.applicationContext
        cacheToken(appContext, token)

        val authHeader = JwtTokenManager(appContext).getBearerToken() ?: return
        val request = DeviceTokenRegisterRequest(
            token = token,
            platform = PLATFORM,
            deviceId = getDeviceId(appContext),
            appVersion = BuildConfig.VERSION_NAME
        )

        scope.launch {
            runCatching { repository.registerToken(authHeader, request) }
        }
    }

    fun unregisterCurrentToken(context: Context, authHeaderOverride: String? = null) {
        val appContext = context.applicationContext
        val authHeader = authHeaderOverride ?: JwtTokenManager(appContext).getBearerToken()
        if (authHeader.isNullOrBlank()) return

        val cachedToken = getCachedToken(appContext)
        if (cachedToken != null) {
            unregisterToken(appContext, cachedToken, authHeader)
            return
        }

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> unregisterToken(appContext, token, authHeader) }
    }

    fun getCachedToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    private fun unregisterToken(context: Context, token: String, authHeader: String) {
        val request = DeviceTokenUnregisterRequest(token = token)

        scope.launch {
            val success = runCatching { repository.unregisterToken(authHeader, request) }
                .getOrDefault(false)
            if (success) {
                clearCachedToken(context)
            }
        }
    }

    private fun cacheToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    private fun clearCachedToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_FCM_TOKEN).apply()
    }

    private fun getDeviceId(context: Context): String? {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
}
