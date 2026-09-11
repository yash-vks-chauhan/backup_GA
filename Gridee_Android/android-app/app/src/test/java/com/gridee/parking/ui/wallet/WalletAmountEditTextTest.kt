package com.gridee.parking.ui.wallet

import android.text.Spanned
import android.text.style.CharacterStyle
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class WalletAmountEditTextTest {
    private fun field() = WalletAmountEditText(RuntimeEnvironment.getApplication())

    @Test fun `IME sees an integer keyboard but pasted decimals are preserved and invalid`() {
        val field = field()
        val info = EditorInfo()
        val connection = field.onCreateInputConnection(info)!!
        assertEquals(android.text.InputType.TYPE_CLASS_NUMBER, info.inputType)
        connection.commitText("10.5", 1)
        assertEquals("10.5", field.text.toString())
        assertNull(field.amountResult.amount)
    }

    @Test fun `inserting a digit in the middle does not jump to the end`() {
        val field = field()
        field.setText("1000")
        field.setSelection(3) // 1,0|00
        field.text!!.insert(3, "2")
        assertEquals("10,200", field.text.toString())
        assertEquals(4, field.selectionStart)
        assertEquals(10200L, field.amountResult.amount)
    }

    @Test fun `backspace and forward delete across a comma delete an adjacent digit`() {
        val field = field()
        field.setText("1000")
        field.setSelection(2)
        field.text!!.delete(1, 2)
        assertEquals("0", field.text.toString())
        assertEquals(0, field.selectionStart)
        field.setText("1000")
        field.setSelection(1)
        field.text!!.delete(1, 2)
        assertEquals("100", field.text.toString())
        assertEquals(1, field.selectionStart)
    }

    @Test fun `selection replacement paste and clear stay native`() {
        val field = field()
        field.setText("50000")
        field.setSelection(0, field.length())
        val connection = field.onCreateInputConnection(EditorInfo())!!
        connection.commitText("250", 1)
        assertEquals("250", field.text.toString())
        assertEquals(250L, field.amountResult.amount)
        connection.commitText(".5", 1)
        assertEquals("250.5", field.text.toString())
        assertNull(field.amountResult.amount)
        field.text!!.clear()
        assertEquals("", field.text.toString())
        assertNull(field.amountResult.amount)
    }

    @Test fun `oversized paste is wholly rejected rather than truncated`() {
        val field = field()
        field.setText("100")
        field.setSelection(0, 3)
        field.onCreateInputConnection(EditorInfo())!!.commitText("9".repeat(100), 1)
        assertEquals("100", field.text.toString())
        assertEquals(100L, field.amountResult.amount)
    }

    @Test fun `stopping animation never leaves hidden characters or replaces editable semantics`() {
        val field = field()
        field.setText("50000")
        field.stopGlyphMotion()
        assertEquals("50,000", field.text.toString())
        assertEquals(0, (field.text as Spanned).getSpans(0, field.length(), CharacterStyle::class.java).size)
        assertEquals("android.widget.EditText", field.accessibilityClassName.toString())
    }

    @Test fun `native saved state restores the amount and the selection`() {
        val field = field()
        field.setText("50000")
        field.setSelection(2, 4)
        val saved = field.onSaveInstanceState()
        val restored = field()
        restored.onRestoreInstanceState(saved)
        assertEquals("50,000", restored.text.toString())
        assertEquals(50000L, restored.amountResult.amount)
        assertEquals(2, restored.selectionStart)
        assertEquals(4, restored.selectionEnd)
    }
}
