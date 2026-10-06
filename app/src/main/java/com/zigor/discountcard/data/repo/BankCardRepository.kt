package com.zigor.discountcard.data.repo

import com.zigor.discountcard.data.bank.BankPalette
import com.zigor.discountcard.data.bank.CardCrypto
import com.zigor.discountcard.data.bank.CardNumber
import com.zigor.discountcard.data.bank.PaymentSystem
import com.zigor.discountcard.data.db.BankCardDao
import com.zigor.discountcard.data.db.BankCardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Черновик банковской карты: то, что пользователь набрал на экране ввода. */
data class BankCardDraft(
    val id: Long = 0,
    val title: String = "",
    val bank: String = "",
    val number: String = "",
    val expiry: String = "",
    val holder: String = "",
    val note: String = "",
    val colorArgb: Int = BankPalette.DEFAULT,
) {
    val system: String get() = PaymentSystem.detect(number)
    val numberValid: Boolean get() = CardNumber.valid(number)
}

/** Расшифрованные реквизиты — живут только в памяти экрана, никогда не логируются. */
data class BankCardSecret(
    val number: String,
    val expiry: String,
    val holder: String,
)

/**
 * Банковские карты лежат в отдельной таблице и намеренно не участвуют
 * в экспорте/импорте через Telegram: передавать реквизиты в мессенджере нельзя.
 */
class BankCardRepository(private val dao: BankCardDao) {

    fun observeCards(): Flow<List<BankCardEntity>> = dao.observeAll()

    fun observeCard(id: Long): Flow<BankCardEntity?> = dao.observeById(id)

    suspend fun save(draft: BankCardDraft): Long = withContext(Dispatchers.IO) {
        val digits = CardNumber.digits(draft.number)
        val title = draft.title.trim().ifBlank {
            draft.bank.trim().ifBlank { PaymentSystem.title(PaymentSystem.detect(digits)) }
        }
        val entity = BankCardEntity(
            id = draft.id,
            title = title,
            bank = draft.bank.trim(),
            system = PaymentSystem.detect(digits),
            last4 = CardNumber.last4(digits),
            numberEnc = CardCrypto.encrypt(digits),
            expiryEnc = CardCrypto.encrypt(draft.expiry.trim()),
            holderEnc = CardCrypto.encrypt(draft.holder.trim().uppercase()),
            colorArgb = draft.colorArgb,
            note = draft.note.trim(),
            createdAt = if (draft.id == 0L) System.currentTimeMillis() else existingCreatedAt(draft.id),
        )
        if (draft.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            draft.id
        }
    }

    private suspend fun existingCreatedAt(id: Long): Long =
        dao.byId(id)?.createdAt ?: System.currentTimeMillis()

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.deleteById(id) }

    suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }

    /** Расшифровывает карту для показа. `null` — ключ потерян, карту нужно ввести заново. */
    suspend fun secret(id: Long): BankCardSecret? = withContext(Dispatchers.IO) {
        val card = dao.byId(id) ?: return@withContext null
        val number = CardCrypto.decrypt(card.numberEnc) ?: return@withContext null
        BankCardSecret(
            number = number,
            expiry = CardCrypto.decrypt(card.expiryEnc).orEmpty(),
            holder = CardCrypto.decrypt(card.holderEnc).orEmpty(),
        )
    }

    /** Черновик для редактирования существующей карты. */
    suspend fun draft(id: Long): BankCardDraft? = withContext(Dispatchers.IO) {
        val card = dao.byId(id) ?: return@withContext null
        val secret = secret(id)
        BankCardDraft(
            id = card.id,
            title = card.title,
            bank = card.bank,
            number = secret?.number.orEmpty(),
            expiry = secret?.expiry.orEmpty(),
            holder = secret?.holder.orEmpty(),
            note = card.note,
            colorArgb = card.colorArgb,
        )
    }
}
