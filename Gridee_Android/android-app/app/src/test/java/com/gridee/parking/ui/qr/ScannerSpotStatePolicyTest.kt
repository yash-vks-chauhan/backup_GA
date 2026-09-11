package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScannerSpotStatePolicyTest {

    @Test
    fun `rotation restores scanner-selected spot in the authenticated lot`() {
        val resolved = ScannerSpotStatePolicy.resolve(
            assignedLotId = "lot-a",
            restored = ScannerSpotState("new-spot", "A-12", "lot-a"),
            launched = ScannerSpotState("old-spot", "A-01", "lot-a"),
        )

        assertEquals("new-spot", resolved.spotId)
        assertEquals("A-12", resolved.spotName)
        assertEquals("lot-a", resolved.lotId)
    }

    @Test
    fun `changed assignment rejects both restored and cross-lot launch context`() {
        val resolved = ScannerSpotStatePolicy.resolve(
            assignedLotId = "lot-b",
            restored = ScannerSpotState("old-spot", "A-01", "lot-a"),
            launched = ScannerSpotState("launch-spot", "A-02", "lot-a"),
        )

        assertNull(resolved.spotId)
        assertNull(resolved.spotName)
        assertEquals("lot-b", resolved.lotId)
    }

    @Test
    fun `missing authenticated lot never trusts an intent spot`() {
        val resolved = ScannerSpotStatePolicy.resolve(
            assignedLotId = "Parking Lot",
            restored = null,
            launched = ScannerSpotState("spot-1", "A-01", "lot-a"),
        )

        assertNull(resolved.spotId)
        assertNull(resolved.spotName)
        assertNull(resolved.lotId)
    }
}
