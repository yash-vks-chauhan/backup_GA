package com.gridee.parking.data.repository.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * Small process-wide read cache used by repositories.
 *
 * A load runs in [loadScope], rather than in the first caller's scope. Cancelling a screen therefore
 * stops only that screen from awaiting the result; another screen asking for the same key can still
 * join the original request. Failed refreshes never replace a good value and may fall back to the
 * last successful (stale) value. Sequential failures are held behind a per-key 2s/4s/8s backoff so
 * rapid lifecycle/manual calls cannot repeatedly hit an already-failing endpoint.
 */
internal class TtlSingleFlightCache<K : Any, V : Any>(
    private val maxEntries: Int = 256,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val loadScope: CoroutineScope = SHARED_LOAD_SCOPE,
) {

    private data class Entry<V>(
        val value: V,
        val loadedAtMillis: Long,
        val generation: Long,
    )
    private data class FlightKey<K>(val key: K, val generation: Long)
    private data class FailureState<V>(
        val generation: Long,
        val consecutiveFailures: Int,
        val retryAfterMillis: Long,
        val outcome: FailedLoad<V>,
    )

    private sealed class FailedLoad<out V> {
        data class Value<V>(val value: V) : FailedLoad<V>()
        data class Error(val error: Exception) : FailedLoad<Nothing>()
    }

    private val entries = ConcurrentHashMap<K, Entry<V>>()
    private val generations = ConcurrentHashMap<K, Long>()
    private val inFlight = ConcurrentHashMap<FlightKey<K>, Deferred<V>>()
    private val failures = ConcurrentHashMap<K, FailureState<V>>()

    suspend fun getOrLoad(
        key: K,
        ttlMillis: Long,
        forceRefresh: Boolean = false,
        isCacheable: (V) -> Boolean = { true },
        useStaleOnFailure: Boolean = true,
        loader: suspend () -> V,
    ): V {
        val generation = generations[key] ?: 0L
        val staleEntry = entries[key]?.takeIf { it.generation == generation }
        if (
            !forceRefresh && staleEntry != null && isFresh(staleEntry, ttlMillis) &&
            (generations[key] ?: 0L) == generation
        ) {
            return staleEntry.value
        }

        failures[key]
            ?.takeIf { failure ->
                failure.generation == generation && clockMillis() < failure.retryAfterMillis
            }
            ?.let { failure ->
                return resolveFailure(
                    key,
                    generation,
                    staleEntry,
                    failure.outcome,
                    useStaleOnFailure,
                )
            }

        val flightKey = FlightKey(key, generation)
        val candidate = loadScope.async(start = CoroutineStart.LAZY) {
            try {
                val loaded = loader()
                if (isCacheable(loaded)) {
                    if ((generations[key] ?: 0L) == generation) {
                        putAtGeneration(key, loaded, generation)
                        clearFailure(key, generation)
                    }
                } else {
                    recordFailure(key, generation, FailedLoad.Value(loaded))
                }
                loaded
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                recordFailure(key, generation, FailedLoad.Error(failure))
                throw failure
            }
        }
        val existing = inFlight.putIfAbsent(flightKey, candidate)
        val sharedLoad = existing ?: candidate.also { winner ->
            winner.invokeOnCompletion { inFlight.remove(flightKey, winner) }
            winner.start()
        }
        if (existing != null) candidate.cancel()

        return try {
            val loaded = sharedLoad.await()
            if ((generations[key] ?: 0L) != generation) {
                // A mutation invalidated this read while it was in flight. The old response is
                // not authoritative for its caller either: join (or start) the current-generation
                // read so a post-mutation refresh can never report pre-mutation data as ready.
                return getOrLoad(
                    key = key,
                    ttlMillis = ttlMillis,
                    forceRefresh = false,
                    isCacheable = isCacheable,
                    useStaleOnFailure = useStaleOnFailure,
                    loader = loader,
                )
            }
            if (isCacheable(loaded)) {
                loaded
            } else {
                resolveFailure(
                    key,
                    generation,
                    staleEntry,
                    FailedLoad.Value(loaded),
                    useStaleOnFailure,
                )
            }
        } catch (cancelled: CancellationException) {
            // This may be the awaiting screen being destroyed. Never turn cancellation into data.
            throw cancelled
        } catch (failure: Exception) {
            if ((generations[key] ?: 0L) != generation) {
                return getOrLoad(
                    key = key,
                    ttlMillis = ttlMillis,
                    forceRefresh = false,
                    isCacheable = isCacheable,
                    useStaleOnFailure = useStaleOnFailure,
                    loader = loader,
                )
            }
            resolveFailure(
                key,
                generation,
                staleEntry,
                FailedLoad.Error(failure),
                useStaleOnFailure,
            )
        }
    }

    fun peek(key: K): V? {
        val generation = generations[key] ?: 0L
        return entries[key]
            ?.takeIf { it.generation == generation && (generations[key] ?: 0L) == generation }
            ?.value
    }

    fun put(key: K, value: V) {
        val generation = generations[key] ?: 0L
        putAtGeneration(key, value, generation)
        clearFailure(key, generation)
    }

    fun invalidate(key: K) {
        generations.compute(key) { _, old -> (old ?: 0L) + 1L }
        entries.remove(key)
        failures.remove(key)
    }

    fun invalidateWhere(predicate: (K) -> Boolean) {
        knownKeys().filter(predicate).forEach(::invalidate)
    }

    fun clear() {
        knownKeys().forEach(::invalidate)
        entries.clear()
    }

    internal fun entryCount(): Int = entries.size
    internal fun inFlightCount(): Int = inFlight.size
    internal fun failureCount(key: K): Int = failures[key]?.consecutiveFailures ?: 0

    private fun isFresh(entry: Entry<V>, ttlMillis: Long): Boolean {
        if (ttlMillis <= 0L) return false
        val age = (clockMillis() - entry.loadedAtMillis).coerceAtLeast(0L)
        return age < ttlMillis
    }

    private fun putAtGeneration(key: K, value: V, generation: Long) {
        if ((generations[key] ?: 0L) != generation) return
        if (entries.size >= maxEntries && !entries.containsKey(key)) {
            entries.entries.minByOrNull { it.value.loadedAtMillis }?.let { oldest ->
                entries.remove(oldest.key, oldest.value)
            }
        }
        val entry = Entry(value, clockMillis(), generation)
        entries[key] = entry
        if ((generations[key] ?: 0L) != generation) {
            entries.remove(key, entry)
        }
    }

    private fun recordFailure(key: K, generation: Long, outcome: FailedLoad<V>) {
        if ((generations[key] ?: 0L) != generation) return
        if (failures.size >= maxEntries && !failures.containsKey(key)) {
            failures.entries.minByOrNull { it.value.retryAfterMillis }?.let { oldest ->
                failures.remove(oldest.key, oldest.value)
            }
        }
        failures.compute(key) { _, previous ->
            if ((generations[key] ?: 0L) != generation) {
                previous
            } else {
                val consecutiveFailures = if (previous?.generation == generation) {
                    previous.consecutiveFailures + 1
                } else {
                    1
                }
                FailureState(
                    generation = generation,
                    consecutiveFailures = consecutiveFailures,
                    retryAfterMillis = clockMillis() + backoffMillis(consecutiveFailures),
                    outcome = outcome,
                )
            }
        }
    }

    private fun clearFailure(key: K, generation: Long) {
        failures[key]?.takeIf { it.generation == generation }?.let { state ->
            failures.remove(key, state)
        }
    }

    private fun resolveFailure(
        key: K,
        generation: Long,
        staleEntry: Entry<V>?,
        failedLoad: FailedLoad<V>,
        useStaleOnFailure: Boolean,
    ): V {
        if (
            useStaleOnFailure && staleEntry?.generation == generation &&
            (generations[key] ?: 0L) == generation
        ) {
            return staleEntry.value
        }
        return when (failedLoad) {
            is FailedLoad.Value -> failedLoad.value
            is FailedLoad.Error -> throw failedLoad.error
        }
    }

    private fun backoffMillis(consecutiveFailures: Int): Long {
        val shift = (consecutiveFailures - 1).coerceIn(0, 2)
        return INITIAL_FAILURE_BACKOFF_MILLIS shl shift
    }

    private fun knownKeys(): Set<K> = buildSet {
        addAll(entries.keys)
        inFlight.keys.forEach { add(it.key) }
        addAll(generations.keys)
        addAll(failures.keys)
    }

    private companion object {
        const val INITIAL_FAILURE_BACKOFF_MILLIS = 2_000L
        val SHARED_LOAD_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
