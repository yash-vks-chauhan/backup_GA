package com.gridee.parking.data.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.gridee.parking.utils.BackendTimestampParser

/**
 * A merchandising banner served by the backend for a given placement.
 *
 * Ads are always optional decoration: every field except [id] and [imageUrl] may be
 * missing, and a malformed ad is dropped rather than surfaced. Nothing in the parking,
 * booking or wallet flows depends on this model.
 */
data class CustomAd(
    val id: String,
    val placement: String,
    val imageUrl: String,
    val title: String? = null,
    val subtitle: String? = null,
    val ctaText: String? = null,
    val clickUrl: String? = null,
    val startAtMillis: Long? = null,
    val endAtMillis: Long? = null,
    val priority: Int = 0,
    val dismissible: Boolean = true,
    /**
     * "ALL", "ANDROID" or "IOS". The backend already filters; this is the client-side backstop.
     * Nullable because Gson rebuilds cached ads through Unsafe, so a snapshot written by a build
     * that predates this field arrives with it unset rather than defaulted.
     */
    val platform: String? = null,
    /** Tenant scoping, echoed back by the backend. Null means the ad is not lot-scoped. */
    val parkingLotId: String? = null,
    val organizationId: String? = null,
    val locationId: String? = null,
    /** "ONCE_PER_SESSION", "ALWAYS", … — see [isOncePerSession]. */
    val displayFrequency: String? = null,
    /** Server-side counters, carried for debugging and reporting; the client never edits them. */
    val impressions: Long = 0,
    val clicks: Long = 0,
    /** Creative aspect ratio as "W:H" — drives the reserved slot so the card never resizes. */
    val aspectRatio: String? = null
) {
    /**
     * Defensive window check. The backend already filters on startAt/endAt, but a device
     * that keeps a cached ad for a few minutes (or a clock-skewed response) must not show
     * a campaign that has already ended.
     */
    fun isLiveAt(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val startsOk = startAtMillis?.let { nowMillis >= it } ?: true
        val endsOk = endAtMillis?.let { nowMillis < it } ?: true
        return startsOk && endsOk
    }

    /**
     * Backstop for the `platform` query parameter. A cached payload from an older build — or a
     * backend that ignores the filter — must not put an iOS-only creative on an Android screen.
     */
    fun isForAndroid(): Boolean {
        val value = platform?.trim()?.uppercase()
        // Unknown platform means "the payload didn't say", which is not grounds for hiding an ad
        // the backend already selected for this device.
        return value.isNullOrEmpty() || value == PLATFORM_ALL || value == PLATFORM_ANDROID
    }

    /**
     * Show at most once per app session. This is the backend's default, so an ad with no
     * stated frequency is treated as once-per-session rather than as a repeating banner.
     */
    fun isOncePerSession(): Boolean {
        val value = displayFrequency?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_')
        return value.isNullOrEmpty() || value == FREQUENCY_ONCE_PER_SESSION
    }

    companion object {
        const val PLATFORM_ALL = "ALL"
        const val PLATFORM_ANDROID = "ANDROID"
        const val FREQUENCY_ONCE_PER_SESSION = "ONCE_PER_SESSION"
    }
}

/**
 * Tolerant parser for `GET /api/custom-ads/active`.
 *
 * Mirrors [BookingPayloadParser]: the payload may arrive as a bare array, wrapped in
 * `data`/`ads`/`content`, or as a single object, and field names vary between
 * `imageUrl`/`image_url`/`bannerUrl` style spellings. Anything we cannot read is skipped.
 */
object CustomAdPayloadParser {

    private val imageKeys = listOf("imageUrl", "image_url", "imageURL", "image", "bannerUrl", "banner_url", "creativeUrl", "mediaUrl")
    private val clickKeys = listOf("clickUrl", "click_url", "targetUrl", "target_url", "linkUrl", "link", "redirectUrl", "destinationUrl")
    private val titleKeys = listOf("title", "headline", "name")
    private val subtitleKeys = listOf("subtitle", "description", "body", "caption", "subText")
    private val ctaKeys = listOf("ctaText", "cta", "buttonText", "actionText")
    private val startKeys = listOf("startAt", "start_at", "startDate", "startTime", "validFrom")
    private val endKeys = listOf("endAt", "end_at", "endDate", "endTime", "validTo", "expiresAt")
    private val placementKeys = listOf("placement", "slot", "position", "screen")
    private val idKeys = listOf("id", "_id", "adId", "campaignId")
    private val platformKeys = listOf("platform", "targetPlatform", "os")
    private val parkingLotKeys = listOf("parkingLotId", "parking_lot_id", "lotId")
    private val organizationKeys = listOf("organizationId", "organization_id", "orgId")
    private val locationKeys = listOf("locationId", "location_id")
    private val frequencyKeys = listOf("displayFrequency", "display_frequency", "frequency")
    private val impressionKeys = listOf("impressions", "impressionCount", "views")
    private val clickKeysCount = listOf("clicks", "clickCount")

