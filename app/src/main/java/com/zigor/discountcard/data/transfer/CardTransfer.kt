package com.zigor.discountcard.data.transfer

import android.content.Context
import android.net.Uri
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.db.CardKind
import com.zigor.discountcard.data.repo.CardRepository
import com.zigor.discountcard.data.store.StoreCatalog
import com.zigor.discountcard.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Перенос карт на другой телефон: все карты и фотографии упаковываются в один
 * файл `.mkcards` (обычный zip с `cards.json` внутри). Файл отправляется любым
 * мессенджером — Telegram, почтой, по Bluetooth — и открывается этим же
 * приложением на другом устройстве. Интернет приложению для этого не нужен.
 */
object CardTransfer {

    const val EXTENSION = "mkcards"
    const val MIME = "application/octet-stream"
    private const val FORMAT = 1
    private const val CARDS_ENTRY = "cards.json"
    private const val PHOTOS_DIR = "photos/"

    data class ExportResult(val file: File, val cards: Int)

    data class Preview(val cards: Int, val exportedAt: Long, val valid: Boolean)

    data class ImportResult(val added: Int, val skipped: Int)

    /** Складывает все карты и фото в файл во временной папке приложения. */
    suspend fun export(context: Context, repository: CardRepository): ExportResult =
        withContext(Dispatchers.IO) {
            val cards = repository.observeCards().first()
            val dir = File(context.cacheDir, "export").apply {
                deleteRecursively()
                mkdirs()
            }
            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val file = File(dir, "moi-karty-$stamp.$EXTENSION")

            val photos = LinkedHashMap<String, File>()
            val array = JSONArray()
            cards.forEach { card ->
                array.put(
                    JSONObject().apply {
                        put("title", card.title)
                        put("storeId", card.storeId)
                        put("colorArgb", card.colorArgb)
                        put("kind", card.kind)
                        put("code", card.code)
                        put("codeFormat", card.codeFormat)
                        put("nfcUid", card.nfcUid)
                        put("nfcTech", card.nfcTech)
                        put("nfcPayload", card.nfcPayload)
                        put("note", card.note)
                        put("favorite", card.favorite)
                        put("createdAt", card.createdAt)
                        put("frontPhoto", photoEntry(card.frontPhoto, photos))
                        put("backPhoto", photoEntry(card.backPhoto, photos))
                    },
                )
            }
            val root = JSONObject().apply {
                put("app", "moi-karty")
                put("format", FORMAT)
                put("exportedAt", System.currentTimeMillis())
                put("cards", array)
            }

            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(CARDS_ENTRY))
                zip.write(root.toString(2).toByteArray())
                zip.closeEntry()
                photos.forEach { (entryName, source) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            ExportResult(file, cards.size)
        }

    /** Что лежит в выбранном файле — показываем до импорта. */
    suspend fun preview(context: Context, uri: Uri): Preview = withContext(Dispatchers.IO) {
        val json = readManifest(context, uri) ?: return@withContext Preview(0, 0L, valid = false)
        Preview(
            cards = json.optJSONArray("cards")?.length() ?: 0,
            exportedAt = json.optLong("exportedAt"),
            valid = json.optString("app") == "moi-karty",
        )
    }

    /**
     * Добавляет карты из файла. Карты, которые уже есть (тот же код или та же
     * NFC-метка), пропускаются — повторный импорт ничего не ломает.
     */
    suspend fun importFrom(
        context: Context,
        uri: Uri,
        repository: CardRepository,
        photoStore: PhotoStore,
    ): ImportResult = withContext(Dispatchers.IO) {
        val json = readManifest(context, uri) ?: return@withContext ImportResult(0, 0)
        val cards = json.optJSONArray("cards") ?: JSONArray()
        val photos = extractPhotos(context, uri, photoStore)

        // Ключи уже имеющихся карт: повторный импорт того же файла ничего не дублирует
        val keys = repository.observeCards().first()
            .map { cardKey(it.code, it.nfcUid, it.title, it.createdAt) }
            .toMutableSet()

        var added = 0
        var skipped = 0
        for (index in 0 until cards.length()) {
            val item = cards.optJSONObject(index) ?: continue
            val code = item.optStringOrNull("code")
            val nfcUid = item.optStringOrNull("nfcUid")
            val title = item.optStringOrNull("title") ?: continue
            val createdAt = item.optLong("createdAt", 0L)
            if (!keys.add(cardKey(code, nfcUid, title, createdAt))) {
                skipped++
                continue
            }
            val entity = CardEntity(
                title = title,
                storeId = item.optStringOrNull("storeId"),
                colorArgb = item.optInt("colorArgb", StoreCatalog.DEFAULT_COLOR),
                kind = item.optStringOrNull("kind") ?: CardKind.BARCODE,
                code = code,
                codeFormat = item.optStringOrNull("codeFormat") ?: "CODE_128",
                nfcUid = nfcUid,
                nfcTech = item.optStringOrNull("nfcTech"),
                nfcPayload = item.optStringOrNull("nfcPayload"),
                frontPhoto = item.optStringOrNull("frontPhoto")?.let { photos[it] },
                backPhoto = item.optStringOrNull("backPhoto")?.let { photos[it] },
                note = item.optStringOrNull("note"),
                favorite = item.optBoolean("favorite"),
                createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis(),
            )
            repository.save(entity)
            if (!code.isNullOrBlank()) {
                repository.remember(code, entity.title, entity.storeId, entity.colorArgb)
            }
            added++
        }
        ImportResult(added, skipped)
    }

    /** По чему считаем, что это «та же самая» карта. */
    private fun cardKey(code: String?, nfcUid: String?, title: String, createdAt: Long): String = when {
        !code.isNullOrBlank() -> "code:$code"
        !nfcUid.isNullOrBlank() -> "nfc:$nfcUid"
        else -> "name:${title.trim().lowercase()}|$createdAt"
    }

    private fun photoEntry(path: String?, into: LinkedHashMap<String, File>): String? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val name = PHOTOS_DIR + file.name
        into[name] = file
        return name
    }

    private fun readManifest(context: Context, uri: Uri): JSONObject? = runCatching {
        openZip(context, uri)?.use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (entry.name == CARDS_ENTRY) {
                    return@use JSONObject(zip.readBytes().decodeToString())
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            null
        }
    }.getOrNull()

    /** Распаковывает фотографии в хранилище приложения: имя в архиве -> новый путь. */
    private fun extractPhotos(
        context: Context,
        uri: Uri,
        photoStore: PhotoStore,
    ): Map<String, String> = runCatching {
        val result = mutableMapOf<String, String>()
        openZip(context, uri)?.use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.startsWith(PHOTOS_DIR) && !entry.isDirectory) {
                    val target = photoStore.newFile()
                    target.outputStream().use { out -> zip.copyTo(out) }
                    result[name] = target.absolutePath
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        result.toMap()
    }.getOrDefault(emptyMap())

    private fun openZip(context: Context, uri: Uri): ZipInputStream? {
        val stream = if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        } ?: return null
        return ZipInputStream(stream.buffered())
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }
}
