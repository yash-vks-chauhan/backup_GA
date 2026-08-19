package com.gridee.parking.data.repository

import android.content.Context
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.CustomAd
import com.gridee.parking.data.model.CustomAdPayloadParser
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.CustomAdCache
import com.gridee.parking.utils.CustomAdSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Ads for a placement, served stale-while-revalidate from [CustomAdCache].
 *
 * Every path here is non-fatal by design: a disabled feature flag, a missing endpoint, a
 * parse failure or a dead network all resolve to "no ad" (or the last cached ad) and never
 * surface an error to the screen hosting the banner.
 */
class CustomAdsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val apiService = ApiClient.apiService

    /**
     * Fetches the ads for [placement], returning the cached list when it is still fresh.
     *
     * @param forceRefresh skips the freshness check (used on app foreground), but still
     *   falls back to the cached list if the call fails.
     */
    suspend fun getAds(
        placement: String,
        forceRefresh: Boolean = false,
        includePreviouslyDisplayed: Boolean = false
    ): List<CustomAd> {
        if (!isAdsFeatureEnabled()) return emptyList()
        // The endpoint is authenticated; before login there is nothing to ask for.
        if (!AuthSession.isAuthenticated(appContext)) return emptyList()

        val parkingLotId = AuthSession.getParkingLotId(appContext)
        val cacheKey = CustomAdCache.cacheKey(placement, parkingLotId)
        val cached = CustomAdCache.get(appContext, cacheKey)

        if (!forceRefresh && cached != null && cached.isFresh) {
            return cached.ads.visibleNow(includePreviouslyDisplayed)
        }

        val fetched = fetchLocked(
            cacheKey = cacheKey,
            placement = placement,
            parkingLotId = parkingLotId,
            forceRefresh = forceRefresh,
            cachedUpdatedAtMillis = cached?.updatedAtMillis
        )
        return (fetched ?: cached?.ads.orEmpty()).visibleNow(includePreviouslyDisplayed)
    }

    /** Cache-only read, for painting before a refresh completes. */
    fun getCachedAds(
        placement: String,
        includePreviouslyDisplayed: Boolean = false
    ): List<CustomAd> {
        if (!isAdsFeatureEnabled()) return emptyList()
        val cacheKey = CustomAdCache.cacheKey(placement, AuthSession.getParkingLotId(appContext))
        return CustomAdCache.get(appContext, cacheKey)?.ads.orEmpty()
            .visibleNow(includePreviouslyDisplayed)
    }

    /**
     * One request per placement at a time: Home's foreground refresh and its first paint can
     * both ask at once, and the server should see one call, not two.
     */
    private suspend fun fetchLocked(
        cacheKey: String,
        placement: String,
        parkingLotId: String?,
        forceRefresh: Boolean,
        cachedUpdatedAtMillis: Long?
    ): List<CustomAd>? {
        val mutex = mutexFor(cacheKey)
        return mutex.withLock {
            // A request that queued behind another one is served by what that one just stored.
            CustomAdCache.get(appContext, cacheKey)?.takeIf { latest ->
                latest.isFresh && (
                    !forceRefresh ||
                        cachedUpdatedAtMillis == null ||
                        latest.updatedAtMillis > cachedUpdatedAtMillis
                    )
            }?.let { return@withLock it.ads }
            fetch(placement, parkingLotId)?.also { CustomAdCache.save(appContext, cacheKey, it) }
        }
    }

    private suspend fun fetch(placement: String, parkingLotId: String?): List<CustomAd>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = apiService.getActiveCustomAds(
                    placement = placement,
                    platform = PLATFORM_ANDROID,
                    parkingLotId = parkingLotId
                )
                if (!response.isSuccessful) return@runCatching null
                CustomAdPayloadParser.parseAds(response.body(), placement)
                    .filter { it.placement.equalsIgnoreCase(placement) }
            }.getOrNull()
        }

    /**
     * Impression ping, fired once per ad per session. Fire-and-forget on an application-scoped
     * coroutine: the caller is a view that may be gone by the time this lands, and a backend
     * without the endpoint simply 404s into the void.
     */
    fun trackImpression(ad: CustomAd) {
        if (!CustomAdSession.markImpressionSent(ad.id)) return
        trackingScope.launch {
            runCatching { apiService.trackCustomAdImpression(ad.id) }
        }
    }

    fun trackClick(ad: CustomAd) {
        trackingScope.launch {
            runCatching { apiService.trackCustomAdClick(ad.id) }
        }
    }

    /**
     * Marks the creative as having had its turn this session. Separate from [trackImpression]
     * because the two answer different questions: the impression ping is analytics the backend
     * counts, this is the local rule that stops a ONCE_PER_SESSION ad reappearing.
     */
    fun markDisplayed(ad: CustomAd) {
        CustomAdSession.markDisplayed(ad.id)
    }

    private fun isAdsFeatureEnabled(): Boolean {
        RemoteConfigManager.loadCached(appContext)
        // Unknown flags default to enabled, so this is a backend kill-switch rather than a gate.
        return RemoteConfigManager.isFeatureEnabled(FEATURE_FLAG)
    }

    /**
     * Drops anything the user should not see right now: ads outside their own start/end window,
     * ones the user closed this session, creatives targeted at another platform, and
     * once-per-session ads that have already had their turn.
     */
    private fun List<CustomAd>.visibleNow(includePreviouslyDisplayed: Boolean): List<CustomAd> {
        val now = System.currentTimeMillis()
        return filter { ad ->
            ad.isLiveAt(now) &&
                ad.isForAndroid() &&
                !CustomAdSession.isDismissed(ad.id) &&
                (includePreviouslyDisplayed ||
                    !(ad.isOncePerSession() && CustomAdSession.hasBeenDisplayed(ad.id)))
        }
    }

    private fun String.equalsIgnoreCase(other: String) = equals(other, ignoreCase = true)

    companion object {
        const val PLATFORM_ANDROID = "ANDROID"
        private const val FEATURE_FLAG = "customAds"

        private val trackingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val mutexes = mutableMapOf<String, Mutex>()

        @Synchronized
        private fun mutexFor(cacheKey: String): Mutex = mutexes.getOrPut(cacheKey) { Mutex() }
    }
}
