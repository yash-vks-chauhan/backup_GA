package com.gridee.parking.utils

import android.content.Context
import com.gridee.parking.data.model.User

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
    private const val KEY_PARKING_LOT_ID = "parking_lot_id"
    private const val KEY_PARKING_LOT_NAME = "parking_lot_name"
    // Tenant of the selected lot. Only known once the user picks a lot that carries it;
    // callers must treat both as optional and let the backend resolve them when absent.
    private const val KEY_ORGANIZATION_ID = "organization_id"
    private const val KEY_LOCATION_ID = "location_id"

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

    fun getParkingLotId(context: Context): String? {
        val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = prefs.getString(KEY_PARKING_LOT_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (legacy != null) return legacy

        val userId = getUserId(context)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return UserProfileCache.get(context, userId)
            ?.parkingLotId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun getParkingLotName(context: Context): String? {
        val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = prefs.getString(KEY_PARKING_LOT_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (legacy != null) return legacy

        val userId = getUserId(context)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return UserProfileCache.get(context, userId)
            ?.parkingLotName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** Organization owning the selected lot, when the backend told us which one it is. */
    fun getOrganizationId(context: Context): String? =
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORGANIZATION_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Location (campus/site) of the selected lot, when known. */
    fun getLocationId(context: Context): String? =
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCATION_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /**
     * Persists the user's selected parking lot locally so lot-scoped screens pick it
     * up immediately, without waiting for a profile refresh. [getParkingLotId] reads
     * legacy prefs first, so writing here is enough for filtering to take effect.
     * The authoritative copy is saved server-side via UserRepository.assignParkingLot.
     *
     * [organizationId] and [locationId] ride along so payment initiation can name the
     * tenant it is topping up against. They are cleared when the caller does not know
     * them, so a stale tenant from a previous lot never leaks into a new one.
     */
    fun saveParkingLot(
        context: Context,
        lotId: String?,
        lotName: String?,
        organizationId: String? = null,
        locationId: String? = null
    ) {
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PARKING_LOT_ID, lotId?.trim()?.takeIf { it.isNotEmpty() })
            .putString(KEY_PARKING_LOT_NAME, lotName?.trim()?.takeIf { it.isNotEmpty() })
            .putString(KEY_ORGANIZATION_ID, organizationId?.trim()?.takeIf { it.isNotEmpty() })
            .putString(KEY_LOCATION_ID, locationId?.trim()?.takeIf { it.isNotEmpty() })
            .apply()
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

    fun updateCachedUserProfile(context: Context, user: User) {
        val userId = user.id?.trim().orEmpty()
        if (userId.isEmpty()) return

        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_PHONE, user.phone)
            .putString(KEY_USER_ROLE, user.role)
            .putString(KEY_PARKING_LOT_ID, user.parkingLotId)
            .putString(KEY_PARKING_LOT_NAME, user.parkingLotName)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()

        UserProfileCache.save(context, user)
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
                .remove(KEY_PARKING_LOT_ID)
                .remove(KEY_PARKING_LOT_NAME)
                .remove(KEY_ORGANIZATION_ID)
                .remove(KEY_LOCATION_ID)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .apply()
        }

        runCatching {
            JwtTokenManager(context).clearAuthToken()
        }

        runCatching {
            UserProfileCache.clear(context)
        }

        runCatching {
            WalletCache.clear(context)
        }

        runCatching {
            // Ads are lot-scoped; the next user must not inherit the previous lot's creatives.
            CustomAdCache.clear(context)
            CustomAdSession.reset()
        }
    }
}
