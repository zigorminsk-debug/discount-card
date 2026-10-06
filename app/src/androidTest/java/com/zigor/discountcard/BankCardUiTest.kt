package com.zigor.discountcard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Путь пользователя: «⋮» → «Банковские карты» → ввод → карта видна только хвостом номера. */
@RunWith(AndroidJUnit4::class)
class BankCardUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val bankCards get() = composeRule.activity.appContainer.bankCards

    @Before
    fun clearBefore() = runBlocking {
        bankCards.observeCards().first().forEach { bankCards.delete(it.id) }
    }

    @After
    fun clearAfter() = runBlocking {
        bankCards.observeCards().first().forEach { bankCards.delete(it.id) }
    }

    @Test
    fun cardAddedFromMenuIsStoredMasked() {
        composeRule.onNodeWithTag("more_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bank_cards_menu").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("bank_add_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("bank_number_field").performTextInput("4111111111111111")
        composeRule.onNodeWithTag("bank_expiry_field").performTextInput("0929")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bank_save_button").performClick()

        composeRule.waitUntil(10_000) { runBlocking { bankCards.count() } == 1 }
        composeRule.waitForIdle()

        // На экране карты номер замаскирован
        composeRule.onNodeWithText("•••• •••• •••• 1111").assertExists()

        val stored = runBlocking { bankCards.observeCards().first().first() }
        assertEquals("1111", stored.last4)
        assertEquals("09/29", runBlocking { bankCards.secret(stored.id)?.expiry })
        assertFalse(stored.numberEnc.contains("4111"))
    }
}
