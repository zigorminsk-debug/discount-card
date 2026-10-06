package com.zigor.discountcard.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Вид карты: обычный код, NFC-метка или карта без кода (только фото). */
object CardKind {
    const val BARCODE = "BARCODE"
    const val NFC = "NFC"
    const val PHOTO = "PHOTO"
}

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Название магазина, которое видит пользователь. */
    val title: String,
    /** id магазина из каталога assets/stores.json, если он распознан. */
    val storeId: String? = null,
    val colorArgb: Int,
    val kind: String = CardKind.BARCODE,
    /** Содержимое кода ровно в том виде, в каком оно было считано. */
    val code: String? = null,
    /** Имя формата ZXing: EAN_13, CODE_128, QR_CODE... */
    val codeFormat: String = "CODE_128",
    val nfcUid: String? = null,
    val nfcTech: String? = null,
    val nfcPayload: String? = null,
    val frontPhoto: String? = null,
    val backPhoto: String? = null,
    val note: String? = null,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val useCount: Int = 0,
) {
    val hasCode: Boolean get() = !code.isNullOrBlank()
}

/**
 * Выученные соответствия «префикс кода -> магазин».
 * Пополняется, когда пользователь сам назвал магазин: следующая карта
 * с таким же префиксом распознается автоматически.
 */
@Entity(tableName = "learned_stores")
data class LearnedStoreEntity(
    @PrimaryKey val codeKey: String,
    val title: String,
    val storeId: String? = null,
    val colorArgb: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)
