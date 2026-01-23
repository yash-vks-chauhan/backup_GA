package com.gridee.parking.utils

import com.google.android.play.core.install.model.AppUpdateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InAppUpdateControllerTest {

    @Test
    fun chooseUpdateType_prefersImmediate_whenAllowed() {
        val result = InAppUpdateController.chooseUpdateType(
            preferImmediate = true,
            immediateAllowed = true,
            flexibleAllowed = true,
        )
        assertEquals(AppUpdateType.IMMEDIATE, result)
    }

    @Test
    fun chooseUpdateType_fallsBackToFlexible_whenImmediateNotAllowed() {
        val result = InAppUpdateController.chooseUpdateType(
            preferImmediate = true,
            immediateAllowed = false,
            flexibleAllowed = true,
        )
        assertEquals(AppUpdateType.FLEXIBLE, result)
    }

    @Test
    fun chooseUpdateType_usesImmediate_whenOnlyImmediateAllowed_evenIfNotPreferred() {
        val result = InAppUpdateController.chooseUpdateType(
            preferImmediate = false,
            immediateAllowed = true,
            flexibleAllowed = false,
        )
        assertEquals(AppUpdateType.IMMEDIATE, result)
    }

    @Test
    fun chooseUpdateType_returnsNull_whenNoTypeAllowed() {
        val result = InAppUpdateController.chooseUpdateType(
            preferImmediate = true,
            immediateAllowed = false,
            flexibleAllowed = false,
        )
        assertNull(result)
    }
}

