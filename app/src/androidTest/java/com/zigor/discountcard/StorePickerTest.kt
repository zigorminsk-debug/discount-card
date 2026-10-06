package com.zigor.discountcard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.db.CardKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Привязка уже сохранённой карты к магазину из каталога. */
@RunWith(AndroidJUnit4::class)
class StorePickerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val repository get() = composeRule.activity.appContainer.repository

    @Before
    fun clearDatabase(): Unit = runBlocking {
        repository.observeCards().first().forEach { repository.delete(it) }
    }

    @After
    fun cleanUp(): Unit = runBlocking {
        repository.observeCards().first().forEach { repository.delete(it) }
    }

    @Test
    fun savedCardCanBeAttachedToStoreFromCatalog() {
        val context = composeRule.activity
        runBlocking {
            repository.save(
                CardEntity(
                    title = "Карта без магазина",
                    colorArgb = 0xFF415F91.toInt(),
                    kind = CardKind.BARCODE,
                    code = "9001234567890",
                    codeFormat = "EAN_13",
                ),
            )
        }
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithText("Карта без магазина").assertExists()
            }.isSuccess
        }

        composeRule.onNodeWithText("Карта без магазина").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_edit)).performClick()
        composeRule.waitForIdle()

        // Магазин не выбран, пока карту не привязали вручную
        composeRule.onNodeWithText(context.getString(R.string.card_store_none)).assertExists()

        composeRule.onNodeWithTag("store_pick_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store_picker_search").performTextInput("гиппо")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store_item_gippo").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.action_save)).performScrollTo().performClick()

        composeRule.waitUntil(10_000) {
            runBlocking { repository.observeCards().first().any { it.storeId == "gippo" } }
        }
        val saved = runBlocking { repository.observeCards().first().first() }
        assertEquals("Гиппо", saved.title)
        assertEquals("gippo", saved.storeId)
    }
}
