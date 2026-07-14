package com.gridee.parking.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type

/**
 * Accepts both wallet transaction response shapes used by the backend:
 * - a raw array of transactions
 * - a paged object with `content`
 */
class WalletTransactionsResponseDeserializer : JsonDeserializer<WalletTransactionsResponse> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): WalletTransactionsResponse {
        if (json == null || json.isJsonNull || context == null) {
            return WalletTransactionsResponse()
        }

        if (json.isJsonArray) {
            val transactions = json.asJsonArray.mapNotNull { element ->
                deserializeTransaction(element, context)
            }
            return WalletTransactionsResponse(
                content = transactions,
                totalElements = transactions.size.toLong(),
                totalPages = if (transactions.isEmpty()) 0 else 1,
                pageNumber = 0,
                last = true
            )
        }

        if (!json.isJsonObject) {
            return WalletTransactionsResponse()
        }

        val obj = json.asJsonObject
        val transactions = extractTransactionArray(obj)?.mapNotNull { element ->
            deserializeTransaction(element, context)
        }.orEmpty()

        return WalletTransactionsResponse(
            content = transactions,
            totalElements = readLong(obj, "totalElements")
                ?: readLong(obj, "total")
                ?: transactions.size.toLong(),
            totalPages = readInt(obj, "totalPages"),
            pageNumber = readInt(obj, "number") ?: readInt(obj, "page"),
            last = readBoolean(obj, "last")
        )
    }

    private fun extractTransactionArray(obj: JsonObject) = listOf("content", "transactions", "data")
        .firstNotNullOfOrNull { key ->
            obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
        }

    private fun deserializeTransaction(
        element: JsonElement,
        context: JsonDeserializationContext
    ): WalletTransaction? {
        return try {
            context.deserialize<WalletTransaction>(element, WalletTransaction::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun readLong(obj: JsonObject, key: String): Long? {
        val element = obj.get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asLong
            primitive.isString -> primitive.asString.toLongOrNull()
            else -> null
        }
    }

    private fun readInt(obj: JsonObject, key: String): Int? {
        val element = obj.get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asInt
            primitive.isString -> primitive.asString.toIntOrNull()
            else -> null
        }
    }

    private fun readBoolean(obj: JsonObject, key: String): Boolean? {
        val element = obj.get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isString -> primitive.asString.toBooleanStrictOrNull()
            else -> null
        }
    }
}
