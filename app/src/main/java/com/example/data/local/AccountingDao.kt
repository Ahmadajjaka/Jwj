package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class InvoiceWithItems(
    @Embedded val invoice: InvoiceEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "invoiceId"
    )
    val items: List<InvoiceItemEntity>
)

@Dao
interface AccountingDao {
    // Store Settings
    @Query("SELECT * FROM store_settings WHERE id = 1")
    fun observeStoreSettings(): Flow<StoreSettingsEntity?>

    @Query("SELECT * FROM store_settings WHERE id = 1")
    suspend fun getStoreSettings(): StoreSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStoreSettings(settings: StoreSettingsEntity)

    // Staff Users
    @Query("SELECT * FROM staff_users ORDER BY id ASC")
    fun observeStaffUsers(): Flow<List<StaffUserEntity>>

    @Query("SELECT * FROM staff_users ORDER BY id ASC")
    suspend fun getAllStaffUsers(): List<StaffUserEntity>

    @Query("SELECT * FROM staff_users WHERE id = :id")
    suspend fun getStaffById(id: Long): StaffUserEntity?

    @Query("SELECT * FROM staff_users WHERE syncId = :syncId LIMIT 1")
    suspend fun getStaffBySyncId(syncId: String): StaffUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaffUser(user: StaffUserEntity): Long

    @Update
    suspend fun updateStaffUser(user: StaffUserEntity)

    @Delete
    suspend fun deleteStaffUser(user: StaffUserEntity)

    @Query("DELETE FROM staff_users WHERE id = :id")
    suspend fun deleteStaffUserById(id: Long)

