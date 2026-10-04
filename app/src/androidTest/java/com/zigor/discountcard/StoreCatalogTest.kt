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
}
