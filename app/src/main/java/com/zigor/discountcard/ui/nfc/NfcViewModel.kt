package com.zigor.discountcard.ui.nfc

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

sealed interface NfcEvent {
    data class Ready(val draft: CardDraft, val tag: NfcTagData) : NfcEvent
    data class Duplicate(val cardId: Long) : NfcEvent
}

class NfcViewModel(private val repository: CardRepository) : ViewModel() {

    private val _event = MutableStateFlow<NfcEvent?>(null)
    val event: StateFlow<NfcEvent?> = _event.asStateFlow()

    private val _lastTag = MutableStateFlow<NfcTagData?>(null)
    val lastTag: StateFlow<NfcTagData?> = _lastTag.asStateFlow()

    private var busy = false

    fun onTag(tag: NfcTagData) {
        if (busy) return
        busy = true
        _lastTag.value = tag
        viewModelScope.launch {
            val existing = tag.uid.takeIf { it.isNotBlank() }?.let { repository.findByNfcUid(it) }
            if (existing != null) {
                _event.value = NfcEvent.Duplicate(existing.id)
                return@launch
            }
            val candidate = tag.codeCandidate.orEmpty()
            val codeFormat = when {
                candidate.startsWith("http", ignoreCase = true) -> "QR_CODE"
                else -> "CODE_128"
            }
            val suggestion = if (candidate.isNotBlank()) {
                repository.resolveStore(candidate, codeFormat)
            } else {
                repository.resolveStore(tag.uid, "CODE_128")
            }
            _event.value = NfcEvent.Ready(
                draft = CardDraft(
                    title = suggestion.title.orEmpty(),
                    storeId = suggestion.storeId,
                    colorArgb = if (suggestion.found) suggestion.colorArgb else StoreCatalog.DEFAULT_COLOR,
                    kind = CardKind.NFC,
                    code = candidate,
                    codeFormat = codeFormat,
                    nfcUid = tag.uid,
                    nfcTech = tag.techs.joinToString(", "),
                    nfcPayload = listOfNotNull(tag.payload, tag.uri, tag.extra).joinToString(" | ")
                        .takeIf { it.isNotBlank() },
                    suggestion = suggestion,
                ),
                tag = tag,
            )
        }
    }

    fun consumed() {
        _event.value = null
        busy = false
    }

    companion object {
        fun factory(repository: CardRepository) = viewModelFactory {
            initializer { NfcViewModel(repository) }
        }
    }
}
