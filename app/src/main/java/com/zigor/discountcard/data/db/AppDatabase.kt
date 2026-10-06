package com.zigor.discountcard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CardEntity::class, LearnedStoreEntity::class, BankCardEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cardDao(): CardDao

    abstract fun learnedStoreDao(): LearnedStoreDao

    abstract fun bankCardDao(): BankCardDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * SQL создания таблицы банковских карт. Вынесен в константу, чтобы тест
         * сравнил его с тем, что Room ожидает увидеть: так обновление поверх
         * старой версии не потеряет уже сохранённые дисконтные карты.
         */
        const val CREATE_BANK_CARDS: String =
            "CREATE TABLE IF NOT EXISTS `bank_cards` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`bank` TEXT NOT NULL, " +
                "`system` TEXT NOT NULL, " +
                "`last4` TEXT NOT NULL, " +
                "`numberEnc` TEXT NOT NULL, " +
                "`expiryEnc` TEXT NOT NULL, " +
                "`holderEnc` TEXT NOT NULL, " +
                "`colorArgb` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"

        /** 1 → 2: появились банковские карты. Старые таблицы не трогаем. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_BANK_CARDS)
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "discount_cards.db",
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
