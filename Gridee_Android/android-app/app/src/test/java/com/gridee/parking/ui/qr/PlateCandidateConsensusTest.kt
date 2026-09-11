package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlateCandidateConsensusTest {

    @Test
    fun `accepts the second consecutive matching frame`() {
        val consensus = PlateCandidateConsensus(requiredConsecutiveMatches = 2, maxGapMs = 650L)

        assertNull(consensus.observe("MH12AB1234", observedAtMs = 1_000L))
        assertEquals("MH12AB1234", consensus.observe("MH12AB1234", observedAtMs = 1_080L))
    }

    @Test
    fun `a different candidate restarts confirmation`() {
        val consensus = PlateCandidateConsensus()

        assertNull(consensus.observe("MH12AB1234", observedAtMs = 1_000L))
        assertNull(consensus.observe("MH12AB1284", observedAtMs = 1_080L))
        assertEquals("MH12AB1284", consensus.observe("MH12AB1284", observedAtMs = 1_160L))
    }

    @Test
    fun `a stale candidate does not complete an old sequence`() {
        val consensus = PlateCandidateConsensus(maxGapMs = 650L)

        assertNull(consensus.observe("MH12AB1234", observedAtMs = 1_000L))
        assertNull(consensus.observe("MH12AB1234", observedAtMs = 1_651L))
    }

    @Test
    fun `clear requires a fresh sequence`() {
        val consensus = PlateCandidateConsensus()

        assertNull(consensus.observe("MH12AB1234", observedAtMs = 1_000L))
        consensus.clear()

        assertNull(consensus.observe("MH12AB1234", observedAtMs = 1_080L))
    }

    @Test
    fun `corrected candidate can require three matching frames`() {
        val consensus = PlateCandidateConsensus()

        assertNull(consensus.observe("MH12AB1234", 1_000L, requiredMatches = 3, allowedGapMs = 900L))
        assertNull(consensus.observe("MH12AB1234", 1_080L, requiredMatches = 3, allowedGapMs = 900L))
        assertEquals(
            "MH12AB1234",
            consensus.observe("MH12AB1234", 1_160L, requiredMatches = 3, allowedGapMs = 900L),
        )
    }

    @Test
    fun `changing confidence requirement starts a fresh sequence`() {
        val consensus = PlateCandidateConsensus()

        assertNull(consensus.observe("MH12AB1234", 1_000L, requiredMatches = 2))
        assertNull(consensus.observe("MH12AB1234", 1_080L, requiredMatches = 3))
        assertNull(consensus.observe("MH12AB1234", 1_160L, requiredMatches = 3))
        assertEquals("MH12AB1234", consensus.observe("MH12AB1234", 1_240L, requiredMatches = 3))
    }
}
