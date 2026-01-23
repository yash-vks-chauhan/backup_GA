package com.gridee.parking.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parses backend timestamps into a [Date] using a tolerant set of ISO-like formats.
 *
 * Important: when the backend timestamp does not include an explicit timezone, we treat it as UTC.
 * This avoids common "hours off" issues when the server stores/sends UTC but omits the zone.
 */
object BackendTimestampParser {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private val patternsWithZone = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ssXX",
        "yyyy-MM-dd'T'HH:mm:ssX"
    )

    private val patternsWithoutZone = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    fun parse(timestamp: String?, nowMillis: Long = System.currentTimeMillis()): Date {
        return Date(parseToMillis(timestamp, nowMillis))
    }

    fun parseToMillis(timestamp: String?, nowMillis: Long = System.currentTimeMillis()): Long {
        val value = timestamp?.trim().orEmpty()
        if (value.isEmpty()) return nowMillis

        // Epoch seconds/millis (as a string) support.
        if (value.all { it == '-' || it.isDigit() }) {
            val epoch = value.toLongOrNull()
            if (epoch != null) {
                // Heuristic:
                // - 10 digits: seconds
                // - 13 digits: millis
                // - 16 digits: micros (convert to millis)
                val absEpoch = kotlin.math.abs(epoch)
                val millis = when {
                    absEpoch >= 1_000_000_000_000_000L -> epoch / 1000L
                    absEpoch >= 1_000_000_000_000L -> epoch
                    else -> epoch * 1000L
                }
                return millis
            }
        }

        patternsWithZone.firstNotNullOfOrNull { pattern ->
            parseWithPattern(value, pattern, utc)
        }?.let { return it.time }

        // No timezone in the string → assume UTC.
        patternsWithoutZone.firstNotNullOfOrNull { pattern ->
            parseWithPattern(value, pattern, utc)
        }?.let { return it.time }

        return nowMillis
    }

    private fun parseWithPattern(value: String, pattern: String, tz: TimeZone): Date? {
        return try {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = tz
                isLenient = false
            }
            formatter.parse(value)
        } catch (_: Exception) {
            null
        }
    }
}

