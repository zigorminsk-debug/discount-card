package com.zigor.discountcard

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zigor.discountcard.data.bank.CardCrypto
import com.zigor.discountcard.data.bank.CardExpiry
import com.zigor.discountcard.data.bank.CardNumber
import com.zigor.discountcard.data.bank.PaymentSystem
import com.zigor.discountcard.data.db.AppDatabase
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.db.CardKind
import com.zigor.discountcard.data.repo.BankCardDraft
import com.zigor.discountcard.data.repo.BankCardRepository
import com.zigor.discountcard.data.repo.CardRepository
import com.zigor.discountcard.data.store.StoreCatalog
import com.zigor.discountcard.data.transfer.CardTransfer
import com.zigor.discountcard.util.PhotoStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.zip.ZipFile

/** Банковские карты: проверка номера, шифрование и то, что реквизиты никуда не утекают. */
@RunWith(AndroidJUnit4::class)
class BankCardTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase
    private lateinit var repository: BankCardRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = BankCardRepository(database.bankCardDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun luhnCatchesTypos() {
        assertTrue(CardNumber.luhnValid("4111 1111 1111 1111"))
        assertTrue(CardNumber.luhnValid("5555555555554444"))
        assertTrue(CardNumber.luhnValid("9112000000000006"))
        // одна цифра изменена — контрольная сумма ломается
        assertFalse(CardNumber.luhnValid("4111111111111121"))
        assertFalse(CardNumber.luhnValid("1234"))
    }

    @Test
    fun paymentSystemIsDetectedByBin() {
        assertEquals(PaymentSystem.VISA, PaymentSystem.detect("4111111111111111"))
        assertEquals(PaymentSystem.MASTERCARD, PaymentSystem.detect("5555555555554444"))
        assertEquals(PaymentSystem.MASTERCARD, PaymentSystem.detect("2223003122003222"))
        assertEquals(PaymentSystem.MIR, PaymentSystem.detect("2200000000000004"))
        assertEquals(PaymentSystem.BELCARD, PaymentSystem.detect("9112 0000 0000 0006"))
        assertEquals(PaymentSystem.AMEX, PaymentSystem.detect("378282246310005"))
        assertEquals(PaymentSystem.UNKNOWN, PaymentSystem.detect("7"))
        assertEquals("БЕЛКАРТ", PaymentSystem.title(PaymentSystem.BELCARD))
    }

    @Test
    fun numberIsGroupedAndMasked() {
        assertEquals("4111 1111 1111 1111", CardNumber.format("4111111111111111"))
        assertEquals("3782 822463 10005", CardNumber.format("378282246310005"))
        assertEquals("4111 11", CardNumber.format("411111"))
        assertEquals("•••• •••• •••• 1111", CardNumber.mask("1111"))
        assertTrue(CardNumber.valid("9112000000000006"))
        // 16 цифр для VISA — норма, 15 — нет
        assertFalse(CardNumber.valid("411111111111111"))
    }

    @Test
    fun expiryIsFormattedAndChecked() {
        assertEquals("09/29", CardExpiry.format("0929"))
        assertEquals("05", CardExpiry.format("5"))
        assertEquals("12/3", CardExpiry.format("123"))
        assertTrue(CardExpiry.wellFormed("09/29"))
        assertFalse(CardExpiry.wellFormed("13/29"))
        assertFalse(CardExpiry.wellFormed("09/2"))

        val march2026 = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 15) }
        assertTrue(CardExpiry.expired("02/26", march2026))
        assertFalse(CardExpiry.expired("03/26", march2026))
        assertFalse(CardExpiry.expired("12/29", march2026))
    }

    @Test
    fun cryptoSurvivesRoundTrip() {
        val number = "4111111111111111"
        val encrypted = CardCrypto.encrypt(number)
        assertFalse(encrypted.contains(number))
        assertTrue(encrypted.contains(":"))
        assertEquals(number, CardCrypto.decrypt(encrypted))
        // каждый раз новый вектор инициализации — одинаковые номера выглядят по-разному
        assertFalse(encrypted == CardCrypto.encrypt(number))
        assertNull(CardCrypto.decrypt("мусор:мусор"))
    }

    @Test
    fun savedCardKeepsNumberEncrypted(): Unit = runBlocking {
        val id = repository.save(
            BankCardDraft(
                bank = "Приорбанк",
                number = "4111 1111 1111 1111",
                expiry = "09/29",
                holder = "ivan ivanov",
            ),
        )
        val stored = database.bankCardDao().byId(id)
        assertNotNull(stored)
        requireNotNull(stored)

        assertEquals("1111", stored.last4)
        assertEquals(PaymentSystem.VISA, stored.system)
        // имя карты подставилось из названия банка
        assertEquals("Приорбанк", stored.title)
        // в открытом виде номера в базе нет
        assertFalse(stored.numberEnc.contains("4111"))
        assertFalse(stored.numberEnc.contains("1111 1111"))
        assertFalse(stored.holderEnc.contains("IVAN"))

        val secret = repository.secret(id)
        assertNotNull(secret)
        requireNotNull(secret)
        assertEquals("4111111111111111", secret.number)
        assertEquals("09/29", secret.expiry)
        assertEquals("IVAN IVANOV", secret.holder)

        repository.delete(id)
        assertEquals(0, repository.count())
    }

    /**
     * Таблица банковских карт появляется миграцией 1→2. Если SQL в миграции
     * разойдётся с тем, что ожидает Room, обновление поверх старой версии
     * снесёт все дисконтные карты — поэтому сравниваем тексты дословно.
     */
    @Test
    fun migrationSqlMatchesRoomSchema() {
        val cursor = database.openHelper.writableDatabase
            .query("SELECT sql FROM sqlite_master WHERE name = 'bank_cards'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(normalizeSql(it.getString(0)), normalizeSql(AppDatabase.CREATE_BANK_CARDS))
        }
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }

    /** Экспорт карт в Telegram не должен выносить банковские реквизиты с телефона. */
    @Test
    fun bankCardsStayOutOfExport(): Unit = runBlocking {
        repository.save(BankCardDraft(title = "Зарплатная", number = "4111111111111111"))

        val cardRepository = CardRepository(
            cardDao = database.cardDao(),
            learnedDao = database.learnedStoreDao(),
            catalog = StoreCatalog(context),
            photoStore = PhotoStore(context),
        )
        cardRepository.save(
            CardEntity(
                title = "Евроопт",
                colorArgb = 0xFF43B02A.toInt(),
                kind = CardKind.BARCODE,
                code = "2612345678904",
                codeFormat = "EAN_13",
            ),
        )

        val result = CardTransfer.export(context, cardRepository)
        val payload = ZipFile(result.file).use { zip ->
            zip.entries().toList().joinToString("\n") { entry ->
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        }
        assertTrue(payload.contains("Евроопт"))
        assertFalse(payload.contains("4111"))
        assertFalse(payload.contains("bank"))
        assertEquals(1, cardRepository.observeCards().first().size)
        result.file.delete()
    }

    private fun normalizeSql(sql: String) = sql
        .replace("IF NOT EXISTS", "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
