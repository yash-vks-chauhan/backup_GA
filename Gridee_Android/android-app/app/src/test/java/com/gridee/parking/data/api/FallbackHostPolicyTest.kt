package com.gridee.parking.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackHostPolicyTest {

    @Test
    fun safeReadsMayBeReplayedAgainstFallbackHost() {
        assertTrue(FallbackHostPolicy.canReplay("GET"))
        assertTrue(FallbackHostPolicy.canReplay("head"))
    }

    @Test
    fun mutationsAreNeverReplayedAgainstFallbackHost() {
        listOf("POST", "PUT", "PATCH", "DELETE").forEach { method ->
            assertFalse(method, FallbackHostPolicy.canReplay(method))
        }
    }
}
