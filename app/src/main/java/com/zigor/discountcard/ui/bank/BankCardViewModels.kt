package com.zigor.discountcard.ui.bank

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zigor.discountcard.data.bank.CardExpiry
import com.zigor.discountcard.data.bank.CardNumber
import com.zigor.discountcard.data.db.BankCardEntity
import com.zigor.discountcard.data.repo.BankCardDraft
import com.zigor.discountcard.data.repo.BankCardRepository
import com.zigor.discountcard.data.repo.BankCardSecret
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Список банковских карт. */
class BankCardsViewModel(repository: BankCardRepository) : ViewModel() {

    val cards: StateFlow<List<BankCardEntity>> = repository.observeCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(repository: BankCardRepository) = viewModelFactory {
            initializer { BankCardsViewModel(repository) }
        }
    }
}

enum class BankEditError { NUMBER_INVALID, EXPIRY_INVALID }

/** Ввод и правка банковской карты. */
class BankCardEditViewModel(
    private val repository: BankCardRepository,
    private val cardId: Long,
) : ViewModel() {

    var draft by mutableStateOf(BankCardDraft(id = cardId))
        private set

    var error by mutableStateOf<BankEditError?>(null)
        private set

    var saving by mutableStateOf(false)
        private set

    val isNew: Boolean get() = cardId == 0L

    init {
        if (cardId != 0L) {
            viewModelScope.launch {
                repository.draft(cardId)?.let { loaded ->
                    draft = loaded.copy(number = CardNumber.format(loaded.number))
                }
            }
        }
    }

    fun setNumber(value: String) {
        draft = draft.copy(number = CardNumber.format(value))
        error = null
    }

    fun setExpiry(value: String) {
        draft = draft.copy(expiry = CardExpiry.format(value))
        error = null
    }

    fun setHolder(value: String) {
        draft = draft.copy(holder = value.filter { it.isLetter() || it == ' ' || it == '-' }.uppercase())
    }

    fun setBank(value: String) {
        draft = draft.copy(bank = value)
    }

    fun setTitle(value: String) {
        draft = draft.copy(title = value)
    }

    fun setNote(value: String) {
        draft = draft.copy(note = value)
    }

    fun setColor(color: Int) {
        draft = draft.copy(colorArgb = color)
    }

    /** Срок уже прошёл? Это не ошибка ввода, просто предупреждение. */
    val expired: Boolean get() = CardExpiry.expired(draft.expiry)

    fun save(onSaved: (Long) -> Unit) {
        if (!CardNumber.valid(draft.number)) {
            error = BankEditError.NUMBER_INVALID
            return
        }
        if (draft.expiry.isNotBlank() && !CardExpiry.wellFormed(draft.expiry)) {
            error = BankEditError.EXPIRY_INVALID
            return
        }
        if (saving) return
        saving = true
        viewModelScope.launch {
            val id = repository.save(draft)
            saving = false
            onSaved(id)
        }
    }

    companion object {
        fun factory(repository: BankCardRepository, cardId: Long) = viewModelFactory {
            initializer { BankCardEditViewModel(repository, cardId) }
        }
    }
}

/** Просмотр карты: реквизиты расшифровываются только по явному запросу. */
class BankCardDetailViewModel(
    private val repository: BankCardRepository,
    private val cardId: Long,
) : ViewModel() {

    val card: StateFlow<BankCardEntity?> = repository.observeCard(cardId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var secret by mutableStateOf<BankCardSecret?>(null)
        private set

    /** Ключ пропал (например, после смены блокировки экрана) — карту надо ввести заново. */
    var decryptFailed by mutableStateOf(false)
        private set

    private var hideJob: Job? = null

    /** Показывает реквизиты и сам прячет их через [VISIBLE_SECONDS] секунд. */
    fun reveal() {
        viewModelScope.launch {
            val value = repository.secret(cardId)
            if (value == null) {
                decryptFailed = true
                return@launch
            }
            secret = value
            decryptFailed = false
            hideJob?.cancel()
            hideJob = viewModelScope.launch {
                delay(VISIBLE_SECONDS * 1000L)
                secret = null
            }
        }
    }

    fun hide() {
        hideJob?.cancel()
        secret = null
    }

    /** Отдаёт номер для копирования, не показывая его на экране. */
    fun withNumber(action: (String) -> Unit) {
        val known = secret
        if (known != null) {
            action(known.number)
            return
        }
        viewModelScope.launch {
            val value = repository.secret(cardId)
            if (value == null) decryptFailed = true else action(value.number)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(cardId)
            onDeleted()
        }
    }

    override fun onCleared() {
        hideJob?.cancel()
        secret = null
        super.onCleared()
    }

    companion object {
        const val VISIBLE_SECONDS = 45

        fun factory(repository: BankCardRepository, cardId: Long) = viewModelFactory {
            initializer { BankCardDetailViewModel(repository, cardId) }
        }
    }
}
