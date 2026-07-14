package com.gridee.parking.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendLotScopedModelsTest {

    private val gson = Gson()

    @Test
    fun checkInRequestSerializesParkingLotId() {
        val request = CheckInRequest(
            mode = CheckInMode.VEHICLE_NUMBER,
            vehicleNumber = "KA01AB1234",
            parkingLotId = "lot_1",
            parkingSpotId = "spot_7"
        )

        val json = gson.toJson(request)

        assertTrue(json.contains("\"parkingLotId\":\"lot_1\""))
        assertTrue(json.contains("\"parkingSpotId\":\"spot_7\""))
    }

    @Test
    fun createSupportTicketRequestSerializesParkingLotMetadata() {
        val request = CreateSupportTicketRequest(
            subject = "Gate issue",
            description = "The entry gate did not open.",
            priority = "HIGH",
            parkingLotId = "lot_1",
            parkingLotName = "Main Lot"
        )

        val json = gson.toJson(request)

        assertTrue(json.contains("\"parkingLotId\":\"lot_1\""))
        assertTrue(json.contains("\"parkingLotName\":\"Main Lot\""))
    }

    @Test
    fun supportTicketParsesParkingLotMetadata() {
        val json = """
            {
              "id": "ticket_1",
              "subject": "Gate issue",
              "description": "The entry gate did not open.",
              "status": "OPEN",
              "priority": "HIGH",
              "parkingLotId": "lot_1",
              "parkingLotName": "Main Lot"
            }
        """.trimIndent()

        val ticket = gson.fromJson(json, SupportTicket::class.java)

        assertEquals("lot_1", ticket.parkingLotId)
        assertEquals("Main Lot", ticket.parkingLotName)
    }
}
