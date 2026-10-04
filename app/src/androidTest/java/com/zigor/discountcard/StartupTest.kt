package com.zigor.discountcard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Приложение должно запускаться и показывать главный экран без падений. */
@RunWith(AndroidJUnit4::class)
class StartupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** Тесты не должны зависеть от порядка запуска: начинаем с пустой базы. */
    @Before
    fun clearDatabase() = runBlocking {
        val repository = composeRule.activity.appContainer.repository
        repository.observeCards().first().forEach { repository.delete(it) }
    }

    @Test
    fun appStartsAndShowsHomeScreen() {
        val context = composeRule.activity
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_scan_barcode)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_scan_nfc)).assertIsDisplayed()
    }

    @Test
    fun menuOffersScanFromImage() {
        val context = composeRule.activity
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.action_scan_image)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_add_manual)).assertIsDisplayed()
    }

    @Test
    fun emptyStateIsShownWhenThereAreNoCards() {
        val context = composeRule.activity
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.home_empty_title)).assertIsDisplayed()
    }
}
