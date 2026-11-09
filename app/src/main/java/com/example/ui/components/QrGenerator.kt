package com.example.ui.components

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrGenerator {
    /**
     * Generates a real, scan-compatible QR Code boolean matrix from the given text.
     * Uses High (Level H) error correction to support central logo overlays without breaking decoding.
     */
    fun generate(content: String): Array<BooleanArray> {
        if (content.isBlank()) {
            return Array(21) { BooleanArray(21) }
        }
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val matrix = Array(height) { BooleanArray(width) }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    matrix[y][x] = bitMatrix.get(x, y)
                }
            }
            matrix
        } catch (e: Exception) {
            // Safe fallback
            Array(21) { BooleanArray(21) }
        }
    }
}
