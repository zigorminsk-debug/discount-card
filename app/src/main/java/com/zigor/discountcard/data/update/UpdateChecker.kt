package com.zigor.discountcard.data.update

import android.content.Context
import com.zigor.discountcard.util.appVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка новой версии на странице релизов GitHub.
 *
 * Единственное место, где приложению нужна сеть: запрашивается только список
 * релизов (номер версии и ссылка на APK). Карты, фотографии и любые личные
 * данные никуда не отправляются.
 */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/zigorminsk-debug/discount-card/releases?per_page=10"
    private const val PREFS = "updates"
    private const val KEY_LAST_CHECK = "last_check_at"
    private const val KEY_SKIPPED = "skipped_version"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Release(
        val version: String,
        val title: String,
        val notes: String,
        val apkUrl: String?,
        val pageUrl: String,
    ) {
        val downloadUrl: String get() = apkUrl ?: pageUrl
    }

    /**
     * Проверка при запуске: не чаще раза в сутки и без повторных напоминаний
     * про версию, которую пользователь уже отложил.
     */
    suspend fun checkInBackground(context: Context): Release? {
        // Отладочная сборка живёт своей жизнью: автоматически ничего не спрашиваем
        if (context.appVersion().name.contains("debug", ignoreCase = true)) return null
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0L)
        if (since in 0 until DAY_MS) return null
        val release = check(context) ?: return null
        return if (release.version == prefs.getString(KEY_SKIPPED, null)) null else release
    }

    /** Проверка по кнопке «Проверить обновления»: без ограничений по времени. */
    suspend fun check(context: Context): Release? {
        val current = context.appVersion().name
        val releases = fetch() ?: return null
        val newest = releases.maxWithOrNull(
            Comparator { a, b -> compareVersions(a.version, b.version) },
        ) ?: return null
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .apply()
        return newest.takeIf { isNewer(it.version, current) }
    }

    /** «Позже»: про эту версию больше не напоминаем сами. */
    fun skip(context: Context, version: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SKIPPED, version)
            .apply()
    }

    /** Сравнение версий вида 1.0.18: «новее» считается по числам, а не по строке. */
    fun isNewer(candidate: String, current: String): Boolean =
        compareVersions(candidate, current) > 0

    internal fun compareVersions(left: String, right: String): Int {
        val a = numbers(left)
        val b = numbers(right)
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun numbers(version: String): List<Int> =
        version.trim().removePrefix("v").split('.', '-', '_', ' ')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    /**
     * Описание релиза приходит в markdown. Для окна обновления оставляем только
     * раздел «Изменения» — короткий список, без таблиц, цитат и звёздочек.
     */
    internal fun cleanNotes(body: String): String {
        val lines = body.lines()
        val start = lines.indexOfFirst { line ->
            line.trimStart().startsWith("#") && line.contains("Изменения", ignoreCase = true)
        }
        val section = if (start >= 0) {
            lines.drop(start + 1).takeWhile { !it.trimStart().startsWith("#") }
        } else {
            lines
        }
        return section
            .map { it.trim() }
            .filterNot { it.isEmpty() || it.startsWith(">") || it.startsWith("|") || it.startsWith("#") }
            .map { line ->
                line.removePrefix("- ").removePrefix("* ")
                    .replace("**", "")
                    .replace("`", "")
                    .trim()
            }
            .filter { it.isNotEmpty() && !it.startsWith("Версия:") }
            .take(6)
            .joinToString("\n") { "• $it" }
    }

    private suspend fun fetch(): List<Release>? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BY-Card")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    internal fun parse(body: String): List<Release> {
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<Release>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optBoolean("draft")) continue
            val tag = item.optString("tag_name")
            if (tag.isBlank()) continue
            val assets = item.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apk = asset.optString("browser_download_url").ifBlank { null }
                        break
                    }
                }
            }
            result += Release(
                version = tag.removePrefix("v"),
                title = item.optString("name").ifBlank { tag },
                notes = cleanNotes(item.optString("body")),
                apkUrl = apk,
                pageUrl = item.optString("html_url"),
            )
        }
        return result
    }
}
