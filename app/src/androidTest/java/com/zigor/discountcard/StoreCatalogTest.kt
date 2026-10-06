package com.zigor.discountcard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zigor.discountcard.data.store.MatchConfidence
import com.zigor.discountcard.data.store.StoreCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Каталог магазинов и распознавание кодов. */
@RunWith(AndroidJUnit4::class)
class StoreCatalogTest {

    private val catalog = StoreCatalog(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun catalogIsLoadedFromAssets() {
        assertTrue("каталог пуст", catalog.stores.size > 30)
        assertNotNull(catalog.byId("evroopt"))
        assertEquals("Пятёрочка", catalog.byId("pyaterochka")?.name)
    }

    @Test
    fun qrWithStoreLinkIsRecognised() {
        val match = catalog.match("https://5ka.ru/card/1234567890", "QR_CODE")
        assertTrue(match.found)
        assertEquals("pyaterochka", match.store?.id)
        assertEquals(MatchConfidence.EXACT, match.confidence)
    }

    @Test
    fun unknownNumericCodeIsNotGuessed() {
        val match = catalog.match("2612345678904", "EAN_13")
        assertFalse("нельзя угадывать магазин по случайному номеру", match.found)
    }

    @Test
    fun gs1IssuerIsDetected() {
        assertTrue(catalog.issuerHint("4601234567890")!!.contains("Росси"))
        assertTrue(catalog.issuerHint("4810123456789")!!.contains("Белар"))
        assertTrue(catalog.issuerHint("2012345678901")!!.contains("внутренний"))
    }

    @Test
    fun suggestionsWorkForTyping() {
        val hints = catalog.suggest("евро")
        assertTrue(hints.any { it.id == "evroopt" })
    }

    @Test
    fun belarusianChainsArePresent() {
        val must = listOf(
            "evroopt", "hit", "dobronom", "kopeechka", "santa", "sosedi", "gippo",
            "korona", "green", "almi", "belmarket", "svetofor", "martinn", "prostore",
            "belfarmacia", "planeta_zdorovya", "adel", "iskamed", "zelenaya_apteka",
            "5element", "elektrosila", "21vek", "oma", "mila", "buslik",
            "belorusneft", "a100",
        )
        val missing = must.filter { catalog.byId(it) == null }
        assertTrue("нет в каталоге: $missing", missing.isEmpty())
        val belarusian = catalog.stores.count { it.country == "BY" }
        assertTrue("белорусских сетей мало: $belarusian", belarusian >= 60)
    }

    @Test
    fun catalogHasNoDuplicatesAndKnownCategories() {
        val ids = catalog.stores.map { it.id }
        assertEquals("есть повторяющиеся id", ids.size, ids.toSet().size)
        assertTrue(catalog.stores.none { it.name.isBlank() })
        assertTrue(catalog.stores.all { it.category in StoreCatalog.CATEGORY_ORDER })
    }

    @Test
    fun catalogIsGroupedForThePicker() {
        val groups = catalog.grouped()
        assertEquals("продукты должны быть первым разделом", "food", groups.first().category)
        val pharmacy = groups.first { it.category == "pharmacy" }
        assertTrue("аптек мало: ${pharmacy.stores.size}", pharmacy.stores.size >= 15)
        // внутри раздела белорусские сети идут первыми
        assertEquals("BY", groups.first().stores.first().country)
        val total = groups.sumOf { it.stores.size }
        assertEquals(catalog.stores.size, total)
    }

    @Test
    fun pickerSearchFindsByNameAndDomain() {
        assertTrue(catalog.search("планета").any { it.id == "planeta_zdorovya" })
        assertTrue(catalog.search("gippo.by").any { it.id == "gippo" })
        assertTrue("поиск по алиасу", catalog.search("родны кут").any { it.id == "belkoopsoyuz" })
        assertTrue(catalog.search("такого магазина нет").isEmpty())
        assertEquals("пустой запрос — весь каталог", catalog.stores.size, catalog.search("").size)
    }

    @Test
    fun catalogAgreesWithRealChains() {
        // сетей не хватало в каталоге
        for (id in listOf("mastak", "mile", "vozhyk")) {
            assertTrue("нет сети $id", catalog.byId(id) != null)
        }
        assertEquals("mastac.by", catalog.byId("mastak")?.domains?.firstOrNull())
        assertEquals("mile.by", catalog.byId("mile")?.domains?.firstOrNull())
        // Mile — отдельный бренд ООО «Астомстрой», а не второе имя «Новосёлкина»
        val novoselkin = catalog.byId("novoselkin")!!
        assertTrue(
            "Mile не должен быть алиасом Новосёлкина",
            novoselkin.aliases.none { it.contains("mile", ignoreCase = true) },
        )
        // «Вожык» — своя сеть «ЮниСтор Групп», а не алиас UNISTORE
        assertTrue(
            "Вожык не должен быть алиасом UNISTORE",
            catalog.byId("unistore")!!.aliases.none { it.contains("вожык", ignoreCase = true) },
        )
    }

    @Test
    fun catalogHasNoForeignDomains() {
        // домены, которые на деле принадлежат чужим сайтам: вернуться не должны
        val foreign = setOf(
            "hit.by", "belmarket.by", "materik.by", "ostrov.by", "ostrovcs.by",
            "svetofor-market.by", "koronatechno.by", "belpharmacia.by", "dobrocen.by",
        )
        val wrong = catalog.stores.filter { store -> store.domains.any { it in foreign } }
        assertTrue("чужие домены у сетей: ${wrong.map { it.id }}", wrong.isEmpty())
        // каждый домен записан как домен, без схемы и пути
        val broken = catalog.stores.filter { store ->
            store.domains.any { !it.contains('.') || it.contains('/') || it.contains(' ') }
        }
        assertTrue("домен записан неверно: ${broken.map { it.id }}", broken.isEmpty())
    }
}
