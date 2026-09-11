package com.gridee.parking.ui.operator

import android.content.Context
import android.os.SystemClock
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.gridee.parking.data.api.BackendGsonFactory
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.model.BookingPolicyResolver
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.UserProfileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response

object OperatorParkingSpotLoader {
    private const val PREFS_NAME = "gridee_prefs"
    private const val KEY_PARKING_LOT_ID = "parking_lot_id"
    private const val KEY_PARKING_LOT_NAME = "parking_lot_name"
    private const val KEY_LAST_SELECTED_SPOT_ID = "operator_last_selected_spot_id"
    private const val KEY_LAST_SELECTED_LOT_ID = "operator_last_selected_lot_id"
    private const val CACHE_TTL_MS = 30_000L
    private const val STALE_CACHE_MAX_AGE_MS = 5 * 60_000L
    private const val MAX_CACHE_ENTRIES = 16
    private val gson = BackendGsonFactory.gson
    private val loadMutex = Mutex()
    private val cacheLock = Any()
    private val resultCache = LinkedHashMap<String, CachedLoadResult>()

    data class LoadResult(
        val spots: List<ParkingSpot>,
        val emptyMessage: String? = null,
        val retryable: Boolean = false,
        internal val allowStaleFallback: Boolean = false
    )

    internal enum class EmptyReason {
        NO_LOT_ASSIGNMENT,
        NO_ACTIVE_SPOTS,
        ACCESS_DENIED,
        LOAD_FAILED
    }

    internal data class AttemptSummary(
        val successful: Boolean,
        val code: Int? = null,
        val message: String? = null,
        val failedWithException: Boolean = false
    )

    suspend fun load(
        context: Context,
        parkingRepository: ParkingRepository,
        userRepository: UserRepository = UserRepository()
    ): LoadResult = withContext(Dispatchers.IO) {
        // A warm result avoids both the profile repair lookup and every spot-list call.
        val quickCacheKey = currentCacheKey(context)
        getCached(quickCacheKey)?.let { return@withContext it }

        loadMutex.withLock {
            getCached(currentCacheKey(context))?.let { return@withLock it }

            val lotContext = resolveAndRepairOperatorLotContext(
                context = context,
                parkingRepository = parkingRepository,
                userRepository = userRepository,
            )
            val assignedLotId = lotContext.id
            if (assignedLotId.isNullOrBlank()) {
                return@withLock LoadResult(
                    spots = emptyList(),
                    emptyMessage = "Your operator account is not assigned to a parking lot. Ask an administrator to assign it, then try again.",
                    retryable = true
                )
            }

            val cacheKey = cacheKey(AuthSession.getUserId(context), assignedLotId)
            getCached(cacheKey)?.let { return@withLock it }

            val staleResult = getCached(cacheKey, allowStale = true)
            loadAssignedLot(
                context = context,
                parkingRepository = parkingRepository,
                lotContext = lotContext,
            ).let { result ->
                if (result.allowStaleFallback && staleResult != null) {
                    return@withLock staleResult
                }
                // Cache authoritative data and authoritative empty lists. Transient failures and
                // access errors remain retryable and must not poison the cache.
                if (result.spots.isNotEmpty() || !result.retryable) {
                    putCached(cacheKey, result)
                }
                result
            }
        }
    }

    /**
     * Refreshes the exact operator-scoped selected-lot source used by the scanner and dashboard.
     * This bypasses both cache layers after a successful mutation, but keeps the last known list
     * available if the refresh fails transiently.
     */
    suspend fun refreshSelectedLot(
        context: Context,
        parkingRepository: ParkingRepository,
        parkingLotId: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val normalizedLotId = parkingLotId.validPreferenceValue() ?: return@withContext false
        val cacheKey = cacheKey(AuthSession.getUserId(context), normalizedLotId)

        loadMutex.withLock {
            val result = loadAssignedLot(
                context = context,
                parkingRepository = parkingRepository,
                lotContext = OperatorLotContext(
                    id = normalizedLotId,
                    name = AuthSession.getParkingLotName(context),
                ),
                forceRefresh = true,
            )

            // Only an authoritative response replaces the operator-visible list. Keeping the
            // prior entry on a transient failure lets the next selector show cached data.
            if (result.spots.isNotEmpty() || !result.retryable) {
                putCached(cacheKey, result)
            } else if (!result.allowStaleFallback) {
                // Authentication/access failures must never leave an older authorized list warm.
                invalidate(normalizedLotId)
            }
            !result.retryable
        }
    }

