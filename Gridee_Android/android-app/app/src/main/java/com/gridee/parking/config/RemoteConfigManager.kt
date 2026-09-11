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

    // Safe fallbacks for when the backend sends invalid or placeholder config
    // (e.g. a config document saved with the OpenAPI/Swagger example defaults:
    // "string", 0.0, additionalProp*). These guard a live production app against
    // being bricked or degraded by a bad server-side config write. The frontend
    // can never fix the server, so it must refuse to trust obviously-invalid values.
    // Must match PaymentController's defensive fallback when the shared config contains the
    // placeholder 0.0 value; otherwise Android would offer amounts the backend rejects.
    private const val FALLBACK_MIN_TOPUP = 10.0
    private const val FALLBACK_MAX_TOPUP = 50000.0
    private const val FALLBACK_CURRENCY = "INR"
    private const val FALLBACK_CURRENCY_SYMBOL = "₹"
    private const val FALLBACK_TIMEZONE = "Asia/Kolkata"
    private const val FALLBACK_ENVIRONMENT = "PRODUCTION"
    private const val FALLBACK_API_VERSION = "v1"

    private val gson = Gson()
    // Lazy so simply reading config (loadCached/sanitize) never forces the network
    // stack (ApiClient → Application) to initialise; it is built on first refresh().
    private val repository by lazy { RemoteConfigRepository() }

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

    suspend fun refresh(context: Context, forceRefresh: Boolean = false): AppRemoteConfig {
        val appContext = context.applicationContext
        loadCached(appContext)

        return withContext(Dispatchers.IO) {
            val fetched = repository.fetchAppConfig(forceRefresh)
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

    /**
     * The booking-transition interstitial, gated on top of the global AdMob switch rather than
     * instead of it.
     *
     * Operable with no backend deploy: the free-form `featureToggleMap` is consulted before
     * the typed flags, and key normalisation strips case, dashes, underscores and a trailing
     * "Enabled", so any of `bookingTransitionInterstitialEnabled`,
     * `booking_transition_interstitial` or `bookingTransitionInterstitial` in that map turns the
     * placement off.
     */
    fun isBookingTransitionInterstitialEnabled(): Boolean =
        isFeatureEnabled("adMob") && isFeatureEnabled("bookingTransitionInterstitial")

    /**
     * The warm preload buffer behind that placement. Off falls back to the just-in-time load,
     * which is the behaviour that predated the buffer — the placement keeps serving.
     */
    fun isBookingTransitionPreloadBufferEnabled(): Boolean =
        isFeatureEnabled("bookingTransitionPreloadBuffer")

    /**
     * Whether the per-user daily cap on completed rewards is enforced.
     *
     * Note the inverted sense against the placement switches above: off here *removes* a limit
     * rather than removing a placement, so this is the escape hatch if the cap proves to cost
     * more engagement than the price improvement is worth.
     */
    fun isRewardedDailyCapEnabled(): Boolean = isFeatureEnabled("rewardedDailyCap")

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
            "bookingtransitioninterstitial" ->
                currentConfig.features.bookingTransitionInterstitialEnabled
            "bookingtransitionpreloadbuffer" ->
                currentConfig.features.bookingTransitionPreloadBufferEnabled
            "rewardeddailycap" -> currentConfig.features.rewardedDailyCapEnabled
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
        // Operational config (maintenance and force-update included) must be reconciled at least
        // every five minutes while the app is used. The backend-provided value may request a
        // shorter local freshness window, but it must not stretch this Android peak-load TTL to
        // the old 15-minute default (or as far as the accepted 24-hour maximum).
        val configuredTtlMs = max(60L, config.cacheTtlSeconds) * 1000L
        val ttlMs = minOf(configuredTtlMs, RemoteConfigRepository.APP_CONFIG_TTL_MILLIS)
        return System.currentTimeMillis() - fetchedAt > ttlMs
    }

    // internal (not private) so unit tests can exercise the hardening directly.
    internal fun sanitize(config: AppRemoteConfig): AppRemoteConfig {
        config.schemaVersion = max(1, config.schemaVersion)
        config.cacheTtlSeconds = config.cacheTtlSeconds.coerceIn(60L, 86400L)

        config.features.maintenanceTitle = config.features.maintenanceTitle.cleanedOr("We'll be right back")
        config.features.maintenanceMessage = config.features.maintenanceMessage.cleanedOr(
            "Gridee is taking a short break for maintenance. We'll be back in a few minutes."
        )
        // Brick-proofing: never let the server force this build to update to a
        // version newer than the one actually running. A bad config (e.g.
        // minAndroidVersionCode set higher than any published build) would
        // otherwise trigger the non-dismissible force-update gate on the splash
        // screen and lock every user out. Genuine forced updates are delegated to
        // Google Play In-App Updates (InAppUpdateController), which only fire when
        // a newer build truly exists on the Play Store.
        config.versions.minAndroidVersionCode = config.versions.minAndroidVersionCode
            .coerceIn(1, max(1, BuildConfig.VERSION_CODE))
        config.versions.latestAndroidVersionCode = max(
            config.versions.minAndroidVersionCode,
            config.versions.latestAndroidVersionCode
        )
        config.versions.androidUpdateMessage = config.versions.androidUpdateMessage.ifBlank {
            "Please update to the latest version."
        }
        config.financial.welcomeBonusAmount = max(0.0, config.financial.welcomeBonusAmount)
        // Guard wallet top-up bounds: a zero/placeholder or inverted range would
        // block every top-up (isAmountAllowed: amount in min..max). We only floor
        // the *bounds* here, never fabricate money-charging values (penalties,
        // refunds) — those stay server-controlled so we can't overcharge a user.
        if (config.financial.minWalletTopUpAmount <= 0.0) {
            config.financial.minWalletTopUpAmount = FALLBACK_MIN_TOPUP
        }
        if (config.financial.maxWalletTopUpAmount <= config.financial.minWalletTopUpAmount) {
            config.financial.maxWalletTopUpAmount =
                max(FALLBACK_MAX_TOPUP, config.financial.minWalletTopUpAmount)
        }
        // Placeholder guard for platform strings. currency in particular is passed
        // straight into the payment checkout options, so "string" must never reach it.
        config.platform.currency = config.platform.currency.cleanedOr(FALLBACK_CURRENCY)
        config.platform.currencySymbol = config.platform.currencySymbol.cleanedOr(FALLBACK_CURRENCY_SYMBOL)
        config.platform.timezone = config.platform.timezone.cleanedOr(FALLBACK_TIMEZONE)
        config.platform.environment = config.platform.environment.cleanedOr(FALLBACK_ENVIRONMENT)
        config.platform.apiVersion = config.platform.apiVersion.cleanedOr(FALLBACK_API_VERSION)
        config.booking.maxConcurrentBookingsPerUser = max(1, config.booking.maxConcurrentBookingsPerUser)
        config.booking.minBookingDurationMinutes = max(1, config.booking.minBookingDurationMinutes)
        config.booking.maxBookingDurationHours = max(
            MIN_FULL_DAY_BOOKING_DURATION_HOURS,
            config.booking.maxBookingDurationHours
        )
        config.booking.noShowGraceMinutes = max(0, config.booking.noShowGraceMinutes)
        // A zero/negative window would imply the backend finalizes an overdue checkout instantly,
        // which would make any countdown the app derives from it nonsensical. Note we deliberately
        // do NOT clamp overdueCheckoutPenaltyPercentage or maxLateCheckoutPenaltyPerMin: those are
        // money-charging values and stay server-controlled, per the rule above. In particular
        // maxLateCheckoutPenaltyPerMin == 0.0 legitimately means "no cap".
        config.booking.autoFinalizeOverdueCheckoutMinutes =
            max(1, config.booking.autoFinalizeOverdueCheckoutMinutes)
        return config
    }
}
