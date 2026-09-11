package com.gridee.parking.ui.qr

import com.gridee.parking.utils.VehicleNumberType
import com.gridee.parking.utils.VehicleNumberValidator

internal data class NormalizedPlateCandidate(
    val value: String,
    val correctedCharacters: Int,
)

/** Normalizes, safely corrects, and format-scores an Indian registration candidate. */
internal class VehiclePlateInterpreter {
    // Legacy series-less plates remain valid, but require operator confirmation because their
    // shorter structure is easier for unrelated text to match accidentally.
    private val regularStrongPattern = Regex("^[A-Z]{2}\\d{2}[A-Z]{1,3}\\d{4}$")
    private val supportedTemplates: List<String> = buildPlateTemplates()

    fun normalizeCandidate(raw: String): NormalizedPlateCandidate? {
        var cleaned = retainAsciiLettersAndDigits(VehicleNumberValidator.normalize(raw))
        if (cleaned.startsWith("IND") && cleaned.length > 3) {
            cleaned = cleaned.removePrefix("IND")
        }
        if (cleaned.length !in 6..16) return null

        var hasLetter = false
        var hasDigit = false
        for (character in cleaned) {
            hasLetter = hasLetter || character in 'A'..'Z'
            hasDigit = hasDigit || character in '0'..'9'
        }
        if (!hasLetter || !hasDigit) return null

        return correct(cleaned)
    }

    fun correct(raw: String): NormalizedPlateCandidate? {
        if (VehicleNumberValidator.isValid(raw)) {
            return NormalizedPlateCandidate(raw, correctedCharacters = 0)
        }

        var best: NormalizedPlateCandidate? = null
        var bestScore = Int.MIN_VALUE
        supportedTemplates.forEach { template ->
            if (template.length != raw.length) return@forEach
            val candidate = coerceToTemplate(raw, template) ?: return@forEach
            if (!VehicleNumberValidator.isValid(candidate.value)) return@forEach
            val candidateScore = score(candidate.value) - (candidate.correctedCharacters * 2)
            if (candidateScore > bestScore) {
                best = candidate
                bestScore = candidateScore
            }
        }
        return best
    }

    fun score(value: String): Int {
        var letters = 0
        var digits = 0
        for (character in value) {
            if (character in 'A'..'Z') letters++
            if (character in '0'..'9') digits++
        }

        var score = value.length * 2 + letters + digits
        if (value.length >= 2 && value[0].isLetter() && value[1].isLetter()) score += 4
        score += when (VehicleNumberValidator.parseType(value)) {
            VehicleNumberType.BH -> 28
            VehicleNumberType.TEMPORARY -> 30
            VehicleNumberType.VINTAGE -> 30
            VehicleNumberType.REGULAR -> 22
            VehicleNumberType.UNKNOWN -> -6
        }
        if (regularStrongPattern.matches(value)) score += 12
        if (value.contains("BH")) score += 6
        if (value.startsWith("T")) score += 4
        if (value.contains("VA")) score += 5
        return score
    }

    fun isStrongAutomaticCandidate(value: String): Boolean {
        if (!VehicleNumberValidator.isValid(value)) return false
        return when (VehicleNumberValidator.parseType(value)) {
            VehicleNumberType.BH,
            VehicleNumberType.TEMPORARY,
            VehicleNumberType.VINTAGE -> true
            VehicleNumberType.REGULAR -> regularStrongPattern.matches(value)
            VehicleNumberType.UNKNOWN -> false
        }
    }

    fun requiresOperatorConfirmation(
        candidate: NormalizedPlateCandidate,
        forceManualConfirmation: Boolean = false,
    ): Boolean {
        return forceManualConfirmation ||
            candidate.correctedCharacters > 0 ||
            !isStrongAutomaticCandidate(candidate.value)
    }

    private fun retainAsciiLettersAndDigits(value: String): String {
        val firstNoiseIndex = value.indexOfFirst { it !in 'A'..'Z' && it !in '0'..'9' }
        if (firstNoiseIndex < 0) return value
        return buildString(value.length) {
            value.forEach { character ->
                if (character in 'A'..'Z' || character in '0'..'9') append(character)
            }
        }
    }

    private fun coerceToTemplate(source: String, template: String): NormalizedPlateCandidate? {
        if (source.length != template.length) return null
        val builder = StringBuilder(template.length)
        var corrections = 0
        for (index in template.indices) {
            val sourceCharacter = source[index]
            val correctedCharacter = when (val templateCharacter = template[index]) {
                'L' -> coerceToLetter(sourceCharacter)
                'D' -> coerceToDigit(sourceCharacter)
                else -> coerceToLiteral(sourceCharacter, templateCharacter)
            } ?: return null
            if (correctedCharacter != sourceCharacter) corrections++
            builder.append(correctedCharacter)
        }
        return NormalizedPlateCandidate(builder.toString(), corrections)
    }

    private fun coerceToDigit(character: Char): Char? = when (val upper = character.uppercaseChar()) {
        in '0'..'9' -> upper
        'O', 'Q' -> '0'
        'I', 'L' -> '1'
        'Z' -> '2'
        'S' -> '5'
        'G' -> '6'
        'B' -> '8'
        else -> null
    }

    private fun coerceToLetter(character: Char): Char? = when (val upper = character.uppercaseChar()) {
        in 'A'..'Z' -> upper
        '0' -> 'O'
        '1' -> 'I'
        '2' -> 'Z'
        '4' -> 'A'
        '5' -> 'S'
        '6' -> 'G'
        '7' -> 'T'
        '8' -> 'B'
        else -> null
    }

    private fun coerceToLiteral(sourceCharacter: Char, literal: Char): Char? {
        if (sourceCharacter.uppercaseChar() == literal) return literal
        return when {
            literal.isDigit() -> coerceToDigit(sourceCharacter)?.takeIf { it == literal }
            literal.isLetter() -> coerceToLetter(sourceCharacter)?.takeIf { it == literal }
            else -> null
        }
    }

    private fun buildPlateTemplates(): List<String> {
        val templates = linkedSetOf(
            "DDBHDDDDL",
            "DDBHDDDDLL",
            "TDDDDLLDDDDL",
            "TDDDDLLDDDDLL",
            "LLVALLDDDD",
        )
        for (districtDigits in 1..2) {
            for (seriesLetters in 0..3) {
                val numberDigitRange = if (seriesLetters == 0) 4..4 else 1..4
                for (numberDigits in numberDigitRange) {
                    templates += buildString {
                        append("LL")
                        append("D".repeat(districtDigits))
                        append("L".repeat(seriesLetters))
                        append("D".repeat(numberDigits))
                    }
                }
            }
        }
        return templates.toList()
    }
}
