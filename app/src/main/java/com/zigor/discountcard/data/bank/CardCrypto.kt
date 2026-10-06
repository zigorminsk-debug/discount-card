package com.zigor.discountcard.data.bank

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Шифрование реквизитов банковских карт.
 *
 * Ключ AES-256 создаётся в Android Keystore: на большинстве устройств он живёт
 * в защищённом аппаратном модуле, его нельзя прочитать даже с root — наружу
 * отдаются только результаты шифрования. Режим GCM добавляет к шифртексту тег
 * целостности, поэтому подменённые данные не расшифруются.
 *
 * Формат хранимой строки: `base64(iv):base64(ciphertext+tag)`.
 */
object CardCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "bank_cards_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val SEPARATOR = ":"

    /** Шифрует строку. Пустая строка остаётся пустой: шифровать нечего. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return encode(cipher.iv) + SEPARATOR + encode(encrypted)
    }

    /**
     * Расшифровывает строку. Возвращает `null`, если данные испорчены или ключ
     * пропал (такое бывает после сброса блокировки экрана на части прошивок):
     * приложение в этом случае честно скажет, что карту нужно ввести заново.
     */
    fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return ""
        return runCatching {
            val parts = stored.split(SEPARATOR)
            if (parts.size != 2) return null
            val iv = decode(parts[0])
            val payload = decode(parts[1])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        existing?.secretKey?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
