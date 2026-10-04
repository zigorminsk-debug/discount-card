package com.zigor.discountcard.data.store

import android.content.Context
import android.graphics.Color
import android.util.Log
import org.json.JSONObject

/** Правило сопоставления кода с магазином (дополняется в assets/stores.json). */
data class CodeRule(
    val prefix: String? = null,
    val regex: Regex? = null,
    val lengths: List<Int> = emptyList(),
    val formats: List<String> = emptyList(),
    val confidence: MatchConfidence = MatchConfidence.LIKELY,
) {
    fun matches(code: String, format: String): Boolean {
        if (formats.isNotEmpty() && formats.none { it.equalsIgnoreCase(format) }) return false
        if (lengths.isNotEmpty() && code.length !in lengths) return false
        if (prefix != null && !code.startsWith(prefix)) return false
        if (regex != null && !regex.matches(code)) return false
        return prefix != null || regex != null || lengths.isNotEmpty()
    }

    private fun String.equalsIgnoreCase(other: String) = equals(other, ignoreCase = true)
}

data class StoreInfo(
    val id: String,
    val name: String,
    val country: String?,
    val colorArgb: Int,
    val aliases: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val rules: List<CodeRule> = emptyList(),
)

enum class MatchConfidence { EXACT, LIKELY, NONE }

data class StoreMatch(
    val store: StoreInfo?,
    val confidence: MatchConfidence,
    /** Человекочитаемое объяснение: по ссылке, по префиксу, из памяти приложения... */
    val reason: String? = null,
) {
    val found: Boolean get() = store != null && confidence != MatchConfidence.NONE
}

/**
 * Каталог магазинов. Грузится один раз из assets/stores.json и держится в памяти.
 * Распознавание работает офлайн и без каких-либо сетевых запросов.
 */
class StoreCatalog(private val context: Context) {

    @Volatile
    private var loaded = false
    private var storesInternal: List<StoreInfo> = emptyList()
    private var issuers: List<IssuerRange> = emptyList()

    val stores: List<StoreInfo>
        get() {
            ensureLoaded()
            return storesInternal
        }

    fun byId(id: String?): StoreInfo? {
        if (id == null) return null
        return stores.firstOrNull { it.id == id }
    }

