package com.zigor.discountcard.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.FileOutputStream
import kotlin.coroutines.resume

/** Код, найденный на готовом изображении. */
data class ScannedCode(val value: String, val format: String)

private const val TAG = "ImageCodeScanner"

/**
 * Ищет штрих-код или QR на картинке: скриншот из мессенджера, фотография карты,
 * сохранённый купон. Работает офлайн той же моделью, что и камера.
 *
 * Снимок экрана часто повёрнут или обрезан, поэтому изображение прогоняется
 * в четырёх ориентациях — так находится и вертикально снятый штрих-код.
 */
suspend fun scanImageForCode(context: Context, uri: Uri): ScannedCode? {
    val bitmap = loadBitmap(context, uri, maxSize = 2048) ?: run {
        Log.w(TAG, "не удалось открыть изображение $uri")
        return null
    }
    val scanner = BarcodeScanning.getClient()
    return try {
        listOf(0, 90, 180, 270).firstNotNullOfOrNull { degrees ->
            scanner.findCode(InputImage.fromBitmap(bitmap, degrees))
        }
    } finally {
        scanner.close()
        bitmap.recycle()
    }
}

/** Сохраняет выбранную картинку как фото карты во внутреннюю память приложения. */
fun importImageAsPhoto(context: Context, uri: Uri, photoStore: PhotoStore): String? {
    val bitmap = loadBitmap(context, uri, maxSize = 1600) ?: return null
    return runCatching {
        val file = photoStore.newFile()
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
        file.absolutePath
    }.onFailure { Log.w(TAG, "не удалось сохранить изображение", it) }
        .also { bitmap.recycle() }
        .getOrNull()
}

private suspend fun BarcodeScanner.findCode(image: InputImage): ScannedCode? =
    suspendCancellableCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { barcodes ->
                val best = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                continuation.resume(
                    best?.let { ScannedCode(it.rawValue!!.trim(), mlKitFormatName(it.format)) },
                )
            }
            .addOnFailureListener {
                Log.w(TAG, "ошибка распознавания", it)
                continuation.resume(null)
            }
    }

private fun loadBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
    resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(
            stream,
            null,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }
}.getOrNull()