    /** Invalidate the selected-lot list after a successful operator mutation. */
    fun invalidate(parkingLotId: String?) {
        val normalizedLotId = parkingLotId.validPreferenceValue() ?: return
        synchronized(cacheLock) {
            val suffix = "|$normalizedLotId"
            resultCache.keys.removeAll { it.endsWith(suffix) }
        }
    }

    private suspend fun loadAssignedLot(
        context: Context,
        parkingRepository: ParkingRepository,
        lotContext: OperatorLotContext,
        forceRefresh: Boolean = false,
    ): LoadResult {
        val assignedLotId = requireNotNull(lotContext.id)
        val attempts = mutableListOf<SpotLoadAttempt>()

        val policyResponse = try {
            parkingRepository.getBookingPolicy(assignedLotId, forceRefresh = forceRefresh)
        } catch (error: Exception) {
            return LoadResult(
                spots = emptyList(),
                emptyMessage = "Parking-lot validation rules could not be loaded. Check your connection and try again.",
                retryable = true,
                allowStaleFallback = true,
            )
        }
        val policy = policyResponse.body()
        if (!policyResponse.isSuccessful || policy == null) {
            return LoadResult(
                spots = emptyList(),
                emptyMessage = "Parking-lot validation rules are unavailable. Try again.",
                retryable = true,
                allowStaleFallback = policyResponse.code() >= 500,
            )
        }
        val resolvedPolicy = runCatching { BookingPolicyResolver.resolve(policy) }.getOrNull()
            ?: return LoadResult(
                spots = emptyList(),
                emptyMessage = "Parking-lot validation rules are incomplete. Try again.",
                retryable = true,
            )
        if (!resolvedPolicy.supportsOperatorValidation) {
            return LoadResult(
                spots = emptyList(),
                emptyMessage = "Operator validation is disabled for ${lotContext.name ?: "this parking lot"}.",
                retryable = false,
            )
        }

        attempts += fetchAttempt {
            parkingRepository.getOperatorParkingSpotsForLotPayload(
                assignedLotId,
                forceRefresh = forceRefresh,
            )
        }
        attempts.last().takeIf { it.successful }?.let { attempt ->
            return successfulResult(attempt.spots, assignedLotId, lotContext.name)
        }

        // Alternate routes are compatibility fallbacks, not retries for an overloaded or
        // unreachable backend. Only an explicit route-not-supported response may use them.
        if (attempts.last().canTryAlternateRoute()) {
            attempts += fetchAttempt {
                parkingRepository.getOperatorParkingSpotsPayload(forceRefresh = forceRefresh)
            }
            attempts.last().takeIf { it.successful }?.let { attempt ->
                return successfulResult(attempt.spots, assignedLotId, lotContext.name)
            }
        }

        if (attempts.last().canTryAlternateRoute()) {
            attempts += fetchAttempt {
                parkingRepository.getParkingSpotsByLotPayload(
                    assignedLotId,
                    forceRefresh = forceRefresh,
                )
            }
            attempts.last().takeIf { it.successful }?.let { attempt ->
                return successfulResult(attempt.spots, assignedLotId, lotContext.name)
            }
        }

        // A single selected-spot lookup is retained only for old servers that do not expose any
        // of the list routes. Network/429/5xx failures never fan out into more immediate calls.
        if (attempts.isNotEmpty() && attempts.all(SpotLoadAttempt::canTryAlternateRoute)) {
            val lastSelectedSpot = fetchLastSelectedSpot(
                context,
                parkingRepository,
                attempts,
                forceRefresh,
                assignedLotId,
            )
            if (lastSelectedSpot != null) {
                return successfulResult(
                    listOf(lastSelectedSpot),
                    assignedLotId,
                    lotContext.name
                )
            }
        }

        val reason = classifyEmptyResult(
            attempts = attempts.map(SpotLoadAttempt::summary),
            hasLotContext = true
        )
        val message = when (reason) {
            EmptyReason.NO_LOT_ASSIGNMENT ->
                "Your operator account is not assigned to a parking lot. Ask an administrator to assign it, then try again."
            EmptyReason.NO_ACTIVE_SPOTS ->
                "No active parking spots are configured for ${lotContext.name ?: "your assigned parking lot"}."
            EmptyReason.ACCESS_DENIED ->
                attempts.firstNotNullOfOrNull { attempt ->
                    attempt.message.takeIf { attempt.code == 403 }
                } ?: "Your operator account does not have access to this parking lot."
            EmptyReason.LOAD_FAILED ->
                "Parking spots could not be loaded. Check your connection and tap to try again."
        }
        return LoadResult(
            spots = emptyList(),
            emptyMessage = message,
            retryable = reason != EmptyReason.NO_ACTIVE_SPOTS,
            allowStaleFallback = canUseStaleFallback(attempts.map(SpotLoadAttempt::summary))
        )
    }