    /** Подсказки для поля «Название магазина». */
    fun suggest(query: String, limit: Int = 6): List<StoreInfo> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return stores.filter { store ->
            store.name.lowercase().contains(q) || store.aliases.any { it.lowercase().contains(q) }
        }.take(limit)
    }

    /**
     * Главный метод распознавания.
     * Порядок: ссылка/домен -> ключевое слово -> явное правило из каталога.
     */
    fun match(code: String, format: String): StoreMatch {
        ensureLoaded()
        val value = code.trim()
        if (value.isEmpty()) return StoreMatch(null, MatchConfidence.NONE)
        val lower = value.lowercase()
        val numericOnly = value.all { it.isDigit() }

        if (!numericOnly) {
            for (store in storesInternal) {
                val domain = store.domains.firstOrNull { lower.contains(it) }
                if (domain != null) return StoreMatch(store, MatchConfidence.EXACT, "ссылка $domain")
            }
            for (store in storesInternal) {
                val keyword = store.keywords.firstOrNull { lower.contains(it) }
                if (keyword != null) return StoreMatch(store, MatchConfidence.EXACT, "код содержит «$keyword»")
            }
        }
        for (store in storesInternal) {
            for (rule in store.rules) {
                if (rule.matches(value, format)) {
                    val how = rule.prefix?.let { "префикс $it" } ?: "шаблон кода"
                    return StoreMatch(store, rule.confidence, how)
                }
            }
        }
        return StoreMatch(null, MatchConfidence.NONE)
    }

    /** Кто выдал код: страна GS1 или «внутренний код магазина». */
    fun issuerHint(code: String): String? {
        ensureLoaded()
        val digits = code.trim()
        if (digits.length < 8 || !digits.all { it.isDigit() }) return null
        // Сначала самые длинные префиксы (4 цифры), потом короткие (2).
        for (width in issuers.map { it.width }.distinct().sortedDescending()) {
            if (digits.length <= width) continue
            val head = digits.take(width).toIntOrNull() ?: continue
            issuers.firstOrNull { it.width == width && head in it.from..it.to }?.let { return it.name }
        }
        return null
    }

    private data class IssuerRange(val width: Int, val from: Int, val to: Int, val name: String)

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        runCatching {
            val raw = context.assets.open("stores.json").bufferedReader().use { it.readText() }
            val root = JSONObject(raw)

            val storesJson = root.optJSONArray("stores")
            val parsed = ArrayList<StoreInfo>(storesJson?.length() ?: 0)
            for (i in 0 until (storesJson?.length() ?: 0)) {
                val o = storesJson!!.getJSONObject(i)
                parsed += StoreInfo(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    country = o.optString("country").takeIf { it.isNotBlank() },
                    colorArgb = parseColor(o.optString("color"), DEFAULT_COLOR),
                    aliases = o.optJSONArray("aliases").toStringList(),
                    domains = o.optJSONArray("domains").toStringList().map { it.lowercase() },
                    keywords = o.optJSONArray("keywords").toStringList().map { it.lowercase() },
                    rules = o.optJSONArray("rules").let { arr ->
                        (0 until (arr?.length() ?: 0)).map { idx ->
                            val r = arr!!.getJSONObject(idx)
                            CodeRule(
                                prefix = r.optString("prefix").takeIf { it.isNotBlank() },
                                regex = r.optString("regex").takeIf { it.isNotBlank() }?.let(::Regex),
                                lengths = r.optJSONArray("lengths").toIntList(),
                                formats = r.optJSONArray("formats").toStringList(),
                                confidence = when (r.optString("confidence").lowercase()) {
                                    "high", "exact" -> MatchConfidence.EXACT
                                    else -> MatchConfidence.LIKELY
                                },
                            )
                        }
                    },
                )
            }
            storesInternal = parsed

            val issuersJson = root.optJSONObject("issuers")
            val issuerList = ArrayList<IssuerRange>()
            issuersJson?.keys()?.forEach { key ->
                val name = issuersJson.optString(key)
                val parts = key.split("-")
                val from = parts.first().trim()
                val to = parts.last().trim()
                val fromInt = from.toIntOrNull()
                val toInt = to.toIntOrNull()
                if (fromInt != null && toInt != null && from.length == to.length) {
                    issuerList += IssuerRange(width = from.length, from = fromInt, to = toInt, name = name)
                }
            }
            issuers = issuerList
        }.onFailure { Log.e("StoreCatalog", "Не удалось прочитать stores.json", it) }
        loaded = true
    }

    private fun org.json.JSONArray?.toStringList(): List<String> =
        (0 until (this?.length() ?: 0)).mapNotNull { this?.optString(it)?.takeIf { s -> s.isNotBlank() } }

    private fun org.json.JSONArray?.toIntList(): List<Int> =
        (0 until (this?.length() ?: 0)).mapNotNull { this?.optInt(it) }

    companion object {
        val DEFAULT_COLOR: Int = Color.parseColor("#2563EB")

        val PALETTE: List<Int> = listOf(
            "#2563EB", "#7C3AED", "#DB2777", "#DC2626", "#EA580C",
            "#CA8A04", "#16A34A", "#0D9488", "#0284C7", "#475569",
        ).map { Color.parseColor(it) }

        fun parseColor(value: String?, fallback: Int): Int =
            runCatching { Color.parseColor(value) }.getOrDefault(fallback)

        /** Цвет по названию магазина — стабильный, чтобы карты не «прыгали» в цветах. */
        fun colorForTitle(title: String): Int {
            if (title.isBlank()) return DEFAULT_COLOR
            val index = (title.lowercase().hashCode().toLong() and 0xFFFFFFFFL) % PALETTE.size
            return PALETTE[index.toInt()]
        }
    }
}
