package com.zigor.discountcard.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zigor.discountcard.data.bank.BankPalette

/**
 * Банковская карта для оплаты в интернете.
 *
 * Номер, срок действия и имя держателя хранятся ТОЛЬКО в зашифрованном виде
 * ([numberEnc], [expiryEnc], [holderEnc]); ключ лежит в Android Keystore и
 * не покидает устройство (см. `data/bank/CardCrypto.kt`).
 *
 * CVV/CVC не хранится никогда — платёжные системы запрещают его сохранять,
 * и без него база, даже украденная, бесполезна для оплаты.
 *
 * В открытом виде остаются только «неопасные» поля: имя карты, банк,
 * платёжная система и последние 4 цифры — их показывают в списке.
 */
@Entity(tableName = "bank_cards")
data class BankCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val bank: String = "",
    val system: String = "",
    val last4: String = "",
    val numberEnc: String = "",
    val expiryEnc: String = "",
    val holderEnc: String = "",
    val colorArgb: Int = BankPalette.DEFAULT,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
