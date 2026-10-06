package com.zigor.discountcard

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.db.CardKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Снимает экраны приложения на эмуляторе в /sdcard/Pictures/moi-karty-shots
 * (пишет shell, поэтому файлы переживают удаление APK после тестов) — CI забирает
 * их через adb pull. Тест не проверяет пиксели, он нужен, чтобы живьём видеть вёрстку.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val repository get() = composeRule.activity.appContainer.repository

    private val demoCards = listOf(
        CardEntity(
            title = "Евроопт",
            storeId = "evroopt",
            colorArgb = 0xFF43B02A.toInt(),
            kind = CardKind.BARCODE,
            code = "2612345678904",
            codeFormat = "EAN_13",
            favorite = true,
        ),
        CardEntity(
            title = "Белмаркет",
            storeId = "belmarket",
            colorArgb = 0xFF95C02C.toInt(),
            kind = CardKind.BARCODE,
            code = "2002345678901",
            codeFormat = "EAN_13",
        ),
        CardEntity(
            title = "Корона",
            storeId = "korona",
            colorArgb = 0xFFF6610A.toInt(),
            kind = CardKind.BARCODE,
            code = "https://korona.by/card/770123456",
            codeFormat = "QR_CODE",
        ),
        CardEntity(
            title = "Пропуск на работу",
            colorArgb = 0xFF415F91.toInt(),
            kind = CardKind.NFC,
            nfcUid = "04:A3:1B:92:5C:6D:80",
            nfcTech = "NfcA, MifareClassic",
        ),
    )

    @Before
    fun clearDatabase() = runBlocking {
        repository.observeCards().first().forEach { repository.delete(it) }
    }

    @After
    fun cleanUp() = runBlocking {
        repository.observeCards().first().forEach { repository.delete(it) }
    }

    @Test
    fun captureMainScreens() {
        val context = composeRule.activity

        composeRule.waitForIdle()
        shot("01-home-empty")

        runBlocking { demoCards.forEach { repository.save(it) } }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTextSafe(demoCards.first().title) > 0
        }
        composeRule.waitForIdle()
        // даём списку отрисоваться и логотипам подтянуться с сайтов сетей
        Thread.sleep(6000)
        composeRule.waitForIdle()
        shot("02-home-cards")

        // Поиск с открытой клавиатурой: поле обязано остаться целым и показывать текст
        composeRule.onNodeWithTag("home_search").performClick()
        composeRule.onNodeWithTag("home_search").performTextInput("гиппо")
        composeRule.waitForIdle()
        Thread.sleep(1500)
        shot("07-search-keyboard")
        composeRule.onNodeWithTag("home_search").performTextClearance()
        shell("input keyevent 4")
        Thread.sleep(800)
        composeRule.waitForIdle()

        // Карточка для кассы — штрих-код, номер, кнопки
        composeRule.onNodeWithText(demoCards.first().title).performClick()
        composeRule.waitForIdle()
        Thread.sleep(600)
        shot("03-card-detail")
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        composeRule.waitForIdle()

        // Экран NFC
        composeRule.onNodeWithText(context.getString(R.string.action_scan_nfc)).performClick()
        composeRule.waitForIdle()
        Thread.sleep(400)
        shot("04-nfc")
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        composeRule.waitForIdle()

        // Меню с дополнительными действиями
        composeRule.onNodeWithTag("more_button").performClick()
        composeRule.waitForIdle()
        Thread.sleep(400)
        shot("05-menu")

        // Ручное добавление карты
        composeRule.onNodeWithText(context.getString(R.string.action_add_manual)).performClick()
        composeRule.waitForIdle()
        Thread.sleep(400)
        shot("06-card-edit")

        // Диалог выбора магазина из каталога
        composeRule.onNodeWithTag("store_pick_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)
        shot("09-store-picker")

        // Поиск магазина по названию его карты
        composeRule.onNodeWithTag("store_picker_search").performTextInput("купилка")
        composeRule.waitForIdle()
        Thread.sleep(500)
        shot("10-picker-by-card-name")

        composeRule.onNodeWithText(context.getString(R.string.action_close)).performClick()
        composeRule.waitForIdle()

        // Инструкция «Как пользоваться»
        shell("input keyevent 4")
        Thread.sleep(700)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.action_help)).performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)
        shot("08-help")
    }

    private fun shot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // screencap выполняется от имени shell и пишет в общую папку: такие файлы
        // переживают удаление приложения после тестов, и CI может их забрать.
        runCatching {
            shell("mkdir -p $SHOT_DIR")
            shell("screencap -p $SHOT_DIR/$name.png")
            Log.i(TAG, "screencap -> $SHOT_DIR/$name.png")
        }.onFailure { Log.w(TAG, "screencap не сработал: ${it.message}") }

        // Запасной вариант — снимок средствами Compose в память приложения
        runCatching {
            val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
                ?: composeRule.onRoot().captureToImage().asAndroidBitmap()
            val dir = File(instrumentation.targetContext.filesDir, "screenshots").apply { mkdirs() }
            val file = File(dir, "$name.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            bitmap.recycle()
            Log.i(TAG, "снимок сохранён: ${file.absolutePath} (${file.length()} байт)")
        }.onFailure { Log.w(TAG, "снимок $name не получился: ${it.message}") }
    }

    private fun shell(command: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    private companion object {
        const val TAG = "ScreenshotTest"
        const val SHOT_DIR = "/sdcard/Pictures/moi-karty-shots"
    }
}

/** Сколько узлов с таким текстом сейчас на экране (без падения, если их нет). */
private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextSafe(text: String): Int =
    runCatching { onAllNodes(hasText(text)).fetchSemanticsNodes().size }.getOrDefault(0)
