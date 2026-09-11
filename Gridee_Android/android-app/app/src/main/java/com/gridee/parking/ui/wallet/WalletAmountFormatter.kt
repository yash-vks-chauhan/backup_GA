package com.gridee.parking.ui.wallet

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/** Whole-rupee editing, kept separate from Android so no payment amount is silently coerced. */
internal class WalletAmountFormatter(locale: Locale) {
    private val symbols = DecimalFormatSymbols.getInstance(locale)
    private val numberFormat = NumberFormat.getIntegerInstance(locale)

    enum class Error { DECIMAL, INVALID, TOO_LARGE }
    data class Result(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int,
        val amount: Long?,
        val error: Error? = null,
    )

    fun format(
        raw: String,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
        allowRegrouping: Boolean = false,
    ): Result {
        fun invalid(error: Error) = Result(raw, selectionStart, selectionEnd, null, error)
        if (raw.isEmpty()) return Result("", 0, 0, null)
        // Never strip a decimal separator, currency symbol, sign or arbitrary pasted text.
        if (raw.any { it == symbols.decimalSeparator || (it == '.' && symbols.groupingSeparator != '.') }) {
            return invalid(Error.DECIMAL)
        }
        if (raw.any { !it.isDigit() && it != symbols.groupingSeparator }) return invalid(Error.INVALID)
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return invalid(Error.INVALID)
        val ascii = digits.map { Character.digit(it, 10).digitToChar() }.joinToString("")
        val amount = ascii.toLongOrNull() ?: return invalid(Error.TOO_LARGE)
        // Above this bound Double cannot exactly represent every integer sent to the backend.
        if (amount > 9_007_199_254_740_991L) return invalid(Error.TOO_LARGE)
        val formatted = numberFormat.format(amount)
        if (!allowRegrouping && raw.contains(symbols.groupingSeparator) && raw != formatted) {
            return invalid(Error.INVALID)
        }
        val removedZeros = (digits.length - digits.trimStart { Character.digit(it, 10) == 0 }.length)
            .coerceAtMost(digits.length - 1)
        fun mapSelection(position: Int): Int {
            val before = raw.take(position.coerceIn(0, raw.length)).count(Char::isDigit)
            val target = (before - removedZeros).coerceAtLeast(0)
            if (target == 0) return 0
            var seen = 0
            for ((index, character) in formatted.withIndex()) {
                if (character.isDigit() && ++seen == target) return index + 1
            }
            return formatted.length
        }
        return Result(formatted, mapSelection(selectionStart), mapSelection(selectionEnd), amount)
    }

    fun display(amount: Number): String = numberFormat.format(amount)
}
