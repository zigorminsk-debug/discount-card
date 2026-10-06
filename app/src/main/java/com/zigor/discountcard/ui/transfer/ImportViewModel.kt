package com.zigor.discountcard.ui.transfer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zigor.discountcard.data.repo.CardRepository
import com.zigor.discountcard.data.transfer.CardTransfer
import com.zigor.discountcard.util.PhotoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Working : ImportState
    data class Ready(val uri: Uri, val preview: CardTransfer.Preview) : ImportState
    data class Done(val result: CardTransfer.ImportResult) : ImportState
    data object Failed : ImportState
}

class ImportViewModel(
    private val repository: CardRepository,
    private val photoStore: PhotoStore,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Working)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun openFile(context: Context, uri: Uri) {
        _state.value = ImportState.Working
        val appContext = context.applicationContext
        viewModelScope.launch {
            val preview = runCatching { CardTransfer.preview(appContext, uri) }.getOrNull()
            _state.value = if (preview == null || !preview.valid || preview.cards == 0) {
                ImportState.Failed
            } else {
                ImportState.Ready(uri, preview)
            }
        }
    }

    fun confirm(context: Context) {
        val ready = _state.value as? ImportState.Ready ?: return
        _state.value = ImportState.Working
        val appContext = context.applicationContext
        viewModelScope.launch {
            val result = runCatching {
                CardTransfer.importFrom(appContext, ready.uri, repository, photoStore)
            }.getOrNull()
            _state.value = if (result == null) ImportState.Failed else ImportState.Done(result)
        }
    }

    companion object {
        fun factory(repository: CardRepository, photoStore: PhotoStore) = viewModelFactory {
            initializer { ImportViewModel(repository, photoStore) }
        }
    }
}
