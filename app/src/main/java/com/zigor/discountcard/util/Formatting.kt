package com.zigor.discountcard.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Группирует длинный номер по 4 символа — так его проще продиктовать кассиру. */
fun formatCardNumber(code: String): String {
    val clean = code.trim()
    if (clean.length !in 8..24 || !clean.all { it.isDigit() }) return clean
    return clean.chunked(4).joinToString(" ")
}

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(timestamp))

fun shortenCode(code: String?, keep: Int = 4): String {
    if (code.isNullOrBlank()) return ""
    return if (code.length <= keep) code else "•••• " + code.takeLast(keep)
}
