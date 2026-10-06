package com.zigor.discountcard.util

import com.google.mlkit.vision.barcode.common.Barcode

/** Имя формата ZXing по константе ML Kit — чтобы нарисовать код тем же кодированием. */
fun mlKitFormatName(format: Int): String = when (format) {
    Barcode.FORMAT_EAN_13 -> "EAN_13"
    Barcode.FORMAT_EAN_8 -> "EAN_8"
    Barcode.FORMAT_UPC_A -> "UPC_A"
    Barcode.FORMAT_UPC_E -> "UPC_E"
    Barcode.FORMAT_CODE_128 -> "CODE_128"
    Barcode.FORMAT_CODE_39 -> "CODE_39"
    Barcode.FORMAT_CODE_93 -> "CODE_93"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_CODABAR -> "CODABAR"
    Barcode.FORMAT_QR_CODE -> "QR_CODE"
    Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    Barcode.FORMAT_PDF417 -> "PDF_417"
    else -> "CODE_128"
}
