package com.gridee.parking.ui.qr

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerInputImageFactoryTest {

    @Test
    fun `copies an even padded crop to NV21 in VU order`() {
        val y = positionedBuffer(
            0, 1, 2, 3, 90, 90,
            10, 11, 12, 13, 90, 90,
            20, 21, 22, 23, 90, 90,
            30, 31, 32, 33, 90, 90,
        )
        val u = positionedBuffer(40, 41, 42, 50, 51, 52)
        val v = positionedBuffer(60, 61, 62, 70, 71, 72)
        val output = ByteArray(12)

        val copied = ScannerInputImageFactory.copyYuv420ToNv21(
            yPlane = ScannerYuvPlane(y, rowStride = 6, pixelStride = 1),
            uPlane = ScannerYuvPlane(u, rowStride = 3, pixelStride = 1),
            vPlane = ScannerYuvPlane(v, rowStride = 3, pixelStride = 1),
            cropLeft = 2,
            cropTop = 0,
            width = 2,
            height = 4,
            destination = output,
        )

        assertTrue(copied)
        assertArrayEquals(
            byteArrayOf(2, 3, 12, 13, 22, 23, 32, 33, 61, 41, 71, 51),
            output,
        )
    }

    @Test
    fun `supports interleaved chroma pixel strides`() {
        val y = positionedBuffer(1, 2, 3, 4, 5, 6, 7, 8)
        val u = positionedBuffer(10, 99, 11, 99)
        val v = positionedBuffer(20, 99, 21, 99)
        val output = ByteArray(12)

        val copied = ScannerInputImageFactory.copyYuv420ToNv21(
            yPlane = ScannerYuvPlane(y, rowStride = 4, pixelStride = 1),
            uPlane = ScannerYuvPlane(u, rowStride = 4, pixelStride = 2),
            vPlane = ScannerYuvPlane(v, rowStride = 4, pixelStride = 2),
            cropLeft = 0,
            cropTop = 0,
            width = 4,
            height = 2,
            destination = output,
        )

        assertTrue(copied)
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 20, 10, 21, 11),
            output,
        )
    }

    @Test
    fun `fails closed for chroma-unsafe odd crops`() {
        val plane = ScannerYuvPlane(positionedBuffer(1, 2, 3, 4), 2, 1)

        assertFalse(
            ScannerInputImageFactory.copyYuv420ToNv21(
                yPlane = plane,
                uPlane = plane,
                vPlane = plane,
                cropLeft = 1,
                cropTop = 0,
                width = 2,
                height = 2,
                destination = ByteArray(6),
            )
        )
    }

    private fun positionedBuffer(vararg values: Int): ByteBuffer {
        val bytes = ByteArray(values.size + 1)
        bytes[0] = 127
        values.forEachIndexed { index, value -> bytes[index + 1] = value.toByte() }
        return ByteBuffer.wrap(bytes).apply { position(1) }
    }
}

