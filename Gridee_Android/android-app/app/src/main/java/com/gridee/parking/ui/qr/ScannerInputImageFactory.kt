package com.gridee.parking.ui.qr

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer

internal data class ScannerYuvPlane(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int,
)

/**
 * Copies the already transformed ImageProxy crop into one reusable NV21 buffer.
 *
 * CameraX's analyzer crop can live on a wrapper rather than the underlying media.Image. Passing
 * that media.Image directly to ML Kit can therefore retain full-frame dimensions. A bounded NV21
 * input makes the detector's pixels and coordinates unambiguously relative to the visible ROI.
 */
internal class ScannerInputImageFactory {
    private var outputBytes = ByteArray(0)
    private var outputBuffer: ByteBuffer = ByteBuffer.wrap(outputBytes)

    fun create(imageProxy: ImageProxy): InputImage? {
        val crop = imageProxy.cropRect
        val width = crop.width()
        val height = crop.height()
        if (width < 2 || height < 2 || width % 2 != 0 || height % 2 != 0) return null
        val planes = imageProxy.planes
        if (planes.size < 3) return null

        val requiredSize = width * height * 3 / 2
        ensureCapacity(requiredSize)
        val copied = copyYuv420ToNv21(
            yPlane = ScannerYuvPlane(
                planes[0].buffer,
                planes[0].rowStride,
                planes[0].pixelStride,
            ),
            uPlane = ScannerYuvPlane(
                planes[1].buffer,
                planes[1].rowStride,
                planes[1].pixelStride,
            ),
            vPlane = ScannerYuvPlane(
                planes[2].buffer,
                planes[2].rowStride,
                planes[2].pixelStride,
            ),
            cropLeft = crop.left,
            cropTop = crop.top,
            width = width,
            height = height,
            destination = outputBytes,
        )
        if (!copied) return null

        outputBuffer.clear()
        outputBuffer.limit(requiredSize)
        return InputImage.fromByteBuffer(
            outputBuffer,
            width,
            height,
            imageProxy.imageInfo.rotationDegrees,
            InputImage.IMAGE_FORMAT_NV21,
        )
    }

    private fun ensureCapacity(requiredSize: Int) {
        if (outputBytes.size >= requiredSize) return
        outputBytes = ByteArray(requiredSize)
        outputBuffer = ByteBuffer.wrap(outputBytes)
    }

    companion object {
        /** Visible for deterministic JVM coverage with synthetic padded/interleaved planes. */
        internal fun copyYuv420ToNv21(
            yPlane: ScannerYuvPlane,
            uPlane: ScannerYuvPlane,
            vPlane: ScannerYuvPlane,
            cropLeft: Int,
            cropTop: Int,
            width: Int,
            height: Int,
            destination: ByteArray,
        ): Boolean {
            if (width < 2 || height < 2 || width % 2 != 0 || height % 2 != 0) return false
            if (cropLeft < 0 || cropTop < 0 || cropLeft % 2 != 0 || cropTop % 2 != 0) return false
            val requiredSize = width * height * 3 / 2
            if (destination.size < requiredSize) return false
            if (yPlane.rowStride <= 0 || yPlane.pixelStride <= 0 ||
                uPlane.rowStride <= 0 || uPlane.pixelStride <= 0 ||
                vPlane.rowStride <= 0 || vPlane.pixelStride <= 0
            ) {
                return false
            }

            return runCatching {
                var destinationIndex = 0
                val yBuffer = yPlane.buffer.duplicate()
                val yBase = yBuffer.position()
                if (yPlane.pixelStride == 1) {
                    repeat(height) { row ->
                        val sourceIndex = yBase + ((cropTop + row) * yPlane.rowStride) + cropLeft
                        yBuffer.position(sourceIndex)
                        yBuffer.get(destination, destinationIndex, width)
                        destinationIndex += width
                    }
                } else {
                    repeat(height) { row ->
                        val rowStart = yBase + ((cropTop + row) * yPlane.rowStride)
                        repeat(width) { column ->
                            destination[destinationIndex++] =
                                yBuffer.get(rowStart + ((cropLeft + column) * yPlane.pixelStride))
                        }
                    }
                }

                val uBuffer = uPlane.buffer.duplicate()
                val vBuffer = vPlane.buffer.duplicate()
                val uBase = uBuffer.position()
                val vBase = vBuffer.position()
                val chromaLeft = cropLeft / 2
                val chromaTop = cropTop / 2
                repeat(height / 2) { row ->
                    val uRowStart = uBase + ((chromaTop + row) * uPlane.rowStride)
                    val vRowStart = vBase + ((chromaTop + row) * vPlane.rowStride)
                    repeat(width / 2) { column ->
                        destination[destinationIndex++] = vBuffer.get(
                            vRowStart + ((chromaLeft + column) * vPlane.pixelStride)
                        )
                        destination[destinationIndex++] = uBuffer.get(
                            uRowStart + ((chromaLeft + column) * uPlane.pixelStride)
                        )
                    }
                }
                destinationIndex == requiredSize
            }.getOrDefault(false)
        }
    }
}

