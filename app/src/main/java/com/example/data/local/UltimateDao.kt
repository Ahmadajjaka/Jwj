package com.example.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class JournalEntryWithLines(
    @Embedded val entry: JournalEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "journalEntryId"
    )
    val lines: List<JournalEntryLineEntity>
)

@Dao
interface UltimateDao {
    // Product Units
    @Query("SELECT * FROM product_units ORDER BY productId ASC, conversionToBase ASC")
    fun observeAllProductUnits(): Flow<List<ProductUnitEntity>>

    @Query("SELECT * FROM product_units ORDER BY productId ASC, conversionToBase ASC")
    suspend fun getAllProductUnits(): List<ProductUnitEntity>

    @Query("SELECT * FROM product_units WHERE productId = :productId ORDER BY conversionToBase ASC")
    suspend fun getUnitsForProduct(productId: Long): List<ProductUnitEntity>

    @Query("SELECT * FROM product_units WHERE barcode = :barcode LIMIT 1")
    suspend fun getUnitByBarcode(barcode: String): ProductUnitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductUnit(unit: ProductUnitEntity): Long

    @Query("DELETE FROM product_units WHERE id = :id")
    suspend fun deleteProductUnitById(id: Long)

    @Query("DELETE FROM product_units")
    suspend fun clearProductUnits()

    // Customer Special Prices
    @Query("SELECT * FROM customer_special_prices ORDER BY partyName ASC")
    fun observeCustomerSpecialPrices(): Flow<List<CustomerSpecialPriceEntity>>

    @Query("SELECT * FROM customer_special_prices")
    suspend fun getAllCustomerSpecialPrices(): List<CustomerSpecialPriceEntity>

    @Query("SELECT * FROM customer_special_prices WHERE partyId = :partyId AND productId = :productId LIMIT 1")
    suspend fun getCustomerSpecialPrice(partyId: Long, productId: Long): CustomerSpecialPriceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerSpecialPrice(price: CustomerSpecialPriceEntity): Long

    @Query("DELETE FROM customer_special_prices WHERE id = :id")
    suspend fun deleteCustomerSpecialPriceById(id: Long)

    @Query("DELETE FROM customer_special_prices")
    suspend fun clearCustomerSpecialPrices()

    // Price History
    @Query("SELECT * FROM price_history ORDER BY changedAtMs DESC LIMIT 200")
    fun observePriceHistory(): Flow<List<ProductPriceHistoryEntity>>

    @Query("SELECT * FROM price_history ORDER BY changedAtMs DESC")
    suspend fun getAllPriceHistory(): List<ProductPriceHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(history: ProductPriceHistoryEntity): Long

    @Query("SELECT COUNT(*) FROM price_history")
    suspend fun countPriceHistory(): Int

    @Query("DELETE FROM price_history WHERE changedAtMs < :cutoffMs")
    suspend fun prunePriceHistoryBefore(cutoffMs: Long): Int

    @Query("DELETE FROM price_history")
    suspend fun clearPriceHistory()

    // Cash Accounts
    @Query("SELECT * FROM cash_accounts ORDER BY id ASC")
    fun observeCashAccounts(): Flow<List<CashAccountEntity>>

    @Query("SELECT * FROM cash_accounts ORDER BY id ASC")
    suspend fun getAllCashAccounts(): List<CashAccountEntity>

    @Query("SELECT * FROM cash_accounts WHERE id = :id LIMIT 1")
    suspend fun getCashAccountById(id: Long): CashAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashAccount(account: CashAccountEntity): Long

    @Query("SELECT COALESCE(SUM(amountSigned), 0.0) FROM cash_transactions WHERE accountId = :accountId")
    suspend fun getBalanceForAccount(accountId: Long): Double

    @Query("DELETE FROM cash_accounts WHERE id > 1")
    suspend fun clearNonDefaultCashAccounts()

    // Cashbox Closings
    @Query("SELECT * FROM cashbox_closings ORDER BY closingDateMs DESC, id DESC")
    fun observeCashboxClosings(): Flow<List<CashboxClosingEntity>>

    @Query("SELECT * FROM cashbox_closings ORDER BY closingDateMs DESC, id DESC")
    suspend fun getAllCashboxClosings(): List<CashboxClosingEntity>

    @Query("SELECT * FROM cashbox_closings WHERE isLocked = 1 AND :dateMs BETWEEN periodStartMs AND periodEndMs LIMIT 1")
    suspend fun findLockedClosingForDate(dateMs: Long): CashboxClosingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashboxClosing(closing: CashboxClosingEntity): Long

    @Query("DELETE FROM cashbox_closings")
    suspend fun clearCashboxClosings()

    // Journal Entries & Lines
    @Transaction
    @Query("SELECT * FROM journal_entries ORDER BY entryDateMs DESC, id DESC")
    fun observeJournalEntries(): Flow<List<JournalEntryWithLines>>

