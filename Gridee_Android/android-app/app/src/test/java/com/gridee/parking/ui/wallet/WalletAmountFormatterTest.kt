package com.gridee.parking.ui.wallet

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class WalletAmountFormatterTest {
    private val formatter = WalletAmountFormatter(Locale.US)

    @Test fun `decimal input is never turned into a larger integer`() {
        listOf("10.5", "10.50", "₹10.50", "0.5", "1,000.50").forEach {
            val result = formatter.format(it, it.length)
            assertEquals(it, result.text)
            assertNull(result.amount)
            assertEquals(WalletAmountFormatter.Error.DECIMAL, result.error)
        }
    }

    @Test fun `ambiguous and signed pastes are rejected intact`() {
        listOf("10,5", "1,00", "1e3", "-100", "+100", "₹100", "100\n200", "100 abc").forEach {
            val result = formatter.format(it, it.length)
            assertEquals(it, result.text)
            assertNull(result.amount)
        }
    }

    @Test fun `whole amounts and formatted pastes remain exact`() {
        listOf("10" to 10L, "50000" to 50000L, "50,000" to 50000L, "50001" to 50001L).forEach { (raw, amount) ->
            assertEquals(amount, formatter.format(raw, raw.length).amount)
        }
        assertEquals("50,000", formatter.format("50000", 5).text)
        assertEquals(6, formatter.format("50000", 5).selectionStart)
    }

    @Test fun `mid-string typing and selection retain digit offsets`() {
        val edited = formatter.format("1,0200", 4, allowRegrouping = true)
        assertEquals("10,200", edited.text)
        assertEquals(4, edited.selectionStart)
        val selected = formatter.format("12345", 1, 4)
        assertEquals("12,345", selected.text)
        assertEquals(1, selected.selectionStart)
        assertEquals(5, selected.selectionEnd)
    }

    @Test fun `empty zero and leading zero input behave predictably`() {
        assertNull(formatter.format("", 0).amount)
        val zero = formatter.format("000", 3)
        assertEquals("0", zero.text)
        assertEquals(1, zero.selectionStart)
        val leading = formatter.format("00010", 5)
        assertEquals("10", leading.text)
        assertEquals(2, leading.selectionStart)
    }

    @Test fun `overflow never rounds or produces a payment amount`() {
        listOf("9999999999999999999999999999", "9007199254740992").forEach {
            assertNull(formatter.format(it, it.length).amount)
            assertEquals(WalletAmountFormatter.Error.TOO_LARGE, formatter.format(it, 0).error)
        }
    }

    @Test fun `locale separators and non Latin numerals are understood`() {
        val german = WalletAmountFormatter(Locale.GERMANY)
        assertEquals(50000L, german.format("50.000", 6).amount)
        assertNull(german.format("10,5", 4).amount)
        assertEquals(500L, formatter.format("५००", 3).amount)
        assertEquals(500L, formatter.format("৫০০", 3).amount)
    }

    @Test fun `glyph matching preserves order and identity across edits`() {
        listOf("100" to "1,000", "50,000" to "5,000", "111" to "1111", "1234" to "12934").forEach { (old, new) ->
            val matches = AmountGlyphDiff.match(old, new)
            matches.forEach { (newIndex, oldIndex) -> assertEquals(old[oldIndex], new[newIndex]) }
            assertEquals(matches.values.sorted(), matches.values.toList())
            assertEquals(matches.size, matches.values.toSet().size)
        }
    }
}
