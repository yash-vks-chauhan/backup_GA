package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class VehiclePlateInterpreterTest {
    private val interpreter = VehiclePlateInterpreter()

    @Test
    fun `normalizes supported Indian plate formats`() {
        val values = mapOf(
            "mh 12-ab-1234" to "MH12AB1234",
            "dl 1 a 12" to "DL1A12",
            "mh 12 abc 1234" to "MH12ABC1234",
            "22 bh 1234 a" to "22BH1234A",
            "22 bh 1234 ab" to "22BH1234AB",
            "T0826 KA 1234 AB" to "T0826KA1234AB",
            "KA VA AB 1234" to "KAVAAB1234",
            "TN 01 1234" to "TN011234",
        )

        values.forEach { (raw, expected) ->
            assertEquals(expected, interpreter.normalizeCandidate(raw)?.value)
        }
    }

    @Test
    fun `records OCR lookalike correction count`() {
        val candidate = interpreter.normalizeCandidate("MHI2AB1234")

        assertEquals("MH12AB1234", candidate?.value)
        assertEquals(1, candidate?.correctedCharacters)
    }

    @Test
    fun `corrects supported letter and digit OCR confusions in format positions`() {
        val digitPositionCases = mapOf(
            "MHO2AB1234" to "MH02AB1234",
            "MHQ2AB1234" to "MH02AB1234",
            "MHL2AB1234" to "MH12AB1234",
            "MHZ2AB1234" to "MH22AB1234",
            "MHS2AB1234" to "MH52AB1234",
            "MHG2AB1234" to "MH62AB1234",
            "MHB2AB1234" to "MH82AB1234",
        )
        val letterPositionCases = mapOf(
            "M012AB1234" to "MO12AB1234",
            "M112AB1234" to "MI12AB1234",
            "M212AB1234" to "MZ12AB1234",
            "M412AB1234" to "MA12AB1234",
            "M512AB1234" to "MS12AB1234",
            "M612AB1234" to "MG12AB1234",
            "M712AB1234" to "MT12AB1234",
            "M812AB1234" to "MB12AB1234",
        )

        (digitPositionCases + letterPositionCases).forEach { (raw, expected) ->
            val candidate = interpreter.normalizeCandidate(raw)
            assertEquals(raw, expected, candidate?.value)
            assertTrue(raw, (candidate?.correctedCharacters ?: 0) > 0)
        }
    }

    @Test
    fun `rejects non-plate text`() {
        assertNull(interpreter.normalizeCandidate("PARKING ONLY"))
        assertNull(interpreter.normalizeCandidate("12345678"))
    }

    @Test
    fun `strong automatic classification remains format aware`() {
        assertTrue(interpreter.isStrongAutomaticCandidate("MH12AB1234"))
        assertTrue(interpreter.isStrongAutomaticCandidate("22BH1234A"))
        assertFalse(interpreter.isStrongAutomaticCandidate("TN011234"))
    }

    @Test
    fun `only strong uncorrected plates can bypass operator confirmation`() {
        assertFalse(
            interpreter.requiresOperatorConfirmation(
                NormalizedPlateCandidate("MH12AB1234", correctedCharacters = 0)
            )
        )
        assertTrue(
            interpreter.requiresOperatorConfirmation(
                NormalizedPlateCandidate("TN011234", correctedCharacters = 0)
            )
        )
        assertTrue(
            interpreter.requiresOperatorConfirmation(
                NormalizedPlateCandidate("MH12AB1234", correctedCharacters = 1)
            )
        )
        assertTrue(
            interpreter.requiresOperatorConfirmation(
                NormalizedPlateCandidate("MH12AB1234", correctedCharacters = 0),
                forceManualConfirmation = true,
            )
        )
    }

    @Test
    fun `normalization benchmark is independent from ML inference`() {
        val samples = listOf(
            "MH 12 AB 1234",
            "MHI2AB1234",
            "22 BH 1234 A",
            "T0826 KA 1234 AB",
            "KA VA AB 1234",
            "TN 01 1234",
            "PARKING ONLY",
        )
        repeat(500) { index -> interpreter.normalizeCandidate(samples[index % samples.size]) }

        var accepted = 0
        val iterations = 10_000
        val elapsedNanos = measureNanoTime {
            repeat(iterations) { index ->
                if (interpreter.normalizeCandidate(samples[index % samples.size]) != null) accepted++
            }
        }

        assertTrue(accepted > 0)
        println("VehiclePlateInterpreter average_ns=${elapsedNanos / iterations} iterations=$iterations")
    }
}
