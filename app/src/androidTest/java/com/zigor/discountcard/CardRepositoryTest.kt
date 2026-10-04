package com.zigor.discountcard

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zigor.discountcard.data.db.AppDatabase
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.db.CardKind
import com.zigor.discountcard.data.repo.CardRepository
import com.zigor.discountcard.data.store.StoreCatalog
import com.zigor.discountcard.util.PhotoStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Хранение карт и «самообучение» распознавания магазина. */
@RunWith(AndroidJUnit4::class)
class CardRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CardRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CardRepository(
            cardDao = database.cardDao(),
            learnedDao = database.learnedStoreDao(),
            catalog = StoreCatalog(context),
            photoStore = PhotoStore(context),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun cardIsSavedAndFoundByCode() = runBlocking {
        val id = repository.save(
            CardEntity(
                title = "Тестовый магазин",
                colorArgb = StoreCatalog.DEFAULT_COLOR,
                kind = CardKind.BARCODE,
                code = "2612345678904",
                codeFormat = "EAN_13",
            ),
        )
        assertTrue(id > 0)
        assertEquals(1, repository.observeCards().first().size)
        assertNotNull(repository.findByCode("2612345678904"))
    }

    @Test
    fun storeNameIsLearnedFromPreviousCard() = runBlocking {
        assertFalse(repository.resolveStore("2612345678904", "EAN_13").found)

        repository.remember("2612345678904", "Магазин у дома", null, StoreCatalog.DEFAULT_COLOR)

        val suggestion = repository.resolveStore("2612349999999", "EAN_13")
        assertTrue("префикс не запомнился", suggestion.found)
        assertEquals("Магазин у дома", suggestion.title)
        assertTrue(suggestion.fromMemory)
    }

    @Test
    fun qrLinkIsResolvedFromCatalog() = runBlocking {
        val suggestion = repository.resolveStore("https://vkusvill.ru/card/777", "QR_CODE")
        assertTrue(suggestion.found)
        assertEquals("ВкусВилл", suggestion.title)
    }

    @Test
    fun deletingCardRemovesItFromList() = runBlocking {
        val card = CardEntity(
            title = "Удалить",
            colorArgb = StoreCatalog.DEFAULT_COLOR,
            code = "1234567890",
        )
        val id = repository.save(card)
        repository.delete(card.copy(id = id))
        assertTrue(repository.observeCards().first().isEmpty())
    }
}
