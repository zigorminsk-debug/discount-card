package com.zigor.discountcard.data.repo

import com.zigor.discountcard.data.db.CardDao
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.db.CardKind
import com.zigor.discountcard.data.db.LearnedStoreDao
import com.zigor.discountcard.data.db.LearnedStoreEntity
import com.zigor.discountcard.data.store.MatchConfidence
import com.zigor.discountcard.data.store.StoreCatalog
import kotlinx.coroutines.flow.Flow

/** Результат распознавания магазина по считанному коду. */
data class StoreSuggestion(
    val title: String? = null,
    val storeId: String? = null,
    val colorArgb: Int = StoreCatalog.DEFAULT_COLOR,
    val confidence: MatchConfidence = MatchConfidence.NONE,
    val reason: String? = null,
    val issuerHint: String? = null,
    val fromMemory: Boolean = false,
) {
    val found: Boolean get() = !title.isNullOrBlank() && confidence != MatchConfidence.NONE
}

/** Черновик карты: то, что считали, но ещё не сохранили. */
data class CardDraft(
    val cardId: Long = 0,
    val title: String = "",
    val storeId: String? = null,
    val colorArgb: Int = StoreCatalog.DEFAULT_COLOR,
    val kind: String = CardKind.BARCODE,
    val code: String = "",
    val codeFormat: String = "CODE_128",
    val nfcUid: String? = null,
    val nfcTech: String? = null,
    val nfcPayload: String? = null,
    val frontPhoto: String? = null,
    val backPhoto: String? = null,
    val note: String = "",
    val suggestion: StoreSuggestion = StoreSuggestion(),
)

class CardRepository(
    private val cardDao: CardDao,
    private val learnedDao: LearnedStoreDao,
    val catalog: StoreCatalog,
    private val photoStore: com.zigor.discountcard.util.PhotoStore,
) {

    fun observeCards(): Flow<List<CardEntity>> = cardDao.observeAll()

    fun observeCard(id: Long): Flow<CardEntity?> = cardDao.observeById(id)

    suspend fun getCard(id: Long): CardEntity? = cardDao.getById(id)

    suspend fun findByCode(code: String): CardEntity? = cardDao.findByCode(code)

    suspend fun findByNfcUid(uid: String): CardEntity? = cardDao.findByNfcUid(uid)

    suspend fun markUsed(id: Long) = cardDao.markUsed(id, System.currentTimeMillis())

    suspend fun setFavorite(id: Long, favorite: Boolean) = cardDao.setFavorite(id, favorite)

    suspend fun save(card: CardEntity): Long =
        if (card.id == 0L) cardDao.insert(card) else cardDao.update(card).let { card.id }

    suspend fun delete(card: CardEntity) {
        photoStore.delete(card.frontPhoto)
        photoStore.delete(card.backPhoto)
        cardDao.deleteById(card.id)
    }

    /**
     * Ищем магазин: сначала в «памяти» приложения (что пользователь уже называл сам),
     * затем в каталоге по ссылке/ключевому слову/правилу префикса.
     */
    suspend fun resolveStore(code: String, format: String): StoreSuggestion {
        val value = code.trim()
        if (value.isEmpty()) return StoreSuggestion()

        learnedDao.bestMatch(lookupKeys(value))?.let { learned ->
            return StoreSuggestion(
                title = learned.title,
                storeId = learned.storeId,
                colorArgb = learned.colorArgb,
                confidence = MatchConfidence.EXACT,
                reason = "вы уже сохраняли такую карту",
                issuerHint = catalog.issuerHint(value),
                fromMemory = true,
            )
        }

        val match = catalog.match(value, format)
        val store = match.store
        return StoreSuggestion(
            title = store?.name,
            storeId = store?.id,
            colorArgb = store?.colorArgb ?: StoreCatalog.DEFAULT_COLOR,
            confidence = match.confidence,
            reason = match.reason,
            issuerHint = catalog.issuerHint(value),
        )
    }

    /** Запоминаем связку «префикс кода -> магазин», чтобы следующая карта распозналась сама. */
    suspend fun remember(code: String, title: String, storeId: String?, colorArgb: Int) {
        val key = learnKey(code.trim()) ?: return
        if (title.isBlank()) return
        learnedDao.upsert(
            LearnedStoreEntity(
                codeKey = key,
                title = title.trim(),
                storeId = storeId,
                colorArgb = colorArgb,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun learnedCount(): Int = learnedDao.count()

    private fun host(code: String): String? = runCatching {
        if (!code.contains("://")) return null
        android.net.Uri.parse(code).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()

    private fun lookupKeys(code: String): List<String> {
        host(code)?.let { return listOf("host:$it") }
        if (!code.all { it.isDigit() }) {
            return listOf("text:" + code.lowercase().take(12))
        }
        return listOf(8, 7, 6, 5, 4).filter { code.length > it }.map { code.take(it) }
    }

    private fun learnKey(code: String): String? {
        host(code)?.let { return "host:$it" }
        if (code.isEmpty()) return null
        if (!code.all { it.isDigit() }) return "text:" + code.lowercase().take(12)
        return when {
            code.length > 8 -> code.take(6)
            code.length > 5 -> code.take(4)
            else -> null
        }
    }
}
