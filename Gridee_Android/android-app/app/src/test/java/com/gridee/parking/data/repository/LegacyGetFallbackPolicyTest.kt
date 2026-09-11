package com.gridee.parking.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyGetFallbackPolicyTest {

    @Test
    fun `fallback is limited to unsupported route responses`() {
        listOf(404, 405, 501).forEach { code ->
            assertTrue("Expected route fallback for HTTP $code", LegacyGetFallbackPolicy.shouldFallback(code))
        }
    }

    @Test
    fun `peak and server errors never call legacy route`() {
        listOf(408, 429, 500, 502, 503, 504).forEach { code ->
            assertFalse("Unexpected route fallback for HTTP $code", LegacyGetFallbackPolicy.shouldFallback(code))
        }
    }
}

