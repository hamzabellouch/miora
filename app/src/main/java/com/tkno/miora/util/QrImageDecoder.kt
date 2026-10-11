package com.tkno.miora.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumMap

object QrImageDecoder {

    /**
     * Decodes QR codes from a given Bitmap. Returns all unique QR codes found in the bitmap.
     */
    fun decodeQrsFromBitmap(bitmap: Bitmap): List<String> {
        val results = mutableListOf<String>()
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }

        // 1. Try multi-reader first to capture all QR codes if there are multiple on the image
        try {
            val multiReader = QRCodeMultiReader()
            val multiResults = multiReader.decodeMultiple(binaryBitmap, hints)
            for (res in multiResults) {
                val text = res.text?.trim()
                if (!text.isNullOrEmpty() && !results.contains(text)) {
                    results.add(text)
                }
            }
        } catch (_: Exception) {}

        // 2. If multi-reader found nothing, try standard single reader fallback
        if (results.isEmpty()) {
            try {
                val singleReader = MultiFormatReader().apply { setHints(hints) }
                val singleResult = singleReader.decode(binaryBitmap)
                val text = singleResult.text?.trim()
                if (!text.isNullOrEmpty()) {
                    results.add(text)
                }
            } catch (_: Exception) {}
        }

        return results
    }

    /**
     * Decodes a QR code from an Image URI.
     */
    suspend fun decodeQrFromImageUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext null

            val codes = decodeQrsFromBitmap(bitmap)
            try { bitmap.recycle() } catch (_: Exception) {}
            codes.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
