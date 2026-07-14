package com.gridee.parking.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type

/**
 * Lenient parser for ParkingSpot that tolerates legacy backend payloads:
 * - `available` can be boolean or number
 * - `status` may be missing/null
 * - `lotName` may be present instead of `lotId`
 */
class ParkingSpotDeserializer : JsonDeserializer<ParkingSpot> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ParkingSpot {
        val obj = json.asJsonObject

        val spotCode = obj.readString("spotId", "spotCode", "code")
        val id = obj.readString("id", "_id")
            ?: spotCode
            ?: ""
        val slotId = obj.get("slotId")?.takeIf { !it.isJsonNull }?.let { element ->
            if (!element.isJsonPrimitive) return@let null
            val primitive = element.asJsonPrimitive
            when {
                primitive.isNumber -> primitive.asInt
                primitive.isString -> primitive.asString.toIntOrNull()
                else -> null
            }
        }
        val slotName = obj.readString("slotName")
        val lotId = obj.readString("lotId", "parkingLotId") ?: ""
        val lotName = obj.readString("lotName", "parkingLotName")
        val name = obj.readString("name")
        val zoneName = obj.readString("zoneName")
        val capacity = obj.get("capacity")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        fun readDouble(key: String): Double? {
            val element = obj.get(key) ?: return null
            if (element.isJsonNull) return null
            if (!element.isJsonPrimitive) return null
            val primitive = element.asJsonPrimitive
            return when {
                primitive.isNumber -> primitive.asDouble
                primitive.isString -> primitive.asString.toDoubleOrNull()
                else -> null
            }
        }

        val availableElement = obj.get("available")
        val available = when {
            availableElement == null || availableElement.isJsonNull -> 0
            availableElement.isJsonPrimitive && availableElement.asJsonPrimitive.isBoolean ->
                if (availableElement.asBoolean) 1 else 0
            availableElement.isJsonPrimitive && availableElement.asJsonPrimitive.isNumber ->
                availableElement.asInt
            availableElement.isJsonPrimitive && availableElement.asJsonPrimitive.isString -> {
                val value = availableElement.asString.trim()
                value.toIntOrNull()
                    ?: value.toBooleanStrictOrNull()?.let { if (it) 1 else 0 }
                    ?: 0
            }
            else -> 0
        }

        val status = obj.readString("status")
            ?: if (available > 0) "available" else "unavailable"
        val bookingRate = readDouble("bookingRate")
            ?: readDouble("hourlyRate")
            ?: 0.0

        return ParkingSpot(
            id = id,
            lotId = lotId,
            lotName = lotName,
            spotCode = spotCode,
            slotId = slotId,
            slotName = slotName,
            name = name,
            zoneName = zoneName,
            capacity = capacity,
            available = available,
            status = status,
            bookingRate = bookingRate
        )
    }

    private fun JsonObject.readString(vararg keys: String): String? {
        for (key in keys) {
            val element = get(key) ?: continue
            if (element.isJsonNull) continue
            if (element.isJsonPrimitive) {
                val value = element.asString.trim()
                if (value.isNotEmpty()) return value
            }
            if (element.isJsonObject) {
                val objectValue = element.asJsonObject
                val objectId = objectValue.get("\$oid")
                    ?: objectValue.get("oid")
                    ?: objectValue.get("value")
                if (objectId != null && objectId.isJsonPrimitive) {
                    val value = objectId.asString.trim()
                    if (value.isNotEmpty()) return value
                }
            }
        }
        return null
    }
}
