package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CashTxType
import com.example.data.local.InvoiceStatus
import com.example.data.local.MamoDatabase
import com.example.data.local.MeasureUnit
import com.example.data.local.PartyType
import com.example.data.local.PaymentMethod
import com.example.data.local.PricingTier
import com.example.data.local.StaffRole
import com.example.data.local.StaffUserEntity
import com.example.data.local.StoreSettingsEntity
import com.example.data.repository.AccountingEngine
import com.example.data.repository.AccountingRepository
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.PeriodFilter
import com.example.ui.MamoMainUiState
import com.example.ui.MamoReportCalculators
import kotlinx.coroutines.flow.first
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
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UltimateAccountingScenariosTest {

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

    private suspend fun buildUiStateSnapshot(): MamoMainUiState {
        return MamoMainUiState(
            settings = repo.dao.getStoreSettings() ?: StoreSettingsEntity(),
            staffUsers = repo.dao.getAllStaffUsers(),
            activeStaff = owner,
            products = repo.dao.getAllProducts().filter { !it.isArchived },
            parties = repo.dao.getAllParties(),
            invoices = repo.invoices.first(),
            stockMovements = repo.dao.getAllStockMovements(),
            cashTransactions = repo.dao.getAllCashTransactions(),
            cashboxBalance = repo.dao.getCashboxBalanceSync(),
            expenses = repo.dao.getAllExpenses(),
            debtPayments = repo.dao.getAllDebtPayments(),
            auditLogs = repo.dao.getAllAuditLogs()
        )
    }

    @Test
    fun scenario01_to_08_multiUnitPurchaseRetailWholesaleDebtsExpensesAndDrawings() = runBlocking {
        // Seed capital
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 50_000.0, "رأس مال افتتاحي", owner)

        val prodId = repo.saveProduct(
            productId = null,
            name = "مفصلة باب ستانلس",
            category = "خردوات أبواب",
            barcode = "6290001000015",
            costPrice = 0.0,
            sellingPrice = 25.0,
            quantity = 0.0,
            minStockLevel = 10.0,
            baseUnit = MeasureUnit.PIECE,
            wholesalePrice = 20.0,
            minSellingPrice = 18.0,
            actingStaff = owner
        )
        repo.saveProductUnit(null, prodId, MeasureUnit.CARTON, 12.0, 280.0, 230.0, "6290001000022", owner)
        repo.saveProductUnit(null, prodId, MeasureUnit.DOZEN, 12.0, 275.0, 230.0, "6290001000039", owner)

        // Scenario 1: Buy 10 cartons x 12 pcs @ 180/carton + 60 extra shipping = 1860 => 120 base qty @ 15.5 weighted avg cost
        val supplierId = repo.saveParty(null, PartyType.SUPPLIER, "شركة المورد الحديث", "099111222", owner)
        repo.postPurchaseInvoice(
            lines = listOf(
                InvoiceLineDraft(
                    productId = prodId,
                    productName = "مفصلة باب ستانلس",
                    quantity = 10.0,
                    unitPrice = 180.0,
                    unitName = MeasureUnit.CARTON,
                    conversionFactor = 12.0
                )
            ),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 1860.0,
            partyId = supplierId,
            supplierName = "",
            supplierPhone = "",
            notes = "شراء 10 كراتين",
            dueDateMs = null,
            purchaseDateMs = System.currentTimeMillis(),
            actingStaff = owner,
            extraCosts = 60.0
        )
        var product = repo.dao.getProductById(prodId)!!
        assertEquals(120.0, product.quantity, 0.001)
        assertEquals(15.5, product.averageCost, 0.001)

        // Scenario 2: Sell 5 pcs retail @ 25 = 125 cash => stock = 115
        repo.postSaleInvoice(
            lines = listOf(
                InvoiceLineDraft(
                    productId = prodId,
                    productName = product.name,
                    quantity = 5.0,
                    unitPrice = 25.0,
                    unitName = MeasureUnit.PIECE,
                    conversionFactor = 1.0,
                    pricingTier = PricingTier.RETAIL
                )
            ),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 125.0,
            partyId = null,
            customerName = "زبون نقدي",
            customerPhone = "",
            notes = "",
            dueDateMs = null,
            actingStaff = owner
        )
        product = repo.dao.getProductById(prodId)!!
        assertEquals(115.0, product.quantity, 0.001)

        // Scenario 3: Sell 1 dozen (12 pcs) wholesale => resolve quote 230, stock = 103
        val wholesaleQuote = repo.resolveProductPriceForLine(prodId, MeasureUnit.DOZEN, PricingTier.WHOLESALE, null, 1.0)
        assertEquals(230.0, wholesaleQuote.unitPrice, 0.001)
        assertEquals(PricingTier.WHOLESALE, wholesaleQuote.pricingTier)

        repo.postSaleInvoice(
            lines = listOf(
                InvoiceLineDraft(
                    productId = prodId,
                    productName = product.name,
                    quantity = 1.0,
                    unitPrice = wholesaleQuote.unitPrice,
                    unitName = MeasureUnit.DOZEN,
                    conversionFactor = 12.0,
                    pricingTier = PricingTier.WHOLESALE
                )
            ),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 230.0,
            partyId = null,
            customerName = "ورشة الوفاء",
            customerPhone = "",
            notes = "",
            dueDateMs = null,
            actingStaff = owner
        )
        product = repo.dao.getProductById(prodId)!!
        assertEquals(103.0, product.quantity, 0.001)

        // Scenario 4: Credit sale + partial customer debt payment
        val customerId = repo.saveParty(null, PartyType.CUSTOMER, "مقاولات النجاح", "099444555", owner)
        repo.postSaleInvoice(
            lines = listOf(InvoiceLineDraft(prodId, product.name, 3.0, 25.0)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CREDIT,
            paidAmountInput = 0.0,
            partyId = customerId,
            customerName = "",
            customerPhone = "",
            notes = "",
            dueDateMs = null,
            actingStaff = owner
        )
        assertEquals(75.0, repo.dao.getPartyById(customerId)!!.balanceDue, 0.001)
        assertEquals(100.0, repo.dao.getProductById(prodId)!!.quantity, 0.001)

        repo.recordDebtPayment(customerId, 45.0, "دفعة أولى من العميل", owner)
        assertEquals(30.0, repo.dao.getPartyById(customerId)!!.balanceDue, 0.001)

        // Scenario 5: Credit purchase invoice (supplier balanceDue = 155, stock = 110)
        repo.postPurchaseInvoice(
            lines = listOf(InvoiceLineDraft(prodId, product.name, 10.0, 15.5)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CREDIT,
            paidAmountInput = 0.0,
            partyId = supplierId,
            supplierName = "",
            supplierPhone = "",
            notes = "شراء آجل",
            dueDateMs = null,
            purchaseDateMs = System.currentTimeMillis(),
            actingStaff = owner
        )
        assertEquals(155.0, repo.dao.getPartyById(supplierId)!!.balanceDue, 0.001)
        assertEquals(110.0, repo.dao.getProductById(prodId)!!.quantity, 0.001)

        // Scenario 6: Partial supplier debt payment (55 paid -> 100 remaining)
        repo.recordDebtPayment(supplierId, 55.0, "دفعة للمورد", owner)
        assertEquals(100.0, repo.dao.getPartyById(supplierId)!!.balanceDue, 0.001)

        // Scenario 7: Cash operating expense (50.0)
        repo.recordExpense("الكهرباء", 50.0, "فاتورة كهرباء المحل", PaymentMethod.CASH, System.currentTimeMillis(), owner)

        // Scenario 8: Manager personal drawing (100.0) excluded from operating expenses
        repo.recordCashMovement(CashTxType.PERSONAL_DRAWING, 100.0, "سحب شخصي للمدير", owner)

        // Cashbox check: 50000 - 1860 + 125 + 230 + 45 - 55 - 50 - 100 = 48335.0
        assertEquals(48_335.0, repo.dao.getCashboxBalanceSync(), 0.001)

        val snap = buildUiStateSnapshot()
        val pl = MamoReportCalculators.computeProfitLoss(snap, emptyList(), PeriodFilter("الكل", 0L, Long.MAX_VALUE))
        assertEquals(50.0, pl.operatingExpenses, 0.001)
        val cap = MamoReportCalculators.computeCapitalSummary(snap)
        assertEquals(100.0, cap.personalDrawings, 0.001)
    }

    @Test
    fun scenario09_dailyCashboxClosingVarianceAndPeriodLockGuard() = runBlocking {
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 1000.0, "رصيد بداية اليوم", owner)
        val pId = repo.saveProduct(null, "قفل أمان", "أقفال", "501", 40.0, 60.0, 10.0, 2.0, owner)
        val saleIdBeforeClosing = repo.postSaleInvoice(
            lines = listOf(InvoiceLineDraft(pId, "قفل أمان", 2.0, 60.0)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 120.0,
            partyId = null,
            customerName = "زبون",
            customerPhone = "",
            notes = "",
            dueDateMs = null,
            actingStaff = owner
        )
        // Expected cash = 1120. Counted = 1105 => variance = -15
        val closing = repo.closeCashboxDay(1L, 1105.0, "جرد نهاية الوردية", lockPeriod = true, actingStaff = owner)
        assertEquals(1120.0, closing.expectedBalance, 0.001)
        assertEquals(1105.0, closing.actualCountedBalance, 0.001)
        assertEquals(-15.0, closing.varianceAmount, 0.001)
        assertEquals(1105.0, repo.dao.getCashboxBalanceSync(), 0.001)

        // Accountant (even with canCancelInvoices=true) cannot cancel invoice from locked shift
        val accountant = repo.registerAccountFromLogin("محاسب سامي", StaffRole.ACCOUNTANT, "7777", "1234")
        var cancelBlocked = false
        try {
            repo.cancelInvoice(saleIdBeforeClosing, "محاولة إلغاء بعد الإغلاق", accountant)
        } catch (_: IllegalStateException) {
            cancelBlocked = true
        }
        assertTrue(cancelBlocked)

        // Backdated purchase inside locked closing range without overrideReason is blocked
        val lockedDateMs = (closing.periodEndMs - 500L).coerceAtLeast(closing.periodStartMs)
        var backdatedBlocked = false
        try {
            repo.postPurchaseInvoice(
                lines = listOf(InvoiceLineDraft(pId, "قفل أمان", 1.0, 40.0)),
                invoiceDiscount = 0.0,
                paymentMethod = PaymentMethod.CASH,
                paidAmountInput = 40.0,
                partyId = null,
                supplierName = "مورد",
                supplierPhone = "",
                notes = "",
                dueDateMs = null,
                purchaseDateMs = lockedDateMs,
                actingStaff = owner
            )
        } catch (_: IllegalStateException) {
            backdatedBlocked = true
        }
        assertTrue(backdatedBlocked)

        // Owner override with explicit reason succeeds on locked period
        val overrideId = repo.postPurchaseInvoice(
            lines = listOf(InvoiceLineDraft(pId, "قفل أمان", 1.0, 40.0)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 40.0,
            partyId = null,
            supplierName = "مورد طارئ",
            supplierPhone = "",
            notes = "إدخال متأخر",
            dueDateMs = null,
            purchaseDateMs = lockedDateMs,
            actingStaff = owner,
            overrideReason = "تصحيح معتمد من المدير العام"
        )
        assertTrue(overrideId > 0L)
    }

    @Test
    fun scenario10_salesReturnAndPurchaseReturnRestoreStockCashAndJournals() = runBlocking {
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 5000.0, "رأس مال", owner)
        val pId = repo.saveProduct(null, "شريط قياس 5م", "عدد", "601", 30.0, 50.0, 0.0, 2.0, owner)
        val supId = repo.saveParty(null, PartyType.SUPPLIER, "توريد العدد", "", owner)
        val custId = repo.saveParty(null, PartyType.CUSTOMER, "ورشة البناء", "", owner)

        val purchaseId = repo.postPurchaseInvoice(
            lines = listOf(InvoiceLineDraft(pId, "شريط قياس 5م", 10.0, 30.0)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CREDIT,
            paidAmountInput = 0.0,
            partyId = supId,
            supplierName = "",
            supplierPhone = "",
            notes = "",
            dueDateMs = null,
            purchaseDateMs = System.currentTimeMillis(),
            actingStaff = owner
        )
        assertEquals(10.0, repo.dao.getProductById(pId)!!.quantity, 0.001)
        assertEquals(300.0, repo.dao.getPartyById(supId)!!.balanceDue, 0.001)

        val saleId = repo.postSaleInvoice(
            lines = listOf(InvoiceLineDraft(pId, "شريط قياس 5م", 4.0, 50.0)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 200.0,
            partyId = custId,
            customerName = "",
            customerPhone = "",
            notes = "",
            dueDateMs = null,
            actingStaff = owner
        )
        assertEquals(6.0, repo.dao.getProductById(pId)!!.quantity, 0.001)
        assertEquals(5200.0, repo.dao.getCashboxBalanceSync(), 0.001)

        // Return 2 sold pcs with 100 cash refund -> stock = 8, cash = 5100
        repo.postSaleReturn(
            originalInvoiceId = saleId,
            productId = pId,
            unitName = MeasureUnit.PIECE,
            conversionFactor = 1.0,
            returnedQty = 2.0,
            refundCashAmount = 100.0,
            reason = "مرتجع عميل غير مطابق",
            actingStaff = owner
        )
        assertEquals(8.0, repo.dao.getProductById(pId)!!.quantity, 0.001)
        assertEquals(5100.0, repo.dao.getCashboxBalanceSync(), 0.001)

        // Return 3 purchased pcs to supplier (deduct from supplier debt: 3 * 30 = 90) -> stock = 5, supplier debt = 210
        repo.postPurchaseReturn(
            originalInvoiceId = purchaseId,
            productId = pId,
            unitName = MeasureUnit.PIECE,
            conversionFactor = 1.0,
            returnedQty = 3.0,
            refundCashAmount = 0.0,
            reason = "تبديل بضاعة مع المورد",
            actingStaff = owner
        )
        assertEquals(5.0, repo.dao.getProductById(pId)!!.quantity, 0.001)
        assertEquals(210.0, repo.dao.getPartyById(supId)!!.balanceDue, 0.001)

        val journals = repo.uDao.getAllJournalEntries()
        assertTrue(journals.isNotEmpty())
        assertTrue(journals.all { abs(it.totalDebit - it.totalCredit) < 0.01 })
    }

    @Test
    fun scenario11_offlineSyncQueueIsIdempotentPerEntitySyncId() = runBlocking {
        val pId = repo.saveProduct(null, "قاطع كهرباء 32A", "كهربائيات", "701", 40.0, 70.0, 5.0, 1.0, owner)
        val prod = repo.dao.getProductById(pId)!!
        // Edit same product repeatedly while offline
        repo.saveProduct(pId, "قاطع كهرباء 32A مطور", "كهربائيات", "701", 42.0, 75.0, 5.0, 1.0, owner)
        repo.adjustProductStock(pId, 7.0, "جرد فعلي", owner)

        val pendingForProd = repo.uDao.getPendingSyncItems().filter { it.entitySyncId == prod.syncId }
        assertEquals(1, pendingForProd.size)
        assertEquals("PENDING", pendingForProd.first().status)
    }

    @Test
    fun scenario12_encryptedAes256BackupAndRestoreWithWrongAndValidPassphrases() = runBlocking {
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 3000.0, "رأس مال", owner)
        repo.saveProduct(null, "لحام كهربائي 200A", "معدات لحام", "801", 900.0, 1300.0, 2.0, 1.0, owner)

        val (encryptedPayload, backupRecord) = repo.exportEncryptedBackup("Mamo@Secure2026", owner)
        assertTrue(encryptedPayload.contains("MAMO_ENCRYPTED_BACKUP_V1"))
        assertTrue(backupRecord.isEncrypted)
        assertTrue(backupRecord.checksumSha256.isNotBlank())

        // Wrong passphrase must fail
        var wrongRejected = false
        try {
            repo.importBackupContent(encryptedPayload, "WrongPass", owner)
        } catch (_: IllegalArgumentException) {
            wrongRejected = true
        }
        assertTrue(wrongRejected)

        // Wipe products then restore with correct passphrase
        repo.resetTransactionalDatabase("1234", owner)
        assertTrue(repo.products.first().isEmpty())

        repo.importBackupContent(encryptedPayload, "Mamo@Secure2026", owner)
        assertEquals(1, repo.products.first().size)
        assertEquals(3000.0, repo.dao.getCashboxBalanceSync(), 0.001)
    }

    @Test
    fun scenario13_cashierPermissionRestrictionsBlockPriceOverrideStocktakeApprovalAndSettings() = runBlocking {
        val pId = repo.saveProduct(null, "متر ليزر 40م", "أدوات قياس", "901", 300.0, 450.0, 8.0, 2.0, owner)
        val cashier = repo.registerAccountFromLogin("كاشير علي", StaffRole.CASHIER, "5678", "1234")
        assertFalse(cashier.canEditPrices)
        assertFalse(cashier.canApproveStocktake)
        assertFalse(cashier.canManageSettings)

        // Seller cannot override price
        var priceBlocked = false
        try {
            repo.postSaleInvoice(
                lines = listOf(InvoiceLineDraft(pId, "متر ليزر 40م", 1.0, 350.0)),
                invoiceDiscount = 0.0,
                paymentMethod = PaymentMethod.CASH,
                paidAmountInput = 350.0,
                partyId = null,
                customerName = "زبون",
                customerPhone = "",
                notes = "",
                dueDateMs = null,
                actingStaff = cashier
            )
        } catch (_: SecurityException) {
            priceBlocked = true
        }
        assertTrue(priceBlocked)

        // Seller cannot approve stocktake
        val countId = repo.submitStockCount(pId, 7.0, "فرق قطعة", owner)
        var approveBlocked = false
        try {
            repo.approveStockCount(countId, cashier)
        } catch (_: IllegalArgumentException) {
            approveBlocked = true
        }
        assertTrue(approveBlocked)

        // Seller cannot update settings
        var settingsBlocked = false
        try {
            repo.updateStoreSettings(StoreSettingsEntity(storeName = "تعديل غير مصرح"), cashier)
        } catch (_: IllegalArgumentException) {
            settingsBlocked = true
        }
        assertTrue(settingsBlocked)
    }

    @Test
    fun scenario14_balanceSheetAndDoubleEntryJournalsAreAlwaysBalanced() = runBlocking {
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 10_000.0, "رأس مال", owner)
        val pId = repo.saveProduct(null, "كابل كهرباء 2.5مم", "كابلات", "910", 100.0, 160.0, 0.0, 2.0, owner)
        val supId = repo.saveParty(null, PartyType.SUPPLIER, "مصنع الكابلات", "", owner)
        val custId = repo.saveParty(null, PartyType.CUSTOMER, "متعهد كهرباء", "", owner)

        repo.postPurchaseInvoice(
            lines = listOf(InvoiceLineDraft(pId, "كابل كهرباء 2.5مم", 20.0, 100.0)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.PARTIAL,
            paidAmountInput = 1200.0,
            partyId = supId,
            supplierName = "",
            supplierPhone = "",
            notes = "",
            dueDateMs = null,
            purchaseDateMs = System.currentTimeMillis(),
            actingStaff = owner
        )
        repo.postSaleInvoice(
            lines = listOf(InvoiceLineDraft(pId, "كابل كهرباء 2.5مم", 10.0, 160.0)),
            invoiceDiscount = 50.0,
            paymentMethod = PaymentMethod.PARTIAL,
            paidAmountInput = 1000.0,
            partyId = custId,
            customerName = "",
            customerPhone = "",
            notes = "",
            dueDateMs = null,
            actingStaff = owner
        )
        repo.recordExpense("النقل", 150.0, "توصيل", PaymentMethod.CASH, System.currentTimeMillis(), owner)
        repo.recordCashMovement(CashTxType.PERSONAL_DRAWING, 300.0, "سحب شخصي", owner)

        val integrity = repo.runDatabaseIntegrityReport(owner)
        assertTrue(integrity.isCashboxReconciled)
        assertTrue(integrity.isEquityBalanced)
        assertEquals(0, integrity.unbalancedJournalCount)

        val bs = MamoReportCalculators.computeBalanceSheet(buildUiStateSnapshot())
        assertTrue(bs.isBalanced)
        assertEquals(bs.totalAssets, bs.totalLiabilities + bs.totalEquity, 0.5)
    }

    @Test
    fun scenario15_duplicateOcrHashIsBlockedAndRoomMigration1To2WorksCleanly(): Unit = runBlocking {
        val pId = repo.saveProduct(null, "برغي صاج 4سم", "براغي", "950", 10.0, 18.0, 10.0, 2.0, owner)
        val ocrHash = AccountingEngine.sha256Hex("SUPPLIER_A|INV_889|100.00")

        repo.postPurchaseInvoice(
            lines = listOf(InvoiceLineDraft(pId, "برغي صاج 4سم", 5.0, 10.0)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CREDIT,
            paidAmountInput = 0.0,
            partyId = null,
            supplierName = "مورد أ",
            supplierPhone = "",
            notes = "OCR",
            dueDateMs = null,
            purchaseDateMs = System.currentTimeMillis(),
            actingStaff = owner,
            sourceOcrHash = ocrHash
        )

        var duplicateBlocked = false
        try {
            repo.postPurchaseInvoice(
                lines = listOf(InvoiceLineDraft(pId, "برغي صاج 4سم", 5.0, 10.0)),
                invoiceDiscount = 0.0,
                paymentMethod = PaymentMethod.CREDIT,
                paidAmountInput = 0.0,
                partyId = null,
                supplierName = "مورد أ",
                supplierPhone = "",
                notes = "OCR مكرر",
                dueDateMs = null,
                purchaseDateMs = System.currentTimeMillis(),
                actingStaff = owner,
                sourceOcrHash = ocrHash
            )
        } catch (_: IllegalStateException) {
            duplicateBlocked = true
        }
        assertTrue(duplicateBlocked)

        // Verify MIGRATION_1_2 on a real v1 SQLite database file
        val dbFile = File(context.cacheDir, "migration_test_${System.currentTimeMillis()}.db")
        if (dbFile.exists()) dbFile.delete()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(sqLiteDb: SupportSQLiteDatabase) {
                        sqLiteDb.execSQL("CREATE TABLE store_settings (id INTEGER PRIMARY KEY NOT NULL, storeName TEXT NOT NULL, managerName TEXT NOT NULL, currencyCode TEXT NOT NULL, currencySymbol TEXT NOT NULL, costingMethod TEXT NOT NULL, invoiceFooter TEXT NOT NULL, logoUri TEXT NOT NULL, dateFormatPattern TEXT NOT NULL, autoBackupEnabled INTEGER NOT NULL, lastSyncAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)")
                        sqLiteDb.execSQL("CREATE TABLE staff_users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, name TEXT NOT NULL, role TEXT NOT NULL, pinHash TEXT NOT NULL, canViewProfits INTEGER NOT NULL, canCancelInvoices INTEGER NOT NULL, canManageInventory INTEGER NOT NULL, canManageExpenses INTEGER NOT NULL, canManageSettings INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                        sqLiteDb.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, barcode TEXT NOT NULL, averageCost REAL NOT NULL, lastPurchaseCost REAL NOT NULL, sellingPrice REAL NOT NULL, quantity REAL NOT NULL, minStockLevel REAL NOT NULL, isArchived INTEGER NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                        sqLiteDb.execSQL("CREATE TABLE parties (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, partyType TEXT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, totalAmount REAL NOT NULL, paidAmount REAL NOT NULL, balanceDue REAL NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                        sqLiteDb.execSQL("CREATE TABLE invoices (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, invoiceNumber TEXT NOT NULL, invoiceType TEXT NOT NULL, partyId INTEGER, partySyncId TEXT NOT NULL, partyName TEXT NOT NULL, partyPhone TEXT NOT NULL, status TEXT NOT NULL, paymentMethod TEXT NOT NULL, subtotal REAL NOT NULL, discountAmount REAL NOT NULL, netTotal REAL NOT NULL, paidAmount REAL NOT NULL, remainingAmount REAL NOT NULL, totalCost REAL NOT NULL, notes TEXT NOT NULL, staffId INTEGER NOT NULL, staffName TEXT NOT NULL, invoiceDateMs INTEGER NOT NULL, dueDateMs INTEGER, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                        sqLiteDb.execSQL("CREATE TABLE invoice_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceId INTEGER NOT NULL, productId INTEGER NOT NULL, productSyncId TEXT NOT NULL, productName TEXT NOT NULL, quantity REAL NOT NULL, unitPrice REAL NOT NULL, discountAmount REAL NOT NULL, lineTotal REAL NOT NULL, unitCostAtPosting REAL NOT NULL, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON DELETE CASCADE)")
                        sqLiteDb.execSQL("CREATE TABLE stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, productId INTEGER NOT NULL, productSyncId TEXT NOT NULL, productName TEXT NOT NULL, movementType TEXT NOT NULL, quantityDelta REAL NOT NULL, unitCost REAL NOT NULL, balanceAfter REAL NOT NULL, reason TEXT NOT NULL, referenceId TEXT NOT NULL, staffName TEXT NOT NULL, occurredAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                        sqLiteDb.execSQL("CREATE TABLE cash_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, txType TEXT NOT NULL, amountSigned REAL NOT NULL, description TEXT NOT NULL, referenceId TEXT NOT NULL, staffName TEXT NOT NULL, occurredAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                        sqLiteDb.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, paymentMethod TEXT NOT NULL, staffName TEXT NOT NULL, expenseDateMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                        sqLiteDb.execSQL("CREATE TABLE debt_payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, partyId INTEGER NOT NULL, partySyncId TEXT NOT NULL, partyName TEXT NOT NULL, partyType TEXT NOT NULL, invoiceId INTEGER, amount REAL NOT NULL, notes TEXT NOT NULL, staffName TEXT NOT NULL, paymentDateMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                        sqLiteDb.execSQL("CREATE TABLE audit_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, action TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, details TEXT NOT NULL, staffName TEXT NOT NULL, occurredAtMs INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                        sqLiteDb.execSQL("CREATE UNIQUE INDEX index_staff_users_syncId ON staff_users(syncId)")
                        sqLiteDb.execSQL("CREATE UNIQUE INDEX index_products_syncId ON products(syncId)")
                        sqLiteDb.execSQL("CREATE INDEX index_products_barcode ON products(barcode)")
                        sqLiteDb.execSQL("CREATE UNIQUE INDEX index_parties_syncId ON parties(syncId)")
                        sqLiteDb.execSQL("CREATE UNIQUE INDEX index_invoices_syncId ON invoices(syncId)")
                        sqLiteDb.execSQL("CREATE UNIQUE INDEX index_invoices_invoiceNumber ON invoices(invoiceNumber)")
                        sqLiteDb.execSQL("CREATE INDEX index_invoices_partyId ON invoices(partyId)")
                        sqLiteDb.execSQL("CREATE INDEX index_invoice_items_invoiceId ON invoice_items(invoiceId)")
                        sqLiteDb.execSQL("CREATE INDEX index_invoice_items_productId ON invoice_items(productId)")
                        sqLiteDb.execSQL("CREATE UNIQUE INDEX index_stock_movements_syncId ON stock_movements(syncId)")
                        sqLiteDb.execSQL("CREATE INDEX index_stock_movements_productId ON stock_movements(productId)")
                        sqLiteDb.execSQL("CREATE UNIQUE INDEX index_cash_transactions_syncId ON cash_transactions(syncId)")
                        sqLiteDb.execSQL("CREATE UNIQUE INDEX index_expenses_syncId ON expenses(syncId)")
                        sqLiteDb.execSQL("CREATE UNIQUE INDEX index_debt_payments_syncId ON debt_payments(syncId)")
                        sqLiteDb.execSQL("CREATE INDEX index_debt_payments_partyId ON debt_payments(partyId)")
                        sqLiteDb.execSQL("CREATE UNIQUE INDEX index_audit_logs_syncId ON audit_logs(syncId)")
                        sqLiteDb.execSQL("INSERT INTO products (syncId, name, category, barcode, averageCost, lastPurchaseCost, sellingPrice, quantity, minStockLevel, isArchived, createdAtMs, updatedAtMs, isSynced) VALUES ('prod_v1_test', 'منشار خشب قديم', 'عدد', '111222', 50.0, 50.0, 90.0, 4.0, 1.0, 0, 1700000000000, 1700000000000, 0)")
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
        val migratedProd = migratedDb.accountingDao().getProductBySyncId("prod_v1_test")
        assertNotNull(migratedProd)
        assertEquals("منشار خشب قديم", migratedProd!!.name)
        assertEquals(MeasureUnit.PIECE, migratedProd.baseUnit)
        assertEquals(90.0, migratedProd.wholesalePrice, 0.001)
        migratedDb.close()
        assertTrue(dbFile.delete())
    }
}
