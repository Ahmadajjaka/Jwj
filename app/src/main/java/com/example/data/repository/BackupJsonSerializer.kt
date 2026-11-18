package com.example.data.repository

import com.example.data.local.AccountingPeriodEntity
import com.example.data.local.ApprovalStatus
import com.example.data.local.AuditLogEntity
import com.example.data.local.CashAccountEntity
import com.example.data.local.CashTransactionEntity
import com.example.data.local.CashboxClosingEntity
import com.example.data.local.CustomerSpecialPriceEntity
import com.example.data.local.DebtPaymentEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.InvoiceItemEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.JournalEntryLineEntity
import com.example.data.local.MeasureUnit
import com.example.data.local.PartyEntity
import com.example.data.local.PricingTier
import com.example.data.local.ProductEntity
import com.example.data.local.ProductPriceHistoryEntity
import com.example.data.local.ProductUnitEntity
import com.example.data.local.ReturnEntity
import com.example.data.local.StaffRole
import com.example.data.local.StaffUserEntity
import com.example.data.local.StockCountEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.StoreSettingsEntity
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val settings: StoreSettingsEntity,
    val staffUsers: List<StaffUserEntity>,
    val products: List<ProductEntity>,
    val parties: List<PartyEntity>,
    val invoices: List<InvoiceEntity>,
    val invoiceItems: List<InvoiceItemEntity>,
    val stockMovements: List<StockMovementEntity>,
    val cashTransactions: List<CashTransactionEntity>,
    val expenses: List<ExpenseEntity>,
    val debtPayments: List<DebtPaymentEntity>,
    val auditLogs: List<AuditLogEntity>,
    val productUnits: List<ProductUnitEntity> = emptyList(),
    val customerSpecialPrices: List<CustomerSpecialPriceEntity> = emptyList(),
    val priceHistory: List<ProductPriceHistoryEntity> = emptyList(),
    val cashAccounts: List<CashAccountEntity> = emptyList(),
    val cashboxClosings: List<CashboxClosingEntity> = emptyList(),
    val journalEntries: List<JournalEntryEntity> = emptyList(),
    val journalEntryLines: List<JournalEntryLineEntity> = emptyList(),
    val stockCounts: List<StockCountEntity> = emptyList(),
    val returns: List<ReturnEntity> = emptyList(),
    val accountingPeriods: List<AccountingPeriodEntity> = emptyList()
)

object BackupJsonSerializer {

    private const val FORMAT_V1 = "MAMO_ACCOUNTING_BACKUP_V1"
    private const val FORMAT_V2 = "MAMO_ACCOUNTING_BACKUP_V2"

    fun totalRecordCount(payload: BackupPayload): Int {
        return payload.staffUsers.size + payload.products.size + payload.parties.size +
            payload.invoices.size + payload.invoiceItems.size + payload.stockMovements.size +
            payload.cashTransactions.size + payload.expenses.size + payload.debtPayments.size +
            payload.auditLogs.size + payload.productUnits.size + payload.customerSpecialPrices.size +
            payload.priceHistory.size + payload.cashAccounts.size + payload.cashboxClosings.size +
            payload.journalEntries.size + payload.journalEntryLines.size + payload.stockCounts.size +
            payload.returns.size + payload.accountingPeriods.size
    }

    fun toJson(payload: BackupPayload): String {
        val dataNode = buildDataJsonObject(payload)
        val checksum = AccountingEngine.sha256Hex(dataNode.toString())
        return JSONObject().apply {
            put("format", FORMAT_V2)
            put("exportedAtMs", System.currentTimeMillis())
            put("checksumSha256", checksum)
            put("data", dataNode)
        }.toString(2)
    }

