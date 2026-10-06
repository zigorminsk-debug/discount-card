package com.zigor.discountcard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BankCardDao {

    @Query("SELECT * FROM bank_cards ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BankCardEntity>>

    @Query("SELECT * FROM bank_cards WHERE id = :id")
    fun observeById(id: Long): Flow<BankCardEntity?>

    @Query("SELECT * FROM bank_cards WHERE id = :id")
    suspend fun byId(id: Long): BankCardEntity?

    @Insert
    suspend fun insert(card: BankCardEntity): Long

    @Update
    suspend fun update(card: BankCardEntity)

    @Query("DELETE FROM bank_cards WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM bank_cards")
    suspend fun count(): Int
}
