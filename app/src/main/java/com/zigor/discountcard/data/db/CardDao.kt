package com.zigor.discountcard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards ORDER BY favorite DESC, lastUsedAt DESC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    fun observeById(id: Long): Flow<CardEntity?>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE code = :code AND code IS NOT NULL LIMIT 1")
    suspend fun findByCode(code: String): CardEntity?

    @Query("SELECT * FROM cards WHERE nfcUid = :uid AND nfcUid IS NOT NULL LIMIT 1")
    suspend fun findByNfcUid(uid: String): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CardEntity): Long

    @Update
    suspend fun update(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE cards SET lastUsedAt = :ts, useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: Long, ts: Long = System.currentTimeMillis())

    @Query("UPDATE cards SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)
}

@Dao
interface LearnedStoreDao {

    @Query("SELECT * FROM learned_stores WHERE codeKey IN (:keys) ORDER BY LENGTH(codeKey) DESC LIMIT 1")
    suspend fun bestMatch(keys: List<String>): LearnedStoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LearnedStoreEntity)

    @Query("SELECT COUNT(*) FROM learned_stores")
    suspend fun count(): Int
}
