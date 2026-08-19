package com.gridee.parking.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gridee.parking.data.model.CustomAd

/**
 * Last-known ad payload per placement, persisted so Home paints an ad instantly on open and
 * refreshes at most once per [FRESH_TTL_MS]. Mirrors [WalletCache]. Cleared on logout via
 * [AuthSession.clearSession].
 *
 * Ads are pure decoration, so a cached entry is also the failure path: if the refresh errors
 * we keep showing what we already had until it goes hard-stale.
 */
object CustomAdCache {

    private const val PREFS_NAME = "gridee_custom_ads_cache"
    private const val KEY_PREFIX_ADS = "ads_"
    private const val KEY_PREFIX_UPDATED_AT = "updated_at_"

    /** Within this window we serve the cache and skip the network entirely. */
    const val FRESH_TTL_MS = 15L * 60 * 1000

    /** Past this the snapshot is dropped rather than shown, even if a refresh fails. */
    private const val MAX_AGE_MS = 6L * 60 * 60 * 1000

    private val gson = Gson()
    private val adListType = object : TypeToken<List<CustomAd>>() {}.type

    data class Entry(
        val ads: List<CustomAd>,
        val ageMs: Long,
        val updatedAtMillis: Long
    ) {
        val isFresh: Boolean get() = ageMs <= FRESH_TTL_MS
    }

    fun cacheKey(placement: String, parkingLotId: String?): String =
        "${placement.trim().uppercase()}|${parkingLotId?.trim().orEmpty().ifEmpty { "-" }}"

    fun get(context: Context, cacheKey: String): Entry? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updatedAt = prefs.getLong(KEY_PREFIX_UPDATED_AT + cacheKey, 0L)
        if (updatedAt <= 0L) return null

        val ageMs = System.currentTimeMillis() - updatedAt
        // Negative age means the device clock moved backwards — treat as stale, not fresh forever.
        if (ageMs < 0 || ageMs > MAX_AGE_MS) return null

        val json = prefs.getString(KEY_PREFIX_ADS + cacheKey, null) ?: return null
        val ads = runCatching {
            gson.fromJson<List<CustomAd>>(json, adListType)
        }.getOrNull() ?: return null

        return Entry(ads, ageMs, updatedAt)
    }

    /** An empty list is cached too — "no ad right now" is an answer worth remembering. */
    fun save(context: Context, cacheKey: String, ads: List<CustomAd>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX_ADS + cacheKey, gson.toJson(ads))
            .putLong(KEY_PREFIX_UPDATED_AT + cacheKey, System.currentTimeMillis())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
