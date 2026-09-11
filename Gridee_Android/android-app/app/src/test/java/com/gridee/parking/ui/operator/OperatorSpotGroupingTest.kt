package com.gridee.parking.ui.operator

import com.gridee.parking.data.model.ParkingSpot
import org.junit.Assert.assertEquals
import org.junit.Test

class OperatorSpotGroupingTest {

    @Test
    fun separatesAndSortsSpotsByParkingLot() {
        val groups = OperatorSpotGrouping.group(
            listOf(
                spot("spot-2", "lot-b", "Corporate South", "B2"),
                spot("spot-1", "lot-a", "Corporate North", "A2"),
                spot("spot-3", "lot-a", "Corporate North", "A1"),
            )
        )

        assertEquals(listOf("lot-a", "lot-b"), groups.map { it.lotId })
        assertEquals("Corporate North", groups.first().title)
        assertEquals(listOf("spot-3", "spot-1"), groups.first().spots.map { it.id })
    }

    @Test
    fun excludesUnscopedRowsAndUsesLotAndSpotAsSelectionIdentity() {
        val groups = OperatorSpotGrouping.group(
            listOf(
                spot("shared", "lot-a", "Lot A", "A1"),
                spot("shared", "lot-b", "Lot B", "B1"),
                spot("unscoped", "", null, "Unknown"),
            )
        )

        assertEquals(2, groups.sumOf { it.spots.size })
        val first = OperatorSpotGrouping.key(groups[0].spots[0])
        val second = OperatorSpotGrouping.key(groups[1].spots[0])
        assertEquals("shared", first.spotId)
        assertEquals("shared", second.spotId)
        assertEquals("lot-a", first.lotId)
        assertEquals("lot-b", second.lotId)
    }

    private fun spot(
        id: String,
        lotId: String,
        lotName: String?,
        name: String,
    ) = ParkingSpot(
        id = id,
        lotId = lotId,
        lotName = lotName,
        name = name,
        status = "available",
    )
}
