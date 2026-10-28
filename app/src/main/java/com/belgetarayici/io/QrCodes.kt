package com.belgetarayici.io

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Share-key QR codes: generation, and decoding from a camera luminance (Y) plane. */
object QrCodes {
    /** Error correction M survives screen glare and camera blur at our key length (~150 chars). */
    fun encode(text: String, size: Int): BitMatrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2),
    )

    /** Rows of [luminance] may be padded to [rowStride], as camera planes often are. */
    fun decode(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? = runCatching {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
    }.getOrNull()
}
