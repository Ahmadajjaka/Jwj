package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for core Transaction CRUD operations on the `cash_transactions` table.
 */
@Dao
interface TransactionDao {
    @Query("SELECT * FROM cash_transactions ORDER BY occurredAtMs DESC, id DESC")
    fun observeAll(): Flow<List<CashTransactionEntity>>

    @Query("SELECT * FROM cash_transactions WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<CashTransactionEntity?>

    @Query("SELECT COALESCE(SUM(amountSigned), 0.0) FROM cash_transactions")
    fun observeBalance(): Flow<Double>

    @Query("SELECT * FROM cash_transactions ORDER BY occurredAtMs DESC, id DESC")
    suspend fun getAll(): List<CashTransactionEntity>

    @Query("SELECT * FROM cash_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CashTransactionEntity?

    @Query("SELECT * FROM cash_transactions WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): CashTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: CashTransactionEntity): Long

    @Update
    suspend fun update(transaction: CashTransactionEntity): Int

    @Delete
    suspend fun delete(transaction: CashTransactionEntity): Int

    @Query("DELETE FROM cash_transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
