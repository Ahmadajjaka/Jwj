package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AccountingPeriodEntity
import com.example.data.local.AiLogEntity
import com.example.data.local.ApprovalStatus
import com.example.data.local.AuditLogEntity
import com.example.data.local.CashTxType
import com.example.data.local.InvoiceType
import com.example.data.local.MamoDatabase
import com.example.data.local.MeasureUnit
import com.example.data.local.PaymentMethod
import com.example.data.local.PricingTier
import com.example.data.local.ProductPriceHistoryEntity
import com.example.data.local.StaffRole
import com.example.data.local.StaffUserEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.remote.CloudCollectionKind
import com.example.data.repository.AccountingEngine
import com.example.data.repository.AccountingRepository
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.LoginAuthResult
import com.example.utils.DeviceLocalBackupManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminApprovalAndDbMaintenanceTest {

    private lateinit var context: Context
    private lateinit var db: MamoDatabase
    private lateinit var repo: AccountingRepository
    private lateinit var owner: StaffUserEntity

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MamoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AccountingRepository(db)
        owner = repo.createOwnerProfile("أحمد نوار المامو", "1234")
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun registration_requiresAdminApproval_and_assignsRoleAndPermissions() = runBlocking {
        // Pre-existing owner is already APPROVED
        assertEquals(ApprovalStatus.APPROVED, owner.approvalStatus)
        assertTrue(owner.isActive)

        // New staff registers without owner PIN -> queued as PENDING with zero privileges
        val pendingStaff = repo.registerAccountFromLogin(
            fullName = "خالد المحاسب",
            role = StaffRole.ACCOUNTANT,
            password = "5678",
            ownerVerifyPin = null,
            identifier = "khalid@mamo.sy"
        )
        assertEquals(ApprovalStatus.PENDING, pendingStaff.approvalStatus)
        assertFalse(pendingStaff.isActive)
        assertEquals(StaffRole.ACCOUNTANT, pendingStaff.requestedRole)
        assertEquals("khalid@mamo.sy", pendingStaff.loginIdentifier)
        assertFalse(pendingStaff.canViewProfits)
        assertFalse(pendingStaff.canCancelInvoices)
        assertFalse(pendingStaff.canManageInventory)
        assertFalse(pendingStaff.canEditPrices)

        // Login attempt while PENDING returns PendingApproval
        val pendingAuth = repo.authenticateWithStatus("khalid@mamo.sy", "5678", null, null)
        assertTrue(pendingAuth is LoginAuthResult.PendingApproval)

        // Non-owner cannot approve pending users
        var unauthorizedBlocked = false
        try {
            repo.approvePendingStaff(
                staffId = pendingStaff.id,
                assignedRole = StaffRole.CASHIER,
                canViewProfits = false,
                canCancelInvoices = false,
                canManageInventory = false,
                canManageExpenses = false,
                canManageSettings = false,
                canEditPrices = false,
                canWithdrawDeposit = false,
                canApproveStocktake = false,
                actingStaff = pendingStaff
            )
        } catch (_: IllegalArgumentException) {
            unauthorizedBlocked = true
        }
        assertTrue(unauthorizedBlocked)

        // Admin approves and changes work nature to WAREHOUSE_KEEPER with custom permissions
        repo.approvePendingStaff(
            staffId = pendingStaff.id,
            assignedRole = StaffRole.WAREHOUSE_KEEPER,
            canViewProfits = false,
            canCancelInvoices = false,
            canManageInventory = true,
            canManageExpenses = false,
            canManageSettings = false,
            canEditPrices = true,
            canWithdrawDeposit = false,
            canApproveStocktake = false,
            actingStaff = owner
        )

        val approvedAuth = repo.authenticateWithStatus("khalid@mamo.sy", "5678", null, null)
        assertTrue(approvedAuth is LoginAuthResult.Success)
        val approvedUser = (approvedAuth as LoginAuthResult.Success).staff
        assertEquals(ApprovalStatus.APPROVED, approvedUser.approvalStatus)
        assertTrue(approvedUser.isActive)
        assertEquals(StaffRole.WAREHOUSE_KEEPER, approvedUser.role)
        assertEquals(owner.name, approvedUser.approvedBy)
        assertTrue(approvedUser.approvedAtMs > 0L)
        assertTrue(approvedUser.canManageInventory)
        assertTrue(approvedUser.canEditPrices)
        assertFalse(approvedUser.canViewProfits)

        // Another candidate gets rejected
        val rejectedCandidate = repo.registerAccountFromLogin(
            fullName = "مستخدم مرفوض",
            role = StaffRole.CASHIER,
            password = "9999",
            ownerVerifyPin = null,
            identifier = "rejected_user"
        )
        repo.rejectPendingStaff(rejectedCandidate.id, "بيانات غير مكتملة", owner)
        val rejectedAuth = repo.authenticateWithStatus("rejected_user", "9999", null, null)
        assertTrue(rejectedAuth is LoginAuthResult.Rejected)
    }

    @Test
    fun sale_and_purchase_customDates_persistAcrossLedgers_and_respectLockedPeriod() = runBlocking {
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 20_000.0, "رأس مال", owner)

        val productId = repo.saveProduct(
            productId = null,
            name = "مفتاح إنجليزي 12 إنش",
            category = "عدد يدوية",
            barcode = "629000999001",
            costPrice = 0.0,
            sellingPrice = 150.0,
            quantity = 0.0,
            minStockLevel = 5.0,
            baseUnit = MeasureUnit.PIECE,
            wholesalePrice = 130.0,
            minSellingPrice = 110.0,
            actingStaff = owner
        )

        val now = System.currentTimeMillis()
        val tenDaysAgo = now - 10L * 86_400_000L
        val fiveDaysAgo = now - 5L * 86_400_000L
        val threeDaysAgo = now - 3L * 86_400_000L

        val purchaseId = repo.postPurchaseInvoice(
            lines = listOf(
                InvoiceLineDraft(
                    productId = productId,
                    productName = "مفتاح إنجليزي 12 إنش",
                    quantity = 10.0,
                    unitPrice = 100.0
                )
            ),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 1000.0,
            partyId = null,
            supplierName = "مورد العدد",
            supplierPhone = "",
            notes = "شراء مؤرخ",
            dueDateMs = null,
            purchaseDateMs = tenDaysAgo,
            actingStaff = owner
        )
        val purchaseInv = repo.dao.getInvoiceWithItems(purchaseId)!!
        assertEquals(InvoiceType.PURCHASE, purchaseInv.invoice.invoiceType)
        assertEquals(tenDaysAgo, purchaseInv.invoice.invoiceDateMs)
        assertTrue(repo.dao.getAllCashTransactions().any {
            it.referenceId == purchaseInv.invoice.syncId && it.occurredAtMs == tenDaysAgo
        })
        assertTrue(repo.dao.getAllStockMovements().any {
            it.referenceId == purchaseInv.invoice.invoiceNumber && it.occurredAtMs == tenDaysAgo
        })
        assertTrue(repo.uDao.getAllJournalEntries().any {
            it.referenceSyncId == purchaseInv.invoice.syncId && it.entryDateMs == tenDaysAgo
        })

        val saleId = repo.postSaleInvoice(
            lines = listOf(
                InvoiceLineDraft(
                    productId = productId,
                    productName = "مفتاح إنجليزي 12 إنش",
                    quantity = 2.0,
                    unitPrice = 150.0,
                    pricingTier = PricingTier.RETAIL
                )
            ),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 300.0,
            partyId = null,
            customerName = "زبون نقدي",
            customerPhone = "",
            notes = "بيع مؤرخ",
            dueDateMs = null,
            actingStaff = owner,
            saleDateMs = fiveDaysAgo
        )
        val saleInv = repo.dao.getInvoiceWithItems(saleId)!!
        assertEquals(InvoiceType.SALE, saleInv.invoice.invoiceType)
        assertEquals(fiveDaysAgo, saleInv.invoice.invoiceDateMs)
        assertTrue(repo.dao.getAllCashTransactions().any {
            it.referenceId == saleInv.invoice.syncId && it.occurredAtMs == fiveDaysAgo
        })
        assertTrue(repo.dao.getAllStockMovements().any {
            it.referenceId == saleInv.invoice.invoiceNumber && it.occurredAtMs == fiveDaysAgo
        })
        assertTrue(repo.uDao.getAllJournalEntries().any {
            it.referenceSyncId == saleInv.invoice.syncId && it.entryDateMs == fiveDaysAgo
        })

        // Edit sale invoice with updated saleDateMs
        val editedSaleId = repo.editSaleInvoice(
            invoiceId = saleId,
            newLines = listOf(
                InvoiceLineDraft(
                    productId = productId,
                    productName = "مفتاح إنجليزي 12 إنش",
                    quantity = 3.0,
                    unitPrice = 150.0,
                    pricingTier = PricingTier.RETAIL
                )
            ),
            newDiscount = 0.0,
            newPaymentMethod = PaymentMethod.CASH,
            newPaidAmount = 450.0,
            newCustomerName = "زبون نقدي",
            newCustomerPhone = "",
            newNotes = "بعد تعديل التاريخ",
            newDueDateMs = null,
            actingStaff = owner,
            newSaleDateMs = threeDaysAgo
        )
        val editedInv = repo.dao.getInvoiceWithItems(editedSaleId)!!
        assertEquals(threeDaysAgo, editedInv.invoice.invoiceDateMs)

        // Lock period covering fiveDaysAgo and verify non-owner cannot post a sale dated inside locked period
        val cashierPending = repo.registerAccountFromLogin(
            fullName = "كاشير الصالة",
            role = StaffRole.CASHIER,
            password = "2468",
            ownerVerifyPin = "1234",
            identifier = "cashier1"
        )
        repo.uDao.insertAccountingPeriod(
            AccountingPeriodEntity(
                name = "فترة مقفلة للاختبار",
                startMs = tenDaysAgo - 86_400_000L,
                endMs = fiveDaysAgo + 1_000L,
                isClosed = true,
                closedBy = owner.name,
                closedAtMs = now
            )
        )
        var lockedBlocked = false
        try {
            repo.postSaleInvoice(
                lines = listOf(
                    InvoiceLineDraft(
                        productId = productId,
                        productName = "مفتاح إنجليزي 12 إنش",
                        quantity = 1.0,
                        unitPrice = 150.0,
                        pricingTier = PricingTier.RETAIL
                    )
                ),
                invoiceDiscount = 0.0,
                paymentMethod = PaymentMethod.CASH,
                paidAmountInput = 150.0,
                partyId = null,
                customerName = "زبون",
                customerPhone = "",
                notes = "",
                dueDateMs = null,
                actingStaff = cashierPending,
                saleDateMs = fiveDaysAgo
            )
        } catch (_: Exception) {
            lockedBlocked = true
        }
        assertTrue(lockedBlocked)
    }

    @Test
    fun resetSyncedFlags_marksOnlyDeletedCloudCollectionsUnsynced() = runBlocking {
        val prodId = repo.saveProduct(
            productId = null,
            name = "مطرقة فولاذية",
            category = "عدد",
            barcode = "777001",
            costPrice = 30.0,
            sellingPrice = 50.0,
            quantity = 12.0,
            minStockLevel = 2.0,
            actingStaff = owner
        )
        val partyId = repo.saveParty(null, "CUSTOMER", "زبون سحابي", "099111222", owner)
        val prod = repo.dao.getProductById(prodId)!!
        val party = repo.dao.getPartyById(partyId)!!
        repo.dao.markProductSynced(prod.syncId)
        repo.dao.markPartySynced(party.syncId)

        assertTrue(repo.dao.getProductById(prodId)!!.isSynced)
        assertTrue(repo.dao.getPartyById(partyId)!!.isSynced)

        // Delete ONLY PRODUCTS from cloud -> products become unsynced, parties stay synced
        repo.resetSyncedFlagsForDeletedCloudCollections(
            collections = setOf(CloudCollectionKind.PRODUCTS),
            wipeProfile = false,
            deletedDocsCount = 1,
            actingStaff = owner
        )
        assertFalse(repo.dao.getProductById(prodId)!!.isSynced)
        assertTrue(repo.dao.getPartyById(partyId)!!.isSynced)
    }

    @Test
    fun gzipBackupRoundTrip_and_realSqliteVacuumCompaction_workEndToEnd() = runBlocking {
        // Delete any leftover file DB before creating a real file-backed mamo_accounting.db
        val dbFile = context.getDatabasePath("mamo_accounting.db")
        val walFile = File("${dbFile.path}-wal")
        val shmFile = File("${dbFile.path}-shm")
        listOf(dbFile, walFile, shmFile).forEach { if (it.exists()) it.delete() }

        val fileDb = Room.databaseBuilder(context, MamoDatabase::class.java, "mamo_accounting.db")
            .allowMainThreadQueries()
            .build()
        try {
            val fileRepo = AccountingRepository(fileDb)
            val fileOwner = fileRepo.createOwnerProfile("أحمد نوار المامو", "1234")
            fileRepo.recordCashMovement(CashTxType.CAPITAL_IN, 10_000.0, "رأس مال حقيقي", fileOwner)
            val prodId = fileRepo.saveProduct(
                productId = null,
                name = "شريط لاصق صناعي",
                category = "مواد لاصقة",
                barcode = "629000555",
                costPrice = 10.0,
                sellingPrice = 20.0,
                quantity = 50.0,
                minStockLevel = 5.0,
                actingStaff = fileOwner
            )

            // 1) Test GZIP plain & GZIP encrypted backup export/import round-trip
            val plainJsonBytes = fileRepo.exportJsonBackup().toByteArray(Charsets.UTF_8)
            val (gzBytes, gzRecord) = fileRepo.exportBackupBytes(compressGzip = true, passphrase = null, actingStaff = fileOwner)
            assertTrue(AccountingEngine.isGzipBytes(gzBytes))
            assertTrue(gzBytes.size < plainJsonBytes.size)
            assertEquals("GZIP_COMPRESSED", gzRecord.backupType)

            val (gzEncBytes, gzEncRecord) = fileRepo.exportBackupBytes(compressGzip = true, passphrase = "Mamo@2026", actingStaff = fileOwner)
            assertEquals("GZIP_ENCRYPTED", gzEncRecord.backupType)
            assertTrue(gzEncRecord.isEncrypted)

            // Reset transactional data, then restore from GZIP encrypted bytes
            fileRepo.resetTransactionalDatabase("1234", fileOwner)
            assertTrue(fileDb.accountingDao().getAllProducts().isEmpty())
            fileRepo.importBackupBytes(gzEncBytes, "Mamo@2026", fileOwner)
            assertTrue(fileDb.accountingDao().getAllProducts().any { it.name == "شريط لاصق صناعي" })

            // Restore from plain GZIP bytes too
            fileRepo.resetTransactionalDatabase("1234", fileOwner)
            fileRepo.importBackupBytes(gzBytes, null, fileOwner)
            val restoredProdId = fileDb.accountingDao().getAllProducts().first { it.name == "شريط لاصق صناعي" }.id

            // 2) Seed old (> 45 days) prunable rows + active pending sync row, then test WAL checkpoint + pruning + SQLite VACUUM
            val now = System.currentTimeMillis()
            val oldMs = now - 45L * 86_400_000L
            for (i in 1..20) {
                fileDb.accountingDao().insertAuditLog(
                    AuditLogEntity(
                        action = "سجل قديم #$i",
                        entityType = "TEST",
                        entityId = "old_$i",
                        details = "تفاصيل متكررة طويلة لضغط قاعدة البيانات ".repeat(6),
                        staffName = fileOwner.name,
                        occurredAtMs = oldMs
                    )
                )
            }
            fileDb.ultimateDao().insertSyncQueueItem(
                SyncQueueEntity(
                    entityType = "PRODUCT",
                    entitySyncId = "old_synced_1",
                    operation = "UPSERT",
                    status = "SYNCED",
                    createdAtMs = oldMs,
                    updatedAtMs = oldMs
                )
            )
            fileDb.ultimateDao().insertSyncQueueItem(
                SyncQueueEntity(
                    entityType = "PRODUCT",
                    entitySyncId = "pending_keep_1",
                    operation = "UPSERT",
                    status = "PENDING",
                    createdAtMs = oldMs,
                    updatedAtMs = oldMs
                )
            )
            fileDb.ultimateDao().insertPriceHistory(
                ProductPriceHistoryEntity(
                    productId = restoredProdId,
                    productName = "شريط لاصق صناعي",
                    changeType = "SELLING_RETAIL",
                    oldPrice = 18.0,
                    newPrice = 20.0,
                    referenceId = "init",
                    staffName = fileOwner.name,
                    changedAtMs = oldMs
                )
            )
            fileDb.ultimateDao().insertAiLog(
                AiLogEntity(
                    staffName = fileOwner.name,
                    modelId = "MAMO-Offline",
                    prompt = "استفسار قديم",
                    responseSummary = "ملخص قديم",
                    wasOfflineFallback = true,
                    createdAtMs = oldMs
                )
            )

            val storageBefore = fileRepo.measureDatabaseSize(context)
            assertTrue(storageBefore.totalBytes > 0L)

            val report = fileRepo.compactAndPruneDatabase(context, retentionDays = 30, actingStaff = fileOwner)
            assertEquals(30, report.retentionDays)
            assertTrue(report.deletedAuditLogs >= 20)
            assertTrue(report.deletedSyncItems >= 1)
            assertTrue(report.deletedPriceHistory >= 1)
            assertTrue(report.deletedAiLogs >= 1)
            assertTrue(report.sizeAfterBytes > 0L)

            val storageAfter = fileRepo.measureDatabaseSize(context)
            assertTrue("Expected compacted DB (${storageAfter.totalBytes}) <= before (${storageBefore.totalBytes})", storageAfter.totalBytes <= storageBefore.totalBytes)
            assertTrue("Expected WAL shrunk (${storageAfter.walFileBytes}) <= before (${storageBefore.walFileBytes})", storageAfter.walFileBytes <= storageBefore.walFileBytes)
            // Active product + pending sync item are preserved
            assertTrue(fileDb.accountingDao().getAllProducts().any { it.name == "شريط لاصق صناعي" })
            assertTrue(fileDb.ultimateDao().getPendingSyncItems().any { it.entitySyncId == "pending_keep_1" })
        } finally {
            fileDb.close()
            listOf(dbFile, walFile, shmFile).forEach { if (it.exists()) it.delete() }
        }
    }

    @Test
    fun migration_v2_to_v3_preservesExistingStaffAsApproved() {
        val dbFile = File(context.cacheDir, "migration_v2_v3_${System.currentTimeMillis()}.db")
        if (dbFile.exists()) dbFile.delete()
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbFile.absolutePath)
                    .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            // Build complete v1 schema, run MIGRATION_1_2, and insert a v2 staff user
                            db.execSQL("CREATE TABLE store_settings (id INTEGER PRIMARY KEY NOT NULL, storeName TEXT NOT NULL, managerName TEXT NOT NULL, currencyCode TEXT NOT NULL, currencySymbol TEXT NOT NULL, costingMethod TEXT NOT NULL, invoiceFooter TEXT NOT NULL, logoUri TEXT NOT NULL, dateFormatPattern TEXT NOT NULL, autoBackupEnabled INTEGER NOT NULL, lastSyncAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE staff_users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, name TEXT NOT NULL, role TEXT NOT NULL, pinHash TEXT NOT NULL, canViewProfits INTEGER NOT NULL, canCancelInvoices INTEGER NOT NULL, canManageInventory INTEGER NOT NULL, canManageExpenses INTEGER NOT NULL, canManageSettings INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, barcode TEXT NOT NULL, averageCost REAL NOT NULL, lastPurchaseCost REAL NOT NULL, sellingPrice REAL NOT NULL, quantity REAL NOT NULL, minStockLevel REAL NOT NULL, isArchived INTEGER NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE parties (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, partyType TEXT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, totalAmount REAL NOT NULL, paidAmount REAL NOT NULL, balanceDue REAL NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE invoices (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, invoiceNumber TEXT NOT NULL, invoiceType TEXT NOT NULL, partyId INTEGER, partySyncId TEXT NOT NULL, partyName TEXT NOT NULL, partyPhone TEXT NOT NULL, status TEXT NOT NULL, paymentMethod TEXT NOT NULL, subtotal REAL NOT NULL, discountAmount REAL NOT NULL, netTotal REAL NOT NULL, paidAmount REAL NOT NULL, remainingAmount REAL NOT NULL, totalCost REAL NOT NULL, notes TEXT NOT NULL, staffId INTEGER NOT NULL, staffName TEXT NOT NULL, invoiceDateMs INTEGER NOT NULL, dueDateMs INTEGER, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE invoice_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceId INTEGER NOT NULL, productId INTEGER NOT NULL, productSyncId TEXT NOT NULL, productName TEXT NOT NULL, quantity REAL NOT NULL, unitPrice REAL NOT NULL, discountAmount REAL NOT NULL, lineTotal REAL NOT NULL, unitCostAtPosting REAL NOT NULL, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON DELETE CASCADE)")
                            db.execSQL("CREATE TABLE stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, productId INTEGER NOT NULL, productSyncId TEXT NOT NULL, productName TEXT NOT NULL, movementType TEXT NOT NULL, quantityDelta REAL NOT NULL, unitCost REAL NOT NULL, balanceAfter REAL NOT NULL, reason TEXT NOT NULL, referenceId TEXT NOT NULL, staffName TEXT NOT NULL, occurredAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE cash_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, txType TEXT NOT NULL, amountSigned REAL NOT NULL, description TEXT NOT NULL, referenceId TEXT NOT NULL, staffName TEXT NOT NULL, occurredAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, paymentMethod TEXT NOT NULL, staffName TEXT NOT NULL, expenseDateMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE debt_payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, partyId INTEGER NOT NULL, partySyncId TEXT NOT NULL, partyName TEXT NOT NULL, partyType TEXT NOT NULL, invoiceId INTEGER, amount REAL NOT NULL, notes TEXT NOT NULL, staffName TEXT NOT NULL, paymentDateMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE audit_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, action TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, details TEXT NOT NULL, staffName TEXT NOT NULL, occurredAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                            db.execSQL("CREATE UNIQUE INDEX index_staff_users_syncId ON staff_users(syncId)")
                            db.execSQL("CREATE UNIQUE INDEX index_products_syncId ON products(syncId)")
                            db.execSQL("CREATE INDEX index_products_barcode ON products(barcode)")
                            db.execSQL("CREATE UNIQUE INDEX index_parties_syncId ON parties(syncId)")
                            db.execSQL("CREATE UNIQUE INDEX index_invoices_syncId ON invoices(syncId)")
                            db.execSQL("CREATE UNIQUE INDEX index_invoices_invoiceNumber ON invoices(invoiceNumber)")
                            db.execSQL("CREATE INDEX index_invoices_partyId ON invoices(partyId)")
                            db.execSQL("CREATE INDEX index_invoice_items_invoiceId ON invoice_items(invoiceId)")
                            db.execSQL("CREATE INDEX index_invoice_items_productId ON invoice_items(productId)")
                            db.execSQL("CREATE UNIQUE INDEX index_stock_movements_syncId ON stock_movements(syncId)")
                            db.execSQL("CREATE INDEX index_stock_movements_productId ON stock_movements(productId)")
                            db.execSQL("CREATE UNIQUE INDEX index_cash_transactions_syncId ON cash_transactions(syncId)")
                            db.execSQL("CREATE UNIQUE INDEX index_expenses_syncId ON expenses(syncId)")
                            db.execSQL("CREATE UNIQUE INDEX index_debt_payments_syncId ON debt_payments(syncId)")
                            db.execSQL("CREATE INDEX index_debt_payments_partyId ON debt_payments(partyId)")
                            db.execSQL("CREATE UNIQUE INDEX index_audit_logs_syncId ON audit_logs(syncId)")
                            MamoDatabase.MIGRATION_1_2.migrate(db)
                            db.execSQL("INSERT INTO staff_users (syncId, name, role, pinHash, canViewProfits, canCancelInvoices, canManageInventory, canManageExpenses, canManageSettings, isActive, createdAtMs, isSynced, canEditPrices, canWithdrawDeposit, canApproveStocktake) VALUES ('staff_v2_legacy', 'موظف قديم', 'ACCOUNTANT', 'hash_abc', 1, 1, 1, 1, 0, 1, 1700000000000, 0, 1, 1, 0)")
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    })
                    .build()
            )
            helper.writableDatabase.close()

            val migratedDb = Room.databaseBuilder(context, MamoDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MamoDatabase.MIGRATION_1_2, MamoDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
            runBlocking {
                val staff = migratedDb.accountingDao().getAllStaffUsers().firstOrNull { it.syncId == "staff_v2_legacy" }
                assertNotNull(staff)
                assertEquals(ApprovalStatus.APPROVED, staff!!.approvalStatus)
                assertEquals(StaffRole.ACCOUNTANT, staff.requestedRole)
                assertEquals(1700000000000L, staff.requestedAtMs)
            }
            migratedDb.close()
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun instantDeviceStoragePersistence_checkpointsWalAndWritesRecoverableGzipSnapshot() = runBlocking {
        val dbFile = context.getDatabasePath("mamo_accounting.db")
        val walFile = File("${dbFile.path}-wal")
        val shmFile = File("${dbFile.path}-shm")
        val snapshotFile = DeviceLocalBackupManager.internalSnapshotFile(context)
        listOf(dbFile, walFile, shmFile, snapshotFile).forEach { if (it.exists()) it.delete() }

        val fileDb = Room.databaseBuilder(context, MamoDatabase::class.java, "mamo_accounting.db")
            .allowMainThreadQueries()
            .build()
        try {
            val fileRepo = AccountingRepository(fileDb)
            val owner = fileRepo.createOwnerProfile("أحمد نوار المامو", "1234")
            val customerId = fileRepo.saveParty(null, "CUSTOMER", "زبون محفوظ", "07701234567", owner)
            val prodId = fileRepo.saveProduct(
                productId = null,
                name = "مفك براغي مغناطيسي",
                category = "عدد يدوية",
                barcode = "629888777",
                costPrice = 15.0,
                sellingPrice = 25.0,
                quantity = 30.0,
                minStockLevel = 5.0,
                actingStaff = owner
            )
            val saleId = fileRepo.postSaleInvoice(
                lines = listOf(
                    InvoiceLineDraft(
                        productId = prodId,
                        productName = "مفك براغي مغناطيسي",
                        quantity = 3.0,
                        unitPrice = 25.0,
                        pricingTier = PricingTier.RETAIL
                    )
                ),
                invoiceDiscount = 0.0,
                paymentMethod = PaymentMethod.CASH,
                paidAmountInput = 75.0,
                partyId = customerId,
                customerName = "زبون محفوظ",
                customerPhone = "07701234567",
                notes = "فاتورة بيع محفوظة بذاكرة الجهاز",
                dueDateMs = null,
                actingStaff = owner
            )
            val postedSale = fileRepo.dao.getInvoiceWithItems(saleId)!!

            val status = DeviceLocalBackupManager.persistInstantDeviceSnapshot(
                context = context,
                repo = fileRepo,
                triggerReason = "اعتماد فاتورة بيع"
            )
            assertTrue(status.internalSnapshotExists)
            assertTrue(status.internalSnapshotBytes > 0L)
            assertTrue(snapshotFile.exists())

            val rawBytes = DeviceLocalBackupManager.readLatestInternalSnapshotBytes(context)
            assertNotNull(rawBytes)
            assertTrue(AccountingEngine.isGzipBytes(rawBytes!!))

            // Simulate clearing transactional DB and restoring directly from internal device snapshot
            fileRepo.resetTransactionalDatabase("1234", owner)
            assertTrue(fileDb.accountingDao().getAllInvoices().isEmpty())

            fileRepo.importBackupBytes(rawBytes, null, owner)
            val restoredInvoices = fileDb.accountingDao().getAllInvoices()
            assertTrue(restoredInvoices.any { it.invoiceNumber == postedSale.invoice.invoiceNumber && it.netTotal == 75.0 })
            assertTrue(fileDb.accountingDao().getAllProducts().any { it.name == "مفك براغي مغناطيسي" })
        } finally {
            fileDb.close()
            listOf(dbFile, walFile, shmFile, snapshotFile).forEach { if (it.exists()) it.delete() }
        }
    }
}
