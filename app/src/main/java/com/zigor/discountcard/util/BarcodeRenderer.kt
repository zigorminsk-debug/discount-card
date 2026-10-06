package com.zigor.discountcard.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

data class RenderedCode(
    val bitmap: Bitmap?,
    val format: String,
    val usedFallback: Boolean = false,
    val error: String? = null,
)

/** Отрисовка штрих-кода ровно в том формате, в котором он был считан. */
object BarcodeRenderer {

    val ONE_D = listOf(
        "EAN_13", "EAN_8", "UPC_A", "UPC_E", "CODE_128", "CODE_39", "CODE_93", "ITF", "CODABAR",
    )
    val TWO_D = listOf("QR_CODE", "DATA_MATRIX", "AZTEC", "PDF_417")
    val ALL = ONE_D + TWO_D

    fun isTwoD(format: String): Boolean = format.uppercase() in TWO_D

    fun humanName(format: String): String = when (format.uppercase()) {
        "EAN_13" -> "EAN-13"
        "EAN_8" -> "EAN-8"
        "UPC_A" -> "UPC-A"
        "UPC_E" -> "UPC-E"
        "CODE_128" -> "Code 128"
        "CODE_39" -> "Code 39"
        "CODE_93" -> "Code 93"
        "ITF" -> "ITF (Interleaved 2 of 5)"
        "CODABAR" -> "Codabar"
        "QR_CODE" -> "QR-код"
        "DATA_MATRIX" -> "Data Matrix"
        "AZTEC" -> "Aztec"
        "PDF_417" -> "PDF417"
        else -> format
    }

    /**
     * Пытается нарисовать код в запрошенном формате.
     * Если значение не подходит под формат (например, неверная контрольная сумма EAN-13),
     * используется запасной формат — Code 128, затем QR.
     */
    fun render(
        value: String,
        formatName: String,
        widthPx: Int,
        heightPx: Int,
        foreground: Int = Color.BLACK,
        background: Int = Color.WHITE,
    ): RenderedCode {
        if (value.isBlank() || widthPx <= 0 || heightPx <= 0) {
            return RenderedCode(null, formatName, error = "пустое значение")
        }
        val candidates = buildList {
            add(formatName.uppercase())
            if (value.length <= 80 && value.all { it.code in 32..126 }) add("CODE_128")
            add("QR_CODE")
        }.distinct()

        var lastError: String? = null
        candidates.forEachIndexed { index, candidate ->
            val format = runCatching { BarcodeFormat.valueOf(candidate) }.getOrNull()
            if (format == null) {
                lastError = "неизвестный формат $candidate"
                return@forEachIndexed
            }
            val square = isTwoD(candidate)
            val w = if (square) minOf(widthPx, heightPx) else widthPx
            val h = if (square) minOf(widthPx, heightPx) else heightPx
            val result = runCatching {
                val hints = mapOf(
                    EncodeHintType.MARGIN to if (square) 1 else 6,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                )
                MultiFormatWriter().encode(value, format, w, h, hints).toBitmap(foreground, background)
            }
            result.getOrNull()?.let { bitmap ->
                return RenderedCode(bitmap, candidate, usedFallback = index > 0)
            }
            lastError = result.exceptionOrNull()?.message ?: "не удалось закодировать"
        }
        return RenderedCode(null, formatName, error = lastError)
    }

    private fun BitMatrix.toBitmap(foreground: Int, background: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (this[x, y]) foreground else background
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
