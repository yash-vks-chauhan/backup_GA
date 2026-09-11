package com.gridee.parking.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashCinematicPolicyTest {

    @Test
    fun `warm process skips without reading system animation state`() {
        val shouldSkip = SplashCinematicPolicy.shouldSkip(
            hasShownCinematic = true,
            areSystemAnimatorsEnabled = { error("warm launch must short circuit") },
        )

        assertTrue(shouldSkip)
    }

    @Test
    fun `first splash skips when system animators are disabled`() {
        assertTrue(
            SplashCinematicPolicy.shouldSkip(
                hasShownCinematic = false,
                areSystemAnimatorsEnabled = { false },
            )
        )
    }

    @Test
    fun `first splash plays when system animators are enabled`() {
        assertFalse(
            SplashCinematicPolicy.shouldSkip(
                hasShownCinematic = false,
                areSystemAnimatorsEnabled = { true },
            )
        )
    }
}
