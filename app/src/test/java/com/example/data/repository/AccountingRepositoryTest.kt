package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CashTransactionEntity
import com.example.data.local.CashTxType
import com.example.data.local.CostingMethod
import com.example.data.local.InvoiceStatus
import com.example.data.local.InvoiceType
import com.example.data.local.MamoDatabase
import com.example.data.local.MovementType
import com.example.data.local.PartyType
import com.example.data.local.PaymentMethod
import com.example.data.local.ProductEntity
import com.example.data.local.StaffRole
import com.example.data.local.StaffUserEntity
import com.example.data.remote.SyncedPartyDoc
import com.example.data.remote.SyncedProductDoc
import com.example.utils.ArabicFormatters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AccountingRepositoryTest {

    private lateinit var db: MamoDatabase
    private lateinit var repo: AccountingRepository
    private lateinit var owner: StaffUserEntity

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun freshDatabase_containsZeroProductsPartiesOrInvoices() = runBlocking {
        assertTrue(repo.products.first().isEmpty())
        assertTrue(repo.parties.first().isEmpty())
        assertTrue(repo.invoices.first().isEmpty())
        assertEquals(0.0, repo.cashboxBalance.first(), 0.001)
    }

    @Test
    fun purchaseFlow_updatesWeightedAverageCostStockCashboxAndPayables() = runBlocking {
        // Seed initial capital into cashbox
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 50000.0, "رأس مال افتتاحي", owner)

        val productId = repo.saveProduct(
            productId = null,
            name = "مطرقة فولاذية",
            category = "عدد يدوية",
            barcode = "1001",
            costPrice = 100.0,
            sellingPrice = 180.0,
            quantity = 10.0,
            minStockLevel = 3.0,
            actingStaff = owner
        )

        val supplierId = repo.saveParty(null, PartyType.SUPPLIER, "مؤسسة الشام التجارية", "099111222", owner)

        // Purchase 10 more units at 140.0 with partial payment 600.0 (total 1400.0, remaining 800.0)
        repo.postPurchaseInvoice(
            lines = listOf(
                InvoiceLineDraft(
                    productId = productId,
                    productName = "مطرقة فولاذية",
                    quantity = 10.0,
                    unitPrice = 140.0
                )
            ),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.PARTIAL,
            paidAmountInput = 600.0,
            partyId = supplierId,
            supplierName = "",
            supplierPhone = "",
            notes = "شراء دفعة ثانية",
            dueDateMs = null,
            purchaseDateMs = System.currentTimeMillis(),
            actingStaff = owner
        )

        val product = repo.dao.getProductById(productId)!!
        assertEquals(20.0, product.quantity, 0.001)
        assertEquals(120.0, product.averageCost, 0.001) // (10*100 + 10*140)/20
        assertEquals(140.0, product.lastPurchaseCost, 0.001)

        val supplier = repo.dao.getPartyById(supplierId)!!
        assertEquals(1400.0, supplier.totalAmount, 0.001)
        assertEquals(600.0, supplier.paidAmount, 0.001)
        assertEquals(800.0, supplier.balanceDue, 0.001)

        // Cashbox: 50000 - 600 = 49400
        assertEquals(49400.0, repo.dao.getCashboxBalanceSync(), 0.001)
    }

    @Test
    fun saleAndCancelFlow_reversesStockCashDebtAndPreventsDoubleCancel() = runBlocking {
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 10000.0, "رأس مال", owner)

        val productId = repo.saveProduct(
            productId = null,
            name = "مفتاح إنجليزي",
            category = "عدد يدوية",
            barcode = "2002",
            costPrice = 200.0,
            sellingPrice = 350.0,
            quantity = 10.0,
            minStockLevel = 2.0,
            actingStaff = owner
        )

        val customerId = repo.saveParty(null, PartyType.CUSTOMER, "ورشة النور", "099333444", owner)

        // Sell 4 units @ 350 with 100 invoice discount => netTotal = 1300, partial paid = 500, debt = 800
        val saleId = repo.postSaleInvoice(
            lines = listOf(
                InvoiceLineDraft(
                    productId = productId,
                    productName = "مفتاح إنجليزي",
                    quantity = 4.0,
                    unitPrice = 350.0
                )
            ),
            invoiceDiscount = 100.0,
            paymentMethod = PaymentMethod.PARTIAL,
            paidAmountInput = 500.0,
            partyId = customerId,
            customerName = "",
            customerPhone = "",
            notes = "بيع آجل جزئي",
            dueDateMs = null,
            actingStaff = owner
        )

        val saleWithItems = repo.dao.getInvoiceWithItems(saleId)!!
        assertEquals(InvoiceStatus.POSTED, saleWithItems.invoice.status)
        assertEquals(1300.0, saleWithItems.invoice.netTotal, 0.001)
        assertEquals(800.0, saleWithItems.invoice.totalCost, 0.001) // 4 * 200
        assertEquals(6.0, repo.dao.getProductById(productId)!!.quantity, 0.001)
        assertEquals(10500.0, repo.dao.getCashboxBalanceSync(), 0.001)
        assertEquals(800.0, repo.dao.getPartyById(customerId)!!.balanceDue, 0.001)

        // Cancel invoice via reversing entries
        repo.cancelInvoice(saleId, "إرجاع كامل البضاعة", owner)

        val cancelled = repo.dao.getInvoiceWithItems(saleId)!!
        assertEquals(InvoiceStatus.CANCELLED, cancelled.invoice.status)
        assertEquals(10.0, repo.dao.getProductById(productId)!!.quantity, 0.001)
        assertEquals(10000.0, repo.dao.getCashboxBalanceSync(), 0.001)
        assertEquals(0.0, repo.dao.getPartyById(customerId)!!.balanceDue, 0.001)

        // Verify double cancellation fails
        var doubleCancelFailed = false
        try {
            repo.cancelInvoice(saleId, "محاولة ثانية", owner)
        } catch (e: IllegalStateException) {
            doubleCancelFailed = true
        }
        assertTrue(doubleCancelFailed)

        // Verify reversal movement and audit logs exist
        val movements = repo.stockMovements.first()
        assertTrue(movements.any { it.movementType == MovementType.REVERSAL && it.quantityDelta == 4.0 })
        val logs = repo.auditLogs.first()
        assertTrue(logs.any { it.action.contains("إلغاء فاتورة") })
    }

    @Test
    fun editSaleInvoice_cancelsOriginalAndPostsReplacementAtomically() = runBlocking {
        val productId = repo.saveProduct(
            productId = null,
            name = "براغي صلب علبة",
            category = "براغي ومسامير",
            barcode = "3003",
            costPrice = 50.0,
            sellingPrice = 90.0,
            quantity = 20.0,
            minStockLevel = 5.0,
            actingStaff = owner
        )

        val firstId = repo.postSaleInvoice(
            lines = listOf(InvoiceLineDraft(productId, "براغي صلب علبة", 5.0, 90.0)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 450.0,
            partyId = null,
            customerName = "زبون نقدي",
            customerPhone = "",
            notes = "",
            dueDateMs = null,
            actingStaff = owner
        )
        assertEquals(15.0, repo.dao.getProductById(productId)!!.quantity, 0.001)

        val secondId = repo.editSaleInvoice(
            invoiceId = firstId,
            newLines = listOf(InvoiceLineDraft(productId, "براغي صلب علبة", 3.0, 90.0)),
            newDiscount = 20.0,
            newPaymentMethod = PaymentMethod.CASH,
            newPaidAmount = 250.0,
            newCustomerName = "زبون نقدي",
            newCustomerPhone = "",
            newNotes = "تم تعديل الكمية",
            newDueDateMs = null,
            actingStaff = owner
        )

        assertEquals(InvoiceStatus.CANCELLED, repo.dao.getInvoiceWithItems(firstId)!!.invoice.status)
        val updatedInv = repo.dao.getInvoiceWithItems(secondId)!!.invoice
        assertEquals(InvoiceStatus.POSTED, updatedInv.status)
        assertEquals(250.0, updatedInv.netTotal, 0.001)
        assertEquals(17.0, repo.dao.getProductById(productId)!!.quantity, 0.001)
        assertEquals(250.0, repo.dao.getCashboxBalanceSync(), 0.001)
    }

    @Test
    fun cashboxOverdraftAndDebtOverpayment_areRejected() = runBlocking {
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 500.0, "رصيد أولي", owner)

        var expenseOverdraftBlocked = false
        try {
            repo.recordExpense("الكهرباء", 800.0, "فاتورة كهرباء", PaymentMethod.CASH, System.currentTimeMillis(), owner)
        } catch (e: IllegalStateException) {
            expenseOverdraftBlocked = true
        }
        assertTrue(expenseOverdraftBlocked)
        assertEquals(500.0, repo.dao.getCashboxBalanceSync(), 0.001)

        // Strict reconciliation check
        val txSum = repo.cashTransactions.first().sumOf { it.amountSigned }
        assertTrue(AccountingEngine.isCashboxReconciled(repo.dao.getCashboxBalanceSync(), txSum))

        // Credit sale -> debt collection overpayment guard
        val productId = repo.saveProduct(null, "قفل نحاسي", "أقفال", "", 100.0, 150.0, 5.0, 1.0, owner)
        val customerId = repo.saveParty(null, PartyType.CUSTOMER, "أبو عمر", "", owner)
        repo.postSaleInvoice(
            lines = listOf(InvoiceLineDraft(productId, "قفل نحاسي", 2.0, 150.0)),
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

        var overpayBlocked = false
        try {
            repo.recordDebtPayment(customerId, 500.0, "دفع زائد", owner)
        } catch (e: IllegalArgumentException) {
            overpayBlocked = true
        }
        assertTrue(overpayBlocked)

        repo.recordDebtPayment(customerId, 120.0, "دفعة أولى", owner)
        assertEquals(180.0, repo.dao.getPartyById(customerId)!!.balanceDue, 0.001)
        assertEquals(620.0, repo.dao.getCashboxBalanceSync(), 0.001)
    }

    @Test
    fun stocktakeAdjustment_requiresReasonAndLogsAudit() = runBlocking {
        val productId = repo.saveProduct(null, "اسطوانة قص", "مستلزمات", "", 40.0, 70.0, 15.0, 4.0, owner)

        var blankReasonRejected = false
        try {
            repo.adjustProductStock(productId, 12.0, "   ", owner)
        } catch (e: IllegalArgumentException) {
            blankReasonRejected = true
        }
        assertTrue(blankReasonRejected)

        repo.adjustProductStock(productId, 12.0, "تلف 3 قطع أثناء الجرد", owner)
        assertEquals(12.0, repo.dao.getProductById(productId)!!.quantity, 0.001)
        val movements = repo.stockMovements.first()
        assertTrue(movements.any { it.movementType == MovementType.ADJUSTMENT && it.quantityDelta == -3.0 && it.reason.contains("تلف") })
    }

    @Test
    fun jsonBackupAndRestore_preservesAllDataAndSettings() = runBlocking {
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 2500.0, "إيداع", owner)
        val pId = repo.saveProduct(null, "مثقاب كهربائي", "أدوات كهربائية", "999", 800.0, 1200.0, 4.0, 1.0, owner)
        repo.postSaleInvoice(
            lines = listOf(InvoiceLineDraft(pId, "مثقاب كهربائي", 1.0, 1200.0)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 1200.0,
            partyId = null,
            customerName = "زبون نقدي",
            customerPhone = "",
            notes = "",
            dueDateMs = null,
            actingStaff = owner
        )
        repo.updateStoreSettings(
            repo.dao.getStoreSettings()!!.copy(costingMethod = CostingMethod.LAST_COST, currencyCode = "USD", currencySymbol = "$"),
            owner
        )

        val backupJson = repo.exportJsonBackup()
        assertFalse(backupJson.isBlank())

        // Restore into database and verify integrity
        repo.importJsonBackup(backupJson, owner)

        assertEquals(3700.0, repo.dao.getCashboxBalanceSync(), 0.001)
        assertEquals(1, repo.products.first().size)
        assertEquals(3.0, repo.products.first().first().quantity, 0.001)
        assertEquals(1, repo.invoices.first().size)
        assertEquals(InvoiceType.SALE, repo.invoices.first().first().invoice.invoiceType)
        assertEquals(CostingMethod.LAST_COST, repo.dao.getStoreSettings()!!.costingMethod)
    }

    @Test
    fun adminDatabaseControls_integrityArchiveResyncCloudMergeAndPinReset_workAccurately() = runBlocking {
        repo.recordCashMovement(CashTxType.CAPITAL_IN, 4000.0, "رأس مال", owner)
        val pId = repo.saveProduct(null, "مفك براغي", "عدد", "555", 20.0, 40.0, 10.0, 2.0, owner)
        repo.postSaleInvoice(
            lines = listOf(InvoiceLineDraft(pId, "مفك براغي", 2.0, 40.0)),
            invoiceDiscount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            paidAmountInput = 80.0,
            partyId = null,
            customerName = "زبون نقدي",
            customerPhone = "",
            notes = "",
            dueDateMs = null,
            actingStaff = owner
        )

        val integrity = repo.runDatabaseIntegrityReport(owner)
        assertTrue(integrity.isCashboxReconciled)
        assertTrue(integrity.isEquityBalanced)
        assertEquals(0, integrity.negativeStockProductsCount)
        assertTrue(integrity.unsyncedTotalCount > 0)

        // Archive & unarchive product
        repo.setProductArchived(pId, true, owner)
        assertTrue(repo.products.first().isEmpty())
        assertEquals(1, repo.allProductsForAdmin.first().size)
        repo.setProductArchived(pId, false, owner)
        assertEquals(1, repo.products.first().size)

        // Mark all synced then mark all unsynced via admin resync tool
        val prodSyncId = repo.dao.getProductById(pId)!!.syncId
        repo.dao.markProductSynced(prodSyncId)
        assertTrue(repo.dao.getProductById(pId)!!.isSynced)
        repo.markAllRecordsForResync(owner)
        assertFalse(repo.dao.getProductById(pId)!!.isSynced)

        // Merge missing items from cloud without duplicating existing syncIds
        val (addedProd, addedParties) = repo.mergeMissingFromCloud(
            cloudProducts = listOf(
                SyncedProductDoc(syncId = prodSyncId, name = "مفك براغي", category = "عدد", averageCost = 20.0, sellingPrice = 40.0, quantity = 8.0, minStockLevel = 2.0),
                SyncedProductDoc(syncId = "prod_cloud_only", name = "كماشة", category = "عدد", averageCost = 50.0, sellingPrice = 85.0, quantity = 6.0, minStockLevel = 2.0)
            ),
            cloudParties = listOf(
                SyncedPartyDoc(syncId = "party_cloud_only", partyType = PartyType.CUSTOMER, name = "عميل سحابي", phone = "099000111")
            ),
            actingStaff = owner
        )
        assertEquals(1, addedProd)
        assertEquals(1, addedParties)

        // Wrong PIN rejected for database reset
        var wrongPinRejected = false
        try {
            repo.resetTransactionalDatabase("0000", owner)
        } catch (e: IllegalArgumentException) {
            wrongPinRejected = true
        }
        assertTrue(wrongPinRejected)

        // Correct PIN clears transactions/products/parties while preserving owner & settings
        repo.resetTransactionalDatabase("1234", owner)
        assertTrue(repo.products.first().isEmpty())
        assertTrue(repo.parties.first().isEmpty())
        assertTrue(repo.invoices.first().isEmpty())
        assertEquals(0.0, repo.dao.getCashboxBalanceSync(), 0.001)
        assertEquals(1, repo.staffUsers.first().size)
        assertTrue(repo.auditLogs.first().any { it.action.contains("تصفير قاعدة البيانات") })
    }

    @Test
    fun loginRegisterAndPasswordRecovery_workEndToEnd() = runBlocking {
        // Authenticate existing owner by name and password
        val authedOwner = repo.authenticateByCredentials("أحمد نوار المامو", "1234", null, null)
        assertEquals(owner.id, authedOwner?.id)

        // Wrong password returns null
        assertEquals(null, repo.authenticateByCredentials("أحمد نوار المامو", "wrong", null, null))

        // Registering new cashier requires existing owner verification
        var blockedWithoutPin = false
        try {
            repo.registerAccountFromLogin("كاشير خالد", "CASHIER", "5678", "0000")
        } catch (e: IllegalArgumentException) {
            blockedWithoutPin = true
        }
        assertTrue(blockedWithoutPin)

        val cashier = repo.registerAccountFromLogin("كاشير خالد", "CASHIER", "5678", "1234")
        assertEquals("CASHIER", cashier.role)
        assertEquals(cashier.id, repo.authenticateByCredentials("كاشير خالد", "5678", null, null)?.id)

        // Password recovery updates hash and logs audit entry
        repo.resetPasswordByRecovery(cashier.id, "9999", "اعتماد المدير")
        assertEquals(null, repo.authenticateByCredentials("كاشير خالد", "5678", null, null))
        assertEquals(cashier.id, repo.authenticateByCredentials("كاشير خالد", "9999", null, null)?.id)
        assertTrue(repo.auditLogs.first().any { it.action == "استعادة كلمة المرور" })
    }

    @Test
    fun roomCoreEntities_productTransactionUser_fullCrudThroughRepositoryAndDaos() = runBlocking {
        // 1. Product CRUD
        val productId = repo.insertProduct(
            ProductEntity(
                name = "مفك نجمة",
                category = "عدد يدوية",
                barcode = "7770",
                averageCost = 35.0,
                lastPurchaseCost = 35.0,
                sellingPrice = 60.0,
                quantity = 12.0,
                minStockLevel = 3.0
            )
        )
        val loadedProduct = repo.getProductById(productId)!!
        assertEquals("مفك نجمة", loadedProduct.name)
        assertEquals("مفك نجمة", repo.observeProductById(productId).first()?.name)

        repo.updateProduct(loadedProduct.copy(sellingPrice = 75.0, quantity = 15.0))
        assertEquals(75.0, repo.getProductById(productId)!!.sellingPrice, 0.001)

        repo.deleteProductById(productId)
        assertNull(repo.getProductById(productId))

        // 2. Transaction CRUD
        val txId = repo.insertTransaction(
            CashTransactionEntity(
                txType = CashTxType.CAPITAL_IN,
                amountSigned = 1500.0,
                description = "إيداع تجريبي",
                staffName = owner.name
            )
        )
        val loadedTx = repo.getTransactionById(txId)!!
        assertEquals(1500.0, loadedTx.amountSigned, 0.001)
        assertEquals(1500.0, repo.observeTransactionById(txId).first()!!.amountSigned, 0.001)

        repo.updateTransaction(loadedTx.copy(amountSigned = 2200.0, description = "إيداع محدث"))
        assertEquals(2200.0, repo.getTransactionById(txId)!!.amountSigned, 0.001)
        assertEquals(2200.0, repo.cashboxBalance.first(), 0.001)

        repo.deleteTransaction(repo.getTransactionById(txId)!!)
        assertNull(repo.getTransactionById(txId))
        assertEquals(0.0, repo.cashboxBalance.first(), 0.001)

        // 3. User CRUD
        val userId = repo.insertUser(
            StaffUserEntity(
                name = "موظف تجريبي",
                role = StaffRole.CASHIER,
                pinHash = AccountingEngine.hashPin("4321"),
                canViewProfits = false,
                canCancelInvoices = false,
                canManageInventory = false,
                canManageExpenses = false,
                canManageSettings = false,
                isActive = true
            )
        )
        val loadedUser = repo.getUserById(userId)!!
        assertEquals("موظف تجريبي", loadedUser.name)
        assertEquals(2, repo.getAllUsers().size)

        repo.updateUser(loadedUser.copy(name = "محاسب محدث", role = StaffRole.ACCOUNTANT))
        assertEquals("محاسب محدث", repo.observeUserById(userId).first()?.name)

        repo.deleteUserById(userId)
        assertNull(repo.getUserById(userId))
        assertEquals(1, repo.getAllUsers().size)
    }

    @Test
    fun arabicDigitsParser_normalizesIndicDigitsAndDecimalSeparators() {
        assertEquals(25.0, ArabicFormatters.parseDoubleOrNull("٢٥")!!, 0.0001)
        assertEquals(12.5, ArabicFormatters.parseDoubleOrNull("١٢٫٥")!!, 0.0001)
        assertEquals(10.75, ArabicFormatters.parseDoubleOrNull("10,75")!!, 0.0001)
        assertEquals(30L, ArabicFormatters.parseLongOrNull("٣٠"))
        assertNull(ArabicFormatters.parseDoubleOrNull("غير رقمي"))
    }
}
