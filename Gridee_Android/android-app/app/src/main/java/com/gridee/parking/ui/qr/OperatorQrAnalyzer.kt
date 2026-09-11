package com.gridee.parking.ui.qr

import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/** Owns the QR detector and fail-closed centre-region result selection. */
internal class OperatorQrAnalyzer(
    autoZoomCallback: (Float) -> Boolean,
    maximumAutoZoomRatio: Float,
    private val acceptancePolicy: OperatorQrAcceptancePolicy = OperatorQrAcceptancePolicy(),
) {
    val detector: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAllPotentialBarcodes()
            .setZoomSuggestionOptions(
                ZoomSuggestionOptions.Builder(
                    ZoomSuggestionOptions.ZoomCallback(autoZoomCallback)
                )
                    .setMaxSupportedZoomRatio(maximumAutoZoomRatio)
                    .build()
            )
            .build()
    )

    fun process(image: InputImage): Task<List<Barcode>> = detector.process(image)

    /** Returns an accepted payload only when exactly one distinct in-frame QR is present. */
    fun selectAcceptedValue(
        barcodes: List<Barcode>,
        scanRegion: ScannerBounds,
        mapBoundsToPreview: (Rect?) -> ScannerBounds?,
    ): OperatorQrSelection {
        val acceptedValues = barcodes.mapNotNull { barcode ->
            val value = barcode.rawValue?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            value.takeIf {
                acceptancePolicy.accepts(mapBoundsToPreview(barcode.boundingBox), scanRegion)
            }
        }
        return OperatorQrSelectionPolicy.select(acceptedValues)
    }

    fun close() = detector.close()
}