    // Products
    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY name ASC")
    fun observeActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY id ASC")
    fun observeAllProductsIncludingArchived(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE syncId = :syncId LIMIT 1")
    suspend fun getProductBySyncId(syncId: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Long)

    // Parties (Customers & Suppliers)
    @Query("SELECT * FROM parties ORDER BY name ASC")
    fun observeParties(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties ORDER BY name ASC")
    suspend fun getAllParties(): List<PartyEntity>

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun getPartyById(id: Long): PartyEntity?

    @Query("SELECT * FROM parties WHERE syncId = :syncId LIMIT 1")
    suspend fun getPartyBySyncId(syncId: String): PartyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: PartyEntity): Long

    @Update
    suspend fun updateParty(party: PartyEntity)

    // Invoices & Items
    @Transaction
    @Query("SELECT * FROM invoices ORDER BY invoiceDateMs DESC, id DESC")
    fun observeInvoicesWithItems(): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :invoiceId")
    suspend fun getInvoiceWithItems(invoiceId: Long): InvoiceWithItems?

    @Query("SELECT * FROM invoices ORDER BY invoiceDateMs DESC, id DESC")
    suspend fun getAllInvoices(): List<InvoiceEntity>

    @Query("SELECT * FROM invoice_items")
    suspend fun getAllInvoiceItems(): List<InvoiceItemEntity>

    @Query("SELECT * FROM invoices WHERE syncId = :syncId LIMIT 1")
    suspend fun getInvoiceBySyncId(syncId: String): InvoiceEntity?

    @Query("SELECT COUNT(*) FROM invoices WHERE invoiceType = :type")
    suspend fun countInvoicesByType(type: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceItems(items: List<InvoiceItemEntity>)

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteItemsForInvoice(invoiceId: Long)

    // Stock Movements
    @Query("SELECT * FROM stock_movements ORDER BY occurredAtMs DESC, id DESC")
    fun observeStockMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY occurredAtMs DESC, id DESC")
    suspend fun getAllStockMovements(): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE syncId = :syncId LIMIT 1")
    suspend fun getStockMovementBySyncId(syncId: String): StockMovementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity): Long

    // Cash Transactions
    @Query("SELECT * FROM cash_transactions ORDER BY occurredAtMs DESC, id DESC")
    fun observeCashTransactions(): Flow<List<CashTransactionEntity>>

    @Query("SELECT * FROM cash_transactions ORDER BY occurredAtMs DESC, id DESC")
    suspend fun getAllCashTransactions(): List<CashTransactionEntity>

    @Query("SELECT COALESCE(SUM(amountSigned), 0.0) FROM cash_transactions")
    suspend fun getCashboxBalanceSync(): Double

    @Query("SELECT COALESCE(SUM(amountSigned), 0.0) FROM cash_transactions")
    fun observeCashboxBalance(): Flow<Double>

    @Query("SELECT * FROM cash_transactions WHERE id = :id LIMIT 1")
    suspend fun getCashTransactionById(id: Long): CashTransactionEntity?

    @Query("SELECT * FROM cash_transactions WHERE syncId = :syncId LIMIT 1")
    suspend fun getCashTransactionBySyncId(syncId: String): CashTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashTransaction(tx: CashTransactionEntity): Long

    @Update
    suspend fun updateCashTransaction(tx: CashTransactionEntity)

    @Delete
    suspend fun deleteCashTransaction(tx: CashTransactionEntity)

    @Query("DELETE FROM cash_transactions WHERE id = :id")
    suspend fun deleteCashTransactionById(id: Long)

    // Expenses
    @Query("SELECT * FROM expenses ORDER BY expenseDateMs DESC, id DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY expenseDateMs DESC, id DESC")
    suspend fun getAllExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE syncId = :syncId LIMIT 1")
    suspend fun getExpenseBySyncId(syncId: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    // Debt Payments
    @Query("SELECT * FROM debt_payments ORDER BY paymentDateMs DESC, id DESC")
    fun observeDebtPayments(): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments ORDER BY paymentDateMs DESC, id DESC")
    suspend fun getAllDebtPayments(): List<DebtPaymentEntity>

    @Query("SELECT * FROM debt_payments WHERE syncId = :syncId LIMIT 1")
    suspend fun getDebtPaymentBySyncId(syncId: String): DebtPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayment(payment: DebtPaymentEntity): Long

    // Audit Logs
    @Query("SELECT * FROM audit_logs ORDER BY occurredAtMs DESC, id DESC")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY occurredAtMs DESC, id DESC")
    suspend fun getAllAuditLogs(): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE syncId = :syncId LIMIT 1")
    suspend fun getAuditLogBySyncId(syncId: String): AuditLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity): Long

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun countAuditLogs(): Int

    @Query("DELETE FROM audit_logs WHERE occurredAtMs < :cutoffMs")
    suspend fun pruneAuditLogsBefore(cutoffMs: Long): Int

    // Sync markers
    @Query("UPDATE products SET isSynced = 1 WHERE syncId = :syncId")
    suspend fun markProductSynced(syncId: String)

    @Query("UPDATE parties SET isSynced = 1 WHERE syncId = :syncId")
    suspend fun markPartySynced(syncId: String)

    @Query("UPDATE invoices SET isSynced = 1 WHERE syncId = :syncId")
    suspend fun markInvoiceSynced(syncId: String)

    @Query("UPDATE cash_transactions SET isSynced = 1 WHERE syncId = :syncId")
    suspend fun markCashTxSynced(syncId: String)

    @Query("UPDATE expenses SET isSynced = 1 WHERE syncId = :syncId")
    suspend fun markExpenseSynced(syncId: String)

    @Query("UPDATE debt_payments SET isSynced = 1 WHERE syncId = :syncId")
    suspend fun markDebtPaymentSynced(syncId: String)

    @Query("UPDATE stock_movements SET isSynced = 1 WHERE syncId = :syncId")
    suspend fun markStockMovementSynced(syncId: String)

    @Query("UPDATE staff_users SET isSynced = 1 WHERE syncId = :syncId")
    suspend fun markStaffSynced(syncId: String)

    @Query("UPDATE audit_logs SET isSynced = 1 WHERE syncId = :syncId")
    suspend fun markAuditLogSynced(syncId: String)

    @Query("UPDATE products SET isSynced = 0")
    suspend fun markAllProductsUnsynced()

    @Query("UPDATE parties SET isSynced = 0")
    suspend fun markAllPartiesUnsynced()

    @Query("UPDATE invoices SET isSynced = 0")
    suspend fun markAllInvoicesUnsynced()

    @Query("UPDATE cash_transactions SET isSynced = 0")
    suspend fun markAllCashTxUnsynced()

    @Query("UPDATE expenses SET isSynced = 0")
    suspend fun markAllExpensesUnsynced()

    @Query("UPDATE debt_payments SET isSynced = 0")
    suspend fun markAllDebtPaymentsUnsynced()

    @Query("UPDATE stock_movements SET isSynced = 0")
    suspend fun markAllStockMovementsUnsynced()

    @Query("UPDATE staff_users SET isSynced = 0")
    suspend fun markAllStaffUnsynced()

    @Query("UPDATE audit_logs SET isSynced = 0")
    suspend fun markAllAuditLogsUnsynced()

    // Clear all transactional data for atomic JSON restore
    @Query("DELETE FROM invoice_items")
    suspend fun clearInvoiceItems()

    @Query("DELETE FROM invoices")
    suspend fun clearInvoices()

    @Query("DELETE FROM stock_movements")
    suspend fun clearStockMovements()

    @Query("DELETE FROM cash_transactions")
    suspend fun clearCashTransactions()

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()

    @Query("DELETE FROM debt_payments")
    suspend fun clearDebtPayments()

    @Query("DELETE FROM audit_logs")
    suspend fun clearAuditLogs()

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("DELETE FROM parties")
    suspend fun clearParties()

    @Query("DELETE FROM staff_users")
    suspend fun clearStaffUsers()
}
