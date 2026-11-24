package com.gogether.app.core.ui

import com.google.zxing.*
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

/** Some valid masks create false finder patterns for ZXing's camera detector. Verify the
 * displayed pixels with that detector, then select another standards-compliant mask if needed.
 */
object CheckInQr {
    fun matrix(text: String, size: Int = 720): BitMatrix {
        require(size >= 128)
        for (mask in -1..7) {
            val hints = if (mask < 0) emptyMap() else mapOf(EncodeHintType.QR_MASK_PATTERN to mask)
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(matrix.width * matrix.height) { i ->
                if (matrix[i % matrix.width, i / matrix.width]) -0x1000000 else -1
            }
            val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
            val decoded = try { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text }
                catch (_: ReaderException) { null }
            if (decoded == text) return matrix
        }
        error("Could not create a readable check-in code. Refresh and try again.")
    }
}