    fun parseAds(payload: JsonElement?, fallbackPlacement: String): List<CustomAd> {
        val elements = extractAdElements(payload) ?: return emptyList()
        return elements
            .mapNotNull { parseAd(it, fallbackPlacement) }
            .sortedByDescending { it.priority }
    }

    private fun extractAdElements(payload: JsonElement?): List<JsonElement>? {
        if (payload == null || payload.isJsonNull) return null
        if (payload.isJsonArray) return payload.asJsonArray.toList()
        if (!payload.isJsonObject) return null

        val obj = payload.asJsonObject
        listOf("ads", "customAds", "data", "content", "items", "results", "records").forEach { key ->
            val value = obj.get(key)
            if (value != null && value.isJsonArray) return value.asJsonArray.toList()
        }
        listOf("data", "payload", "result", "response").forEach { key ->
            val extracted = extractAdElements(obj.get(key))
            if (!extracted.isNullOrEmpty()) return extracted
        }
        return if (looksLikeAd(obj)) listOf(payload) else null
    }

    private fun parseAd(element: JsonElement, fallbackPlacement: String): CustomAd? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        if (!looksLikeAd(obj)) return null

        val imageUrl = obj.firstString(imageKeys)?.takeIf { isLoadableImageUrl(it) } ?: return null
        val id = obj.firstString(idKeys)
            ?: imageUrl.hashCode().toString() // stable enough for per-session dismiss/dedupe

        // An ad the backend explicitly marked inactive is never shown, whatever else it says.
        val active = obj.firstBoolean(listOf("active", "isActive", "enabled")) ?: true
        if (!active) return null

        return CustomAd(
            id = id,
            placement = obj.firstString(placementKeys) ?: fallbackPlacement,
            imageUrl = imageUrl,
            title = obj.firstString(titleKeys),
            subtitle = obj.firstString(subtitleKeys),
            ctaText = obj.firstString(ctaKeys),
            clickUrl = obj.firstString(clickKeys)?.takeIf { isOpenableClickUrl(it) },
            startAtMillis = obj.firstTimestamp(startKeys),
            endAtMillis = obj.firstTimestamp(endKeys),
            priority = obj.firstInt(listOf("priority", "rank", "weight")) ?: 0,
            dismissible = obj.firstBoolean(listOf("dismissible", "isDismissible", "closable")) ?: true,
            platform = obj.firstString(platformKeys)?.trim()?.uppercase(),
            parkingLotId = obj.firstString(parkingLotKeys),
            organizationId = obj.firstString(organizationKeys),
            locationId = obj.firstString(locationKeys),
            displayFrequency = obj.firstString(frequencyKeys),
            impressions = obj.firstLong(impressionKeys) ?: 0L,
            clicks = obj.firstLong(clickKeysCount) ?: 0L,
            aspectRatio = obj.firstString(listOf("aspectRatio", "ratio"))
        )
    }

    private fun looksLikeAd(obj: JsonObject): Boolean = imageKeys.any { obj.has(it) }

    /** Only http(s) creatives — never let a payload point the loader at a local or app scheme. */
    private fun isLoadableImageUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("https://") || lower.startsWith("http://")
    }

    /** Same rule for taps: the ad can open a web page, nothing else. */
    fun isOpenableClickUrl(url: String): Boolean = isLoadableImageUrl(url)

    private fun JsonObject.firstString(keys: List<String>): String? {
        keys.forEach { key ->
            val value = get(key) ?: return@forEach
            if (value.isJsonNull || !value.isJsonPrimitive) return@forEach
            val text = runCatching { value.asString }.getOrNull()?.trim()
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    private fun JsonObject.firstBoolean(keys: List<String>): Boolean? {
        keys.forEach { key ->
            val value = get(key) ?: return@forEach
            if (value.isJsonNull || !value.isJsonPrimitive) return@forEach
            runCatching { value.asBoolean }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun JsonObject.firstInt(keys: List<String>): Int? {
        keys.forEach { key ->
            val value = get(key) ?: return@forEach
            if (value.isJsonNull || !value.isJsonPrimitive) return@forEach
            runCatching { value.asInt }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun JsonObject.firstLong(keys: List<String>): Long? {
        keys.forEach { key ->
            val value = get(key) ?: return@forEach
            if (value.isJsonNull || !value.isJsonPrimitive) return@forEach
            runCatching { value.asLong }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun JsonObject.firstTimestamp(keys: List<String>): Long? {
        val raw = firstString(keys) ?: return null
        // parseToMillis falls back to "now" for unreadable input, which would silently make a
        // window check pass; treat unreadable timestamps as absent instead.
        val parsed = runCatching { BackendTimestampParser.parseToMillis(raw, NOT_A_TIMESTAMP) }.getOrNull()
        return parsed?.takeIf { it != NOT_A_TIMESTAMP }
    }

    private const val NOT_A_TIMESTAMP = Long.MIN_VALUE
}
