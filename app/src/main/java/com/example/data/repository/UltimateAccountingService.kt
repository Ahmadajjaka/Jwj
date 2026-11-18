package com.example.data.repository

import com.example.data.local.AccountingDao
import com.example.data.local.AccountingPeriodEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupRecordEntity
import com.example.data.local.CashAccountEntity
import com.example.data.local.CashAccountType
import com.example.data.local.CashTransactionEntity
import com.example.data.local.CashTxType
import com.example.data.local.CashboxClosingEntity
import com.example.data.local.ChartAccounts
import com.example.data.local.CustomerSpecialPriceEntity
import com.example.data.local.InvoiceStatus
import com.example.data.local.InvoiceType
import com.example.data.local.JournalEntryEntity
import com.example.data.local.JournalEntryLineEntity
import com.example.data.local.MamoDatabase
import com.example.data.local.MeasureUnit
import com.example.data.local.MovementType
import com.example.data.local.PartyType
import com.example.data.local.PricingTier
import com.example.data.local.ProductPriceHistoryEntity
import com.example.data.local.ProductUnitEntity
import com.example.data.local.ReturnEntity
import com.example.data.local.ReturnType
import com.example.data.local.StaffRole
import com.example.data.local.StaffUserEntity
import com.example.data.local.StockCountEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.StoreSettingsEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.local.UltimateDao
import java.util.Locale
import kotlin.math.abs

data class JournalDraftLine(
    val accountCode: String,
    val accountName: String,
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val partyId: Long? = null,
    val notes: String = ""
)

