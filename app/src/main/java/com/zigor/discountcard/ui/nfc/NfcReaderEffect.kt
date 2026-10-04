package com.zigor.discountcard.ui.nfc

import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.zigor.discountcard.util.findActivity

enum class NfcAvailability { READY, DISABLED, UNSUPPORTED }

fun nfcAvailability(adapter: NfcAdapter?): NfcAvailability = when {
    adapter == null -> NfcAvailability.UNSUPPORTED
    !adapter.isEnabled -> NfcAvailability.DISABLED
    else -> NfcAvailability.READY
}

/**
 * Пока экран открыт — телефон работает в режиме NFC-ридера.
 * Это надёжнее intent-фильтров: ни одна метка не «уводит» пользователя в другое приложение.
 */
@Composable
fun NfcReaderEffect(enabled: Boolean, onTag: (NfcTagData) -> Unit) {
    val context = LocalContext.current
    val currentOnTag by rememberUpdatedState(onTag)

    DisposableEffect(enabled) {
        val activity = context.findActivity()
        val adapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        if (!enabled || activity == null || adapter == null || !adapter.isEnabled) {
            return@DisposableEffect onDispose { }
        }
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = NfcAdapter.ReaderCallback { tag ->
            val data = runCatching { NfcTagReader.read(tag) }.getOrNull()
            if (data != null) mainHandler.post { currentOnTag(data) }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE
        runCatching { adapter.enableReaderMode(activity, callback, flags, null) }

        onDispose {
            runCatching { adapter.disableReaderMode(activity) }
        }
    }
}
