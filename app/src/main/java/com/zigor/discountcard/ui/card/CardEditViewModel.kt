package com.zigor.discountcard.ui.card

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.db.CardKind
import com.zigor.discountcard.data.repo.CardDraft
import com.zigor.discountcard.data.repo.CardRepository
import com.zigor.discountcard.data.store.StoreCatalog
import com.zigor.discountcard.data.store.StoreInfo
import com.zigor.discountcard.util.PhotoStore
import kotlinx.coroutines.launch

enum class EditError { NAME_REQUIRED, CODE_REQUIRED }

class CardEditViewModel(
    private val repository: CardRepository,
    private val photoStore: PhotoStore,
    private val cardId: Long,
    initialDraft: CardDraft?,
) : ViewModel() {

    var draft by mutableStateOf(initialDraft ?: CardDraft())
        private set

    var loading by mutableStateOf(cardId != 0L)
        private set

    var error by mutableStateOf<EditError?>(null)
        private set

    private var original: CardEntity? = null

    val isNew: Boolean get() = cardId == 0L

    init {
        if (cardId != 0L) {
            viewModelScope.launch {
                repository.getCard(cardId)?.let { entity ->
                    original = entity
                    draft = CardDraft(
                        cardId = entity.id,
                        title = entity.title,
                        storeId = entity.storeId,
                        colorArgb = entity.colorArgb,
                        kind = entity.kind,
                        code = entity.code.orEmpty(),
                        codeFormat = entity.codeFormat,
                        nfcUid = entity.nfcUid,
                        nfcTech = entity.nfcTech,
                        nfcPayload = entity.nfcPayload,
                        frontPhoto = entity.frontPhoto,
                        backPhoto = entity.backPhoto,
                        note = entity.note.orEmpty(),
                    )
                }
                loading = false
            }
        }
    }

    fun setTitle(value: String) {
        draft = draft.copy(title = value)
        error = null
    }

    fun applyStore(store: StoreInfo) {
        draft = draft.copy(title = store.name, storeId = store.id, colorArgb = store.colorArgb)
        error = null
    }

    fun setCode(value: String) {
        draft = draft.copy(code = value.trim())
        error = null
    }

    fun setFormat(value: String) {
        draft = draft.copy(codeFormat = value)
    }

    fun setColor(argb: Int) {
        draft = draft.copy(colorArgb = argb)
    }

    fun setNote(value: String) {
        draft = draft.copy(note = value)
    }

    fun setPhoto(side: String, path: String) {
        draft = if (side == SIDE_BACK) draft.copy(backPhoto = path) else draft.copy(frontPhoto = path)
    }

    fun removePhoto(side: String) {
        val path = if (side == SIDE_BACK) draft.backPhoto else draft.frontPhoto
        // Удаляем файл, только если он ещё не сохранён в карточке.
        if (path != null && path != original?.frontPhoto && path != original?.backPhoto) {
            photoStore.delete(path)
        }
        draft = if (side == SIDE_BACK) draft.copy(backPhoto = null) else draft.copy(frontPhoto = null)
    }

    fun suggestions(query: String): List<StoreInfo> = repository.catalog.suggest(query)

    fun save(onSaved: (Long) -> Unit) {
        val current = draft
        if (current.title.isBlank()) {
            error = EditError.NAME_REQUIRED
            return
        }
        if (current.code.isBlank() && current.frontPhoto == null && current.nfcUid == null) {
            error = EditError.CODE_REQUIRED
            return
        }
        viewModelScope.launch {
            val base = original
            val kind = when {
                current.nfcUid != null -> CardKind.NFC
                current.code.isNotBlank() -> CardKind.BARCODE
                else -> CardKind.PHOTO
            }
            val entity = CardEntity(
                id = base?.id ?: 0L,
                title = current.title.trim(),
                storeId = current.storeId,
                colorArgb = current.colorArgb,
                kind = kind,
                code = current.code.takeIf { it.isNotBlank() },
                codeFormat = current.codeFormat,
                nfcUid = current.nfcUid,
                nfcTech = current.nfcTech,
                nfcPayload = current.nfcPayload,
                frontPhoto = current.frontPhoto,
                backPhoto = current.backPhoto,
                note = current.note.takeIf { it.isNotBlank() },
                favorite = base?.favorite ?: false,
                createdAt = base?.createdAt ?: System.currentTimeMillis(),
                lastUsedAt = System.currentTimeMillis(),
                useCount = base?.useCount ?: 0,
            )
            val id = repository.save(entity)
            if (current.code.isNotBlank()) {
                repository.remember(current.code, entity.title, entity.storeId, entity.colorArgb)
            }
            onSaved(id)
        }
    }

    companion object {
        const val SIDE_FRONT = "front"
        const val SIDE_BACK = "back"

        val COLORS: List<Int> get() = StoreCatalog.PALETTE

        fun factory(
            repository: CardRepository,
            photoStore: PhotoStore,
            cardId: Long,
            draft: CardDraft?,
        ) = viewModelFactory {
            initializer { CardEditViewModel(repository, photoStore, cardId, draft) }
        }
    }
}
