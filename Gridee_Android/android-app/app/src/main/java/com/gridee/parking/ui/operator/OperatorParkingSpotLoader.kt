package com.gridee.parking.ui.operator

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.gridee.parking.data.api.BackendGsonFactory
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.UserProfileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

object OperatorParkingSpotLoader {
    private const val PREFS_NAME = "gridee_prefs"
    private const val KEY_PARKING_LOT_ID = "parking_lot_id"
    private const val KEY_PARKING_LOT_NAME = "parking_lot_name"
    private const val KEY_LAST_SELECTED_SPOT_ID = "operator_last_selected_spot_id"
    private const val LEGACY_LOT_ID_PREFIX = "pl"
    private val gson = BackendGsonFactory.gson

    data class LoadResult(
        val spots: List<ParkingSpot>,
        val emptyMessage: String? = null,
        val retryable: Boolean = false
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
        logTag: String,
        userRepository: UserRepository = UserRepository()
    ): LoadResult = withContext(Dispatchers.IO) {
        val lotContext = resolveAndRepairOperatorLotContext(
            context = context,
            parkingRepository = parkingRepository,
            userRepository = userRepository,
            logTag = logTag
        )
        val attempts = mutableListOf<SpotLoadAttempt>()

        if (!lotContext.id.isNullOrBlank()) {
            attempts += fetchAttempt(logTag, "operator assigned-lot parking spots") {
                parkingRepository.getOperatorParkingSpotsForLotPayload(lotContext.id)
            }
            attempts.last().spots.takeIf { it.isNotEmpty() }?.let { spots ->
                return@withContext successfulResult(spots, lotContext.id, logTag)
            }
        }

        attempts += fetchAttempt(logTag, "operator parking spots") {
            parkingRepository.getOperatorParkingSpotsPayload()
        }
        attempts.last().spots.takeIf { it.isNotEmpty() }?.let { spots ->
            return@withContext successfulResult(spots, lotContext.id, logTag)
        }

        // Keep a generic lot-scoped endpoint as a rollout fallback. It remains
        // constrained to the operator's assigned parking lot.
        if (!lotContext.id.isNullOrBlank()) {
            attempts += fetchAttempt(logTag, "assigned-lot parking spots") {
                parkingRepository.getParkingSpotsByLotPayload(lotContext.id)
            }
            attempts.last().spots.takeIf { it.isNotEmpty() }?.let { spots ->
                return@withContext successfulResult(spots, lotContext.id, logTag)
            }
        }

        // Retain a safe single-spot fallback for operators who already selected a
        // spot before a list endpoint became unavailable.
        val lastSelectedSpot = fetchLastSelectedSpot(
            context,
            parkingRepository,
            logTag,
            attempts
        )
        if (lastSelectedSpot != null) {
            return@withContext successfulResult(listOf(lastSelectedSpot), lotContext.id, logTag)
        }

        val reason = classifyEmptyResult(
            attempts = attempts.map(SpotLoadAttempt::summary),
            hasLotContext = !lotContext.id.isNullOrBlank()
        )
        val message = when (reason) {
            EmptyReason.NO_LOT_ASSIGNMENT ->
                "Your operator account is not assigned to a parking lot. Ask an administrator to assign it, then try again."
            EmptyReason.NO_ACTIVE_SPOTS ->
                "No active parking spots are configured for ${lotContext.name ?: "your assigned parking lot"}."
            EmptyReason.ACCESS_DENIED ->
                attempts.firstNotNullOfOrNull { attempt ->
                    attempt.message.takeIf { attempt.code == 403 }
                }
                    ?: "Your operator account does not have access to this parking lot."
            EmptyReason.LOAD_FAILED ->
                "Parking spots could not be loaded. Check your connection and tap to try again."
        }
        LoadResult(
            spots = emptyList(),
            emptyMessage = message,
            retryable = reason != EmptyReason.NO_ACTIVE_SPOTS
        )
    }

    private suspend fun resolveAndRepairOperatorLotContext(
        context: Context,
        parkingRepository: ParkingRepository,
        userRepository: UserRepository,
        logTag: String
    ): OperatorLotContext {
        val userId = AuthSession.getUserId(context)
        val cachedUser = userId?.let { UserProfileCache.get(context, it) }
        val freshUser = userId?.let { id ->
            runCatching { userRepository.getUserById(id) }
                .onFailure { Log.w(logTag, "Unable to refresh operator profile", it) }
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
            val resolvedLot = resolveLotFromAllLots(parkingRepository, lotName, logTag)
            if (resolvedLot != null) {
                val repairResponse = runCatching {
                    userRepository.assignParkingLot(
                        userId,
                        resolvedLot.id,
                        resolvedLot.name ?: lotName
                    )
                }.onFailure {
                    Log.w(logTag, "Unable to repair legacy operator parking-lot assignment", it)
                }.getOrNull()

                if (repairResponse?.isSuccessful == true) {
                    lotId = resolvedLot.id
                    lotName = resolvedLot.name ?: lotName
                    AuthSession.saveParkingLot(context, lotId, lotName)
                    profile?.copy(parkingLotId = lotId, parkingLotName = lotName)
                        ?.let { AuthSession.updateCachedUserProfile(context, it) }
                    Log.i(logTag, "Repaired legacy operator parking-lot assignment")
                } else {
                    Log.w(
                        logTag,
                        "Legacy operator parking-lot repair failed: ${repairResponse?.code() ?: "network error"}"
                    )
                }
            }
        }

        return OperatorLotContext(lotId, lotName)
    }

