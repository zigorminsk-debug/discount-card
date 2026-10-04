package com.zigor.discountcard.ui.imagescan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zigor.discountcard.data.db.CardKind
import com.zigor.discountcard.data.repo.CardDraft
import com.zigor.discountcard.data.repo.CardRepository
import com.zigor.discountcard.data.store.StoreCatalog
import com.zigor.discountcard.util.PhotoStore
import com.zigor.discountcard.util.importImageAsPhoto
import com.zigor.discountcard.util.scanImageForCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImageScanState {
    /** Ждём, пока пользователь выберет картинку. */
    data object Picking : ImageScanState

    data object Working : ImageScanState

    /** Код найден — дальше обычный экран карточки. */
    data class Ready(val draft: CardDraft) : ImageScanState

    /** Такой код уже сохранён. */
    data class Duplicate(val cardId: Long) : ImageScanState

    /** Кода на картинке нет — предлагаем сохранить её как фото карты. */
    data class NoCode(val uri: Uri) : ImageScanState
}

/** Распознавание кода на готовом изображении (скриншот, фото из галереи). */
class ImageScanViewModel(
    private val repository: CardRepository,
    private val photoStore: PhotoStore,
) : ViewModel() {

    private val _state = MutableStateFlow<ImageScanState>(ImageScanState.Picking)
    val state: StateFlow<ImageScanState> = _state.asStateFlow()

    fun onImagePicked(context: Context, uri: Uri) {
        if (_state.value is ImageScanState.Working) return
        _state.value = ImageScanState.Working
        val appContext = context.applicationContext
        viewModelScope.launch {
            val code = withContext(Dispatchers.Default) { scanImageForCode(appContext, uri) }
            if (code == null) {
                _state.value = ImageScanState.NoCode(uri)
                return@launch
            }
            val existing = repository.findByCode(code.value)
            if (existing != null) {
                _state.value = ImageScanState.Duplicate(existing.id)
                return@launch
            }
            val suggestion = repository.resolveStore(code.value, code.format)
            _state.value = ImageScanState.Ready(
                CardDraft(
                    title = suggestion.title.orEmpty(),
                    storeId = suggestion.storeId,
                    colorArgb = if (suggestion.found) suggestion.colorArgb else StoreCatalog.DEFAULT_COLOR,
                    kind = CardKind.BARCODE,
                    code = code.value,
                    codeFormat = code.format,
                    suggestion = suggestion,
                ),
            )
        }
    }

    /** Кода нет — сохраняем картинку как лицевую сторону карты. */
    fun useAsCardPhoto(context: Context, uri: Uri) {
        _state.value = ImageScanState.Working
        val appContext = context.applicationContext
        viewModelScope.launch {
            val path = withContext(Dispatchers.Default) {
                importImageAsPhoto(appContext, uri, photoStore)
            }
            _state.value = ImageScanState.Ready(
                CardDraft(
                    colorArgb = StoreCatalog.DEFAULT_COLOR,
                    kind = CardKind.PHOTO,
                    frontPhoto = path,
                ),
            )
        }
    }

    fun pickAgain() {
        _state.value = ImageScanState.Picking
    }

    companion object {
        fun factory(repository: CardRepository, photoStore: PhotoStore) = viewModelFactory {
            initializer { ImageScanViewModel(repository, photoStore) }
        }
    }
}
