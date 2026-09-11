package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScannerCropMappingTest {

    @Test
    fun `maps detector bounds back to the same raw crop area at every rotation`() {
        val expected = ScannerBounds(150f, 230f, 250f, 290f)
        val rotatedBounds = mapOf(
            0 to ScannerBounds(50f, 30f, 150f, 90f),
            90 to ScannerBounds(110f, 50f, 170f, 150f),
            180 to ScannerBounds(250f, 110f, 350f, 170f),
            270 to ScannerBounds(30f, 250f, 90f, 350f),
        )

        rotatedBounds.forEach { (rotation, detectorBounds) ->
            val mapping = ScannerCropMapping(
                left = 100,
                top = 200,
                width = 400,
                height = 200,
                rotationDegrees = rotation,
            )

            assertBounds(expected, mapping.mapRotatedBoundsToRaw(detectorBounds))
        }
    }

    @Test
    fun `maps the complete detector input to landscape and portrait raw crops`() {
        listOf(400 to 200, 200 to 400).forEach { (width, height) ->
            listOf(0, 90, 180, 270).forEach { rotation ->
                val mapping = ScannerCropMapping(24, 42, width, height, rotation)
                val rotatedWidth = if (rotation == 90 || rotation == 270) height else width
                val rotatedHeight = if (rotation == 90 || rotation == 270) width else height

                assertBounds(
                    ScannerBounds(
                        left = 24f,
                        top = 42f,
                        right = (24 + width).toFloat(),
                        bottom = (42 + height).toFloat(),
                    ),
                    mapping.mapRotatedBoundsToRaw(
                        ScannerBounds(0f, 0f, rotatedWidth.toFloat(), rotatedHeight.toFloat())
                    ),
                )
            }
        }
    }

    @Test
    fun `fails closed for unusable detector bounds`() {
        val mapping = ScannerCropMapping(0, 0, 400, 200, 0)

        assertNull(mapping.mapRotatedBoundsToRaw(ScannerBounds(10f, 10f, 10f, 20f)))
        assertNull(mapping.mapRotatedBoundsToRaw(ScannerBounds(Float.NaN, 0f, 20f, 20f)))
    }

    private fun assertBounds(expected: ScannerBounds, actual: ScannerBounds?) {
        requireNotNull(actual)
        assertEquals(expected.left, actual.left, 0.001f)
        assertEquals(expected.top, actual.top, 0.001f)
        assertEquals(expected.right, actual.right, 0.001f)
        assertEquals(expected.bottom, actual.bottom, 0.001f)
    }
}
