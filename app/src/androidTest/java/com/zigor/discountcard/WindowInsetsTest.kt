package com.zigor.discountcard

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интерфейс рисуется «от края до края» (Android 15 это навязывает), поэтому
 * содержимое обязано само отступать от системных панелей. Тест ловит ровно ту
 * ошибку, из-за которой кнопки внизу главного экрана уезжали под панель навигации.
 */
@RunWith(AndroidJUnit4::class)
class WindowInsetsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private data class Bars(val top: Float, val bottom: Float)

    private fun systemBars(): Bars {
        val activity = composeRule.activity
        var top = 0
        var bottom = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            val bars = insets?.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            top = bars?.top ?: 0
            bottom = bars?.bottom ?: 0
        }
        val density = activity.resources.displayMetrics.density
        return Bars(top / density, bottom / density)
    }

    @Test
    fun bottomButtonsAreAboveSystemNavigationBar() {
        composeRule.waitForIdle()
        val bars = systemBars()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val screenBottom = root.bottom.value
        val safeBottom = screenBottom - bars.bottom

        listOf("scan_barcode_button", "scan_nfc_button", "add_manual_button").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(
                "Кнопка $tag заходит под системную навигацию: низ ${bounds.bottom.value} dp, " +
                    "допустимо $safeBottom dp (экран $screenBottom dp, панель ${bars.bottom} dp)",
                bounds.bottom.value <= safeBottom + 1f,
            )
        }
    }

    @Test
    fun titleIsBelowStatusBar() {
        composeRule.waitForIdle()
        val activity = composeRule.activity
        val bars = systemBars()
        val title = composeRule.onNodeWithText(activity.getString(R.string.home_title))
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Заголовок залезает на строку состояния: верх ${title.top.value} dp " +
                "при высоте панели ${bars.top} dp",
            title.top.value >= bars.top - 1f,
        )
    }

    @Test
    fun bottomBarFitsIntoScreenWidth() {
        composeRule.waitForIdle()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val screenRight = root.right.value

        listOf("scan_barcode_button", "scan_nfc_button", "add_manual_button").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(
                "Кнопка $tag выходит за правый край: ${bounds.right.value} dp при ширине $screenRight dp",
                bounds.right.value <= screenRight + 1f,
            )
            assertTrue(
                "Кнопка $tag выходит за левый край: ${bounds.left.value} dp",
                bounds.left.value >= -1f,
            )
        }
    }
}
