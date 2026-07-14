package com.gridee.parking.data.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.gridee.parking.data.api.BackendGsonFactory

object BookingPayloadParser {
    private val gson = BackendGsonFactory.gson

    fun parseBookings(payload: JsonElement?): List<Booking> {
        val bookingElements = extractBookingElements(payload) ?: return emptyList()
        return bookingElements.mapNotNull { parseBooking(it) }
    }

    private fun extractBookingElements(payload: JsonElement?): List<JsonElement>? {
        if (payload == null || payload.isJsonNull) return null

        if (payload.isJsonArray) {
            return payload.asJsonArray.toList()
        }
        if (!payload.isJsonObject) {
            return null
        }

        val obj = payload.asJsonObject
        val arrayKeys = listOf("bookings", "history", "data", "content", "items", "results", "records")
        arrayKeys.forEach { key ->
            val value = obj.get(key)
            if (value != null && value.isJsonArray) {
                return value.asJsonArray.toList()
            }
        }

        val nestedObjectKeys = listOf("data", "payload", "result", "response")
        nestedObjectKeys.forEach { key ->
            val nested = obj.get(key)
            val extracted = extractBookingElements(nested)
            if (!extracted.isNullOrEmpty()) {
                return extracted
            }
        }

        for ((_, value) in obj.entrySet()) {
            if (value.isJsonArray) {
                return value.asJsonArray.toList()
            }
        }

        for ((_, value) in obj.entrySet()) {
            if (value.isJsonObject) {
                val extracted = extractBookingElements(value)
                if (!extracted.isNullOrEmpty()) {
                    return extracted
                }
            }
        }

        return if (looksLikeBooking(obj)) listOf(payload) else null
    }

    private fun parseBooking(json: JsonElement): Booking? {
        if (!json.isJsonObject) return null
        val obj = json.asJsonObject
        if (!looksLikeBooking(obj)) return null
        return try {
            gson.fromJson(normalizeBookingObject(obj), Booking::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeBookingObject(obj: JsonObject): JsonObject {
        val normalized = obj.deepCopy()
        val recoveredId = if (!normalized.has("id") || normalized.get("id").isJsonNull) {
            readString(obj.get("_id"))
                ?: readString(obj.get("bookingId"))
                ?: readString(obj.get("bookingID"))
        } else {
            null
        }
        recoveredId?.let { id ->
            normalized.addProperty("id", id)
        }
        return normalized
    }

    private fun readString(value: JsonElement?): String? {
        if (value == null || value.isJsonNull) return null
        if (value.isJsonPrimitive) {
            return value.asString.trim().takeIf { it.isNotEmpty() }
        }
        if (value.isJsonObject) {
            val obj = value.asJsonObject
            listOf("\$oid", "oid", "value", "id").forEach { key ->
                readString(obj.get(key))?.let { return it }
            }
        }
        return null
    }

    private fun looksLikeBooking(obj: JsonObject): Boolean {
        return obj.has("id") ||
            obj.has("_id") ||
            obj.has("bookingId") ||
            obj.has("bookingID") ||
            obj.has("spotId") ||
            obj.has("lotId") ||
            obj.has("status") ||
            obj.has("checkInTime") ||
            obj.has("checkOutTime")
    }
}
