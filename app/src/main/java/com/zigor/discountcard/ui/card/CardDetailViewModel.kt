package com.zigor.discountcard.ui.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.repo.CardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardDetailViewModel(
    private val repository: CardRepository,
    private val cardId: Long,
) : ViewModel() {

    val card: StateFlow<CardEntity?> = repository.observeCard(cardId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { repository.markUsed(cardId) }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            card.value?.let { repository.setFavorite(it.id, !it.favorite) }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            card.value?.let { repository.delete(it) }
            onDeleted()
        }
    }

    companion object {
        fun factory(repository: CardRepository, cardId: Long) = viewModelFactory {
            initializer { CardDetailViewModel(repository, cardId) }
        }
    }
}
