package com.gridee.parking.data.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.model.ParkingSpotDeserializer
import com.gridee.parking.data.model.WalletTransactionsResponse
import com.gridee.parking.data.model.WalletTransactionsResponseDeserializer
import com.gridee.parking.utils.BackendTimestampParser
import java.lang.reflect.Type
import java.util.Date

object BackendGsonFactory {

    val gson: Gson by lazy {
        GsonBuilder()
            .setLenient()
            .registerTypeAdapter(Date::class.java, BackendDateAdapter())
            .registerTypeAdapter(ParkingSpot::class.java, ParkingSpotDeserializer())
            .registerTypeAdapter(
                WalletTransactionsResponse::class.java,
                WalletTransactionsResponseDeserializer()
            )
            .create()
    }
}

private class BackendDateAdapter : JsonDeserializer<Date>, JsonSerializer<Date> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Date? {
        if (json == null || json.isJsonNull || !json.isJsonPrimitive) {
            return null
        }

        val primitive = json.asJsonPrimitive
        if (primitive.isBoolean) {
            return null
        }

        val rawValue = when {
            primitive.isNumber -> primitive.asNumber.toString()
            primitive.isString -> primitive.asString
            else -> return null
        }.trim()

        if (rawValue.isEmpty()) {
            return null
        }

        val millis = BackendTimestampParser.parseToMillis(rawValue, INVALID_TIMESTAMP)
        return millis.takeUnless { it == INVALID_TIMESTAMP }?.let(::Date)
    }

    override fun serialize(
        src: Date?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return src?.let { JsonPrimitive(it.time) } ?: JsonNull.INSTANCE
    }

    companion object {
        private const val INVALID_TIMESTAMP = Long.MIN_VALUE
    }
}