    private fun buildDataJsonObject(payload: BackupPayload): JSONObject {
        val root = JSONObject()
        val s = payload.settings
        root.put(
            "storeSettings",
            JSONObject().apply {
                put("storeName", s.storeName)
                put("managerName", s.managerName)
                put("currencyCode", s.currencyCode)
                put("currencySymbol", s.currencySymbol)
                put("costingMethod", s.costingMethod)
                put("invoiceFooter", s.invoiceFooter)
                put("logoUri", s.logoUri)
                put("dateFormatPattern", s.dateFormatPattern)
                put("autoBackupEnabled", s.autoBackupEnabled)
                put("storePhone", s.storePhone)
                put("storeAddress", s.storeAddress)
                put("taxEnabled", s.taxEnabled)
                put("taxRatePercent", s.taxRatePercent)
                put("printPaperSize", s.printPaperSize)
                put("printShowStamp", s.printShowStamp)
                put("printShowSignature", s.printShowSignature)
                put("backupEncryptionEnabled", s.backupEncryptionEnabled)
                put("autoBackupIntervalHours", s.autoBackupIntervalHours)
                put("staleDaysThreshold", s.staleDaysThreshold)
            }
        )

        root.put("staffUsers", JSONArray().apply {
            payload.staffUsers.forEach { u ->
                put(JSONObject().apply {
                    put("id", u.id); put("syncId", u.syncId); put("name", u.name); put("role", u.role)
                    put("pinHash", u.pinHash); put("canViewProfits", u.canViewProfits)
                    put("canCancelInvoices", u.canCancelInvoices); put("canManageInventory", u.canManageInventory)
                    put("canManageExpenses", u.canManageExpenses); put("canManageSettings", u.canManageSettings)
                    put("canEditPrices", u.canEditPrices); put("canWithdrawDeposit", u.canWithdrawDeposit)
                    put("canApproveStocktake", u.canApproveStocktake)
                    put("isActive", u.isActive); put("createdAtMs", u.createdAtMs)
                    put("approvalStatus", u.approvalStatus); put("requestedRole", u.requestedRole)
                    put("loginIdentifier", u.loginIdentifier); put("requestedAtMs", u.requestedAtMs)
                    put("approvedBy", u.approvedBy); put("approvedAtMs", u.approvedAtMs)
                })
            }
        })

        root.put("products", JSONArray().apply {
            payload.products.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("syncId", p.syncId); put("name", p.name); put("category", p.category)
                    put("barcode", p.barcode); put("sku", p.sku); put("imageUri", p.imageUri)
                    put("baseUnit", p.baseUnit); put("averageCost", p.averageCost)
                    put("lastPurchaseCost", p.lastPurchaseCost); put("sellingPrice", p.sellingPrice)
                    put("wholesalePrice", p.wholesalePrice); put("minSellingPrice", p.minSellingPrice)
                    put("defaultSupplierName", p.defaultSupplierName); put("quantity", p.quantity)
                    put("minStockLevel", p.minStockLevel); put("isArchived", p.isArchived)
                    put("createdAtMs", p.createdAtMs); put("updatedAtMs", p.updatedAtMs)
                    put("lastPurchaseDateMs", p.lastPurchaseDateMs); put("lastSaleDateMs", p.lastSaleDateMs)
                })
            }
        })

        root.put("parties", JSONArray().apply {
            payload.parties.forEach { pt ->
                put(JSONObject().apply {
                    put("id", pt.id); put("syncId", pt.syncId); put("partyType", pt.partyType)
                    put("name", pt.name); put("phone", pt.phone); put("address", pt.address)
                    put("openingBalance", pt.openingBalance); put("notes", pt.notes)
                    put("totalAmount", pt.totalAmount); put("paidAmount", pt.paidAmount)
                    put("balanceDue", pt.balanceDue); put("createdAtMs", pt.createdAtMs); put("updatedAtMs", pt.updatedAtMs)
                })
            }
        })

        root.put("invoices", JSONArray().apply {
            payload.invoices.forEach { inv ->
                put(JSONObject().apply {
                    put("id", inv.id); put("syncId", inv.syncId); put("invoiceNumber", inv.invoiceNumber)
                    put("invoiceType", inv.invoiceType); put("partyId", inv.partyId ?: JSONObject.NULL)
                    put("partySyncId", inv.partySyncId); put("partyName", inv.partyName); put("partyPhone", inv.partyPhone)
                    put("status", inv.status); put("paymentMethod", inv.paymentMethod)
                    put("subtotal", inv.subtotal); put("discountAmount", inv.discountAmount)
                    put("netTotal", inv.netTotal); put("paidAmount", inv.paidAmount); put("remainingAmount", inv.remainingAmount)
                    put("totalCost", inv.totalCost); put("extraCosts", inv.extraCosts)
                    put("taxRate", inv.taxRate); put("taxAmount", inv.taxAmount)
                    put("accountId", inv.accountId); put("sourceOcrHash", inv.sourceOcrHash)
                    put("notes", inv.notes); put("staffId", inv.staffId); put("staffName", inv.staffName)
                    put("invoiceDateMs", inv.invoiceDateMs); put("dueDateMs", inv.dueDateMs ?: JSONObject.NULL)
                    put("createdAtMs", inv.createdAtMs); put("updatedAtMs", inv.updatedAtMs)
                })
            }
        })

        root.put("invoiceItems", JSONArray().apply {
            payload.invoiceItems.forEach { it ->
                put(JSONObject().apply {
                    put("id", it.id); put("invoiceId", it.invoiceId); put("productId", it.productId)
                    put("productSyncId", it.productSyncId); put("productName", it.productName)
                    put("quantity", it.quantity); put("unitPrice", it.unitPrice)
                    put("discountAmount", it.discountAmount); put("lineTotal", it.lineTotal)
                    put("unitCostAtPosting", it.unitCostAtPosting); put("unitName", it.unitName)
                    put("conversionFactor", it.conversionFactor); put("baseQuantity", it.baseQuantity)
                    put("pricingTier", it.pricingTier)
                })
            }
        })

        root.put("stockMovements", JSONArray().apply {
            payload.stockMovements.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("syncId", m.syncId); put("productId", m.productId)
                    put("productSyncId", m.productSyncId); put("productName", m.productName)
                    put("movementType", m.movementType); put("quantityDelta", m.quantityDelta)
                    put("unitCost", m.unitCost); put("balanceAfter", m.balanceAfter)
                    put("reason", m.reason); put("referenceId", m.referenceId)
                    put("staffName", m.staffName); put("occurredAtMs", m.occurredAtMs)
                })
            }
        })

        root.put("cashTransactions", JSONArray().apply {
            payload.cashTransactions.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("syncId", c.syncId); put("txType", c.txType)
                    put("amountSigned", c.amountSigned); put("description", c.description)
                    put("referenceId", c.referenceId); put("staffName", c.staffName)
                    put("occurredAtMs", c.occurredAtMs); put("accountId", c.accountId)
                    put("accountName", c.accountName)
                })
            }
        })

        root.put("expenses", JSONArray().apply {
            payload.expenses.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("syncId", e.syncId); put("category", e.category)
                    put("amount", e.amount); put("description", e.description)
                    put("paymentMethod", e.paymentMethod); put("staffName", e.staffName)
                    put("expenseDateMs", e.expenseDateMs); put("receiptImageUri", e.receiptImageUri)
                    put("accountId", e.accountId)
                })
            }
        })

        root.put("debtPayments", JSONArray().apply {
            payload.debtPayments.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id); put("syncId", d.syncId); put("partyId", d.partyId)
                    put("partySyncId", d.partySyncId); put("partyName", d.partyName)
                    put("partyType", d.partyType); put("invoiceId", d.invoiceId ?: JSONObject.NULL)
                    put("amount", d.amount); put("notes", d.notes)
                    put("staffName", d.staffName); put("paymentDateMs", d.paymentDateMs)
                })
            }
        })

        root.put("auditLogs", JSONArray().apply {
            payload.auditLogs.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id); put("syncId", a.syncId); put("action", a.action)
                    put("entityType", a.entityType); put("entityId", a.entityId)
                    put("details", a.details); put("staffName", a.staffName); put("occurredAtMs", a.occurredAtMs)
                })
            }
        })

        root.put("productUnits", JSONArray().apply {
            payload.productUnits.forEach { u ->
                put(JSONObject().apply {
                    put("id", u.id); put("syncId", u.syncId); put("productId", u.productId)
                    put("unitName", u.unitName); put("conversionToBase", u.conversionToBase)
                    put("sellingPrice", u.sellingPrice); put("wholesalePrice", u.wholesalePrice)
                    put("barcode", u.barcode)
                })
            }
        })

        root.put("customerSpecialPrices", JSONArray().apply {
            payload.customerSpecialPrices.forEach { sp ->
                put(JSONObject().apply {
                    put("id", sp.id); put("syncId", sp.syncId); put("partyId", sp.partyId)
                    put("partyName", sp.partyName); put("productId", sp.productId)
                    put("productName", sp.productName); put("specialPrice", sp.specialPrice)
                    put("minQty", sp.minQty); put("updatedAtMs", sp.updatedAtMs)
                })
            }
        })

        root.put("priceHistory", JSONArray().apply {
            payload.priceHistory.forEach { ph ->
                put(JSONObject().apply {
                    put("id", ph.id); put("productId", ph.productId); put("productName", ph.productName)
                    put("changeType", ph.changeType); put("oldPrice", ph.oldPrice)
                    put("newPrice", ph.newPrice); put("referenceId", ph.referenceId)
                    put("staffName", ph.staffName); put("changedAtMs", ph.changedAtMs)
                })
            }
        })

        root.put("cashAccounts", JSONArray().apply {
            payload.cashAccounts.forEach { acc ->
                put(JSONObject().apply {
                    put("id", acc.id); put("syncId", acc.syncId); put("name", acc.name)
                    put("accountType", acc.accountType); put("isDefault", acc.isDefault)
                    put("createdAtMs", acc.createdAtMs)
                })
            }
        })

        root.put("cashboxClosings", JSONArray().apply {
            payload.cashboxClosings.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("syncId", c.syncId); put("accountId", c.accountId)
                    put("accountName", c.accountName); put("closingDateMs", c.closingDateMs)
                    put("periodStartMs", c.periodStartMs); put("periodEndMs", c.periodEndMs)
                    put("expectedBalance", c.expectedBalance); put("actualCountedBalance", c.actualCountedBalance)
                    put("varianceAmount", c.varianceAmount); put("notes", c.notes)
                    put("closedByStaff", c.closedByStaff); put("isLocked", c.isLocked)
                    put("createdAtMs", c.createdAtMs)
                })
            }
        })

        root.put("journalEntries", JSONArray().apply {
            payload.journalEntries.forEach { j ->
                put(JSONObject().apply {
                    put("id", j.id); put("syncId", j.syncId); put("entryNumber", j.entryNumber)
                    put("referenceType", j.referenceType); put("referenceSyncId", j.referenceSyncId)
                    put("description", j.description); put("totalDebit", j.totalDebit)
                    put("totalCredit", j.totalCredit); put("staffName", j.staffName)
                    put("entryDateMs", j.entryDateMs); put("isReversed", j.isReversed)
                })
            }
        })

        root.put("journalEntryLines", JSONArray().apply {
            payload.journalEntryLines.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id); put("journalEntryId", l.journalEntryId)
                    put("accountCode", l.accountCode); put("accountName", l.accountName)
                    put("debit", l.debit); put("credit", l.credit)
                    put("partyId", l.partyId ?: JSONObject.NULL); put("notes", l.notes)
                })
            }
        })

        root.put("stockCounts", JSONArray().apply {
            payload.stockCounts.forEach { sc ->
                put(JSONObject().apply {
                    put("id", sc.id); put("syncId", sc.syncId); put("productId", sc.productId)
                    put("productName", sc.productName); put("systemQty", sc.systemQty)
                    put("countedQty", sc.countedQty); put("varianceQty", sc.varianceQty)
                    put("varianceValue", sc.varianceValue); put("status", sc.status)
                    put("notes", sc.notes); put("countedBy", sc.countedBy)
                    put("approvedBy", sc.approvedBy); put("countedAtMs", sc.countedAtMs)
                    put("approvedAtMs", sc.approvedAtMs)
                })
            }
        })

        root.put("returns", JSONArray().apply {
            payload.returns.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("syncId", r.syncId); put("returnNumber", r.returnNumber)
                    put("returnType", r.returnType); put("originalInvoiceId", r.originalInvoiceId)
                    put("originalInvoiceNumber", r.originalInvoiceNumber)
                    put("partyId", r.partyId ?: JSONObject.NULL); put("partyName", r.partyName)
                    put("productId", r.productId); put("productName", r.productName)
                    put("unitName", r.unitName); put("conversionFactor", r.conversionFactor)
                    put("returnedQty", r.returnedQty); put("baseQty", r.baseQty)
                    put("unitPrice", r.unitPrice); put("totalRefundAmount", r.totalRefundAmount)
                    put("refundedCashAmount", r.refundedCashAmount); put("reason", r.reason)
                    put("staffName", r.staffName); put("returnDateMs", r.returnDateMs)
                })
            }
        })

        root.put("accountingPeriods", JSONArray().apply {
            payload.accountingPeriods.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("syncId", p.syncId); put("name", p.name)
                    put("startMs", p.startMs); put("endMs", p.endMs)
                    put("isClosed", p.isClosed); put("closedBy", p.closedBy); put("closedAtMs", p.closedAtMs)
                })
            }
        })
        return root
    }

    fun fromJson(json: String): BackupPayload {
        val wrapper = JSONObject(json)
        val format = wrapper.optString("format")
        require(format == FORMAT_V1 || format == FORMAT_V2) {
            "ملف النسخة الاحتياطية غير صالح أو بصيغة غير مدعومة"
        }

        val root = if (format == FORMAT_V2) {
            val dataNode = wrapper.getJSONObject("data")
            val expectedHash = wrapper.optString("checksumSha256", "")
            if (expectedHash.isNotBlank()) {
                val actualHash = AccountingEngine.sha256Hex(dataNode.toString())
                require(actualHash == expectedHash) {
                    "فشل التحقق من سلامة النسخة الاحتياطية (الملف معدل أو تالف)"
                }
            }
            dataNode
        } else {
            wrapper
        }

        val sObj = root.getJSONObject("storeSettings")
        val settings = StoreSettingsEntity(
            id = 1,
            storeName = sObj.getString("storeName"),
            managerName = sObj.getString("managerName"),
            currencyCode = sObj.getString("currencyCode"),
            currencySymbol = sObj.getString("currencySymbol"),
            costingMethod = sObj.getString("costingMethod"),
            invoiceFooter = sObj.optString("invoiceFooter", ""),
            logoUri = sObj.optString("logoUri", ""),
            dateFormatPattern = sObj.optString("dateFormatPattern", "dd/MM/yyyy"),
            autoBackupEnabled = sObj.optBoolean("autoBackupEnabled", true),
            storePhone = sObj.optString("storePhone", ""),
            storeAddress = sObj.optString("storeAddress", ""),
            taxEnabled = sObj.optBoolean("taxEnabled", false),
            taxRatePercent = sObj.optDouble("taxRatePercent", 0.0),
            printPaperSize = sObj.optString("printPaperSize", "A4"),
            printShowStamp = sObj.optBoolean("printShowStamp", true),
            printShowSignature = sObj.optBoolean("printShowSignature", true),
            backupEncryptionEnabled = sObj.optBoolean("backupEncryptionEnabled", true),
            autoBackupIntervalHours = sObj.optInt("autoBackupIntervalHours", 24),
            staleDaysThreshold = sObj.optInt("staleDaysThreshold", 60)
        )

        val staffUsers = buildList {
            val arr = root.getJSONArray("staffUsers")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val role = o.getString("role")
                add(
                    StaffUserEntity(
                        id = o.getLong("id"),
                        syncId = o.getString("syncId"),
                        name = o.getString("name"),
                        role = role,
                        pinHash = o.getString("pinHash"),
                        canViewProfits = o.getBoolean("canViewProfits"),
                        canCancelInvoices = o.getBoolean("canCancelInvoices"),
                        canManageInventory = o.getBoolean("canManageInventory"),
                        canManageExpenses = o.getBoolean("canManageExpenses"),
                        canManageSettings = o.getBoolean("canManageSettings"),
                        isActive = o.getBoolean("isActive"),
                        createdAtMs = o.getLong("createdAtMs"),
                        isSynced = false,
                        canEditPrices = o.optBoolean("canEditPrices", role == StaffRole.OWNER || role == StaffRole.ACCOUNTANT),
                        canWithdrawDeposit = o.optBoolean("canWithdrawDeposit", role == StaffRole.OWNER || role == StaffRole.ACCOUNTANT),
                        canApproveStocktake = o.optBoolean("canApproveStocktake", role == StaffRole.OWNER),
                        approvalStatus = o.optString("approvalStatus", ApprovalStatus.APPROVED),
                        requestedRole = o.optString("requestedRole", role),
                        loginIdentifier = o.optString("loginIdentifier", ""),
                        requestedAtMs = o.optLong("requestedAtMs", o.getLong("createdAtMs")),
                        approvedBy = o.optString("approvedBy", ""),
                        approvedAtMs = o.optLong("approvedAtMs", 0L)
                    )
                )
            }
        }

        val products = buildList {
            val arr = root.getJSONArray("products")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val avg = o.getDouble("averageCost")
                val sell = o.getDouble("sellingPrice")
                val created = o.getLong("createdAtMs")
                add(
                    ProductEntity(
                        id = o.getLong("id"),
                        syncId = o.getString("syncId"),
                        name = o.getString("name"),
                        category = o.getString("category"),
                        barcode = o.optString("barcode", ""),
                        averageCost = avg,
                        lastPurchaseCost = o.getDouble("lastPurchaseCost"),
                        sellingPrice = sell,
                        quantity = o.getDouble("quantity"),
                        minStockLevel = o.getDouble("minStockLevel"),
                        isArchived = o.optBoolean("isArchived", false),
                        createdAtMs = created,
                        updatedAtMs = o.getLong("updatedAtMs"),
                        isSynced = false,
                        sku = o.optString("sku", ""),
                        imageUri = o.optString("imageUri", ""),
                        baseUnit = o.optString("baseUnit", MeasureUnit.PIECE),
                        wholesalePrice = o.optDouble("wholesalePrice", sell),
                        minSellingPrice = o.optDouble("minSellingPrice", avg),
                        defaultSupplierName = o.optString("defaultSupplierName", ""),
                        lastPurchaseDateMs = o.optLong("lastPurchaseDateMs", created),
                        lastSaleDateMs = o.optLong("lastSaleDateMs", 0L)
                    )
                )
            }
        }

        val parties = buildList {
            val arr = root.getJSONArray("parties")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    PartyEntity(
                        id = o.getLong("id"),
                        syncId = o.getString("syncId"),
                        partyType = o.getString("partyType"),
                        name = o.getString("name"),
                        phone = o.optString("phone", ""),
                        totalAmount = o.getDouble("totalAmount"),
                        paidAmount = o.getDouble("paidAmount"),
                        balanceDue = o.getDouble("balanceDue"),
                        createdAtMs = o.getLong("createdAtMs"),
                        updatedAtMs = o.getLong("updatedAtMs"),
                        isSynced = false,
                        address = o.optString("address", ""),
                        openingBalance = o.optDouble("openingBalance", 0.0),
                        notes = o.optString("notes", "")
                    )
                )
            }
        }

        val invoices = buildList {
            val arr = root.getJSONArray("invoices")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    InvoiceEntity(
                        id = o.getLong("id"),
                        syncId = o.getString("syncId"),
                        invoiceNumber = o.getString("invoiceNumber"),
                        invoiceType = o.getString("invoiceType"),
                        partyId = if (o.isNull("partyId")) null else o.getLong("partyId"),
                        partySyncId = o.optString("partySyncId", ""),
                        partyName = o.getString("partyName"),
                        partyPhone = o.optString("partyPhone", ""),
                        status = o.getString("status"),
                        paymentMethod = o.getString("paymentMethod"),
                        subtotal = o.getDouble("subtotal"),
                        discountAmount = o.getDouble("discountAmount"),
                        netTotal = o.getDouble("netTotal"),
                        paidAmount = o.getDouble("paidAmount"),
                        remainingAmount = o.getDouble("remainingAmount"),
                        totalCost = o.getDouble("totalCost"),
                        notes = o.optString("notes", ""),
                        staffId = o.getLong("staffId"),
                        staffName = o.getString("staffName"),
                        invoiceDateMs = o.getLong("invoiceDateMs"),
                        dueDateMs = if (o.isNull("dueDateMs")) null else o.getLong("dueDateMs"),
                        createdAtMs = o.getLong("createdAtMs"),
                        updatedAtMs = o.getLong("updatedAtMs"),
                        isSynced = false,
                        extraCosts = o.optDouble("extraCosts", 0.0),
                        taxRate = o.optDouble("taxRate", 0.0),
                        taxAmount = o.optDouble("taxAmount", 0.0),
                        accountId = o.optLong("accountId", 1L),
                        sourceOcrHash = o.optString("sourceOcrHash", "")
                    )
                )
            }
        }

        val invoiceItems = buildList {
            val arr = root.getJSONArray("invoiceItems")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val qty = o.getDouble("quantity")
                add(
                    InvoiceItemEntity(
                        id = o.getLong("id"),
                        invoiceId = o.getLong("invoiceId"),
                        productId = o.getLong("productId"),
                        productSyncId = o.getString("productSyncId"),
                        productName = o.getString("productName"),
                        quantity = qty,
                        unitPrice = o.getDouble("unitPrice"),
                        discountAmount = o.getDouble("discountAmount"),
                        lineTotal = o.getDouble("lineTotal"),
                        unitCostAtPosting = o.getDouble("unitCostAtPosting"),
                        unitName = o.optString("unitName", MeasureUnit.PIECE),
                        conversionFactor = o.optDouble("conversionFactor", 1.0),
                        baseQuantity = o.optDouble("baseQuantity", qty),
                        pricingTier = o.optString("pricingTier", PricingTier.RETAIL)
                    )
                )
            }
        }

        val stockMovements = buildList {
            val arr = root.getJSONArray("stockMovements")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    StockMovementEntity(
                        id = o.getLong("id"), syncId = o.getString("syncId"),
                        productId = o.getLong("productId"), productSyncId = o.getString("productSyncId"),
                        productName = o.getString("productName"), movementType = o.getString("movementType"),
                        quantityDelta = o.getDouble("quantityDelta"), unitCost = o.getDouble("unitCost"),
                        balanceAfter = o.getDouble("balanceAfter"), reason = o.getString("reason"),
                        referenceId = o.optString("referenceId", ""), staffName = o.getString("staffName"),
                        occurredAtMs = o.getLong("occurredAtMs"), isSynced = false
                    )
                )
            }
        }

        val cashTransactions = buildList {
            val arr = root.getJSONArray("cashTransactions")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    CashTransactionEntity(
                        id = o.getLong("id"), syncId = o.getString("syncId"),
                        txType = o.getString("txType"), amountSigned = o.getDouble("amountSigned"),
                        description = o.getString("description"), referenceId = o.optString("referenceId", ""),
                        staffName = o.getString("staffName"), occurredAtMs = o.getLong("occurredAtMs"),
                        isSynced = false, accountId = o.optLong("accountId", 1L),
                        accountName = o.optString("accountName", "الصندوق الرئيسي")
                    )
                )
            }
        }

        val expenses = buildList {
            val arr = root.getJSONArray("expenses")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ExpenseEntity(
                        id = o.getLong("id"), syncId = o.getString("syncId"),
                        category = o.getString("category"), amount = o.getDouble("amount"),
                        description = o.optString("description", ""), paymentMethod = o.getString("paymentMethod"),
                        staffName = o.getString("staffName"), expenseDateMs = o.getLong("expenseDateMs"),
                        isSynced = false, receiptImageUri = o.optString("receiptImageUri", ""),
                        accountId = o.optLong("accountId", 1L)
                    )
                )
            }
        }

        val debtPayments = buildList {
            val arr = root.getJSONArray("debtPayments")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    DebtPaymentEntity(
                        id = o.getLong("id"), syncId = o.getString("syncId"),
                        partyId = o.getLong("partyId"), partySyncId = o.getString("partySyncId"),
                        partyName = o.getString("partyName"), partyType = o.getString("partyType"),
                        invoiceId = if (o.isNull("invoiceId")) null else o.getLong("invoiceId"),
                        amount = o.getDouble("amount"), notes = o.optString("notes", ""),
                        staffName = o.getString("staffName"), paymentDateMs = o.getLong("paymentDateMs"),
                        isSynced = false
                    )
                )
            }
        }

        val auditLogs = buildList {
            val arr = root.getJSONArray("auditLogs")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    AuditLogEntity(
                        id = o.getLong("id"), syncId = o.getString("syncId"),
                        action = o.getString("action"), entityType = o.getString("entityType"),
                        entityId = o.optString("entityId", ""), details = o.getString("details"),
                        staffName = o.getString("staffName"), occurredAtMs = o.getLong("occurredAtMs"),
                        isSynced = false
                    )
                )
            }
        }

        val productUnits = optArray(root, "productUnits") { o ->
            ProductUnitEntity(
                id = o.getLong("id"), syncId = o.getString("syncId"), productId = o.getLong("productId"),
                unitName = o.getString("unitName"), conversionToBase = o.getDouble("conversionToBase"),
                sellingPrice = o.getDouble("sellingPrice"), wholesalePrice = o.getDouble("wholesalePrice"),
                barcode = o.optString("barcode", "")
            )
        }

        val customerSpecialPrices = optArray(root, "customerSpecialPrices") { o ->
            CustomerSpecialPriceEntity(
                id = o.getLong("id"), syncId = o.getString("syncId"), partyId = o.getLong("partyId"),
                partyName = o.getString("partyName"), productId = o.getLong("productId"),
                productName = o.getString("productName"), specialPrice = o.getDouble("specialPrice"),
                minQty = o.optDouble("minQty", 1.0), updatedAtMs = o.getLong("updatedAtMs")
            )
        }

        val priceHistory = optArray(root, "priceHistory") { o ->
            ProductPriceHistoryEntity(
                id = o.getLong("id"), productId = o.getLong("productId"), productName = o.getString("productName"),
                changeType = o.getString("changeType"), oldPrice = o.getDouble("oldPrice"),
                newPrice = o.getDouble("newPrice"), referenceId = o.optString("referenceId", ""),
                staffName = o.getString("staffName"), changedAtMs = o.getLong("changedAtMs")
            )
        }

        val cashAccounts = optArray(root, "cashAccounts") { o ->
            CashAccountEntity(
                id = o.getLong("id"), syncId = o.getString("syncId"), name = o.getString("name"),
                accountType = o.getString("accountType"), isDefault = o.getBoolean("isDefault"),
                createdAtMs = o.getLong("createdAtMs")
            )
        }

        val cashboxClosings = optArray(root, "cashboxClosings") { o ->
            CashboxClosingEntity(
                id = o.getLong("id"), syncId = o.getString("syncId"), accountId = o.getLong("accountId"),
                accountName = o.getString("accountName"), closingDateMs = o.getLong("closingDateMs"),
                periodStartMs = o.getLong("periodStartMs"), periodEndMs = o.getLong("periodEndMs"),
                expectedBalance = o.getDouble("expectedBalance"), actualCountedBalance = o.getDouble("actualCountedBalance"),
                varianceAmount = o.getDouble("varianceAmount"), notes = o.optString("notes", ""),
                closedByStaff = o.getString("closedByStaff"), isLocked = o.getBoolean("isLocked"),
                createdAtMs = o.getLong("createdAtMs"), isSynced = false
            )
        }

        val journalEntries = optArray(root, "journalEntries") { o ->
            JournalEntryEntity(
                id = o.getLong("id"), syncId = o.getString("syncId"), entryNumber = o.getString("entryNumber"),
                referenceType = o.getString("referenceType"), referenceSyncId = o.getString("referenceSyncId"),
                description = o.getString("description"), totalDebit = o.getDouble("totalDebit"),
                totalCredit = o.getDouble("totalCredit"), staffName = o.getString("staffName"),
                entryDateMs = o.getLong("entryDateMs"), isReversed = o.optBoolean("isReversed", false),
                isSynced = false
            )
        }

        val journalEntryLines = optArray(root, "journalEntryLines") { o ->
            JournalEntryLineEntity(
                id = o.getLong("id"), journalEntryId = o.getLong("journalEntryId"),
                accountCode = o.getString("accountCode"), accountName = o.getString("accountName"),
                debit = o.getDouble("debit"), credit = o.getDouble("credit"),
                partyId = if (o.isNull("partyId")) null else o.getLong("partyId"),
                notes = o.optString("notes", "")
            )
        }

        val stockCounts = optArray(root, "stockCounts") { o ->
            StockCountEntity(
                id = o.getLong("id"), syncId = o.getString("syncId"), productId = o.getLong("productId"),
                productName = o.getString("productName"), systemQty = o.getDouble("systemQty"),
                countedQty = o.getDouble("countedQty"), varianceQty = o.getDouble("varianceQty"),
                varianceValue = o.getDouble("varianceValue"), status = o.getString("status"),
                notes = o.optString("notes", ""), countedBy = o.getString("countedBy"),
                approvedBy = o.optString("approvedBy", ""), countedAtMs = o.getLong("countedAtMs"),
                approvedAtMs = o.optLong("approvedAtMs", 0L), isSynced = false
            )
        }

        val returns = optArray(root, "returns") { o ->
            ReturnEntity(
                id = o.getLong("id"), syncId = o.getString("syncId"), returnNumber = o.getString("returnNumber"),
                returnType = o.getString("returnType"), originalInvoiceId = o.getLong("originalInvoiceId"),
                originalInvoiceNumber = o.getString("originalInvoiceNumber"),
                partyId = if (o.isNull("partyId")) null else o.getLong("partyId"),
                partyName = o.getString("partyName"), productId = o.getLong("productId"),
                productName = o.getString("productName"), unitName = o.getString("unitName"),
                conversionFactor = o.getDouble("conversionFactor"), returnedQty = o.getDouble("returnedQty"),
                baseQty = o.getDouble("baseQty"), unitPrice = o.getDouble("unitPrice"),
                totalRefundAmount = o.getDouble("totalRefundAmount"),
                refundedCashAmount = o.getDouble("refundedCashAmount"),
                reason = o.optString("reason", ""), staffName = o.getString("staffName"),
                returnDateMs = o.getLong("returnDateMs"), isSynced = false
            )
        }

        val accountingPeriods = optArray(root, "accountingPeriods") { o ->
            AccountingPeriodEntity(
                id = o.getLong("id"), syncId = o.getString("syncId"), name = o.getString("name"),
                startMs = o.getLong("startMs"), endMs = o.getLong("endMs"),
                isClosed = o.getBoolean("isClosed"), closedBy = o.optString("closedBy", ""),
                closedAtMs = o.optLong("closedAtMs", 0L)
            )
        }

        return BackupPayload(
            settings = settings,
            staffUsers = staffUsers,
            products = products,
            parties = parties,
            invoices = invoices,
            invoiceItems = invoiceItems,
            stockMovements = stockMovements,
            cashTransactions = cashTransactions,
            expenses = expenses,
            debtPayments = debtPayments,
            auditLogs = auditLogs,
            productUnits = productUnits,
            customerSpecialPrices = customerSpecialPrices,
            priceHistory = priceHistory,
            cashAccounts = cashAccounts,
            cashboxClosings = cashboxClosings,
            journalEntries = journalEntries,
            journalEntryLines = journalEntryLines,
            stockCounts = stockCounts,
            returns = returns,
            accountingPeriods = accountingPeriods
        )
    }

    private inline fun <T> optArray(root: JSONObject, key: String, mapper: (JSONObject) -> T): List<T> {
        val arr = root.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                add(mapper(arr.getJSONObject(i)))
            }
        }
    }
}
