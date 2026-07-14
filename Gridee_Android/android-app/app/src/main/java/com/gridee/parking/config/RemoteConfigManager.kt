package com.gridee.parking.config

import android.content.Context
import com.google.gson.Gson
import com.gridee.parking.BuildConfig
import com.gridee.parking.data.model.AppRemoteConfig
import com.gridee.parking.data.repository.RemoteConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

object RemoteConfigManager {

    private const val PREFS_NAME = "gridee_remote_config"
    private const val KEY_CONFIG_JSON = "config_json"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val DEFAULT_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.gridee.parking"
    private const val MIN_FULL_DAY_BOOKING_DURATION_HOURS = 9

    private val gson = Gson()
    private val repository = RemoteConfigRepository()

    @Volatile
    var currentConfig: AppRemoteConfig = AppRemoteConfig()
        private set

    fun loadCached(context: Context): AppRemoteConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString(KEY_CONFIG_JSON, null)
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)

        val cached = runCatching {
            if (cachedJson.isNullOrBlank()) null else gson.fromJson(cachedJson, AppRemoteConfig::class.java)
        }.getOrNull()

        currentConfig = sanitize(cached ?: currentConfig)

        // Never let stale cached operational blocks trap users when the backend cannot be reached.
        if (isCacheExpired(currentConfig, fetchedAt)) {
            currentConfig.features.maintenanceMode = false
            currentConfig.versions.forceAndroidUpdate = false
        }

        return currentConfig
    }

    suspend fun refresh(context: Context): AppRemoteConfig {
        val appContext = context.applicationContext
        loadCached(appContext)

        return withContext(Dispatchers.IO) {
            val fetched = repository.fetchAppConfig()
            if (fetched != null) {
                val sanitized = sanitize(fetched)
                currentConfig = sanitized
                cache(appContext, sanitized)
                sanitized
            } else {
                currentConfig
            }
        }
    }

    suspend fun refreshIfStale(context: Context): AppRemoteConfig {
        val appContext = context.applicationContext
        loadCached(appContext)
        return if (isCacheStale(appContext)) refresh(appContext) else currentConfig
    }

    fun isCacheStale(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return isCacheExpired(currentConfig, prefs.getLong(KEY_FETCHED_AT, 0L))
    }

    fun areNotificationsEnabled(): Boolean {
        return isFeatureEnabled("notifications") && isFeatureEnabled("pushNotifications")
    }

    fun isWalletEnabled(): Boolean = isFeatureEnabled("wallet")

    fun isBookingEnabled(): Boolean = isFeatureEnabled("booking")

    fun isEmailSignInEnabled(): Boolean = isFeatureEnabled("emailSignIn")

    fun isGoogleSignInEnabled(): Boolean = isFeatureEnabled("googleSignIn")

    fun shouldShowHomeSlotFilters(): Boolean {
        return runCatching { currentConfig.home.showSlotFilters }.getOrDefault(true)
    }

    fun isMaintenanceMode(): Boolean {
        return currentConfig.features.maintenanceMode
    }

    fun isForceUpdateRequired(): Boolean {
        val versions = currentConfig.versions
        return AndroidVersionPolicy.isBelowMinimum(
            currentVersionCode = BuildConfig.VERSION_CODE,
            currentVersionName = BuildConfig.VERSION_NAME,
            versions = versions,
        )
    }

    fun isRecommendedUpdateAvailable(): Boolean {
        val versions = currentConfig.versions
        return versions.recommendedAndroidUpdate ||
                AndroidVersionPolicy.isBelowLatest(
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    versions = versions,
                )
    }

    fun getAndroidUpdateMessage(): String {
        return currentConfig.versions.androidUpdateMessage.ifBlank {
            "Please update to the latest version."
        }
    }

    fun getAndroidPlayStoreUrl(): String {
        return currentConfig.versions.androidPlayStoreUrl.ifBlank { DEFAULT_PLAY_STORE_URL }
    }

    fun isFeatureEnabled(featureName: String): Boolean {
        featureToggleOverride(featureName)?.let { return it }
        return typedFeatureFlag(featureName) ?: true
    }

    private fun featureToggleOverride(featureName: String): Boolean? {
        val normalized = normalizeFeatureKey(featureName)
        currentConfig.features.featureToggleMap[featureName]?.let { return it }
        currentConfig.features.featureToggleMap[normalized]?.let { return it }
        return currentConfig.features.featureToggleMap.entries
            .firstOrNull { normalizeFeatureKey(it.key) == normalized }
            ?.value
    }

    private fun typedFeatureFlag(featureName: String): Boolean? {
        return when (normalizeFeatureKey(featureName)) {
            "applesignin" -> currentConfig.features.appleSignInEnabled
            "googlesignin" -> currentConfig.features.googleSignInEnabled
            "emailsignin" -> currentConfig.features.emailSignInEnabled
            "wallet" -> currentConfig.features.walletFeatureEnabled
            "booking" -> currentConfig.features.bookingFeatureEnabled
            "notifications" -> currentConfig.features.notificationsEnabled
            "pushnotifications" -> currentConfig.features.pushNotificationsEnabled
            "emailnotifications" -> currentConfig.features.emailNotificationsEnabled
            "sequentialbooking" -> currentConfig.features.sequentialBookingEnabled
            "multiplebookings", "multiplebookingsallowed" -> currentConfig.features.multipleBookingsAllowed
            "maintenance", "maintenancemode" -> currentConfig.features.maintenanceMode
            "locationtracking" -> currentConfig.features.locationTrackingEnabled
            "ratelimiting" -> currentConfig.features.rateLimitingEnabled
            "admob" -> currentConfig.features.adMobEnabled
            "rewards" -> currentConfig.features.rewardsEnabled
            else -> null
        }
    }

    /**
     * Returns [fallback] when the value is blank or a known placeholder. Guards
     * against config saved with the OpenAPI/Swagger default ("string"), which
     * would otherwise surface verbatim to users on the maintenance screen.
     */
    private fun String?.cleanedOr(fallback: String): String {
        val value = this?.trim().orEmpty()
        val placeholder = value.equals("string", ignoreCase = true) ||
                value.equals("null", ignoreCase = true)
        return if (value.isEmpty() || placeholder) fallback else value
    }

    private fun normalizeFeatureKey(featureName: String): String {
        val key = featureName
            .trim()
            .replace("-", "")
            .replace("_", "")
            .lowercase(Locale.ROOT)
        return when {
            key.endsWith("featureenabled") -> key.removeSuffix("featureenabled")
            key.endsWith("enabled") -> key.removeSuffix("enabled")
            else -> key
        }
    }

    private fun cache(context: Context, config: AppRemoteConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG_JSON, gson.toJson(config))
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun isCacheExpired(config: AppRemoteConfig, fetchedAt: Long): Boolean {
        if (fetchedAt <= 0) return true
        val ttlMs = max(60L, config.cacheTtlSeconds) * 1000L
        return System.currentTimeMillis() - fetchedAt > ttlMs
    }

    private fun sanitize(config: AppRemoteConfig): AppRemoteConfig {
        config.schemaVersion = max(1, config.schemaVersion)
        config.cacheTtlSeconds = config.cacheTtlSeconds.coerceIn(60L, 86400L)

        config.features.maintenanceTitle = config.features.maintenanceTitle.cleanedOr("We'll be right back")
        config.features.maintenanceMessage = config.features.maintenanceMessage.cleanedOr(
            "Gridee is taking a short break for maintenance. We'll be back in a few minutes."
        )
        config.versions.minAndroidVersionCode = max(1, config.versions.minAndroidVersionCode)
        config.versions.latestAndroidVersionCode = max(
            config.versions.minAndroidVersionCode,
            config.versions.latestAndroidVersionCode
        )
        config.versions.androidUpdateMessage = config.versions.androidUpdateMessage.ifBlank {
            "Please update to the latest version."
        }
        config.financial.welcomeBonusAmount = max(0.0, config.financial.welcomeBonusAmount)
        config.booking.maxConcurrentBookingsPerUser = max(1, config.booking.maxConcurrentBookingsPerUser)
        config.booking.minBookingDurationMinutes = max(1, config.booking.minBookingDurationMinutes)
        config.booking.maxBookingDurationHours = max(
            MIN_FULL_DAY_BOOKING_DURATION_HOURS,
            config.booking.maxBookingDurationHours
        )
        config.booking.noShowGraceMinutes = max(0, config.booking.noShowGraceMinutes)
        return config
    }
}