internal class UltimateAccountingService(
    private val db: MamoDatabase,
    private val dao: AccountingDao,
    private val uDao: UltimateDao
) {

    suspend fun ensureDefaultCashAccount(): CashAccountEntity {
        val existing = uDao.getCashAccountById(1L)
        if (existing != null) return existing
        val all = uDao.getAllCashAccounts()
        if (all.isNotEmpty()) return all.first()
        val def = CashAccountEntity(
            id = 1L,
            name = "الصندوق الرئيسي",
            accountType = CashAccountType.CASHBOX,
            isDefault = true
        )
        uDao.insertCashAccount(def)
        return def
    }

    suspend fun getAccountOrDefault(accountId: Long): CashAccountEntity {
        ensureDefaultCashAccount()
        return uDao.getCashAccountById(accountId) ?: ensureDefaultCashAccount()
    }

    suspend fun assertDateNotLocked(
        dateMs: Long,
        actingStaff: StaffUserEntity,
        overrideReason: String = ""
    ) {
        val lockedClosing = uDao.findLockedClosingForDate(dateMs)
        val closedPeriod = uDao.findClosedPeriodForDate(dateMs)
        if (lockedClosing != null || closedPeriod != null) {
            val isOwnerWithReason = actingStaff.role == StaffRole.OWNER && overrideReason.isNotBlank()
            if (!isOwnerWithReason) {
                val periodName = closedPeriod?.name ?: "إغلاق الصندوق اليومي"
                throw IllegalStateException("الفترة المحاسبية ($periodName) مقفلة. لا يمكن التعديل إلا بصلاحية المدير العام مع تسجيل سبب التعديل.")
            }
        }
    }

    suspend fun recordBalancedJournalEntry(
        referenceType: String,
        referenceSyncId: String,
        description: String,
        staffName: String,
        entryDateMs: Long,
        rawLines: List<JournalDraftLine>
    ): Long {
        val nonZeroLines = rawLines.filter { it.debit > 0.004 || it.credit > 0.004 }.map {
            it.copy(
                debit = AccountingEngine.roundMoney(it.debit),
                credit = AccountingEngine.roundMoney(it.credit)
            )
        }
        if (nonZeroLines.isEmpty()) return 0L
        val totalDebit = AccountingEngine.calculateCashboxBalance(nonZeroLines.map { it.debit })
        val totalCredit = AccountingEngine.calculateCashboxBalance(nonZeroLines.map { it.credit })
        require(AccountingEngine.isJournalBalanced(listOf(totalDebit), listOf(totalCredit))) {
            "القيد المحاسبي غير متوازن: المدين ($totalDebit) لا يساوي الدائن ($totalCredit)"
        }
        val seq = uDao.countJournalEntries() + 1
        val entryNumber = String.format(Locale.US, "JE-%05d", seq)
        val entry = JournalEntryEntity(
            entryNumber = entryNumber,
            referenceType = referenceType,
            referenceSyncId = referenceSyncId,
            description = description,
            totalDebit = totalDebit,
            totalCredit = totalCredit,
            staffName = staffName,
            entryDateMs = entryDateMs
        )
        val entryId = uDao.insertJournalEntry(entry)
        uDao.insertJournalEntryLines(
            nonZeroLines.map { line ->
                JournalEntryLineEntity(
                    journalEntryId = entryId,
                    accountCode = line.accountCode,
                    accountName = line.accountName,
                    debit = line.debit,
                    credit = line.credit,
                    partyId = line.partyId,
                    notes = line.notes
                )
            }
        )
        return entryId
    }

    suspend fun enqueueSync(
        entityType: String,
        entitySyncId: String,
        operation: String = "UPSERT",
        payloadJson: String = ""
    ) {
        if (entitySyncId.isBlank()) return
        val now = System.currentTimeMillis()
        val existingPending = uDao.findPendingByEntitySyncId(entitySyncId)
        if (existingPending != null) {
            uDao.updateSyncQueueItem(
                existingPending.copy(
                    operation = operation,
                    payloadJson = payloadJson,
                    updatedAtMs = now
                )
            )
        } else {
            uDao.insertSyncQueueItem(
                SyncQueueEntity(
                    entityType = entityType,
                    entitySyncId = entitySyncId,
                    operation = operation,
                    payloadJson = payloadJson,
                    status = "PENDING",
                    createdAtMs = now,
                    updatedAtMs = now
                )
            )
        }
    }

    suspend fun logPriceChangeIfNeeded(
        productId: Long,
        productName: String,
        changeType: String,
        oldPrice: Double,
        newPrice: Double,
        referenceId: String,
        staffName: String,
        changedAtMs: Long
    ) {
        if (abs(oldPrice - newPrice) <= 0.005) return
        uDao.insertPriceHistory(
            ProductPriceHistoryEntity(
                productId = productId,
                productName = productName,
                changeType = changeType,
                oldPrice = AccountingEngine.roundMoney(oldPrice),
                newPrice = AccountingEngine.roundMoney(newPrice),
                referenceId = referenceId,
                staffName = staffName,
                changedAtMs = changedAtMs
            )
        )
    }

    suspend fun resolveProductPriceForLine(
        productId: Long,
        unitName: String,
        pricingTier: String,
        partyId: Long?,
        requestedQuantity: Double
    ): ResolvedPriceQuote {
        val product = dao.getProductById(productId) ?: error("الصنف غير موجود")
        val units = uDao.getUnitsForProduct(productId)
        val matchedUnit = units.firstOrNull { it.unitName == unitName }
        val factor = matchedUnit?.conversionToBase ?: 1.0
        val baseCostPerUnit = AccountingEngine.roundMoney(product.averageCost * factor)
        val baseMinPricePerUnit = AccountingEngine.roundMoney(product.minSellingPrice * factor)

        val special = if (partyId != null && requestedQuantity >= 1.0) {
            uDao.getCustomerSpecialPrice(partyId, productId)?.takeIf { requestedQuantity >= it.minQty }
        } else null

        val resolvedUnitPrice: Double
        val effectiveTier: String
        if (pricingTier == PricingTier.SPECIAL && special != null) {
            resolvedUnitPrice = AccountingEngine.roundMoney(special.specialPrice * factor)
            effectiveTier = PricingTier.SPECIAL
        } else if (pricingTier == PricingTier.WHOLESALE) {
            resolvedUnitPrice = AccountingEngine.roundMoney(
                matchedUnit?.wholesalePrice ?: (product.wholesalePrice * factor)
            )
            effectiveTier = PricingTier.WHOLESALE
        } else if (special != null && pricingTier == PricingTier.RETAIL) {
            resolvedUnitPrice = AccountingEngine.roundMoney(special.specialPrice * factor)
            effectiveTier = PricingTier.SPECIAL
        } else {
            resolvedUnitPrice = AccountingEngine.roundMoney(
                matchedUnit?.sellingPrice ?: (product.sellingPrice * factor)
            )
            effectiveTier = PricingTier.RETAIL
        }

        val isBelowCost = resolvedUnitPrice + 0.001 < baseCostPerUnit
        val isBelowMin = resolvedUnitPrice + 0.001 < baseMinPricePerUnit
        val margin = AccountingEngine.calculateProfitMarginPercent(resolvedUnitPrice, baseCostPerUnit)
        return ResolvedPriceQuote(
            unitPrice = resolvedUnitPrice,
            pricingTier = effectiveTier,
            costForUnit = baseCostPerUnit,
            minPriceForUnit = baseMinPricePerUnit,
            isBelowCost = isBelowCost,
            isBelowMinimum = isBelowMin,
            marginPercent = margin
        )
    }

    suspend fun postSaleReturn(
        originalInvoiceId: Long,
        productId: Long,
        unitName: String,
        conversionFactor: Double,
        returnedQty: Double,
        refundCashAmountInput: Double,
        reason: String,
        accountId: Long,
        actingStaff: StaffUserEntity
    ): Long {
        require(returnedQty > 0.0) { "الكمية المرتجعة يجب أن تكون أكبر من صفر" }
        require(reason.isNotBlank()) { "يرجى إدخال سبب المرتجع" }
        val now = System.currentTimeMillis()
        assertDateNotLocked(now, actingStaff)

        val withItems = dao.getInvoiceWithItems(originalInvoiceId) ?: error("الفاتورة الأصلية غير موجودة")
        val inv = withItems.invoice
        if (inv.status != InvoiceStatus.POSTED || inv.invoiceType != InvoiceType.SALE) {
            throw IllegalStateException("المرتجع متاح لفواتير البيع المعتمدة فقط")
        }
        val origItem = withItems.items.firstOrNull { it.productId == productId }
            ?: throw IllegalArgumentException("الصنف المحدد غير موجود ضمن الفاتورة")

        val baseReturnQty = AccountingEngine.toBaseQuantity(returnedQty, conversionFactor)
        val alreadyReturnedBase = uDao.getAllReturns()
            .filter { it.originalInvoiceId == originalInvoiceId && it.productId == productId && it.returnType == ReturnType.SALE_RETURN }
            .sumOf { it.baseQty }
        if (alreadyReturnedBase + baseReturnQty > origItem.baseQuantity + 0.001) {
            throw IllegalStateException("الكمية المرتجعة تتجاوز الكمية المباعة المتبقية في الفاتورة")
        }

        val unitPriceForReturn = origItem.unitPrice * (conversionFactor / origItem.conversionFactor.coerceAtLeast(0.0001))
        val totalRefund = AccountingEngine.roundMoney(returnedQty * unitPriceForReturn)
        val product = dao.getProductById(productId) ?: error("الصنف غير موجود")
        val restoredQty = AccountingEngine.roundQty(product.quantity + baseReturnQty)
        dao.updateProduct(product.copy(quantity = restoredQty, updatedAtMs = now, isSynced = false))

        val account = getAccountOrDefault(accountId)
        val party = inv.partyId?.let { dao.getPartyById(it) }
        val requestedCashRefund = refundCashAmountInput.coerceIn(0.0, totalRefund)
        val debtReduction = if (party != null) {
            (totalRefund - requestedCashRefund).coerceAtMost(party.balanceDue).coerceAtLeast(0.0)
        } else 0.0
        val actualCashRefund = if (party == null) totalRefund else AccountingEngine.roundMoney(totalRefund - debtReduction)

        if (actualCashRefund > 0.0) {
            val avail = uDao.getBalanceForAccount(account.id)
            if (avail + 0.01 < actualCashRefund) {
                throw IllegalStateException("رصيد (${account.name}) الحالي لا يكفي لرد المبلغ النقدي ($actualCashRefund)")
            }
        }

        val retSeq = uDao.countReturns() + 1
        val retNumber = String.format(Locale.US, "SR-%04d", retSeq)
        val retEntity = ReturnEntity(
            returnNumber = retNumber,
            returnType = ReturnType.SALE_RETURN,
            originalInvoiceId = inv.id,
            originalInvoiceNumber = inv.invoiceNumber,
            partyId = party?.id,
            partyName = inv.partyName,
            productId = product.id,
            productName = product.name,
            unitName = unitName,
            conversionFactor = conversionFactor,
            returnedQty = returnedQty,
            baseQty = baseReturnQty,
            unitPrice = AccountingEngine.roundMoney(unitPriceForReturn),
            totalRefundAmount = totalRefund,
            refundedCashAmount = actualCashRefund,
            reason = reason.trim(),
            staffName = actingStaff.name,
            returnDateMs = now
        )
        val retId = uDao.insertReturn(retEntity)

        if (actualCashRefund > 0.0) {
            dao.insertCashTransaction(
                CashTransactionEntity(
                    txType = CashTxType.SALE_RETURN_REFUND,
                    amountSigned = -actualCashRefund,
                    description = "رد نقدي لمرتجع بيع $retNumber (فاتورة ${inv.invoiceNumber})",
                    referenceId = retEntity.syncId,
                    staffName = actingStaff.name,
                    occurredAtMs = now,
                    accountId = account.id,
                    accountName = account.name
                )
            )
        }

        if (party != null && debtReduction > 0.0) {
            dao.updateParty(
                party.copy(
                    balanceDue = (party.balanceDue - debtReduction).coerceAtLeast(0.0),
                    updatedAtMs = now,
                    isSynced = false
                )
            )
            enqueueSync("PARTY", party.syncId)
        }

        val mv = StockMovementEntity(
            productId = product.id,
            productSyncId = product.syncId,
            productName = product.name,
            movementType = MovementType.SALE_RETURN,
            quantityDelta = baseReturnQty,
            unitCost = origItem.unitCostAtPosting,
            balanceAfter = restoredQty,
            reason = "مرتجع بيع $retNumber: $reason",
            referenceId = retEntity.syncId,
            staffName = actingStaff.name,
            occurredAtMs = now
        )
        dao.insertStockMovement(mv)

        val returnedCost = AccountingEngine.roundMoney(origItem.unitCostAtPosting * baseReturnQty)
        recordBalancedJournalEntry(
            referenceType = ReturnType.SALE_RETURN,
            referenceSyncId = retEntity.syncId,
            description = "قيد مرتجع مبيعات $retNumber لفاتورة ${inv.invoiceNumber}",
            staffName = actingStaff.name,
            entryDateMs = now,
            rawLines = listOf(
                JournalDraftLine(ChartAccounts.SALES_RETURN_CODE, "مردودات المبيعات", debit = totalRefund),
                JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, credit = actualCashRefund),
                JournalDraftLine(ChartAccounts.RECEIVABLES_CODE, "ذمم العملاء", credit = debtReduction, partyId = party?.id),
                JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", debit = returnedCost),
                JournalDraftLine(ChartAccounts.COGS_CODE, "تكلفة البضاعة المباعة", credit = returnedCost)
            )
        )

        dao.insertAuditLog(
            AuditLogEntity(
                action = "اعتماد مرتجع مبيعات",
                entityType = "RETURN",
                entityId = retEntity.syncId,
                details = "$retNumber | ${product.name} | الكمية الأساسية: $baseReturnQty | الإجمالي: $totalRefund",
                staffName = actingStaff.name,
                occurredAtMs = now
            )
        )
        enqueueSync("STOCK_MOVEMENT", mv.syncId)
        enqueueSync("PRODUCT", product.syncId)
        return retId
    }

    suspend fun postPurchaseReturn(
        originalInvoiceId: Long,
        productId: Long,
        unitName: String,
        conversionFactor: Double,
        returnedQty: Double,
        refundCashAmountInput: Double,
        reason: String,
        accountId: Long,
        actingStaff: StaffUserEntity
    ): Long {
        require(actingStaff.canManageInventory) { "ليس لديك صلاحية مرتجعات المشتريات" }
        require(returnedQty > 0.0) { "الكمية المرتجعة يجب أن تكون أكبر من صفر" }
        require(reason.isNotBlank()) { "يرجى إدخال سبب الإرجاع للمورد" }
        val now = System.currentTimeMillis()
        assertDateNotLocked(now, actingStaff)

        val withItems = dao.getInvoiceWithItems(originalInvoiceId) ?: error("فاتورة الشراء غير موجودة")
        val inv = withItems.invoice
        if (inv.status != InvoiceStatus.POSTED || inv.invoiceType != InvoiceType.PURCHASE) {
            throw IllegalStateException("المرتجع متاح لفواتير الشراء المعتمدة فقط")
        }
        val origItem = withItems.items.firstOrNull { it.productId == productId }
            ?: throw IllegalArgumentException("الصنف المحدد غير موجود ضمن الفاتورة")

        val baseReturnQty = AccountingEngine.toBaseQuantity(returnedQty, conversionFactor)
        val product = dao.getProductById(productId) ?: error("الصنف غير موجود")
        if (product.quantity + 0.001 < baseReturnQty) {
            throw IllegalStateException("المتوفر بالمخزون (${product.quantity}) لا يكفي لإرجاع ($baseReturnQty)")
        }
        val newQty = AccountingEngine.roundQty(product.quantity - baseReturnQty)
        dao.updateProduct(product.copy(quantity = newQty, updatedAtMs = now, isSynced = false))

        val totalRefund = AccountingEngine.roundMoney(baseReturnQty * origItem.unitCostAtPosting)
        val account = getAccountOrDefault(accountId)
        val supplier = inv.partyId?.let { dao.getPartyById(it) }
        val requestedCash = refundCashAmountInput.coerceIn(0.0, totalRefund)
        val debtReduction = if (supplier != null) {
            (totalRefund - requestedCash).coerceAtMost(supplier.balanceDue).coerceAtLeast(0.0)
        } else 0.0
        val actualCashRefund = if (supplier == null) totalRefund else AccountingEngine.roundMoney(totalRefund - debtReduction)

        val retSeq = uDao.countReturns() + 1
        val retNumber = String.format(Locale.US, "PR-%04d", retSeq)
        val retEntity = ReturnEntity(
            returnNumber = retNumber,
            returnType = ReturnType.PURCHASE_RETURN,
            originalInvoiceId = inv.id,
            originalInvoiceNumber = inv.invoiceNumber,
            partyId = supplier?.id,
            partyName = inv.partyName,
            productId = product.id,
            productName = product.name,
            unitName = unitName,
            conversionFactor = conversionFactor,
            returnedQty = returnedQty,
            baseQty = baseReturnQty,
            unitPrice = origItem.unitCostAtPosting,
            totalRefundAmount = totalRefund,
            refundedCashAmount = actualCashRefund,
            reason = reason.trim(),
            staffName = actingStaff.name,
            returnDateMs = now
        )
        val retId = uDao.insertReturn(retEntity)

        if (actualCashRefund > 0.0) {
            dao.insertCashTransaction(
                CashTransactionEntity(
                    txType = CashTxType.PURCHASE_RETURN_REFUND,
                    amountSigned = actualCashRefund,
                    description = "استرداد نقدي لمرتجع شراء $retNumber (فاتورة ${inv.invoiceNumber})",
                    referenceId = retEntity.syncId,
                    staffName = actingStaff.name,
                    occurredAtMs = now,
                    accountId = account.id,
                    accountName = account.name
                )
            )
        }

        if (supplier != null && debtReduction > 0.0) {
            dao.updateParty(
                supplier.copy(
                    balanceDue = (supplier.balanceDue - debtReduction).coerceAtLeast(0.0),
                    updatedAtMs = now,
                    isSynced = false
                )
            )
            enqueueSync("PARTY", supplier.syncId)
        }

        val mv = StockMovementEntity(
            productId = product.id,
            productSyncId = product.syncId,
            productName = product.name,
            movementType = MovementType.PURCHASE_RETURN,
            quantityDelta = -baseReturnQty,
            unitCost = origItem.unitCostAtPosting,
            balanceAfter = newQty,
            reason = "مرتجع شراء $retNumber: $reason",
            referenceId = retEntity.syncId,
            staffName = actingStaff.name,
            occurredAtMs = now
        )
        dao.insertStockMovement(mv)

        recordBalancedJournalEntry(
            referenceType = ReturnType.PURCHASE_RETURN,
            referenceSyncId = retEntity.syncId,
            description = "قيد مرتجع مشتريات $retNumber لفاتورة ${inv.invoiceNumber}",
            staffName = actingStaff.name,
            entryDateMs = now,
            rawLines = listOf(
                JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, debit = actualCashRefund),
                JournalDraftLine(ChartAccounts.SUPPLIER_PAYABLES_CODE, "ذمم الموردين", debit = debtReduction, partyId = supplier?.id),
                JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", credit = totalRefund)
            )
        )

        dao.insertAuditLog(
            AuditLogEntity(
                action = "اعتماد مرتجع مشتريات",
                entityType = "RETURN",
                entityId = retEntity.syncId,
                details = "$retNumber | ${product.name} | الكمية: $baseReturnQty | الإجمالي: $totalRefund",
                staffName = actingStaff.name,
                occurredAtMs = now
            )
        )
        enqueueSync("STOCK_MOVEMENT", mv.syncId)
        enqueueSync("PRODUCT", product.syncId)
        return retId
    }

    suspend fun writeOffDamagedStock(
        productId: Long,
        damagedBaseQty: Double,
        reason: String,
        actingStaff: StaffUserEntity
    ) {
        require(actingStaff.canManageInventory) { "ليس لديك صلاحية تسجيل البضاعة التالفة" }
        require(damagedBaseQty > 0.0) { "الكمية التالفة يجب أن تكون أكبر من صفر" }
        require(reason.isNotBlank()) { "يرجى ذكر سبب التلف" }
        val now = System.currentTimeMillis()
        assertDateNotLocked(now, actingStaff)

        val product = dao.getProductById(productId) ?: error("الصنف غير موجود")
        if (product.quantity + 0.001 < damagedBaseQty) {
            throw IllegalStateException("الكمية التالفة أكبر من الرصيد المتاح بالمخزون (${product.quantity})")
        }
        val newQty = AccountingEngine.roundQty(product.quantity - damagedBaseQty)
        val lossValue = AccountingEngine.roundMoney(damagedBaseQty * product.averageCost)
        dao.updateProduct(product.copy(quantity = newQty, updatedAtMs = now, isSynced = false))

        val mv = StockMovementEntity(
            productId = product.id,
            productSyncId = product.syncId,
            productName = product.name,
            movementType = MovementType.DAMAGED,
            quantityDelta = -damagedBaseQty,
            unitCost = product.averageCost,
            balanceAfter = newQty,
            reason = "إتلاف بضاعة: $reason",
            referenceId = product.syncId,
            staffName = actingStaff.name,
            occurredAtMs = now
        )
        dao.insertStockMovement(mv)

        recordBalancedJournalEntry(
            referenceType = MovementType.DAMAGED,
            referenceSyncId = mv.syncId,
            description = "إتلاف بضاعة (${product.name}) - $reason",
            staffName = actingStaff.name,
            entryDateMs = now,
            rawLines = listOf(
                JournalDraftLine(ChartAccounts.INVENTORY_ADJUSTMENT_CODE, "خسائر وتلف المخزون", debit = lossValue),
                JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", credit = lossValue)
            )
        )
        dao.insertAuditLog(
            AuditLogEntity(
                action = "شطب بضاعة تالفة",
                entityType = "PRODUCT",
                entityId = product.syncId,
                details = "${product.name} | الكمية: $damagedBaseQty | التكلفة: $lossValue | $reason",
                staffName = actingStaff.name,
                occurredAtMs = now
            )
        )
        enqueueSync("PRODUCT", product.syncId)
        enqueueSync("STOCK_MOVEMENT", mv.syncId)
    }

    suspend fun writeOffBadDebt(
        partyId: Long,
        amount: Double,
        reason: String,
        actingStaff: StaffUserEntity
    ) {
        require(actingStaff.role == StaffRole.OWNER || actingStaff.canManageExpenses) {
            "شطب الديون المعدومة متاح للمدير أو المحاسب فقط"
        }
        require(amount > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }
        require(reason.isNotBlank()) { "يرجى تسجيل سبب شطب الدين" }
        val now = System.currentTimeMillis()
        assertDateNotLocked(now, actingStaff)

        val party = dao.getPartyById(partyId) ?: error("العميل غير موجود")
        require(party.partyType == PartyType.CUSTOMER) { "شطب الديون المعدومة يخص ذمم الزبائن فقط" }
        if (amount > party.balanceDue + 0.01) {
            throw IllegalArgumentException("المبلغ أكبر من رصيد الدين المستحق (${party.balanceDue})")
        }
        dao.updateParty(
            party.copy(
                balanceDue = (party.balanceDue - amount).coerceAtLeast(0.0),
                updatedAtMs = now,
                isSynced = false
            )
        )
        recordBalancedJournalEntry(
            referenceType = CashTxType.BAD_DEBT_WRITE_OFF,
            referenceSyncId = party.syncId,
            description = "شطب دين معدوم للعميل ${party.name} - $reason",
            staffName = actingStaff.name,
            entryDateMs = now,
            rawLines = listOf(
                JournalDraftLine(ChartAccounts.BAD_DEBT_CODE, "مصروف الديون المعدومة", debit = amount, partyId = party.id),
                JournalDraftLine(ChartAccounts.RECEIVABLES_CODE, "ذمم العملاء", credit = amount, partyId = party.id)
            )
        )
        val aud = AuditLogEntity(
            action = "شطب دين معدوم",
            entityType = "PARTY",
            entityId = party.syncId,
            details = "${party.name} | المبلغ: $amount | السبب: $reason",
            staffName = actingStaff.name,
            occurredAtMs = now
        )
        dao.insertAuditLog(aud)
        enqueueSync("PARTY", party.syncId)
        enqueueSync("AUDIT_LOG", aud.syncId)
    }

    suspend fun transferBetweenAccounts(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        notes: String,
        actingStaff: StaffUserEntity
    ) {
        require(actingStaff.canWithdrawDeposit || actingStaff.role == StaffRole.OWNER) {
            "ليس لديك صلاحية التحويل بين الحسابات"
        }
        require(fromAccountId != toAccountId) { "اختر حسابين مختلفين للتحويل" }
        require(amount > 0.0) { "مبلغ التحويل يجب أن يكون أكبر من صفر" }
        val now = System.currentTimeMillis()
        assertDateNotLocked(now, actingStaff)

        val fromAcc = getAccountOrDefault(fromAccountId)
        val toAcc = getAccountOrDefault(toAccountId)
        val fromBalance = uDao.getBalanceForAccount(fromAcc.id)
        if (fromBalance + 0.01 < amount) {
            throw IllegalStateException("رصيد (${fromAcc.name}) الحالي ($fromBalance) لا يكفي للتحويل")
        }

        val desc = notes.trim().ifBlank { "تحويل من ${fromAcc.name} إلى ${toAcc.name}" }
        val outTx = CashTransactionEntity(
            txType = CashTxType.CASH_TRANSFER_OUT,
            amountSigned = -amount,
            description = desc,
            staffName = actingStaff.name,
            occurredAtMs = now,
            accountId = fromAcc.id,
            accountName = fromAcc.name
        )
        val inTx = CashTransactionEntity(
            txType = CashTxType.CASH_TRANSFER_IN,
            amountSigned = amount,
            description = desc,
            referenceId = outTx.syncId,
            staffName = actingStaff.name,
            occurredAtMs = now,
            accountId = toAcc.id,
            accountName = toAcc.name
        )
        dao.insertCashTransaction(outTx)
        dao.insertCashTransaction(inTx)

        recordBalancedJournalEntry(
            referenceType = "CASH_TRANSFER",
            referenceSyncId = outTx.syncId,
            description = desc,
            staffName = actingStaff.name,
            entryDateMs = now,
            rawLines = listOf(
                JournalDraftLine(
                    if (toAcc.accountType == CashAccountType.BANK) ChartAccounts.BANK_CODE else ChartAccounts.CASHBOX_CODE,
                    toAcc.name,
                    debit = amount
                ),
                JournalDraftLine(
                    if (fromAcc.accountType == CashAccountType.BANK) ChartAccounts.BANK_CODE else ChartAccounts.CASHBOX_CODE,
                    fromAcc.name,
                    credit = amount
                )
            )
        )
        dao.insertAuditLog(
            AuditLogEntity(
                action = "تحويل مالي بين الحسابات",
                entityType = "CASHBOX",
                entityId = outTx.syncId,
                details = "${fromAcc.name} -> ${toAcc.name} | $amount | $desc",
                staffName = actingStaff.name,
                occurredAtMs = now
            )
        )
        enqueueSync("CASH_TX", outTx.syncId)
        enqueueSync("CASH_TX", inTx.syncId)
    }

    suspend fun closeCashboxDay(
        accountId: Long,
        actualCountedBalance: Double,
        notes: String,
        lockPeriod: Boolean,
        actingStaff: StaffUserEntity
    ): CashboxClosingEntity {
        require(actingStaff.canWithdrawDeposit || actingStaff.canManageExpenses || actingStaff.role == StaffRole.OWNER) {
            "ليس لديك صلاحية إغلاق الصندوق اليومي"
        }
        require(actualCountedBalance >= 0.0) { "الرصيد الفعلي لا يمكن أن يكون سالباً" }
        val now = System.currentTimeMillis()
        val account = getAccountOrDefault(accountId)
        val expectedBalance = AccountingEngine.roundMoney(uDao.getBalanceForAccount(account.id))
        val variance = AccountingEngine.roundMoney(actualCountedBalance - expectedBalance)

        if (abs(variance) > 0.01) {
            val varTx = CashTransactionEntity(
                txType = CashTxType.CLOSING_VARIANCE,
                amountSigned = variance,
                description = "تسوية فرق إغلاق (${account.name}): المتوقع $expectedBalance والفعلي $actualCountedBalance",
                staffName = actingStaff.name,
                occurredAtMs = now,
                accountId = account.id,
                accountName = account.name
            )
            dao.insertCashTransaction(varTx)
            val absVar = abs(variance)
            recordBalancedJournalEntry(
                referenceType = CashTxType.CLOSING_VARIANCE,
                referenceSyncId = varTx.syncId,
                description = "قيد فرق إغلاق الصندوق (${account.name})",
                staffName = actingStaff.name,
                entryDateMs = now,
                rawLines = if (variance < 0.0) {
                    listOf(
                        JournalDraftLine(ChartAccounts.CASH_VARIANCE_CODE, "عجز وفروقات الصندوق", debit = absVar),
                        JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, credit = absVar)
                    )
                } else {
                    listOf(
                        JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, debit = absVar),
                        JournalDraftLine(ChartAccounts.CASH_VARIANCE_CODE, "زيادة وفروقات الصندوق", credit = absVar)
                    )
                }
            )
            enqueueSync("CASH_TX", varTx.syncId)
        }

        val lastClosingEnd = uDao.getAllCashboxClosings()
            .filter { it.accountId == account.id }
            .maxOfOrNull { it.periodEndMs } ?: 0L
        val closing = CashboxClosingEntity(
            accountId = account.id,
            accountName = account.name,
            closingDateMs = now,
            periodStartMs = if (lastClosingEnd > 0L) lastClosingEnd + 1L else 0L,
            periodEndMs = now,
            expectedBalance = expectedBalance,
            actualCountedBalance = AccountingEngine.roundMoney(actualCountedBalance),
            varianceAmount = variance,
            notes = notes.trim(),
            closedByStaff = actingStaff.name,
            isLocked = lockPeriod,
            createdAtMs = now
        )
        val id = uDao.insertCashboxClosing(closing)
        val saved = closing.copy(id = id)
        val aud = AuditLogEntity(
            action = "إغلاق الصندوق اليومي",
            entityType = "CLOSING",
            entityId = saved.syncId,
            details = "${account.name} | المتوقع: $expectedBalance | الفعلي: $actualCountedBalance | الفرق: $variance | قفل: $lockPeriod",
            staffName = actingStaff.name,
            occurredAtMs = now
        )
        dao.insertAuditLog(aud)
        enqueueSync("AUDIT_LOG", aud.syncId)
        return saved
    }

    suspend fun submitStockCount(
        productId: Long,
        countedQty: Double,
        notes: String,
        actingStaff: StaffUserEntity
    ): Long {
        require(actingStaff.canManageInventory) { "ليس لديك صلاحية إجراء الجرد" }
        require(countedQty >= 0.0) { "الكمية الفعلية لا يمكن أن تكون سالبة" }
        val now = System.currentTimeMillis()
        val product = dao.getProductById(productId) ?: error("الصنف غير موجود")
        val varianceQty = AccountingEngine.roundQty(countedQty - product.quantity)
        val varianceValue = AccountingEngine.roundMoney(varianceQty * product.averageCost)
        val countEntity = StockCountEntity(
            productId = product.id,
            productName = product.name,
            systemQty = product.quantity,
            countedQty = countedQty,
            varianceQty = varianceQty,
            varianceValue = varianceValue,
            status = "PENDING",
            notes = notes.trim(),
            countedBy = actingStaff.name,
            countedAtMs = now
        )
        return uDao.insertStockCount(countEntity)
    }

    suspend fun approveStockCount(stockCountId: Long, actingStaff: StaffUserEntity) {
        require(actingStaff.canApproveStocktake || actingStaff.role == StaffRole.OWNER) {
            "اعتماد تسوية الجرد متاح للمدير المخوّل فقط"
        }
        val now = System.currentTimeMillis()
        assertDateNotLocked(now, actingStaff)

        val count = uDao.getStockCountById(stockCountId) ?: error("سجل الجرد غير موجود")
        if (count.status == "APPROVED") {
            throw IllegalStateException("تم اعتماد هذا الجرد مسبقاً")
        }
        val product = dao.getProductById(count.productId) ?: error("الصنف غير موجود")
        val delta = AccountingEngine.roundQty(count.countedQty - product.quantity)
        dao.updateProduct(product.copy(quantity = count.countedQty, updatedAtMs = now, isSynced = false))

        val mv = StockMovementEntity(
            productId = product.id,
            productSyncId = product.syncId,
            productName = product.name,
            movementType = MovementType.ADJUSTMENT,
            quantityDelta = delta,
            unitCost = product.averageCost,
            balanceAfter = count.countedQty,
            reason = "اعتماد جلسة جرد (${count.notes.ifBlank { "تسوية" }})",
            referenceId = count.syncId,
            staffName = actingStaff.name,
            occurredAtMs = now
        )
        dao.insertStockMovement(mv)

        val absVal = AccountingEngine.roundMoney(abs(delta * product.averageCost))
        if (absVal > 0.01) {
            recordBalancedJournalEntry(
                referenceType = "STOCK_COUNT",
                referenceSyncId = count.syncId,
                description = "قيد تسوية جرد (${product.name})",
                staffName = actingStaff.name,
                entryDateMs = now,
                rawLines = if (delta >= 0.0) {
                    listOf(
                        JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", debit = absVal),
                        JournalDraftLine(ChartAccounts.INVENTORY_ADJUSTMENT_CODE, "فروقات جرد المخزون", credit = absVal)
                    )
                } else {
                    listOf(
                        JournalDraftLine(ChartAccounts.INVENTORY_ADJUSTMENT_CODE, "عجز جرد المخزون", debit = absVal),
                        JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", credit = absVal)
                    )
                }
            )
        }

        uDao.updateStockCount(
            count.copy(
                systemQty = product.quantity,
                varianceQty = delta,
                varianceValue = AccountingEngine.roundMoney(delta * product.averageCost),
                status = "APPROVED",
                approvedBy = actingStaff.name,
                approvedAtMs = now,
                isSynced = false
            )
        )
        dao.insertAuditLog(
            AuditLogEntity(
                action = "اعتماد تسوية جرد مخزني",
                entityType = "STOCK_COUNT",
                entityId = count.syncId,
                details = "${product.name} | الدفتري: ${product.quantity} | الفعلي: ${count.countedQty} | الفرق: $delta",
                staffName = actingStaff.name,
                occurredAtMs = now
            )
        )
        enqueueSync("PRODUCT", product.syncId)
        enqueueSync("STOCK_MOVEMENT", mv.syncId)
    }

    suspend fun clearAllUltimateTables() {
        uDao.clearJournalEntryLines()
        uDao.clearJournalEntries()
        uDao.clearReturns()
        uDao.clearStockCounts()
        uDao.clearCashboxClosings()
        uDao.clearPriceHistory()
        uDao.clearCustomerSpecialPrices()
        uDao.clearProductUnits()
        uDao.clearAccountingPeriods()
        uDao.clearSyncQueue()
        uDao.clearNonDefaultCashAccounts()
    }
}
