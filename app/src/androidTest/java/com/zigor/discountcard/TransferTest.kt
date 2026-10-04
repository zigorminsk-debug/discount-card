package com.zigor.discountcard

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zigor.discountcard.data.db.AppDatabase
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.db.CardKind
import com.zigor.discountcard.data.repo.CardRepository
import com.zigor.discountcard.data.store.StoreCatalog
import com.zigor.discountcard.data.transfer.CardTransfer
import com.zigor.discountcard.util.PhotoStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Перенос карт на другой телефон: файл экспорта должен полностью восстанавливаться. */
@RunWith(AndroidJUnit4::class)
class TransferTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase
    private lateinit var sourceRepo: CardRepository
    private lateinit var targetRepo: CardRepository
    private lateinit var photoStore: PhotoStore

    private fun newRepository(database: AppDatabase) = CardRepository(
        cardDao = database.cardDao(),
        learnedDao = database.learnedStoreDao(),
        catalog = StoreCatalog(context),
        photoStore = photoStore,
    )

    @Before
    fun setUp() {
        photoStore = PhotoStore(context)
        source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        sourceRepo = newRepository(source)
        targetRepo = newRepository(target)
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun cardsAndPhotosSurviveExportAndImport() = runBlocking {
        val photo = photoStore.newFile()
        photo.writeBytes(ByteArray(2048) { (it % 251).toByte() })

        sourceRepo.save(
            CardEntity(
                title = "Евроопт",
                colorArgb = 0xFFE4002B.toInt(),
                kind = CardKind.BARCODE,
                code = "2612345678904",
                codeFormat = "EAN_13",
                frontPhoto = photo.absolutePath,
                favorite = true,
            ),
        )
        sourceRepo.save(
            CardEntity(
                title = "Карта без кода",
                colorArgb = StoreCatalog.DEFAULT_COLOR,
                kind = CardKind.PHOTO,
                frontPhoto = photo.absolutePath,
            ),
        )
        sourceRepo.save(
            CardEntity(
                title = "Пропуск",
                colorArgb = StoreCatalog.DEFAULT_COLOR,
                kind = CardKind.NFC,
                nfcUid = "04:A3:1B:92:5C:6D:80",
            ),
        )

        val export = CardTransfer.export(context, sourceRepo)
        assertEquals(3, export.cards)
        assertTrue("файл экспорта пустой", export.file.length() > 0)

        val uri = Uri.fromFile(export.file)
        val preview = CardTransfer.preview(context, uri)
        assertTrue("файл не распознан как свой", preview.valid)
        assertEquals(3, preview.cards)

        // «другой телефон»
        val result = CardTransfer.importFrom(context, uri, targetRepo, photoStore)
        assertEquals(3, result.added)
        assertEquals(0, result.skipped)

        val restored = targetRepo.observeCards().first()
        assertEquals(3, restored.size)
        val barcode = restored.first { it.code == "2612345678904" }
        assertEquals("Евроопт", barcode.title)
        assertEquals("EAN_13", barcode.codeFormat)
        assertEquals(0xFFE4002B.toInt(), barcode.colorArgb)
        assertTrue("фото не восстановилось", File(barcode.frontPhoto.orEmpty()).exists())
        assertTrue("фото пустое", File(barcode.frontPhoto.orEmpty()).length() > 0)
        assertTrue(restored.any { it.nfcUid == "04:A3:1B:92:5C:6D:80" })

        // Выученный магазин переезжает вместе с картой
        val suggestion = targetRepo.resolveStore("2612345678904", "EAN_13")
        assertEquals("Евроопт", suggestion.title)

        // Повторный импорт того же файла ничего не дублирует
        val again = CardTransfer.importFrom(context, uri, targetRepo, photoStore)
        assertEquals(0, again.added)
        assertEquals(3, again.skipped)
        assertEquals(3, targetRepo.observeCards().first().size)
    }

    @Test
    fun foreignFileIsRejected() = runBlocking {
        val junk = File(context.cacheDir, "junk.mkcards")
        junk.writeBytes("это не архив".toByteArray())
        val preview = CardTransfer.preview(context, Uri.fromFile(junk))
        assertTrue("чужой файл не должен считаться своим", !preview.valid)
        junk.delete()
    }
}
