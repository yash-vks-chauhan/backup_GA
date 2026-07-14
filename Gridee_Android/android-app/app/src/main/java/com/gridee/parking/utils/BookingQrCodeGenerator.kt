package com.gridee.parking.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import java.util.EnumMap

/**
 * Renders booking-pass QR codes in a soft, rounded "dot" style — circular data
 * modules with rounded-square finder eyes — instead of hard black squares.
 *
 * The bitmap is drawn on a transparent canvas, so it sits cleanly on the white
 * rounded QR plate the layouts already provide (bg_qr_minimal / bg_booking_qr_image).
 * Error correction stays at level H, so the rounded rendering remains comfortably
 * scannable.
 */
object BookingQrCodeGenerator {

    // Pure black keeps maximum contrast against the white plate for reliable scanning.
    private const val MODULE_COLOR = Color.BLACK

    // Diameter just under one module → crisp dots with a hair of breathing room between them.
    private const val DOT_RADIUS_RATIO = 0.46f

    // Quiet zone (in modules) baked into the bitmap, on top of the plate's own padding.
    private const val QUIET_MODULES = 2

    // A QR finder pattern (the corner "eye") is always 7×7 modules.
    private const val EYE_MODULES = 7

    /**
     * Generate a rounded QR bitmap for the given content.
     */
    fun generate(content: String, sizePx: Int = 256): Bitmap? {
        val value = content.trim()
        if (value.isBlank() || sizePx <= 0) return null

        return runCatching {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            }
            // Encoder gives us the raw module grid (no quiet zone), which is what we
            // need to draw each module individually rather than a pre-scaled matrix.
            val matrix = Encoder.encode(value, ErrorCorrectionLevel.H, hints).matrix
                ?: return@runCatching null
            renderRounded(matrix, sizePx)
        }.getOrNull()
    }

    private fun renderRounded(matrix: ByteMatrix, sizePx: Int): Bitmap {
        val modules = matrix.width // QR grids are square: width == height
        val moduleSize = sizePx.toFloat() / (modules + QUIET_MODULES * 2)
        val origin = moduleSize * QUIET_MODULES

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap) // transparent — the white plate shows through
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MODULE_COLOR
            style = Paint.Style.FILL
        }

        // Top-left, top-right, bottom-left finder eyes (module coordinates of their origin).
        val eyes = listOf(
            0 to 0,
            modules - EYE_MODULES to 0,
            0 to modules - EYE_MODULES
        )

        fun insideEye(col: Int, row: Int): Boolean = eyes.any { (ex, ey) ->
            col in ex until ex + EYE_MODULES && row in ey until ey + EYE_MODULES
        }

        // Data modules → dots. Skip the eye regions; they're drawn as rounded frames below.
        val dotRadius = moduleSize * DOT_RADIUS_RATIO
        for (row in 0 until modules) {
            for (col in 0 until modules) {
                if (matrix.get(col, row).toInt() != 1) continue
                if (insideEye(col, row)) continue
                val cx = origin + (col + 0.5f) * moduleSize
                val cy = origin + (row + 0.5f) * moduleSize
                canvas.drawCircle(cx, cy, dotRadius, paint)
            }
        }

        eyes.forEach { (ex, ey) -> drawEye(canvas, paint, origin, moduleSize, ex, ey) }

        return bitmap
    }

    /**
     * Draws one finder eye as a rounded-square ring (1 module thick) with a rounded
     * 3×3 centre — mirroring the standard finder pattern, just with soft corners.
     */
    private fun drawEye(
        canvas: Canvas,
        paint: Paint,
        origin: Float,
        moduleSize: Float,
        eyeCol: Int,
        eyeRow: Int
    ) {
        val left = origin + eyeCol * moduleSize
        val top = origin + eyeRow * moduleSize
        val full = EYE_MODULES * moduleSize

        // Outer ring as a stroke so the 1-module-wide white gap shows the plate through it.
        val ringPaint = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = moduleSize
        }
        // Stroke is centred on its path, so inset half a module to keep the ring inside the 7×7 box.
        val ring = RectF(
            left + moduleSize * 0.5f,
            top + moduleSize * 0.5f,
            left + full - moduleSize * 0.5f,
            top + full - moduleSize * 0.5f
        )
        canvas.drawRoundRect(ring, moduleSize * 1.6f, moduleSize * 1.6f, ringPaint)

        // Solid rounded 3×3 centre (modules 2..5).
        val center = RectF(
            left + moduleSize * 2f,
            top + moduleSize * 2f,
            left + full - moduleSize * 2f,
            top + full - moduleSize * 2f
        )
        canvas.drawRoundRect(center, moduleSize * 0.9f, moduleSize * 0.9f, paint)
    }
}
