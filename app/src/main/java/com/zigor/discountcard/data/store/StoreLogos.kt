package com.zigor.discountcard.data.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Логотипы магазинов.
 *
 * Три источника, в порядке приоритета:
 *  1) файл из APK — assets/logos/<id>.png, работает сразу и офлайн;
 *  2) ранее скачанный логотип в памяти приложения;
 *  3) загрузка иконки с сайта сети (только если пользователь это разрешил).
 *
 * Из логотипа вычисляется фирменный цвет: он точнее, чем цвет, записанный
 * в каталоге руками, и автоматически обновляется вместе с ребрендингом сети.
 */
class StoreLogoStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val memory = LruCache<String, Bitmap>(64)
    private val missing = HashSet<String>()
    private val attempted = HashSet<String>()

    private val _updates = MutableStateFlow(0)

    /** Растёт после каждой удачной загрузки — экраны по нему перерисовываются. */
    val updates: StateFlow<Int> = _updates.asStateFlow()

    private val cacheDir: File by lazy {
        File(appContext.filesDir, "logos").apply { mkdirs() }
    }

    /** Разрешена ли загрузка логотипов из интернета. */
    var downloadEnabled: Boolean
        get() = prefs.getBoolean(KEY_DOWNLOAD, true)
        set(value) = prefs.edit().putBoolean(KEY_DOWNLOAD, value).apply()

    /** Готовый логотип, если он уже есть на телефоне. Без сети и без ожидания. */
    fun logo(storeId: String?): Bitmap? {
        val id = storeId ?: return null
        memory.get(id)?.let { return it }
        if (id in missing) return null
        val bitmap = readAsset(id) ?: readCache(id)
        if (bitmap == null) {
            missing += id
            return null
        }
        memory.put(id, bitmap)
        return bitmap
    }

    /** Цвет, вычисленный по логотипу; null — если логотипа ещё нет. */
    fun brandColor(storeId: String?): Int? {
        val id = storeId ?: return null
        val saved = prefs.getInt(colorKey(id), 0)
        if (saved != 0) return saved
        val bitmap = logo(id) ?: return null
        val color = extractBrandColor(bitmap) ?: return null
        prefs.edit().putInt(colorKey(id), color).apply()
        return color
    }

    /** Есть ли логотип в APK — такие сети показывают логотип всегда, даже без интернета. */
    fun isBundled(storeId: String?): Boolean {
        val id = storeId ?: return false
        return runCatching { appContext.assets.open("logos/$id.png").close() }.isSuccess
    }

    /**
     * Догружает логотип с сайта сети. Возвращает true, если логотип появился.
     * Молча ничего не делает, если загрузка выключена или у сети нет сайта.
     */
    suspend fun fetch(store: StoreInfo): Boolean = withContext(Dispatchers.IO) {
        if (!downloadEnabled) return@withContext false
        if (logo(store.id) != null) return@withContext false
        val domain = store.domains.firstOrNull() ?: return@withContext false
        synchronized(attempted) { if (!attempted.add(store.id)) return@withContext false }
        // 1. привычные адреса значка на самом сайте
        for (url in iconUrls(domain).filterNot { it.startsWith(FAVICON_SERVICE) }) {
            if (saveIcon(store.id, download(url) ?: continue)) return@withContext true
        }
        // 2. значок, объявленный в вёрстке главной страницы — многие сети держат его там
        val html = downloadText("https://$domain/")
        if (html != null) {
            for (url in iconsFromHtml(domain, html)) {
                if (saveIcon(store.id, download(url) ?: continue)) return@withContext true
            }
        }
        // 3. сервис-посредник зовём только если сайт вообще отозвался: для несуществующего
        // домена он вернёт картинку-заглушку, а чужой значок на карте хуже монограммы
        if (html == null && !siteResponds(domain)) return@withContext false
        val service = iconUrls(domain).first { it.startsWith(FAVICON_SERVICE) }
        saveIcon(store.id, download(service) ?: return@withContext false)
    }

    /** Сохраняет скачанный значок, если это картинка годного размера. */
    private fun saveIcon(id: String, bytes: ByteArray): Boolean {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        if (bitmap.width < MIN_SIZE || bitmap.height < MIN_SIZE) return false
        val scaled = fit(bitmap, MAX_SIZE)
        runCatching {
            File(cacheDir, "$id.png").outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.onFailure { Log.w(TAG, "логотип $id не сохранился: ${it.message}") }
        missing -= id
        memory.put(id, scaled)
        extractBrandColor(scaled)?.let { prefs.edit().putInt(colorKey(id), it).apply() }
        _updates.value += 1
        return true
    }

    /** Забыть скачанные логотипы (из APK остаются). */
    fun clearDownloaded() {
        runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
        memory.evictAll()
        missing.clear()
        synchronized(attempted) { attempted.clear() }
        _updates.value += 1
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(PREFIX_COLOR) }.forEach { remove(it) }
        }.apply()
    }

    private fun readAsset(id: String): Bitmap? = runCatching {
        appContext.assets.open("logos/$id.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun readCache(id: String): Bitmap? {
        val file = File(cacheDir, "$id.png")
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /** Отвечает ли сайт сети вообще — проверка перед обращением к сервису значков. */
    private fun siteResponds(domain: String): Boolean =
        respondsTo(domain, "HEAD") || respondsTo(domain, "GET")

    private fun respondsTo(domain: String, method: String): Boolean = runCatching {
        val connection = (URL("https://" + domain + "/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            requestMethod = method
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            connection.responseCode in 200..499
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    /** Главная страница сайта — нужна, чтобы достать из неё адрес значка. */
    private fun downloadText(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { stream ->
                val buffer = ByteArray(MAX_HTML)
                var read = 0
                while (read < MAX_HTML) {
                    val part = stream.read(buffer, read, MAX_HTML - read)
                    if (part <= 0) break
                    read += part
                }
                String(buffer, 0, read, Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun download(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "image/png,image/*;q=0.8")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size < 200 || bytes.size > 512 * 1024) null else bytes
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun fit(bitmap: Bitmap, max: Int): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= max) return bitmap
        val scale = max.toFloat() / side
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun colorKey(id: String) = PREFIX_COLOR + id

    companion object {
        private const val TAG = "StoreLogoStore"
        private const val PREFS = "logos"
        private const val KEY_DOWNLOAD = "download_enabled"
        private const val PREFIX_COLOR = "color_"
        private const val MIN_SIZE = 24
        private const val MAX_SIZE = 192
        private const val MAX_HTML = 256 * 1024
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) BY-Card/1.0"
        private const val FAVICON_SERVICE = "https://www.google.com/s2/favicons"

        /**
         * Сначала — иконка с самого сайта сети: она крупная и всегда актуальная.
         * Если её нет, берём значок сайта через сервис Google (он умеет доставать
         * favicon из вёрстки, но картинка мельче).
         */
        fun iconUrls(domain: String): List<String> = listOf(
            "https://$domain/apple-touch-icon.png",
            "https://$domain/apple-touch-icon-precomposed.png",
            "https://$domain/favicon.png",
            "https://www.google.com/s2/favicons?domain=$domain&sz=128",
        )

        /**
         * Значки, объявленные в вёрстке страницы: <link rel="icon" href="...">.
         * Крупные идут первыми. Форматы .ico и .svg пропускаем — Android их не читает.
         */
        fun iconsFromHtml(domain: String, html: String): List<String> {
            val relPattern = Regex("""rel\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val hrefPattern = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val sizePattern = Regex("""sizes\s*=\s*["'](\d+)x""", RegexOption.IGNORE_CASE)
            val found = ArrayList<Pair<Int, String>>()
            for (tag in Regex("<link\\s[^>]*>", RegexOption.IGNORE_CASE).findAll(html)) {
                val text = tag.value
                val rel = relPattern.find(text)?.groupValues?.get(1)?.lowercase() ?: continue
                if (!rel.contains("icon")) continue
                val href = hrefPattern.find(text)?.groupValues?.get(1) ?: continue
                val url = absoluteUrl(domain, href) ?: continue
                if (url.endsWith(".ico", true) || url.endsWith(".svg", true)) continue
                val size = sizePattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
                    ?: if (rel.contains("apple")) 180 else 32
                found += size to url
            }
            return found.sortedByDescending { it.first }.map { it.second }.distinct()
        }

        private fun absoluteUrl(domain: String, href: String): String? = when {
            href.isBlank() || href.startsWith("data:") -> null
            href.startsWith("https://") -> href
            href.startsWith("http://") -> "https://" + href.removePrefix("http://")
            href.startsWith("//") -> "https:$href"
            href.startsWith("/") -> "https://$domain$href"
            else -> "https://$domain/$href"
        }

        /**
         * Фирменный цвет логотипа: самый «весомый» насыщенный оттенок.
         * Белый фон, серые рамки и чёрный текст не считаются.
         */
        fun extractBrandColor(bitmap: Bitmap): Int? {
            val size = 32
            val small = runCatching {
                Bitmap.createScaledBitmap(bitmap, size, size, true)
            }.getOrNull() ?: return null
            val weight = HashMap<Int, Int>()
            val sumR = HashMap<Int, Long>()
            val sumG = HashMap<Int, Long>()
            val sumB = HashMap<Int, Long>()
            val count = HashMap<Int, Int>()
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val pixel = small.getPixel(x, y)
                    if (Color.alpha(pixel) < 128) continue
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    val saturation = max - min
                    if (max < 40 || saturation < 40) continue
                    val key = (r / 32 shl 10) or (g / 32 shl 5) or (b / 32)
                    weight[key] = (weight[key] ?: 0) + 1 + saturation / 16
                    sumR[key] = (sumR[key] ?: 0L) + r
                    sumG[key] = (sumG[key] ?: 0L) + g
                    sumB[key] = (sumB[key] ?: 0L) + b
                    count[key] = (count[key] ?: 0) + 1
                }
            }
            val best = weight.maxByOrNull { it.value }?.key ?: return null
            val n = count[best] ?: return null
            if (n < 3) return null
            return Color.rgb(
                (sumR.getValue(best) / n).toInt(),
                (sumG.getValue(best) / n).toInt(),
                (sumB.getValue(best) / n).toInt(),
            )
        }

        /** Монограмма: одна-две буквы названия для магазинов без логотипа. */
        fun initials(title: String): String {
            val words = title.trim().split(' ', '-', '«', '»', '"').filter { it.isNotBlank() }
            if (words.isEmpty()) return "?"
            val first = words[0]
            return if (words.size == 1) {
                first.take(if (first.length > 3) 2 else 1).uppercase()
            } else {
                (first.take(1) + words[1].take(1)).uppercase()
            }
        }
    }
}
