package com.zigor.discountcard

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
 * Снимает экраны приложения на эмуляторе и складывает PNG в
 * /sdcard/Android/data/<id>/files/screenshots — CI забирает их через adb pull.
 * Тест не проверяет пиксели, он нужен, чтобы живьём видеть вёрстку после правок.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val repository get() = composeRule.activity.appContainer.repository

    private val demoCards = listOf(
        CardEntity(
            title = "Евроопт",
            colorArgb = 0xFFE4002B.toInt(),
            kind = CardKind.BARCODE,
            code = "2612345678904",
            codeFormat = "EAN_13",
            favorite = true,
        ),
        CardEntity(
            title = "Пятёрочка",
            colorArgb = 0xFF00923F.toInt(),
            kind = CardKind.BARCODE,
            code = "2002345678901",
            codeFormat = "EAN_13",
        ),
        CardEntity(
            title = "ВкусВилл",
            colorArgb = 0xFF0E7A3C.toInt(),
            kind = CardKind.BARCODE,
            code = "https://vkusvill.ru/card/770123456",
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
        shot("02-home-cards")

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

        // Ручное добавление карты
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_add_manual)).performClick()
        composeRule.waitForIdle()
        Thread.sleep(400)
        shot("05-card-edit")
    }

    private fun shot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Снимок всего экрана вместе с системными панелями — именно он показывает,
        // не уехал ли интерфейс под панель навигации. Если uiAutomation недоступен,
        // берём хотя бы окно приложения средствами Compose.
        val bitmap: Bitmap = runCatching { instrumentation.uiAutomation.takeScreenshot() }.getOrNull()
            ?: runCatching { composeRule.onRoot().captureToImage().asAndroidBitmap() }.getOrNull()
            ?: run {
                Log.w(TAG, "снимок $name не получился: оба способа вернули null")
                return
            }
        val context = instrumentation.targetContext
        // Пишем и во внутреннюю память (её CI забирает через run-as),
        // и во внешнюю (её видно через adb pull, если есть доступ).
        listOfNotNull(context.filesDir, context.getExternalFilesDir(null)).forEach { base ->
            runCatching {
                val dir = File(base, "screenshots").apply { mkdirs() }
                val file = File(dir, "$name.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file.setReadable(true, false)
                dir.setReadable(true, false)
                dir.setExecutable(true, false)
                Log.i(TAG, "снимок сохранён: ${file.absolutePath} (${file.length()} байт)")
            }.onFailure { Log.w(TAG, "не удалось записать $name в $base: ${it.message}") }
        }
        bitmap.recycle()
    }

    private companion object {
        const val TAG = "ScreenshotTest"
    }
}

/** Сколько узлов с таким текстом сейчас на экране (без падения, если их нет). */
private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextSafe(text: String): Int =
    runCatching { onAllNodes(hasText(text)).fetchSemanticsNodes().size }.getOrDefault(0)
