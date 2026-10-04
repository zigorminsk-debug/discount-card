package com.zigor.discountcard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.repo.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val cards: List<CardEntity> = emptyList(),
    val total: Int = 0,
    val query: String = "",
    val loading: Boolean = true,
)

class HomeViewModel(private val repository: CardRepository) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    val state: StateFlow<HomeUiState> =
        combine(repository.observeCards(), queryFlow) { cards, query ->
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) {
                cards
            } else {
                cards.filter { card ->
                    card.title.lowercase().contains(q) ||
                        card.code.orEmpty().lowercase().contains(q) ||
                        card.note.orEmpty().lowercase().contains(q)
                }
            }
            HomeUiState(cards = filtered, total = cards.size, query = query, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(value: String) {
        queryFlow.value = value
    }

    fun toggleFavorite(card: CardEntity) {
        viewModelScope.launch { repository.setFavorite(card.id, !card.favorite) }
    }

    companion object {
        fun factory(repository: CardRepository) = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