    private suspend fun fetchAttempt(
        logTag: String,
        source: String,
        request: suspend () -> Response<JsonElement>
    ): SpotLoadAttempt {
        return try {
            val response = request()
            if (!response.isSuccessful) {
                val backendMessage = response.readBackendErrorMessage()
                Log.w(logTag, "$source request failed: ${response.code()} $backendMessage")
                SpotLoadAttempt(
                    code = response.code(),
                    message = backendMessage
                )
            } else {
                val spots = parseParkingSpots(response.body(), logTag, source)
                Log.d(logTag, "$source response count=${spots.size}")
                SpotLoadAttempt(
                    successful = true,
                    spots = spots
                )
            }
        } catch (error: Exception) {
            Log.w(logTag, "$source source failed", error)
            SpotLoadAttempt(failedWithException = true, message = error.message)
        }
    }

    private suspend fun fetchLastSelectedSpot(
        context: Context,
        parkingRepository: ParkingRepository,
        logTag: String,
        attempts: MutableList<SpotLoadAttempt>
    ): ParkingSpot? {
        val spotId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SELECTED_SPOT_ID, null)
            .validPreferenceValue()
            ?: return null
        return try {
            val response = parkingRepository.getParkingSpotById(spotId)
            if (response.isSuccessful) {
                response.body()?.takeIf { it.id.isNotBlank() }.also { spot ->
                    attempts += SpotLoadAttempt(
                        successful = true,
                        spots = listOfNotNull(spot)
                    )
                }
            } else {
                val message = response.readBackendErrorMessage()
                attempts += SpotLoadAttempt(code = response.code(), message = message)
                Log.w(logTag, "Last selected parking spot request failed: ${response.code()} $message")
                null
            }
        } catch (error: Exception) {
            attempts += SpotLoadAttempt(failedWithException = true, message = error.message)
            Log.w(logTag, "Last selected parking spot source failed", error)
            null
        }
    }

    private fun successfulResult(
        spots: List<ParkingSpot>,
        assignedLotId: String?,
        logTag: String
    ): LoadResult {
        val normalized = spots.map { spot ->
            if (spot.lotId.isBlank() && !assignedLotId.isNullOrBlank()) {
                spot.copy(lotId = assignedLotId)
            } else {
                spot
            }
        }
        return LoadResult(spots = processSpots(normalized, logTag))
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

    private suspend fun fetchSource(
        logTag: String,
        sourceName: String,
        block: suspend () -> List<ParkingSpot>
    ): List<ParkingSpot> {
        return runCatching { block() }
            .onFailure { Log.w(logTag, "$sourceName source failed", it) }
            .getOrDefault(emptyList())
    }

    private suspend fun fetchAllParkingSpots(
        parkingRepository: ParkingRepository,
        logTag: String
    ): List<ParkingSpot> {
        val response = parkingRepository.getParkingSpotsPayload()
        if (!response.isSuccessful) {
            Log.w(logTag, "All parking spots request failed: ${response.code()}")
            return emptyList()
        }

        val spots = parseParkingSpots(response.body(), logTag, "all parking spots")
        Log.d(logTag, "All parking spots response count=${spots.size}")
        return spots
    }

    private suspend fun fetchOperatorParkingSpots(
        parkingRepository: ParkingRepository,
        logTag: String
    ): List<ParkingSpot> {
        val response = parkingRepository.getOperatorParkingSpotsPayload()
        if (!response.isSuccessful) {
            Log.w(logTag, "Operator parking spots request failed: ${response.code()}")
            return emptyList()
        }

        val spots = parseParkingSpots(response.body(), logTag, "operator parking spots")
        Log.d(logTag, "Operator parking spots response count=${spots.size}")
        return spots
    }

    private suspend fun fetchAssignedLotSpots(
        context: Context,
        parkingRepository: ParkingRepository,
        logTag: String
    ): List<ParkingSpot> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUser = AuthSession.getUserId(context)
            ?.let { userId -> UserProfileCache.get(context, userId) }

        val assignedLotId = prefs.getString(KEY_PARKING_LOT_ID, null).validPreferenceValue()
            ?: cachedUser?.parkingLotId.validPreferenceValue()
        val assignedLotName = prefs.getString(KEY_PARKING_LOT_NAME, null).validPreferenceValue()
            ?: cachedUser?.parkingLotName.validPreferenceValue()

        if (!assignedLotId.isNullOrBlank()) {
            val spots = fetchSpotsByLot(parkingRepository, assignedLotId, logTag, "assigned lot id")
            if (spots.isNotEmpty()) return spots
        }

        if (!assignedLotName.isNullOrBlank()) {
            val lotByNameSpots = fetchSpotsByAssignedLotName(parkingRepository, assignedLotName, logTag)
            if (lotByNameSpots.isNotEmpty()) return lotByNameSpots
        }

        val fallbackLotKey = assignedLotId ?: assignedLotName
        if (!fallbackLotKey.isNullOrBlank()) {
            val resolvedLot = resolveLotFromAllLots(parkingRepository, fallbackLotKey, logTag)
            if (resolvedLot != null) {
                val spots = fetchSpotsForLotCandidate(parkingRepository, resolvedLot, logTag, "resolved assigned lot")
                if (spots.isNotEmpty()) return spots
            }
        }

        Log.w(logTag, "No assigned lot spots found for operator")
        return emptyList()
    }

    private suspend fun fetchSpotsByAssignedLotName(
        parkingRepository: ParkingRepository,
        assignedLotName: String,
        logTag: String
    ): List<ParkingSpot> {
        val lotResponse = parkingRepository.getParkingLotByName(assignedLotName)
        if (lotResponse.isSuccessful) {
            val resolvedLotId = lotResponse.body()?.id.validPreferenceValue()
            if (!resolvedLotId.isNullOrBlank()) {
                val spots = fetchSpotsByLot(parkingRepository, resolvedLotId, logTag, "assigned lot name")
                if (spots.isNotEmpty()) return spots
            }
        } else {
            Log.w(logTag, "Parking lot lookup by name failed: ${lotResponse.code()}")
        }

        val resolvedLot = resolveLotFromAllLots(parkingRepository, assignedLotName, logTag)
        if (resolvedLot != null) {
            return fetchSpotsForLotCandidate(parkingRepository, resolvedLot, logTag, "assigned lot name from lots")
        }

        return emptyList()
    }

    private suspend fun fetchLastSelectedSpotLotSpots(
        context: Context,
        parkingRepository: ParkingRepository,
        logTag: String
    ): List<ParkingSpot> {
        val lastSelectedSpotId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SELECTED_SPOT_ID, null)
            .validPreferenceValue()
            ?: return emptyList()

        val spotResponse = parkingRepository.getParkingSpotById(lastSelectedSpotId)
        if (!spotResponse.isSuccessful) {
            Log.w(logTag, "Last selected spot lookup failed: ${spotResponse.code()}")
            return emptyList()
        }

        val spot = spotResponse.body() ?: return emptyList()
        if (spot.id.isBlank()) return emptyList()

        if (spot.lotId.isNotBlank()) {
            val lotSpots = fetchSpotsByLot(
                parkingRepository,
                spot.lotId,
                logTag,
                "last selected spot lot ${spot.lotId}"
            )
            if (lotSpots.isNotEmpty()) return lotSpots
        }

        Log.d(logTag, "Using last selected parking spot fallback")
        return listOf(spot)
    }

    private suspend fun resolveLotFromAllLots(
        parkingRepository: ParkingRepository,
        lotKey: String,
        logTag: String
    ): ParkingLotCandidate? {
        val lotsResponse = parkingRepository.getParkingLotsPayload()
        if (!lotsResponse.isSuccessful) {
            Log.w(logTag, "Parking lots request for assigned lot resolution failed: ${lotsResponse.code()}")
            return null
        }

        val normalizedKey = lotKey.normalizeLookupKey()
        val lots = parseParkingLots(lotsResponse.body(), logTag)
        val matches = lots.filter { lot ->
            lot.id.normalizeLookupKey() == normalizedKey ||
                lot.name.normalizeLookupKey() == normalizedKey
        }
        if (matches.size > 1) {
            Log.w(logTag, "Parking lot assignment '$lotKey' is ambiguous; refusing automatic repair")
            return null
        }
        return matches.singleOrNull()
    }

    private suspend fun fetchSpotsFromAllLots(
        parkingRepository: ParkingRepository,
        logTag: String
    ): List<ParkingSpot> {
        val lotsResponse = parkingRepository.getParkingLotsPayload()
        if (!lotsResponse.isSuccessful) {
            Log.w(logTag, "Parking lots request failed: ${lotsResponse.code()}")
            return emptyList()
        }

        val spots = mutableListOf<ParkingSpot>()
        parseParkingLots(lotsResponse.body(), logTag)
            .filter { it.id.isNotBlank() }
            .forEach { lot ->
                spots += fetchSource(logTag, "parking spots for lot ${lot.id}") {
                    fetchSpotsForLotCandidate(parkingRepository, lot, logTag, "lot ${lot.id}")
                }
            }

        val distinctSpots = spots.distinctBy { it.id }
        Log.d(logTag, "All lots spot fallback count=${distinctSpots.size}")
        return distinctSpots
    }

    private suspend fun fetchSpotsByLot(
        parkingRepository: ParkingRepository,
        lotId: String,
        logTag: String,
        source: String
    ): List<ParkingSpot> {
        val response = parkingRepository.getParkingSpotsByLotPayload(lotId)
        if (!response.isSuccessful) {
            Log.w(logTag, "Parking spots by $source failed: ${response.code()}")
            return emptyList()
        }

        val spots = parseParkingSpots(response.body(), logTag, "parking spots by $source")
        Log.d(logTag, "Parking spots by $source count=${spots.size}")
        return spots
    }

    private suspend fun fetchSpotsForLotCandidate(
        parkingRepository: ParkingRepository,
        lot: ParkingLotCandidate,
        logTag: String,
        source: String
    ): List<ParkingSpot> {
        for (lotId in lot.lookupIds()) {
            val spots = fetchSpotsByLot(parkingRepository, lotId, logTag, "$source id $lotId")
            if (spots.isNotEmpty()) return spots
        }
        return emptyList()
    }

    private fun parseParkingSpots(
        payload: JsonElement?,
        logTag: String,
        source: String
    ): List<ParkingSpot> {
        val array = payload.findPayloadArray() ?: run {
            Log.w(logTag, "No spot array found in $source payload")
            return emptyList()
        }

        return array.mapNotNull { element ->
            runCatching { gson.fromJson(element, ParkingSpot::class.java) }
                .onFailure { Log.w(logTag, "Unable to parse one $source item", it) }
                .getOrNull()
        }.filter { spot ->
            if (spot.id.isBlank()) {
                Log.w(logTag, "Dropping $source item without an id: $spot")
                false
            } else {
                true
            }
        }
    }

    private fun parseParkingLots(
        payload: JsonElement?,
        logTag: String
    ): List<ParkingLotCandidate> {
        val array = payload.findPayloadArray() ?: run {
            Log.w(logTag, "No parking lot array found in parking lots payload")
            return emptyList()
        }

        return array.mapIndexedNotNull { index, element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapIndexedNotNull null
            val id = obj.readString("id", "_id").orEmpty()
            if (id.isBlank()) {
                Log.w(logTag, "Dropping parking lot item without an id: $obj")
                null
            } else {
                ParkingLotCandidate(
                    id = id,
                    name = obj.readString("name"),
                    fallbackIds = listOf("$LEGACY_LOT_ID_PREFIX${index + 1}")
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

    private fun processSpots(spots: List<ParkingSpot>, logTag: String): List<ParkingSpot> {
        val processedSpots = mutableListOf<ParkingSpot>()
        val handledIds = mutableSetOf<String>()

        for (spot in spots) {
            if (handledIds.contains(spot.id)) continue

            val displayName = getSpotDisplayName(spot)
            if (displayName.endsWith(" Morning", ignoreCase = true) || displayName.endsWith(" Quick", ignoreCase = true)) {
                val baseName = if (displayName.endsWith(" Morning", ignoreCase = true)) {
                    displayName.substring(0, displayName.length - " Morning".length)
                } else {
                    displayName.substring(0, displayName.length - " Quick".length)
                }

                val suffixNeeded = if (displayName.endsWith(" Morning", ignoreCase = true)) " Quick" else " Morning"
                val counterpart = spots.find {
                    !handledIds.contains(it.id) && getSpotDisplayName(it).equals(baseName + suffixNeeded, ignoreCase = true)
                }

                if (counterpart != null) {
                    processedSpots.add(
                        spot.copy(
                            name = "$baseName (M&Q)",
                            capacity = maxOf(spot.capacity, counterpart.capacity),
                            available = maxOf(spot.available, counterpart.available)
                        )
                    )
                    handledIds.add(spot.id)
                    handledIds.add(counterpart.id)
                } else {
                    processedSpots.add(spot)
                    handledIds.add(spot.id)
                }
            } else {
                processedSpots.add(spot)
                handledIds.add(spot.id)
            }
        }

        Log.d(logTag, "Processed operator spots count=${processedSpots.size}")
        return processedSpots.sortedBy { getSpotDisplayName(it).lowercase() }
    }

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
        val fallbackIds: List<String> = emptyList()
    ) {
        fun lookupIds(): List<String> {
            return (listOf(id) + fallbackIds)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }
    }

    private data class OperatorLotContext(
        val id: String?,
        val name: String?
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
    }
}
