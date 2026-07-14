package com.gridee.parking.utils

import android.content.Context
import android.provider.Settings
import com.google.firebase.messaging.FirebaseMessaging
import com.gridee.parking.BuildConfig
import com.gridee.parking.config.RemoteConfigManager
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
    private const val KEY_PUSH_DISABLED_BY_BACKEND = "push_disabled_by_backend"
    private const val PLATFORM = "ANDROID"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = NotificationRepository()

    fun registerCurrentToken(context: Context) {
        val appContext = context.applicationContext
        RemoteConfigManager.loadCached(appContext)
        if (!RemoteConfigManager.areNotificationsEnabled()) {
            clearCachedToken(appContext)
            return
        }
        clearBackendDisabledMarker(appContext)

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> registerToken(appContext, token) }
    }

    fun registerToken(context: Context, token: String) {
        val appContext = context.applicationContext
        RemoteConfigManager.loadCached(appContext)
        if (!RemoteConfigManager.areNotificationsEnabled()) {
            clearCachedToken(appContext)
            return
        }
        clearBackendDisabledMarker(appContext)

        cacheToken(appContext, token)

        val authHeader = JwtTokenManager(appContext).getBearerToken() ?: return
        val request = DeviceTokenRegisterRequest(
            token = token,
            platform = PLATFORM,
            deviceId = getDeviceId(appContext),
            appVersion = BuildConfig.VERSION_NAME
        )

        scope.launch {
            val result = runCatching { repository.registerToken(authHeader, request) }.getOrNull()
            if (result?.success == true) {
                setPushDisabledByBackend(appContext, false)
            } else if (result?.featureDisabled == true) {
                clearCachedToken(appContext)
                setPushDisabledByBackend(appContext, true)
            }
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

    private fun isPushDisabledByBackend(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PUSH_DISABLED_BY_BACKEND, false)
    }

    private fun setPushDisabledByBackend(context: Context, disabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PUSH_DISABLED_BY_BACKEND, disabled).apply()
    }

    private fun clearBackendDisabledMarker(context: Context) {
        if (isPushDisabledByBackend(context)) {
            setPushDisabledByBackend(context, false)
        }
    }

    private fun getDeviceId(context: Context): String? {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
}
