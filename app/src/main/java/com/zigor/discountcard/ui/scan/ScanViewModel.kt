package com.zigor.discountcard.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zigor.discountcard.data.db.CardKind
import com.zigor.discountcard.data.repo.CardDraft
import com.zigor.discountcard.data.repo.CardRepository
import com.zigor.discountcard.data.store.StoreCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanEvent {
    /** Код прочитан, магазин найден или нет — дальше экран карточки. */
    data class Ready(val draft: CardDraft) : ScanEvent

    /** Такая карта уже есть — открываем её. */
    data class Duplicate(val cardId: Long) : ScanEvent
}

class ScanViewModel(private val repository: CardRepository) : ViewModel() {

    private val _event = MutableStateFlow<ScanEvent?>(null)
    val event: StateFlow<ScanEvent?> = _event.asStateFlow()

    private var busy = false

    fun onCodeScanned(value: String, format: String) {
        if (busy || value.isBlank()) return
        busy = true
        viewModelScope.launch {
            val existing = repository.findByCode(value)
            if (existing != null) {
                _event.value = ScanEvent.Duplicate(existing.id)
                return@launch
            }
            val suggestion = repository.resolveStore(value, format)
            _event.value = ScanEvent.Ready(
                CardDraft(
                    title = suggestion.title.orEmpty(),
                    storeId = suggestion.storeId,
                    colorArgb = if (suggestion.found) suggestion.colorArgb else StoreCatalog.DEFAULT_COLOR,
                    kind = CardKind.BARCODE,
                    code = value,
                    codeFormat = format,
                    suggestion = suggestion,
                ),
            )
        }
    }

    fun consumed() {
        _event.value = null
        busy = false
    }

    companion object {
        fun factory(repository: CardRepository) = viewModelFactory {
            initializer { ScanViewModel(repository) }
        }
    }
}
