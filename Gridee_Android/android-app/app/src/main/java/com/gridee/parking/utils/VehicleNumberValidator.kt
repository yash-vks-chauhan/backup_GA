package com.gridee.parking.utils

import java.util.Locale

enum class VehicleNumberType {
    REGULAR,
    BH,
    TEMPORARY,
    VINTAGE,
    UNKNOWN
}

/**
 * Validates the Indian vehicle registration formats that the app can support
 * reliably from text input alone.
 */
object VehicleNumberValidator {

    private const val EXAMPLES = "MH12AB1234, 22BH1234A, T0826KA1234AB, KAVAAB1234"

    private val separatorPattern = Regex("[\\s\\-./]")
    private val bhPattern = Regex("^\\d{2}BH\\d{4}[A-HJ-NP-Z]{1,2}$")
    private val temporaryPattern = Regex("^T\\d{2}\\d{2}[A-Z]{2}\\d{4}[A-HJ-NP-Z]{1,2}$")
    private val vintagePattern = Regex("^[A-Z]{2}VA[A-Z]{2}\\d{4}$")
    private val bhDisplayPattern = Regex("^(\\d{2})(BH)(\\d{4})([A-HJ-NP-Z]{1,2})$")
    private val temporaryDisplayPattern = Regex("^(T\\d{4})([A-Z]{2})(\\d{4})([A-HJ-NP-Z]{1,2})$")
    private val vintageDisplayPattern = Regex("^([A-Z]{2})(VA)([A-Z]{2})(\\d{4})$")

    // Intentionally broad: Indian civilian registrations are not safely limited
    // to one fixed 9-10 character shape across current and legacy series.
    private val regularPattern = Regex("^[A-Z]{2}\\d{1,2}[A-Z]{1,3}\\d{1,4}$")
    private val regularDisplayPattern = Regex("^([A-Z]{2})(\\d{1,2})([A-Z]{1,3})(\\d{1,4})$")

    fun normalize(number: String): String = number
        .trim()
        .uppercase(Locale.ROOT)
        .replace(separatorPattern, "")

    fun formatForDisplay(number: String): String {
        val normalized = normalize(number)
        if (normalized.isEmpty()) return ""

        return when (parseType(normalized)) {
            VehicleNumberType.REGULAR -> {
                regularDisplayPattern.matchEntire(normalized)
                    ?.destructured
                    ?.let { (state, district, series, digits) ->
                        "$state $district $series $digits"
                    }
                    ?: normalized
            }
            VehicleNumberType.BH -> {
                bhDisplayPattern.matchEntire(normalized)
                    ?.destructured
                    ?.let { (year, bh, digits, suffix) ->
                        "$year $bh $digits $suffix"
                    }
                    ?: normalized
            }
            VehicleNumberType.TEMPORARY -> {
                temporaryDisplayPattern.matchEntire(normalized)
                    ?.destructured
                    ?.let { (prefix, state, digits, suffix) ->
                        "$prefix $state $digits $suffix"
                    }
                    ?: normalized
            }
            VehicleNumberType.VINTAGE -> {
                vintageDisplayPattern.matchEntire(normalized)
                    ?.destructured
                    ?.let { (state, vintage, series, digits) ->
                        "$state $vintage $series $digits"
                    }
                    ?: normalized
            }
            VehicleNumberType.UNKNOWN -> normalized
        }
    }

    fun parseType(number: String): VehicleNumberType {
        val normalized = normalize(number)
        if (normalized.isEmpty()) return VehicleNumberType.UNKNOWN

        return when {
            bhPattern.matches(normalized) -> VehicleNumberType.BH
            temporaryPattern.matches(normalized) -> VehicleNumberType.TEMPORARY
            vintagePattern.matches(normalized) -> VehicleNumberType.VINTAGE
            regularPattern.matches(normalized) -> VehicleNumberType.REGULAR
            else -> VehicleNumberType.UNKNOWN
        }
    }

    fun isValid(number: String): Boolean = parseType(number) != VehicleNumberType.UNKNOWN

    fun areEquivalent(first: String, second: String): Boolean {
        val normalizedFirst = normalize(first)
        val normalizedSecond = normalize(second)
        return normalizedFirst.isNotEmpty() && normalizedFirst == normalizedSecond
    }

    fun containsEquivalent(numbers: Iterable<String>, candidate: String): Boolean {
        val normalizedCandidate = normalize(candidate)
        if (normalizedCandidate.isEmpty()) return false

        return numbers.any { normalize(it) == normalizedCandidate }
    }

    fun getError(number: String): String? {
        val normalized = normalize(number)

        if (normalized.isEmpty()) return "Please enter a vehicle registration number"
        if (normalized.length < 6) return "Enter a valid Indian vehicle registration number"

        return if (isValid(normalized)) {
            null
        } else {
            "Enter a valid Indian vehicle registration number. Examples: $EXAMPLES"
        }
    }
}
