package com.zigor.discountcard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zigor.discountcard.util.BarcodeRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Код должен рисоваться тем же форматом, каким был считан, а при невозможности — запасным. */
@RunWith(AndroidJUnit4::class)
class BarcodeRendererTest {

    @Test
    fun validEan13IsRenderedAsEan13() {
        val result = BarcodeRenderer.render("4601234567893", "EAN_13", 800, 240)
        assertNotNull("EAN-13 не нарисован", result.bitmap)
        assertEquals("EAN_13", result.format)
        assertFalse(result.usedFallback)
        assertEquals(800, result.bitmap!!.width)
    }

    @Test
    fun invalidValueForFormatFallsBackToCode128() {
        val result = BarcodeRenderer.render("ABC-123-XYZ", "EAN_13", 800, 240)
        assertNotNull("нет запасного варианта", result.bitmap)
        assertTrue(result.usedFallback)
        assertEquals("CODE_128", result.format)
    }

    @Test
    fun qrCodeIsRenderedSquare() {
        val result = BarcodeRenderer.render("https://example.com/card/42", "QR_CODE", 500, 500)
        assertNotNull(result.bitmap)
        assertEquals(result.bitmap!!.width, result.bitmap!!.height)
    }

    @Test
    fun longTextStillProducesSomething() {
        val result = BarcodeRenderer.render("x".repeat(300), "EAN_8", 600, 200)
        assertNotNull(result.bitmap)
        assertEquals("QR_CODE", result.format)
    }
}