    private fun currentCacheKey(context: Context): String? {
        val userId = AuthSession.getUserId(context)
        val cachedUser = userId?.let { UserProfileCache.get(context, it) }
        val lotId = cachedUser?.parkingLotId.validPreferenceValue()
            ?: AuthSession.getParkingLotId(context).validPreferenceValue()
        return cacheKey(userId, lotId)
    }

    private fun cacheKey(userId: String?, parkingLotId: String?): String? {
        val normalizedUserId = userId.validPreferenceValue() ?: return null
        val normalizedLotId = parkingLotId.validPreferenceValue() ?: return null
        return "$normalizedUserId|$normalizedLotId"
    }

    private fun getCached(key: String?, allowStale: Boolean = false): LoadResult? {
        if (key == null) return null
        val now = SystemClock.elapsedRealtime()
        return synchronized(cacheLock) {
            val entry = resultCache[key] ?: return@synchronized null
            val ageMs = now - entry.storedAtElapsedRealtimeMs
            if (ageMs < 0L || ageMs >= STALE_CACHE_MAX_AGE_MS) {
                resultCache.remove(key)
                null
            } else if (!allowStale && ageMs >= CACHE_TTL_MS) {
                null
            } else {
                entry.result
            }
        }
    }

    private fun putCached(key: String?, result: LoadResult) {
        if (key == null) return
        synchronized(cacheLock) {
            resultCache[key] = CachedLoadResult(
                result = result,
                storedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
            )
            while (resultCache.size > MAX_CACHE_ENTRIES) {
                val oldestKey = resultCache.entries.minByOrNull {
                    it.value.storedAtElapsedRealtimeMs
                }?.key ?: break
                resultCache.remove(oldestKey)
            }
        }
    }

    private suspend fun resolveAndRepairOperatorLotContext(
        context: Context,
        parkingRepository: ParkingRepository,
        userRepository: UserRepository,
    ): OperatorLotContext {
        val userId = AuthSession.getUserId(context)
        val cachedUser = userId?.let { UserProfileCache.get(context, it) }
        val freshUser = userId?.let { id ->
            runCatching { userRepository.getUserById(id) }
                .getOrNull()
        }
        if (freshUser != null) {
            AuthSession.updateCachedUserProfile(context, freshUser)
        }

        val profile = freshUser ?: cachedUser
        var lotId = profile?.parkingLotId.validPreferenceValue()
            ?: AuthSession.getParkingLotId(context).validPreferenceValue()
        var lotName = profile?.parkingLotName.validPreferenceValue()
            ?: AuthSession.getParkingLotName(context).validPreferenceValue()

        // Older operator accounts may contain only parkingLotName. Resolve that
        // unambiguous name and persist the canonical ID on the operator's own profile
        // so the strict lot-scoped backend can authorize this and future requests.
        if (lotId.isNullOrBlank() && !lotName.isNullOrBlank() && !userId.isNullOrBlank()) {
            val resolvedLot = resolveLotFromAllLots(parkingRepository, lotName)
            if (resolvedLot != null) {
                val repairResponse = runCatching {
                    userRepository.assignParkingLot(
                        userId,
                        resolvedLot.id,
                        resolvedLot.name ?: lotName
                    )
                }.getOrNull()

                if (repairResponse?.isSuccessful == true) {
                    lotId = resolvedLot.id
                    lotName = resolvedLot.name ?: lotName
                    AuthSession.saveParkingLot(context, lotId, lotName)
                    profile?.copy(parkingLotId = lotId, parkingLotName = lotName)
                        ?.let { AuthSession.updateCachedUserProfile(context, it) }
                }
            }
        }

        return OperatorLotContext(lotId, lotName)
    }

    private suspend fun fetchAttempt(
        request: suspend () -> Response<JsonElement>
    ): SpotLoadAttempt {
        return try {
            val response = request()
            if (!response.isSuccessful) {
                val backendMessage = response.readBackendErrorMessage()
                SpotLoadAttempt(
                    code = response.code(),
                    message = backendMessage
                )
            } else {
                val spots = parseParkingSpots(response.body())
                SpotLoadAttempt(
                    successful = true,
                    spots = spots
                )
            }
        } catch (error: Exception) {
            SpotLoadAttempt(failedWithException = true, message = error.message)
        }
    }

