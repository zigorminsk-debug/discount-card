package com.zigor.discountcard.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.util.UUID

/** Фотографии карт лежат во внутренней памяти приложения и никуда не отправляются. */
class PhotoStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }

    fun newFile(): File = File(dir, "card_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    fun exists(path: String?): Boolean = !path.isNullOrBlank() && File(path).exists()

    /** Загрузка с уменьшением и учётом EXIF-поворота. */
    fun load(path: String?, maxSize: Int = 1280): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val degrees = when (ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) {
                bitmap
            } else {
                Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height,
                    Matrix().apply { postRotate(degrees) }, true,
                )
            }
        }.getOrNull()
    }
}
