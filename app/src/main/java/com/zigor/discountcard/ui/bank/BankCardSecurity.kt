package com.zigor.discountcard.ui.bank

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.zigor.discountcard.util.findActivity

/**
 * Единственный способ отключить FLAG_SECURE — из инструментального теста,
 * которому нужно снять экран. Из интерфейса приложения флаг не меняется.
 */
object SecureScreenSettings {
    @Volatile
    var enabled: Boolean = true
}

/**
 * Пока экран с реквизитами открыт — запрещаем снимки экрана и показ окна
 * в списке недавних приложений. Для дисконтных карт это не нужно, а здесь
 * штатная защита от «посмотрел через плечо и сфотографировал».
 */
@Composable
fun SecureScreenEffect(enabled: Boolean = SecureScreenSettings.enabled) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (enabled && window != null) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        onDispose { if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Настроена ли на телефоне блокировка экрана (ПИН, пароль, отпечаток). */
fun Context.deviceSecured(): Boolean {
    val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    return keyguard?.isDeviceSecure == true
}

/**
 * Запрашивает отпечаток/ПИН устройства перед показом реквизитов.
 * Если блокировка не настроена — вызывает [onConfirmed] сразу: требовать
 * нечего, а экран об этом честно предупреждает.
 */
@Composable
fun rememberCredentialGate(
    title: String,
    description: String,
    onConfirmed: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onConfirmed)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) callback.value()
    }
    return remember(title, description) {
        {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            @Suppress("DEPRECATION")
            val intent = if (keyguard?.isDeviceSecure == true) {
                keyguard.createConfirmDeviceCredentialIntent(title, description)
            } else {
                null
            }
            if (intent == null) {
                callback.value()
            } else {
                runCatching { launcher.launch(intent) }.onFailure { callback.value() }
            }
        }
    }
}

/**
 * Копирует реквизиты в буфер обмена, помечая их как чувствительные:
 * начиная с Android 13 система не показывает такой текст в превью буфера.
 */
fun copySensitive(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
        as? android.content.ClipboardManager ?: return
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    runCatching { clipboard.setPrimaryClip(clip) }
}

/** Чистит буфер обмена, если там всё ещё лежит наш номер. */
fun clearClipboard(context: Context, expected: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
        as? android.content.ClipboardManager ?: return
    runCatching {
        val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (current != null && current != expected) return
        if (Build.VERSION.SDK_INT >= 28) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
