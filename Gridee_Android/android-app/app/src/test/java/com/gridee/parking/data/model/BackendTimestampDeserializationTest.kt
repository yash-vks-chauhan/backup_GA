package com.gridee.parking.data.model

import com.google.gson.JsonParser
import com.gridee.parking.data.api.BackendGsonFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendTimestampDeserializationTest {

    @Test
    fun parsesBookingPayloadWhenBackendReturnsEpochMillis() {
        val createdAt = 1744518600000L
        val checkInTime = 1744522200000L
        val checkOutTime = 1744525800000L

        val json = """
            [
              {
                "id": "booking_1",
                "userId": "user_1",
                "lotId": "lot_1",
                "spotId": "spot_7",
                "status": "PENDING",
                "amount": 120.0,
                "createdAt": $createdAt,
                "updatedAt": $createdAt,
                "checkInTime": $checkInTime,
                "checkOutTime": $checkOutTime
              }
            ]
        """.trimIndent()

        val bookings = BookingPayloadParser.parseBookings(JsonParser.parseString(json))

        assertEquals(1, bookings.size)
        assertEquals("booking_1", bookings.first().id)
        assertEquals(createdAt, bookings.first().createdAt?.time)
        assertEquals(checkInTime, bookings.first().checkInTime?.time)
        assertEquals(checkOutTime, bookings.first().checkOutTime?.time)
    }

    @Test
    fun parsesUpdatedAuthResponseFieldsAndNumericUserTimestamps() {
        val createdAt = 1744518600000L
        val updatedAt = 1744522200000L

        val json = """
            {
              "token": "jwt-token",
              "tokenType": "Bearer",
              "profileComplete": false,
              "requiresProfileCompletion": true,
              "isNewUser": true,
              "message": "Login successful",
              "user": {
                "id": "user_1",
                "name": "Test User",
                "email": "test@example.com",
                "phone": "9999999999",
                "vehicleNumbers": ["MH01AB1234"],
                "firstUser": true,
                "walletCoins": 15,
                "createdAt": $createdAt,
                "updatedAt": $updatedAt,
                "role": "USER",
                "parkingLotId": "lot_1",
                "parkingLotName": "Main Lot",
                "active": true
              }
            }
        """.trimIndent()

        val response = BackendGsonFactory.gson.fromJson(json, AuthResponse::class.java)

        assertEquals("jwt-token", response.token)
        assertEquals("Bearer", response.tokenType)
        assertFalse(response.profileComplete!!)
        assertTrue(response.requiresProfileCompletion!!)
        assertTrue(response.isNewUser!!)
        assertEquals(createdAt.toString(), response.user.createdAt)
        assertEquals(updatedAt.toString(), response.user.updatedAt)
        assertNotNull(response.user.vehicleNumbers)
    }

    @Test
    fun parsesAuthResponseWhenUserTimestampsAreIsoStrings() {
        val createdAt = "2026-04-10T14:30:00.000+05:30"
        val updatedAt = "2026-04-10T15:30:00.000+05:30"

        val json = """
            {
              "token": "jwt-token",
              "tokenType": "Bearer",
              "profileComplete": true,
              "requiresProfileCompletion": false,
              "isNewUser": false,
              "message": "Login successful",
              "user": {
                "id": "user_1",
                "name": "Test User",
                "email": "test@example.com",
                "phone": "9999999999",
                "vehicleNumbers": ["MH01AB1234"],
                "firstUser": true,
                "walletCoins": 15,
                "createdAt": "$createdAt",
                "updatedAt": "$updatedAt",
                "role": "USER",
                "parkingLotId": "lot_1",
                "parkingLotName": "Main Lot",
                "active": true
              }
            }
        """.trimIndent()

        val response = BackendGsonFactory.gson.fromJson(json, AuthResponse::class.java)

        assertEquals("jwt-token", response.token)
        assertEquals(createdAt, response.user.createdAt)
        assertEquals(updatedAt, response.user.updatedAt)
    }

    @Test
    fun parsesMfaRequiredAuthResponseWithNullToken() {
        val json = """
            {
              "token": null,
              "tokenType": "Bearer",
              "mfaRequired": true,
              "mfaEnabled": true,
              "message": "MFA required",
              "user": {
                "id": "admin_1",
                "name": "Admin User",
                "email": "admin@example.com",
                "phone": "9999999999",
                "role": "ADMIN",
                "active": true
              }
            }
        """.trimIndent()

        val response = BackendGsonFactory.gson.fromJson(json, AuthResponse::class.java)

        assertNull(response.token)
        assertTrue(response.mfaRequired!!)
        assertTrue(response.mfaEnabled!!)
        assertEquals("ADMIN", response.role)
    }
}