    private suspend fun fetchLastSelectedSpot(
        context: Context,
        parkingRepository: ParkingRepository,
        attempts: MutableList<SpotLoadAttempt>,
        forceRefresh: Boolean,
        assignedLotId: String,
    ): ParkingSpot? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLotId = prefs.getString(KEY_LAST_SELECTED_LOT_ID, null)
            .validPreferenceValue()
        if (savedLotId != assignedLotId) return null
        val spotId = prefs.getString(KEY_LAST_SELECTED_SPOT_ID, null)
            .validPreferenceValue()
            ?: return null
        return try {
            val response = parkingRepository.getParkingSpotById(
                spotId,
                forceRefresh = forceRefresh,
            )
            if (response.isSuccessful) {
                response.body()?.takeIf {
                    it.id.isNotBlank() && it.lotId.trim() == assignedLotId
                }.also { spot ->
                    attempts += SpotLoadAttempt(
                        successful = true,
                        spots = listOfNotNull(spot)
                    )
                }
            } else {
                val message = response.readBackendErrorMessage()
                attempts += SpotLoadAttempt(code = response.code(), message = message)
                null
            }
        } catch (error: Exception) {
            attempts += SpotLoadAttempt(failedWithException = true, message = error.message)
            null
        }
    }

    private fun successfulResult(
        spots: List<ParkingSpot>,
        assignedLotId: String,
        assignedLotName: String?
    ): LoadResult {
        val normalized = spots.map { spot ->
            if (spot.lotId.isBlank()) {
                spot.copy(lotId = assignedLotId, lotName = spot.lotName ?: assignedLotName)
            } else if (spot.lotId.trim() == assignedLotId && spot.lotName.isNullOrBlank()) {
                spot.copy(lotName = assignedLotName)
            } else {
                spot
            }
        }.filter { spot -> spot.lotId.trim() == assignedLotId }
        val processed = processSpots(normalized)
        return LoadResult(
            spots = processed,
            emptyMessage = if (processed.isEmpty()) {
                "No active parking spots are configured for ${assignedLotName ?: "your assigned parking lot"}."
            } else {
                null
            },
            retryable = false
        )
    }

    internal fun classifyEmptyResult(
        attempts: List<AttemptSummary>,
        hasLotContext: Boolean
    ): EmptyReason {
        if (attempts.any { it.successful }) return EmptyReason.NO_ACTIVE_SPOTS
        if (attempts.any { it.code == 403 }) {
            return if (hasLotContext) EmptyReason.ACCESS_DENIED else EmptyReason.NO_LOT_ASSIGNMENT
        }
        if (!hasLotContext) return EmptyReason.NO_LOT_ASSIGNMENT
        return EmptyReason.LOAD_FAILED
    }

    internal fun canTryAlternateRoute(attempt: AttemptSummary): Boolean {
        return attempt.code == 404 || attempt.code == 405 || attempt.code == 501
    }

    internal fun canUseStaleFallback(attempts: List<AttemptSummary>): Boolean {
        if (attempts.any { it.code == 401 || it.code == 403 }) return false
        return attempts.any { attempt ->
            attempt.failedWithException || attempt.code == 429 ||
                attempt.code == 500 || attempt.code == 502 ||
                attempt.code == 503 || attempt.code == 504
        }
    }

    private fun Response<*>.readBackendErrorMessage(): String? {
        val rawBody = runCatching { errorBody()?.string() }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return runCatching {
            gson.fromJson(rawBody, JsonObject::class.java)
                ?.readString("message", "error")
        }.getOrNull() ?: rawBody.take(200)
    }

    fun getSpotDisplayName(spot: ParkingSpot): String {
        return listOf(spot.name, spot.zoneName, spot.spotCode, spot.id)
            .firstOrNull { !it.isNullOrBlank() }
            ?: "Unknown Spot"
    }

    private suspend fun resolveLotFromAllLots(
        parkingRepository: ParkingRepository,
        lotKey: String,
    ): ParkingLotCandidate? {
        val lotsResponse = parkingRepository.getParkingLotsPayload()
        if (!lotsResponse.isSuccessful) {
            return null
        }

        val normalizedKey = lotKey.normalizeLookupKey()
        val lots = parseParkingLots(lotsResponse.body())
        val matches = lots.filter { lot ->
            lot.id.normalizeLookupKey() == normalizedKey ||
                lot.name.normalizeLookupKey() == normalizedKey
        }
        if (matches.size > 1) {
            return null
        }
        return matches.singleOrNull()
    }

    private fun parseParkingSpots(payload: JsonElement?): List<ParkingSpot> {
        val array = payload.findPayloadArray() ?: run {
            return emptyList()
        }

        return array.mapNotNull { element ->
            runCatching { gson.fromJson(element, ParkingSpot::class.java) }
                .getOrNull()
        }.filter { spot ->
            if (spot.id.isBlank()) {
                false
            } else {
                true
            }
        }
    }

    private fun parseParkingLots(payload: JsonElement?): List<ParkingLotCandidate> {
        val array = payload.findPayloadArray() ?: run {
            return emptyList()
        }

        return array.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val id = obj.readString("id", "_id").orEmpty()
            if (id.isBlank()) {
                null
            } else {
                ParkingLotCandidate(
                    id = id,
                    name = obj.readString("name"),
                )
            }
        }
    }

    private fun JsonElement?.findPayloadArray(): JsonArray? {
        if (this == null || isJsonNull) return null
        if (isJsonArray) return asJsonArray
        val obj = asJsonObjectOrNull() ?: return null

        val knownKeys = listOf("content", "data", "items", "results", "spots", "parkingSpots", "parkingLots", "lots")
        for (key in knownKeys) {
            val value = obj.get(key)
            if (value != null && value.isJsonArray) return value.asJsonArray
            val nestedArray = value.findPayloadArray()
            if (nestedArray != null) return nestedArray
        }

        for (entry in obj.entrySet()) {
            val value = entry.value
            if (value != null && value.isJsonArray) return value.asJsonArray
            val nestedArray = value.findPayloadArray()
            if (nestedArray != null) return nestedArray
        }

        return null
    }

    private fun JsonElement?.asJsonObjectOrNull(): JsonObject? {
        return if (this != null && !isJsonNull && isJsonObject) asJsonObject else null
    }

    private fun JsonObject.readString(vararg keys: String): String? {
        for (key in keys) {
            val element = get(key) ?: continue
            if (element.isJsonNull) continue
            if (element.isJsonPrimitive) {
                val value = element.asString.trim()
                if (value.isNotEmpty()) return value
            }
            val objectValue = element.asJsonObjectOrNull()
            if (objectValue != null) {
                val objectId = objectValue.get("\$oid")
                    ?: objectValue.get("oid")
                    ?: objectValue.get("value")
                if (objectId != null && objectId.isJsonPrimitive) {
                    val value = objectId.asString.trim()
                    if (value.isNotEmpty()) return value
                }
            }
        }
        return null
    }

    private fun processSpots(spots: List<ParkingSpot>): List<ParkingSpot> = spots
        // A morning/quick pair may represent distinct backend spot IDs. Never merge them: the
        // exact lot + spot pair selected here is also the pair validated after every scan.
        .distinctBy { it.lotId.trim() to it.id.trim() }
        .sortedBy { getSpotDisplayName(it).lowercase() }

    private fun String?.validPreferenceValue(): String? {
        val value = this?.trim().orEmpty()
        return value.takeIf {
            it.isNotEmpty() &&
                !it.equals("Parking Lot", ignoreCase = true) &&
                !it.equals("null", ignoreCase = true) &&
                !it.equals("nil", ignoreCase = true)
        }
    }

    private fun String?.normalizeLookupKey(): String {
        return this
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()
    }

    private data class ParkingLotCandidate(
        val id: String,
        val name: String?,
    )

    private data class OperatorLotContext(
        val id: String?,
        val name: String?
    )

    private data class CachedLoadResult(
        val result: LoadResult,
        val storedAtElapsedRealtimeMs: Long
    )

    private data class SpotLoadAttempt(
        val successful: Boolean = false,
        val spots: List<ParkingSpot> = emptyList(),
        val code: Int? = null,
        val message: String? = null,
        val failedWithException: Boolean = false
    ) {
        val summary: AttemptSummary
            get() = AttemptSummary(
                successful = successful,
                code = code,
                message = message,
                failedWithException = failedWithException
            )

        fun canTryAlternateRoute(): Boolean {
            return OperatorParkingSpotLoader.canTryAlternateRoute(summary)
        }
    }
}
