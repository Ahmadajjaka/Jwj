package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StoreSettingsEntity::class,
        StaffUserEntity::class,
        ProductEntity::class,
        PartyEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        StockMovementEntity::class,
        CashTransactionEntity::class,
        ExpenseEntity::class,
        DebtPaymentEntity::class,
        AuditLogEntity::class,
        ProductUnitEntity::class,
        CustomerSpecialPriceEntity::class,
        ProductPriceHistoryEntity::class,
        CashAccountEntity::class,
        CashboxClosingEntity::class,
        JournalEntryEntity::class,
        JournalEntryLineEntity::class,
        StockCountEntity::class,
        ReturnEntity::class,
        AccountingPeriodEntity::class,
        SyncQueueEntity::class,
        BackupRecordEntity::class,
        AiLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MamoDatabase : RoomDatabase() {
    abstract fun accountingDao(): AccountingDao
    abstract fun ultimateDao(): UltimateDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: MamoDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE store_settings ADD COLUMN storePhone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN storeAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN taxEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN taxRatePercent REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN printPaperSize TEXT NOT NULL DEFAULT 'A4'")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN printShowStamp INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN printShowSignature INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN backupEncryptionEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN autoBackupIntervalHours INTEGER NOT NULL DEFAULT 24")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN lastAutoBackupAtMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN lastAutoBackupStatus TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN staleDaysThreshold INTEGER NOT NULL DEFAULT 60")

                db.execSQL("ALTER TABLE staff_users ADD COLUMN canEditPrices INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE staff_users ADD COLUMN canWithdrawDeposit INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE staff_users ADD COLUMN canApproveStocktake INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE products ADD COLUMN sku TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN imageUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN baseUnit TEXT NOT NULL DEFAULT 'قطعة'")
                db.execSQL("ALTER TABLE products ADD COLUMN wholesalePrice REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE products ADD COLUMN minSellingPrice REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE products ADD COLUMN defaultSupplierName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN lastPurchaseDateMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN lastSaleDateMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE products SET wholesalePrice = sellingPrice, minSellingPrice = averageCost, lastPurchaseDateMs = createdAtMs")

                db.execSQL("ALTER TABLE parties ADD COLUMN address TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE parties ADD COLUMN openingBalance REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE parties ADD COLUMN notes TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE invoices ADD COLUMN extraCosts REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN taxRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN taxAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN accountId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE invoices ADD COLUMN sourceOcrHash TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE invoice_items ADD COLUMN unitName TEXT NOT NULL DEFAULT 'قطعة'")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN conversionFactor REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN baseQuantity REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN pricingTier TEXT NOT NULL DEFAULT 'RETAIL'")
                db.execSQL("UPDATE invoice_items SET baseQuantity = quantity")

                db.execSQL("ALTER TABLE cash_transactions ADD COLUMN accountId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE cash_transactions ADD COLUMN accountName TEXT NOT NULL DEFAULT 'الصندوق الرئيسي'")

                db.execSQL("ALTER TABLE expenses ADD COLUMN receiptImageUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN accountId INTEGER NOT NULL DEFAULT 1")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS product_units (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        productId INTEGER NOT NULL,
                        unitName TEXT NOT NULL,
                        conversionToBase REAL NOT NULL,
                        sellingPrice REAL NOT NULL,
                        wholesalePrice REAL NOT NULL,
                        barcode TEXT NOT NULL,
                        FOREIGN KEY(productId) REFERENCES products(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_product_units_syncId ON product_units(syncId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_units_productId ON product_units(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_units_barcode ON product_units(barcode)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS customer_special_prices (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        partyId INTEGER NOT NULL,
                        partyName TEXT NOT NULL,
                        productId INTEGER NOT NULL,
                        productName TEXT NOT NULL,
                        specialPrice REAL NOT NULL,
                        minQty REAL NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customer_special_prices_syncId ON customer_special_prices(syncId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_special_prices_partyId_productId ON customer_special_prices(partyId, productId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        productId INTEGER NOT NULL,
                        productName TEXT NOT NULL,
                        changeType TEXT NOT NULL,
                        oldPrice REAL NOT NULL,
                        newPrice REAL NOT NULL,
                        referenceId TEXT NOT NULL,
                        staffName TEXT NOT NULL,
                        changedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_history_productId ON price_history(productId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cash_accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        accountType TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cash_accounts_syncId ON cash_accounts(syncId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cashbox_closings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        accountId INTEGER NOT NULL,
                        accountName TEXT NOT NULL,
                        closingDateMs INTEGER NOT NULL,
                        periodStartMs INTEGER NOT NULL,
                        periodEndMs INTEGER NOT NULL,
                        expectedBalance REAL NOT NULL,
                        actualCountedBalance REAL NOT NULL,
                        varianceAmount REAL NOT NULL,
                        notes TEXT NOT NULL,
                        closedByStaff TEXT NOT NULL,
                        isLocked INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        isSynced INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cashbox_closings_syncId ON cashbox_closings(syncId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS journal_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        entryNumber TEXT NOT NULL,
                        referenceType TEXT NOT NULL,
                        referenceSyncId TEXT NOT NULL,
                        description TEXT NOT NULL,
                        totalDebit REAL NOT NULL,
                        totalCredit REAL NOT NULL,
                        staffName TEXT NOT NULL,
                        entryDateMs INTEGER NOT NULL,
                        isReversed INTEGER NOT NULL,
                        isSynced INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_journal_entries_syncId ON journal_entries(syncId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_journal_entries_entryNumber ON journal_entries(entryNumber)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS journal_entry_lines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        journalEntryId INTEGER NOT NULL,
                        accountCode TEXT NOT NULL,
                        accountName TEXT NOT NULL,
                        debit REAL NOT NULL,
                        credit REAL NOT NULL,
                        partyId INTEGER,
                        notes TEXT NOT NULL,
                        FOREIGN KEY(journalEntryId) REFERENCES journal_entries(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entry_lines_journalEntryId ON journal_entry_lines(journalEntryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entry_lines_accountCode ON journal_entry_lines(accountCode)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_counts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        productId INTEGER NOT NULL,
                        productName TEXT NOT NULL,
                        systemQty REAL NOT NULL,
                        countedQty REAL NOT NULL,
                        varianceQty REAL NOT NULL,
                        varianceValue REAL NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        countedBy TEXT NOT NULL,
                        approvedBy TEXT NOT NULL,
                        countedAtMs INTEGER NOT NULL,
                        approvedAtMs INTEGER NOT NULL,
                        isSynced INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_counts_syncId ON stock_counts(syncId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_counts_productId ON stock_counts(productId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS returns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        returnNumber TEXT NOT NULL,
                        returnType TEXT NOT NULL,
                        originalInvoiceId INTEGER NOT NULL,
                        originalInvoiceNumber TEXT NOT NULL,
                        partyId INTEGER,
                        partyName TEXT NOT NULL,
                        productId INTEGER NOT NULL,
                        productName TEXT NOT NULL,
                        unitName TEXT NOT NULL,
                        conversionFactor REAL NOT NULL,
                        returnedQty REAL NOT NULL,
                        baseQty REAL NOT NULL,
                        unitPrice REAL NOT NULL,
                        totalRefundAmount REAL NOT NULL,
                        refundedCashAmount REAL NOT NULL,
                        reason TEXT NOT NULL,
                        staffName TEXT NOT NULL,
                        returnDateMs INTEGER NOT NULL,
                        isSynced INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_returns_syncId ON returns(syncId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_returns_originalInvoiceId ON returns(originalInvoiceId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS accounting_periods (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        startMs INTEGER NOT NULL,
                        endMs INTEGER NOT NULL,
                        isClosed INTEGER NOT NULL,
                        closedBy TEXT NOT NULL,
                        closedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounting_periods_syncId ON accounting_periods(syncId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entitySyncId TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        status TEXT NOT NULL,
                        retryCount INTEGER NOT NULL,
                        lastError TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_queue_syncId ON sync_queue(syncId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_status ON sync_queue(status)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS backups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        backupType TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        isEncrypted INTEGER NOT NULL,
                        checksumSha256 TEXT NOT NULL,
                        recordCount INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backups_syncId ON backups(syncId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL,
                        staffName TEXT NOT NULL,
                        modelId TEXT NOT NULL,
                        prompt TEXT NOT NULL,
                        responseSummary TEXT NOT NULL,
                        wasOfflineFallback INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ai_logs_syncId ON ai_logs(syncId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE staff_users ADD COLUMN approvalStatus TEXT NOT NULL DEFAULT 'APPROVED'")
                db.execSQL("ALTER TABLE staff_users ADD COLUMN requestedRole TEXT NOT NULL DEFAULT 'CASHIER'")
                db.execSQL("UPDATE staff_users SET requestedRole = role")
                db.execSQL("ALTER TABLE staff_users ADD COLUMN loginIdentifier TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE staff_users ADD COLUMN requestedAtMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE staff_users SET requestedAtMs = createdAtMs")
                db.execSQL("ALTER TABLE staff_users ADD COLUMN approvedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE staff_users ADD COLUMN approvedAtMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): MamoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MamoDatabase::class.java,
                    "mamo_accounting.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            runCatching {
                                db.query("PRAGMA synchronous = FULL").use { it.moveToFirst() }
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
