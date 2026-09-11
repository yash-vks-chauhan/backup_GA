package com.gridee.parking.config

import com.gridee.parking.BuildConfig
import com.gridee.parking.data.model.AppRemoteConfig
import com.gridee.parking.data.model.RemoteAppVersions
import com.gridee.parking.data.model.RemoteBookingSettings
import com.gridee.parking.data.model.RemoteFinancialSettings
import com.gridee.parking.data.model.RemotePlatformSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigManagerTest {

    @Test
    fun forceUpdateUsesVersionNameWhenMinimumCodeIsDefault() {
        val versions = RemoteAppVersions(
            minAndroidVersion = "1.65",
            minAndroidVersionCode = 1,
        )

        assertTrue(AndroidVersionPolicy.isBelowMinimum(65, "1.64", versions))
        assertFalse(AndroidVersionPolicy.isBelowMinimum(65, "1.65", versions))
        assertFalse(AndroidVersionPolicy.isBelowMinimum(66, "1.66", versions))
    }

    @Test
    fun forceUpdateUsesVersionCodeWhenMinimumCodeIsConfigured() {
        val versions = RemoteAppVersions(
            minAndroidVersion = "2.0",
            minAndroidVersionCode = 65,
        )

        assertTrue(AndroidVersionPolicy.isBelowMinimum(64, "2.0", versions))
        assertFalse(AndroidVersionPolicy.isBelowMinimum(65, "1.64", versions))
    }

    // ---- Phase 0B hardening: app must self-protect against a bad/placeholder
    // server config (the live prod config was found saved with OpenAPI example
    // defaults: minAndroidVersionCode far above any published build, 0.0 wallet
    // bounds, "string" platform values). sanitize() must neutralise all of these.

    @Test
    fun sanitizeCapsMinVersionCodeSoTheRunningBuildIsNeverForceLocked() {
        // Mirrors the corrupted prod config: minimum demanded far above the
        // installed build. Without the cap this force-locks every user on the
        // non-dismissible splash gate, pointing at a build that isn't published.
        val config = AppRemoteConfig().apply {
            versions = RemoteAppVersions(
                minAndroidVersionCode = 99999,
                latestAndroidVersionCode = 99999,
                minAndroidVersion = "1.00",
            )
        }

        val sanitized = RemoteConfigManager.sanitize(config)

        assertTrue(
            "min code must be capped at the installed build",
            sanitized.versions.minAndroidVersionCode <= BuildConfig.VERSION_CODE
        )
        assertFalse(
            "the running build must never be 'below minimum' after sanitising",
            AndroidVersionPolicy.isBelowMinimum(
                BuildConfig.VERSION_CODE,
                BuildConfig.VERSION_NAME,
                sanitized.versions
            )
        )
    }

    @Test
    fun sanitizeRestoresZeroedWalletTopUpBoundsThatWouldBlockEveryTopUp() {
        val config = AppRemoteConfig().apply {
            financial = RemoteFinancialSettings(
                minWalletTopUpAmount = 0.0,
                maxWalletTopUpAmount = 0.0,
            )
        }

        val sanitized = RemoteConfigManager.sanitize(config)

        assertEquals(10.0, sanitized.financial.minWalletTopUpAmount, 0.0)
        assertTrue(
            "max must be a usable range above min",
            sanitized.financial.maxWalletTopUpAmount > sanitized.financial.minWalletTopUpAmount
        )
    }

    @Test
    fun sanitizeReplacesPlaceholderPlatformStringsSoTheyNeverReachCheckout() {
        val config = AppRemoteConfig().apply {
            platform = RemotePlatformSettings(
                currency = "string",
                currencySymbol = "string",
                timezone = "string",
                environment = "string",
                apiVersion = "string",
            )
        }

        val sanitized = RemoteConfigManager.sanitize(config)

        assertEquals("INR", sanitized.platform.currency)
        assertEquals("₹", sanitized.platform.currencySymbol)
        assertNotEquals("string", sanitized.platform.timezone.lowercase())
        assertNotEquals("string", sanitized.platform.environment.lowercase())
        assertNotEquals("string", sanitized.platform.apiVersion.lowercase())
    }

    @Test
    fun sanitizeLeavesLegitimateConfigUntouched() {
        // A correctly-populated config must pass through unchanged, so the guard
        // only ever corrects genuinely-invalid values.
        val config = AppRemoteConfig().apply {
            versions = RemoteAppVersions(minAndroidVersionCode = 1, latestAndroidVersionCode = 1)
            financial = RemoteFinancialSettings(
                minWalletTopUpAmount = 100.0,
                maxWalletTopUpAmount = 50000.0,
            )
            platform = RemotePlatformSettings(
                currency = "INR",
                currencySymbol = "₹",
                timezone = "Asia/Kolkata",
                environment = "PRODUCTION",
                apiVersion = "v1",
            )
        }

        val sanitized = RemoteConfigManager.sanitize(config)

        assertEquals(100.0, sanitized.financial.minWalletTopUpAmount, 0.0)
        assertEquals(50000.0, sanitized.financial.maxWalletTopUpAmount, 0.0)
        assertEquals("₹", sanitized.platform.currencySymbol)
        assertEquals("Asia/Kolkata", sanitized.platform.timezone)
    }

    @Test
    fun sanitizeKeepsUncappedLateCheckoutPenaltyUncapped() {
        // The backend uses maxLateCheckoutPenaltyPerMin == 0.0 to mean "no per-minute ceiling".
        // sanitize() must not "fix" that to a non-zero cap, or the app would silently disagree
        // with what the backend actually charges. Same for the overdue-checkout surcharge: both
        // are money-charging values and stay exactly as the server sent them.
        val config = AppRemoteConfig().apply {
            financial = RemoteFinancialSettings(
                maxLateCheckoutPenaltyPerMin = 0.0,
                overdueCheckoutPenaltyPercentage = 10.0,
            )
        }

        val sanitized = RemoteConfigManager.sanitize(config)

        assertEquals(0.0, sanitized.financial.maxLateCheckoutPenaltyPerMin, 0.0)
        assertEquals(10.0, sanitized.financial.overdueCheckoutPenaltyPercentage, 0.0)
    }

    @Test
    fun sanitizeFloorsNonsensicalOverdueFinalizeWindow() {
        // A zero window would mean "finalize instantly", making any countdown derived from it
        // meaningless. Unlike the penalty values this one is operational, so it is safe to floor.
        val config = AppRemoteConfig().apply {
            booking = RemoteBookingSettings(autoFinalizeOverdueCheckoutMinutes = 0)
        }

        val sanitized = RemoteConfigManager.sanitize(config)

        assertTrue(sanitized.booking.autoFinalizeOverdueCheckoutMinutes >= 1)
    }

    @Test
    fun bookingAndFinancialDefaultsMatchBackendDefaults() {
        // These mirror AppGlobalConfig on the backend. They are what the app falls back to when
        // /api/config/all is unreachable, so drift here means the app quietly assumes different
        // rules than the server enforces.
        val financial = RemoteFinancialSettings()
        val booking = RemoteBookingSettings()

        assertEquals(100.0, financial.minWalletTopUpAmount, 0.0)
        assertEquals(0.0, financial.maxLateCheckoutPenaltyPerMin, 0.0)
        assertEquals(10.0, financial.overdueCheckoutPenaltyPercentage, 0.0)
        assertEquals(180, booking.noShowGraceMinutes)
        assertTrue(booking.autoFinalizeOverdueCheckouts)
        assertEquals(90, booking.autoFinalizeOverdueCheckoutMinutes)
    }
}
