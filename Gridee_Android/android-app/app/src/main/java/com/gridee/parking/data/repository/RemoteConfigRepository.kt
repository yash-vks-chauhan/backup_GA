package com.gridee.parking.data.repository

import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.api.ApiService
import com.gridee.parking.data.model.AppRemoteConfig
import com.gridee.parking.data.repository.cache.TtlSingleFlightCache
import kotlinx.coroutines.CancellationException

class RemoteConfigRepository(
    private val apiService: ApiService = ApiClient.apiService,
) {

    suspend fun fetchAppConfig(forceRefresh: Boolean = false): AppRemoteConfig? {
        return configCache.getOrLoad(
            key = CONFIG_KEY,
            ttlMillis = APP_CONFIG_TTL_MILLIS,
            forceRefresh = forceRefresh,
            isCacheable = { it.isSuccess },
            // RemoteConfigManager distinguishes a fresh fetch from its persisted last-known-good
            // config. Returning an expired repository value here would cache it with a new
            // fetched-at timestamp and could re-enable stale maintenance/force-update controls.
            useStaleOnFailure = false,
        ) {
            try {
                val response = apiService.getAppConfig()
                val config = response.body()?.takeIf { response.isSuccessful && it.success }?.data
                config?.let { Result.success(it) }
                    ?: Result.failure(Exception("Unable to load app config (HTTP ${response.code()})"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }.getOrNull()
    }

    companion object {
        const val APP_CONFIG_TTL_MILLIS = 5 * 60 * 1000L
        private const val CONFIG_KEY = "app-config"
        private val configCache = TtlSingleFlightCache<String, Result<AppRemoteConfig>>(1)

        @JvmStatic
        fun invalidateCache() = configCache.invalidate(CONFIG_KEY)
    }
}
