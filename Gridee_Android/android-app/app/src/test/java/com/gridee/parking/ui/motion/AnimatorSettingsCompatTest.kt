package com.gridee.parking.ui.motion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatorSettingsCompatTest {

    @Test
    fun `API 24 uses zero legacy scale as disabled without invoking API 26`() {
        val enabled = AnimatorSettingsCompat.resolveForSdk(
            sdkInt = 24,
            api26Enabled = { error("API 26 reader must not run") },
            legacyDurationScale = { 0f },
        )

        assertFalse(enabled)
    }

    @Test
    fun `API 25 accepts every positive legacy scale`() {
        listOf(0.5f, 1f, 10f).forEach { scale ->
            assertTrue(
                AnimatorSettingsCompat.resolveForSdk(
                    sdkInt = 25,
                    api26Enabled = { error("API 26 reader must not run") },
                    legacyDurationScale = { scale },
                )
            )
        }
    }

    @Test
    fun `API 24 defaults to enabled when the settings provider fails`() {
        val enabled = AnimatorSettingsCompat.resolveForSdk(
            sdkInt = 24,
            api26Enabled = { error("API 26 reader must not run") },
            legacyDurationScale = { error("settings provider unavailable") },
        )

        assertTrue(enabled)
    }

    @Test
    fun `API 26 uses the platform disabled state without reading legacy settings`() {
        val enabled = AnimatorSettingsCompat.resolveForSdk(
            sdkInt = 26,
            api26Enabled = { false },
            legacyDurationScale = { error("legacy reader must not run") },
        )

        assertFalse(enabled)
    }

    @Test
    fun `API 26 and newer use the platform enabled state`() {
        listOf(26, 33, 36).forEach { sdkInt ->
            assertTrue(
                AnimatorSettingsCompat.resolveForSdk(
                    sdkInt = sdkInt,
                    api26Enabled = { true },
                    legacyDurationScale = { error("legacy reader must not run") },
                )
            )
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `API 26 does not hide platform failures`() {
        AnimatorSettingsCompat.resolveForSdk(
            sdkInt = 26,
            api26Enabled = { error("unexpected OEM failure") },
            legacyDurationScale = { error("legacy reader must not run") },
        )
    }
}
