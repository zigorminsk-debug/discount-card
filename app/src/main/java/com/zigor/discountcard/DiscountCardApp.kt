package com.zigor.discountcard

import android.app.Application
import android.content.Context
import com.zigor.discountcard.data.db.AppDatabase
import com.zigor.discountcard.data.repo.CardRepository
import com.zigor.discountcard.data.store.StoreCatalog
import com.zigor.discountcard.util.PhotoStore

/** Простейший DI-контейнер: без лишних библиотек и кодогенерации. */
class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)
    val catalog = StoreCatalog(context)
    val photoStore = PhotoStore(context)
    val repository = CardRepository(
        cardDao = database.cardDao(),
        learnedDao = database.learnedStoreDao(),
        catalog = catalog,
        photoStore = photoStore,
    )
}

class DiscountCardApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DiscountCardApp).container
