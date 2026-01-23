package com.gridee.parking.utils

import android.content.Context

/**
 * Centralized session helpers to keep JWT storage and legacy SharedPreferences in sync.
 *
 * NOTE: Many parts of the app still read user/session data from "gridee_prefs".
 * JWT tokens are stored in [JwtTokenManager]. This object bridges both.
 */
object AuthSession {

    private const val LEGACY_PREFS_NAME = "gridee_prefs"

    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_PHONE = "user_phone"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_PARKING_LOT_NAME = "parking_lot_name"

    fun isAuthenticated(context: Context): Boolean {
        return JwtTokenManager(context).isAuthenticated()
    }

    fun getUserId(context: Context): String? {
        val jwtManager = JwtTokenManager(context)
        val jwtUserId = if (jwtManager.isAuthenticated()) jwtManager.getUserId() else null
        if (!jwtUserId.isNullOrBlank()) {
            val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val legacy = prefs.getString(KEY_USER_ID, null)
            if (legacy.isNullOrBlank() || legacy != jwtUserId) {
                syncLegacyPrefsFromJwt(context)
            }
            return jwtUserId
        }
        val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, null)
    }

    fun getUserName(context: Context): String? {
        val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = prefs.getString(KEY_USER_NAME, null)
        return legacy ?: JwtTokenManager(context).getUserName()
    }

    fun getUserRole(context: Context): String? {
        val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = prefs.getString(KEY_USER_ROLE, null)
        return legacy ?: JwtTokenManager(context).getUserRole()
    }

    /**
     * Ensures legacy prefs have the minimum user fields required by older screens.
     * Safe to call repeatedly.
     */
    fun syncLegacyPrefsFromJwt(context: Context) {
        val jwtManager = JwtTokenManager(context)
        if (!jwtManager.isAuthenticated()) return

        val userId = jwtManager.getUserId().orEmpty()
        if (userId.isBlank()) return

        val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, jwtManager.getUserName())
            .putString(KEY_USER_ROLE, jwtManager.getUserRole())
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    /**
     * Clears authentication state while keeping unrelated user preferences intact.
     */
    fun clearSession(context: Context) {
        runCatching {
            val authHeader = JwtTokenManager(context).getBearerToken()
            NotificationTokenManager.unregisterCurrentToken(context, authHeader)
        }

        runCatching {
            val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(KEY_USER_ID)
                .remove(KEY_USER_NAME)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_PHONE)
                .remove(KEY_USER_ROLE)
                .remove(KEY_PARKING_LOT_NAME)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .apply()
        }

        runCatching {
            JwtTokenManager(context).clearAuthToken()
        }
    }
}
