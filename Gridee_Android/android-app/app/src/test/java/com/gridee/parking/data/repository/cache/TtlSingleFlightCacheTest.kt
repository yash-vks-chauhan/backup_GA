package com.gridee.parking.data.repository.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TtlSingleFlightCacheTest {

    @Test
    fun `fresh value skips another load until ttl expires`() = runBlocking {
        var now = 1_000L
        val loads = AtomicInteger()
        val cache = TtlSingleFlightCache<String, String>(clockMillis = { now })

        val first = cache.getOrLoad("spots", ttlMillis = 30_000L) {
            "value-${loads.incrementAndGet()}"
        }
        now += 29_999L
        val cached = cache.getOrLoad("spots", ttlMillis = 30_000L) {
            "value-${loads.incrementAndGet()}"
        }
        now += 1L
        val refreshed = cache.getOrLoad("spots", ttlMillis = 30_000L) {
            "value-${loads.incrementAndGet()}"
        }

        assertEquals("value-1", first)
        assertEquals("value-1", cached)
        assertEquals("value-2", refreshed)
        assertEquals(2, loads.get())
    }

    @Test
    fun `concurrent callers await one shared load`() = runBlocking {
        val loads = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cache = TtlSingleFlightCache<String, String>()

        val first = async {
            cache.getOrLoad("wallet", ttlMillis = 30_000L) {
                loads.incrementAndGet()
                started.complete(Unit)
                release.await()
                "balance"
            }
        }
        started.await()
        val second = async {
            cache.getOrLoad("wallet", ttlMillis = 30_000L) {
                loads.incrementAndGet()
                "duplicate"
            }
        }
        release.complete(Unit)

        assertEquals("balance", first.await())
        assertEquals("balance", second.await())
        assertEquals(1, loads.get())
    }

    @Test
    fun `cancelling first caller does not cancel shared request`() = runBlocking {
        val loads = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cache = TtlSingleFlightCache<String, String>()

        val firstCaller = async {
            cache.getOrLoad("bookings", ttlMillis = 20_000L) {
                loads.incrementAndGet()
                started.complete(Unit)
                release.await()
                "active-bookings"
            }
        }
        started.await()
        firstCaller.cancelAndJoin()

        val replacementCaller = async {
            cache.getOrLoad("bookings", ttlMillis = 20_000L) {
                loads.incrementAndGet()
                "duplicate"
            }
        }
        release.complete(Unit)

        assertEquals("active-bookings", replacementCaller.await())
        assertEquals(1, loads.get())
    }

    @Test
    fun `failed refresh returns last successful stale value`() = runBlocking {
        data class LoadResult(val value: String, val successful: Boolean)

        var now = 10L
        val cache = TtlSingleFlightCache<String, LoadResult>(clockMillis = { now })
        val successful = LoadResult("cached", successful = true)
        cache.getOrLoad("config", 100L, isCacheable = { it.successful }) { successful }

        now += 101L
        val returned = cache.getOrLoad(
            key = "config",
            ttlMillis = 100L,
            isCacheable = { it.successful },
        ) { LoadResult("server-error", successful = false) }

        assertEquals(successful, returned)
    }

    @Test
    fun `network exception returns last successful stale value`() = runBlocking {
        var now = 50L
        val cache = TtlSingleFlightCache<String, String>(clockMillis = { now })
        cache.getOrLoad("wallet", 100L) { "last-known-balance" }

        now += 101L
        val returned = cache.getOrLoad("wallet", 100L) {
            throw IllegalStateException("network unavailable")
        }

        assertEquals("last-known-balance", returned)
    }

    @Test
    fun `sequential failures use two four eight second retry cooldowns`() = runBlocking {
        data class LoadResult(val value: String, val successful: Boolean)

        var now = 0L
        var failedLoads = 0
        val cache = TtlSingleFlightCache<String, LoadResult>(clockMillis = { now })
        val cached = LoadResult("cached-spots", successful = true)
        cache.getOrLoad("spots", 10L, isCacheable = { it.successful }) { cached }
        now = 11L

        suspend fun failedRefresh(): LoadResult = cache.getOrLoad(
            key = "spots",
            ttlMillis = 10L,
            forceRefresh = true,
            isCacheable = { it.successful },
        ) {
            failedLoads += 1
            LoadResult("failure-$failedLoads", successful = false)
        }

        assertEquals(cached, failedRefresh())
        assertEquals(1, failedLoads)
        assertEquals(1, cache.failureCount("spots"))

        now = 2_010L
        assertEquals(cached, failedRefresh())
        assertEquals(1, failedLoads)
        now = 2_011L
        assertEquals(cached, failedRefresh())
        assertEquals(2, failedLoads)

        now = 6_010L
        assertEquals(cached, failedRefresh())
        assertEquals(2, failedLoads)
        now = 6_011L
        assertEquals(cached, failedRefresh())
        assertEquals(3, failedLoads)

        now = 14_010L
        assertEquals(cached, failedRefresh())
        assertEquals(3, failedLoads)
        now = 14_011L
        assertEquals(cached, failedRefresh())
        assertEquals(4, failedLoads)

        // The fourth and later failures stay capped at eight seconds.
        now = 22_010L
        assertEquals(cached, failedRefresh())
        assertEquals(4, failedLoads)
        now = 22_011L
        assertEquals(cached, failedRefresh())
        assertEquals(5, failedLoads)
    }

    @Test
    fun `stale fallback can be disabled without bypassing failure cooldown`() = runBlocking {
        data class LoadResult(val value: String, val successful: Boolean)

        var now = 100L
        var failedLoads = 0
        val cache = TtlSingleFlightCache<String, LoadResult>(clockMillis = { now })
        cache.getOrLoad("config", 10L, isCacheable = { it.successful }) {
            LoadResult("fresh-config", successful = true)
        }
        now += 11L

        suspend fun refresh(): LoadResult = cache.getOrLoad(
            key = "config",
            ttlMillis = 10L,
            forceRefresh = true,
            isCacheable = { it.successful },
            useStaleOnFailure = false,
        ) {
            failedLoads += 1
            LoadResult("fetch-failure-$failedLoads", successful = false)
        }

        assertEquals("fetch-failure-1", refresh().value)
        assertEquals("fetch-failure-1", refresh().value)
        assertEquals(1, failedLoads)
        assertEquals(1, cache.failureCount("config"))
    }

    @Test
    fun `invalidation clears failure cooldown immediately`() = runBlocking {
        data class LoadResult(val value: String, val successful: Boolean)

        var loads = 0
        val cache = TtlSingleFlightCache<String, LoadResult>(clockMillis = { 1_000L })
        val failed = cache.getOrLoad(
            key = "wallet",
            ttlMillis = 10L,
            isCacheable = { it.successful },
            useStaleOnFailure = false,
        ) {
            loads += 1
            LoadResult("failed", successful = false)
        }
        assertEquals("failed", failed.value)
        assertEquals(1, cache.failureCount("wallet"))

        cache.invalidate("wallet")
        val loaded = cache.getOrLoad("wallet", 10L, isCacheable = { it.successful }) {
            loads += 1
            LoadResult("after-invalidation", successful = true)
        }

        assertEquals("after-invalidation", loaded.value)
        assertEquals(2, loads)
        assertEquals(0, cache.failureCount("wallet"))
    }

    @Test
    fun `successful retry resets exponential failure count`() = runBlocking {
        data class LoadResult(val value: String, val successful: Boolean)

        var now = 0L
        val cache = TtlSingleFlightCache<String, LoadResult>(clockMillis = { now })
        val firstFailure = cache.getOrLoad(
            key = "lots",
            ttlMillis = 10L,
            isCacheable = { it.successful },
            useStaleOnFailure = false,
        ) { LoadResult("failure-one", successful = false) }
        assertEquals("failure-one", firstFailure.value)
        assertEquals(1, cache.failureCount("lots"))

        now = 2_000L
        val success = cache.getOrLoad("lots", 10L, isCacheable = { it.successful }) {
            LoadResult("recovered", successful = true)
        }
        assertEquals("recovered", success.value)
        assertEquals(0, cache.failureCount("lots"))

        val secondFailure = cache.getOrLoad(
            key = "lots",
            ttlMillis = 10L,
            forceRefresh = true,
            isCacheable = { it.successful },
            useStaleOnFailure = false,
        ) { LoadResult("new-failure-one", successful = false) }
        assertEquals("new-failure-one", secondFailure.value)
        assertEquals(1, cache.failureCount("lots"))
    }

    @Test
    fun `invalidation prevents older in-flight response from repopulating cache`() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val cache = TtlSingleFlightCache<String, String>()

        val oldRequest = async {
            cache.getOrLoad("spots", 30_000L) {
                oldStarted.complete(Unit)
                releaseOld.await()
                "before-mutation"
            }
        }
        oldStarted.await()
        cache.invalidate("spots")

        val afterMutation = cache.getOrLoad("spots", 30_000L) { "after-mutation" }
        releaseOld.complete(Unit)
        assertEquals("after-mutation", oldRequest.await())

        assertEquals("after-mutation", afterMutation)
        assertEquals("after-mutation", cache.peek("spots"))
    }

    @Test
    fun `caller of invalidated flight starts current generation when no refresh exists yet`() =
        runBlocking {
            val loads = AtomicInteger()
            val oldStarted = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val cache = TtlSingleFlightCache<String, String>()

            val caller = async {
                cache.getOrLoad("spots", 30_000L) {
                    when (loads.incrementAndGet()) {
                        1 -> {
                            oldStarted.complete(Unit)
                            releaseOld.await()
                            "before-mutation"
                        }
                        else -> "after-mutation"
                    }
                }
            }
            oldStarted.await()
            cache.invalidate("spots")
            releaseOld.complete(Unit)

            assertEquals("after-mutation", caller.await())
            assertEquals("after-mutation", cache.peek("spots"))
            assertEquals(2, loads.get())
        }

    @Test
    fun `older in-flight failure cannot apply cooldown to new generation`() = runBlocking {
        data class LoadResult(val value: String, val successful: Boolean)

        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val cache = TtlSingleFlightCache<String, LoadResult>()
        cache.getOrLoad("bookings", 20_000L, isCacheable = { it.successful }) {
            LoadResult("pre-mutation-stale", successful = true)
        }

        val oldRequest = async {
            cache.getOrLoad(
                key = "bookings",
                ttlMillis = 20_000L,
                forceRefresh = true,
                isCacheable = { it.successful },
            ) {
                oldStarted.complete(Unit)
                releaseOld.await()
                LoadResult("old-failure", successful = false)
            }
        }
        oldStarted.await()
        cache.invalidate("bookings")
        val current = cache.getOrLoad("bookings", 20_000L, isCacheable = { it.successful }) {
            LoadResult("new-success", successful = true)
        }
        releaseOld.complete(Unit)

        // Default stale fallback must not cross the invalidation generation boundary.
        assertEquals("new-success", oldRequest.await().value)
        assertEquals("new-success", current.value)
        assertEquals("new-success", cache.peek("bookings")?.value)
        assertEquals(0, cache.failureCount("bookings"))
    }

    @Test
    fun `force refresh and invalidation deliberately bypass fresh entry`() = runBlocking {
        val loads = AtomicInteger()
        val cache = TtlSingleFlightCache<String, String>()
        suspend fun read(force: Boolean = false): String = cache.getOrLoad(
            key = "lots",
            ttlMillis = 300_000L,
            forceRefresh = force,
        ) { "value-${loads.incrementAndGet()}" }

        assertEquals("value-1", read())
        assertEquals("value-2", read(force = true))
        cache.invalidate("lots")
        assertEquals("value-3", read())
        assertEquals(3, loads.get())
        assertTrue(cache.entryCount() <= 1)
    }
}
