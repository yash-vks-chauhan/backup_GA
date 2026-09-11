package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class OperatorQrSelectionPolicyTest {

    @Test
    fun `one accepted QR is selected`() {
        assertEquals(
            OperatorQrSelection.Accepted("booking-1"),
            OperatorQrSelectionPolicy.select(listOf(" booking-1 ")),
        )
    }

    @Test
    fun `duplicate observations of the same QR are not ambiguous`() {
        assertEquals(
            OperatorQrSelection.Accepted("booking-1"),
            OperatorQrSelectionPolicy.select(listOf("booking-1", "booking-1")),
        )
    }

    @Test
    fun `two distinct accepted QRs fail closed`() {
        assertEquals(
            OperatorQrSelection.Ambiguous,
            OperatorQrSelectionPolicy.select(listOf("booking-1", "booking-2")),
        )
    }

    @Test
    fun `blank and missing values are ignored`() {
        assertEquals(
            OperatorQrSelection.None,
            OperatorQrSelectionPolicy.select(listOf(null, "", "   ")),
        )
    }
}
