package com.zigor.discountcard

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

        listOf("scan_barcode_button", "scan_nfc_button", "more_button").forEach { tag ->
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

        listOf("scan_barcode_button", "scan_nfc_button", "more_button").forEach { tag ->
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

    /**
     * Клавиатура не должна съедать поле поиска. Раньше отступ под клавиатуру
     * учитывался дважды (нижняя панель + содержимое), и на телефонах с высокой
     * клавиатурой поле схлопывалось, пряча набранный текст.
     */
    @Test
    fun searchFieldStaysVisibleWhenKeyboardIsOpen() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_search").performClick()
        composeRule.onNodeWithTag("home_search").performTextInput("гиппо")

        var keyboardShown = false
        repeat(15) {
            if (!keyboardShown) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    keyboardShown = ViewCompat
                        .getRootWindowInsets(composeRule.activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                if (!keyboardShown) Thread.sleep(200)
            }
        }
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("home_search").getBoundsInRoot()
        val visibleHeight = bounds.bottom.value - bounds.top.value
        assertTrue(
            "Поле поиска схлопнулось при открытой клавиатуре: видно $visibleHeight dp " +
                "(клавиатура на экране: $keyboardShown)",
            visibleHeight >= 40f,
        )
        composeRule.onNodeWithTag("home_search").assertIsDisplayed()
        composeRule.onNodeWithText("гиппо").assertExists()

        // убираем клавиатуру, чтобы не мешала следующим тестам
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 4")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        Thread.sleep(500)
    }
}