    @Query("SELECT * FROM journal_entries ORDER BY entryDateMs DESC, id DESC")
    suspend fun getAllJournalEntries(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entry_lines")
    suspend fun getAllJournalEntryLines(): List<JournalEntryLineEntity>

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun countJournalEntries(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntryLines(lines: List<JournalEntryLineEntity>)

    @Query("DELETE FROM journal_entry_lines")
    suspend fun clearJournalEntryLines()

    @Query("DELETE FROM journal_entries")
    suspend fun clearJournalEntries()

    // Stock Counts
    @Query("SELECT * FROM stock_counts ORDER BY countedAtMs DESC, id DESC")
    fun observeStockCounts(): Flow<List<StockCountEntity>>

    @Query("SELECT * FROM stock_counts ORDER BY countedAtMs DESC, id DESC")
    suspend fun getAllStockCounts(): List<StockCountEntity>

    @Query("SELECT * FROM stock_counts WHERE id = :id LIMIT 1")
    suspend fun getStockCountById(id: Long): StockCountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockCount(count: StockCountEntity): Long

    @Update
    suspend fun updateStockCount(count: StockCountEntity)

    @Query("DELETE FROM stock_counts")
    suspend fun clearStockCounts()

    // Returns
    @Query("SELECT * FROM returns ORDER BY returnDateMs DESC, id DESC")
    fun observeReturns(): Flow<List<ReturnEntity>>

    @Query("SELECT * FROM returns ORDER BY returnDateMs DESC, id DESC")
    suspend fun getAllReturns(): List<ReturnEntity>

    @Query("SELECT COUNT(*) FROM returns")
    suspend fun countReturns(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturn(ret: ReturnEntity): Long

    @Query("DELETE FROM returns")
    suspend fun clearReturns()

    // Accounting Periods
    @Query("SELECT * FROM accounting_periods ORDER BY startMs DESC")
    fun observeAccountingPeriods(): Flow<List<AccountingPeriodEntity>>

    @Query("SELECT * FROM accounting_periods ORDER BY startMs DESC")
    suspend fun getAllAccountingPeriods(): List<AccountingPeriodEntity>

    @Query("SELECT * FROM accounting_periods WHERE isClosed = 1 AND :dateMs BETWEEN startMs AND endMs LIMIT 1")
    suspend fun findClosedPeriodForDate(dateMs: Long): AccountingPeriodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountingPeriod(period: AccountingPeriodEntity): Long

    @Update
    suspend fun updateAccountingPeriod(period: AccountingPeriodEntity)

    @Query("DELETE FROM accounting_periods")
    suspend fun clearAccountingPeriods()

    // Sync Queue
    @Query("SELECT * FROM sync_queue ORDER BY createdAtMs DESC, id DESC")
    fun observeSyncQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAtMs ASC")
    suspend fun getPendingSyncItems(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE entitySyncId = :entitySyncId AND status = 'PENDING' LIMIT 1")
    suspend fun findPendingByEntitySyncId(entitySyncId: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncQueueItem(item: SyncQueueEntity): Long

    @Update
    suspend fun updateSyncQueueItem(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET status = 'SYNCED', updatedAtMs = :now WHERE entitySyncId = :entitySyncId AND status != 'SYNCED'")
    suspend fun markEntitySyncedInQueue(entitySyncId: String, now: Long)

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun countSyncQueueItems(): Int

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED' AND updatedAtMs < :cutoffMs")
    suspend fun pruneSyncedQueueItemsBefore(cutoffMs: Long): Int

    @Query("DELETE FROM sync_queue")
    suspend fun clearSyncQueue()

    // Backups Log
    @Query("SELECT * FROM backups ORDER BY createdAtMs DESC, id DESC")
    fun observeBackups(): Flow<List<BackupRecordEntity>>

    @Query("SELECT * FROM backups ORDER BY createdAtMs DESC, id DESC")
    suspend fun getAllBackups(): List<BackupRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupRecord(record: BackupRecordEntity): Long

    @Query("DELETE FROM backups")
    suspend fun clearBackupRecords()

    // AI Logs
    @Query("SELECT * FROM ai_logs ORDER BY createdAtMs DESC, id DESC LIMIT 100")
    fun observeAiLogs(): Flow<List<AiLogEntity>>

    @Query("SELECT * FROM ai_logs ORDER BY createdAtMs DESC, id DESC")
    suspend fun getAllAiLogs(): List<AiLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiLog(log: AiLogEntity): Long

    @Query("SELECT COUNT(*) FROM ai_logs")
    suspend fun countAiLogs(): Int

    @Query("DELETE FROM ai_logs WHERE createdAtMs < :cutoffMs")
    suspend fun pruneAiLogsBefore(cutoffMs: Long): Int

    @Query("DELETE FROM ai_logs")
    suspend fun clearAiLogs()
}
