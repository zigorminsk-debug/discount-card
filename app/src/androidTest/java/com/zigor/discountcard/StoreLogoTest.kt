package com.zigor.discountcard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zigor.discountcard.data.store.StoreCatalog
import com.zigor.discountcard.data.store.StoreLogoStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Логотипы магазинов: файлы в APK, вычисление фирменного цвета, монограммы. */
@RunWith(AndroidJUnit4::class)
class StoreLogoTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logos = StoreLogoStore(context)
    private val catalog = StoreCatalog(context)

    @Test
    fun bundledLogosAreReadable() {
        val bundled = listOf("belmarket", "korona")
        bundled.forEach { id ->
            assertTrue("логотипа $id нет в APK", logos.isBundled(id))
            val bitmap = logos.logo(id)
            assertNotNull("логотип $id не декодировался", bitmap)
            assertTrue("логотип $id слишком мелкий", bitmap!!.width >= 24 && bitmap.height >= 24)
        }
    }

    @Test
    fun storeWithoutLogoReturnsNothing() {
        assertFalse(logos.isBundled("такого-магазина-нет"))
        assertEquals(null, logos.logo("такого-магазина-нет"))
    }

    @Test
    fun brandColourIsTakenFromTheLogo() {
        val color = logos.brandColor("korona")
        assertNotNull("цвет не вычислился", color)
        // логотип «Короны» оранжевый: красного канала заметно больше синего
        assertTrue(
            "ожидали оранжевый, получили #%06X".format(0xFFFFFF and color!!),
            Color.red(color) > Color.blue(color) + 60,
        )
    }

    @Test
    fun whiteBackgroundIsNotMistakenForBrandColour() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(20f, 20f, 44f, 44f, Paint().apply { color = Color.rgb(0, 160, 80) })
        val color = StoreLogoStore.extractBrandColor(bitmap)
        assertNotNull(color)
        assertTrue("зелёный должен победить белый", Color.green(color!!) > Color.red(color))
        assertTrue(Color.green(color) > Color.blue(color))
    }

    @Test
    fun initialsAreBuiltFromStoreName() {
        assertEquals("ЕВ", StoreLogoStore.initials("Евроопт"))
        assertEquals("MI", StoreLogoStore.initials("Mart Inn"))
        assertEquals("ПЗ", StoreLogoStore.initials("Планета здоровья"))
        assertEquals("?", StoreLogoStore.initials("   "))
    }

    @Test
    fun iconUrlsPointToTheStoreSite() {
        val urls = StoreLogoStore.iconUrls("evroopt.by")
        assertTrue(urls.any { it.contains("evroopt.by") })
        assertTrue(urls.all { it.startsWith("https://") })
    }

    @Test
    fun downloadIsSkippedWhenSwitchedOff(): Unit = runBlocking {
        val store = catalog.byId("sosedi")!!
        val was = logos.downloadEnabled
        logos.downloadEnabled = false
        assertFalse("с выключенной загрузкой сеть трогать нельзя", logos.fetch(store))
        logos.downloadEnabled = was
    }

    /**
     * Диагностика: получится ли взять логотип с сайта сети на живом устройстве.
     * Тест не падает без интернета — результат виден в логе прогона.
     */
    @Test
    fun logoDownloadFromSiteIsReported(): Unit = runBlocking {
        val store = catalog.byId("evroopt")!!
        logos.downloadEnabled = true
        val loaded = logos.fetch(store)
        val bitmap = logos.logo(store.id)
        val color = logos.brandColor(store.id)?.let { "#%06X".format(0xFFFFFF and it) }
        Log.i(
            "LogoDiag",
            "evroopt.by: загружен=$loaded размер=${bitmap?.width}x${bitmap?.height} цвет=$color",
        )
    }
}
