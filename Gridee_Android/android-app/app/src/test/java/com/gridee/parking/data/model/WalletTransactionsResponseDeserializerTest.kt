package com.gridee.parking.data.model

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletTransactionsResponseDeserializerTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(
            WalletTransactionsResponse::class.java,
            WalletTransactionsResponseDeserializer()
        )
        .create()

    @Test
    fun parsesRawTransactionArrayResponse() {
        val json = """
            [
              {
                "id": "tx_1",
                "type": "WALLET_TOP_UP",
                "status": "completed",
                "amount": 150.0,
                "timestamp": "2026-03-13T10:30:00Z"
              }
            ]
        """.trimIndent()

        val response = gson.fromJson(json, WalletTransactionsResponse::class.java)

        assertEquals(1, response.content.size)
        assertEquals("tx_1", response.content.first().id)
        assertEquals(1L, response.totalElements)
        assertTrue(response.last == true)
    }

    @Test
    fun parsesPagedTransactionObjectResponse() {
        val json = """
            {
              "content": [
                {
                  "id": "tx_2",
                  "type": "BOOKING_FEE",
                  "status": "completed",
                  "amount": -45.0,
                  "timestamp": "2026-03-13T11:00:00Z"
                }
              ],
              "totalElements": 1,
              "totalPages": 1,
              "number": 0,
              "last": true
            }
        """.trimIndent()

        val response = gson.fromJson(json, WalletTransactionsResponse::class.java)

        assertEquals(1, response.content.size)
        assertEquals("tx_2", response.content.first().id)
        assertEquals(1L, response.totalElements)
        assertEquals(1, response.totalPages)
        assertEquals(0, response.pageNumber)
        assertTrue(response.last == true)
    }

    @Test
    fun parsesWelcomeBonusAndBookingLinkedFields() {
        val json = """
            [
              {
                "id": "tx_3",
                "type": "WELCOME_BONUS",
                "status": "completed",
                "amount": 20.0,
                "description": "Welcome bonus credited",
                "timestamp": "2026-03-13T12:00:00Z",
                "bookingId": "booking_1",
                "lotId": "lot_1",
                "lotName": "Main Lot",
                "spotId": "spot_7",
                "referenceId": "ref_1",
                "currency": "INR",
                "method": "wallet",
                "gateway": "internal",
                "failureReason": null
              }
            ]
        """.trimIndent()

        val response = gson.fromJson(json, WalletTransactionsResponse::class.java)
        val transaction = response.content.first()

        assertEquals("WELCOME_BONUS", transaction.type)
        assertEquals("booking_1", transaction.bookingId)
        assertEquals("lot_1", transaction.lotId)
        assertEquals("Main Lot", transaction.lotName)
        assertEquals("spot_7", transaction.spotId)
        assertEquals("ref_1", transaction.referenceId)
        assertEquals("INR", transaction.currency)
        assertEquals("wallet", transaction.method)
        assertEquals("internal", transaction.gateway)
    }
}
