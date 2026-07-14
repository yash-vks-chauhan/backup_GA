package com.gridee.parking.config

import com.gridee.parking.data.model.RemoteAppVersions
import org.junit.Assert.assertFalse
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
}
