package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object CostingMethod {
    const val WEIGHTED_AVERAGE = "WEIGHTED_AVERAGE"
    const val LAST_COST = "LAST_COST"
}

object StaffRole {
    const val OWNER = "OWNER"
    const val ACCOUNTANT = "ACCOUNTANT"
    const val CASHIER = "CASHIER" // بائع / كاشير
    const val WAREHOUSE_KEEPER = "WAREHOUSE_KEEPER" // أمين مخزن
    const val AUDITOR = "AUDITOR" // مراقب فقط
}

object MeasureUnit {
    const val PIECE = "قطعة"
    const val CARTON = "كرتون"
    const val DOZEN = "دستة"
    const val METER = "متر"
    const val KILOGRAM = "كيلوغرام"
    const val ROLL = "لفة"
    const val BOX = "علبة"
    const val PACK = "حزمة"

    val ALL_UNITS = listOf(PIECE, CARTON, DOZEN, METER, KILOGRAM, ROLL, BOX, PACK)
}

object PricingTier {
    const val RETAIL = "RETAIL"
    const val WHOLESALE = "WHOLESALE"
    const val SPECIAL = "SPECIAL"
}

object PartyType {
    const val CUSTOMER = "CUSTOMER"
    const val SUPPLIER = "SUPPLIER"
}

object InvoiceType {
    const val SALE = "SALE"
    const val PURCHASE = "PURCHASE"
}

object InvoiceStatus {
    const val POSTED = "POSTED"
    const val CANCELLED = "CANCELLED"
}

object ReturnType {
    const val SALE_RETURN = "SALE_RETURN"
    const val PURCHASE_RETURN = "PURCHASE_RETURN"
}

object PaymentMethod {
    const val CASH = "CASH"
    const val CREDIT = "CREDIT"
    const val PARTIAL = "PARTIAL"
}

object MovementType {
    const val PURCHASE = "PURCHASE"
    const val SALE = "SALE"
    const val REVERSAL = "REVERSAL"
    const val ADJUSTMENT = "ADJUSTMENT"
    const val SALE_RETURN = "SALE_RETURN"
    const val PURCHASE_RETURN = "PURCHASE_RETURN"
    const val DAMAGED = "DAMAGED"
}

object CashTxType {
    const val CAPITAL_IN = "CAPITAL_IN"
    const val PERSONAL_DRAWING = "PERSONAL_DRAWING"
    const val SALE_RECEIPT = "SALE_RECEIPT"
    const val PURCHASE_PAYMENT = "PURCHASE_PAYMENT"
    const val EXPENSE_PAYMENT = "EXPENSE_PAYMENT"
    const val DEBT_COLLECTION = "DEBT_COLLECTION"
    const val SUPPLIER_PAYMENT = "SUPPLIER_PAYMENT"
    const val OTHER_INCOME = "OTHER_INCOME"
    const val OTHER_OUTFLOW = "OTHER_OUTFLOW"
    const val REVERSAL = "REVERSAL"
    const val SALE_RETURN_REFUND = "SALE_RETURN_REFUND"
    const val PURCHASE_RETURN_REFUND = "PURCHASE_RETURN_REFUND"
    const val CASH_TRANSFER_IN = "CASH_TRANSFER_IN"
    const val CASH_TRANSFER_OUT = "CASH_TRANSFER_OUT"
    const val CLOSING_VARIANCE = "CLOSING_VARIANCE"
    const val BAD_DEBT_WRITE_OFF = "BAD_DEBT_WRITE_OFF"
}

object CashAccountType {
    const val CASHBOX = "CASHBOX"
    const val BANK = "BANK"
    const val SAFE = "SAFE"
}

object ChartAccounts {
    const val CASHBOX_CODE = "1010"
    const val BANK_CODE = "1020"
    const val INVENTORY_CODE = "1100"
    const val RECEIVABLES_CODE = "1200"
    const val SUPPLIER_PAYABLES_CODE = "2000"
    const val TAX_PAYABLE_CODE = "2100"
    const val CAPITAL_CODE = "3000"
    const val DRAWINGS_CODE = "3100"
    const val SALES_REVENUE_CODE = "4000"
    const val SALES_RETURN_CODE = "4100"
    const val OTHER_INCOME_CODE = "4200"
    const val COGS_CODE = "5000"
    const val OPERATING_EXPENSE_CODE = "5100"
    const val INVENTORY_ADJUSTMENT_CODE = "5200"
    const val BAD_DEBT_CODE = "5300"
    const val CASH_VARIANCE_CODE = "5400"
}

@Entity(tableName = "store_settings")
data class StoreSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val storeName: String = "خردوات المامو",
    val managerName: String = "أحمد نوار المامو",
    val currencyCode: String = "SYP",
    val currencySymbol: String = "ل.س",
    val costingMethod: String = CostingMethod.WEIGHTED_AVERAGE,
    val invoiceFooter: String = "شكراً لتعاملكم مع خردوات المامو — الإدارة: أحمد نوار المامو",
    val logoUri: String = "",
    val dateFormatPattern: String = "dd/MM/yyyy",
    val autoBackupEnabled: Boolean = true,
    val lastSyncAtMs: Long = 0L,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val storePhone: String = "",
    val storeAddress: String = "",
    val taxEnabled: Boolean = false,
    val taxRatePercent: Double = 0.0,
    val printPaperSize: String = "A4",
    val printShowStamp: Boolean = true,
    val printShowSignature: Boolean = true,
    val backupEncryptionEnabled: Boolean = true,
    val autoBackupIntervalHours: Int = 24,
    val lastAutoBackupAtMs: Long = 0L,
    val lastAutoBackupStatus: String = "",
    val staleDaysThreshold: Int = 60
)

object ApprovalStatus {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
}

@Entity(
    tableName = "staff_users",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class StaffUserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "staff_${UUID.randomUUID().toString().replace("-", "")}",
    val name: String,
    val role: String,
    val pinHash: String,
    val canViewProfits: Boolean,
    val canCancelInvoices: Boolean,
    val canManageInventory: Boolean,
    val canManageExpenses: Boolean,
    val canManageSettings: Boolean,
    val isActive: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val canEditPrices: Boolean = role == StaffRole.OWNER || role == StaffRole.ACCOUNTANT,
    val canWithdrawDeposit: Boolean = role == StaffRole.OWNER || role == StaffRole.ACCOUNTANT,
    val canApproveStocktake: Boolean = role == StaffRole.OWNER,
    val approvalStatus: String = ApprovalStatus.APPROVED,
    val requestedRole: String = role,
    val loginIdentifier: String = "",
    val requestedAtMs: Long = createdAtMs,
    val approvedBy: String = "",
    val approvedAtMs: Long = 0L
)

@Entity(
    tableName = "products",
    indices = [Index(value = ["syncId"], unique = true), Index(value = ["barcode"])]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "prod_${UUID.randomUUID().toString().replace("-", "")}",
    val name: String,
    val category: String,
    val barcode: String = "",
    val averageCost: Double,
    val lastPurchaseCost: Double,
    val sellingPrice: Double,
    val quantity: Double,
    val minStockLevel: Double,
    val isArchived: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val sku: String = "",
    val imageUri: String = "",
    val baseUnit: String = MeasureUnit.PIECE,
    val wholesalePrice: Double = sellingPrice,
    val minSellingPrice: Double = averageCost,
    val defaultSupplierName: String = "",
    val lastPurchaseDateMs: Long = createdAtMs,
    val lastSaleDateMs: Long = 0L
)

@Entity(
    tableName = "parties",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "party_${UUID.randomUUID().toString().replace("-", "")}",
    val partyType: String,
    val name: String,
    val phone: String = "",
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balanceDue: Double = 0.0,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val address: String = "",
    val openingBalance: Double = 0.0,
    val notes: String = ""
)

@Entity(
    tableName = "invoices",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["invoiceNumber"], unique = true),
        Index(value = ["partyId"])
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "inv_${UUID.randomUUID().toString().replace("-", "")}",
    val invoiceNumber: String,
    val invoiceType: String,
    val partyId: Long? = null,
    val partySyncId: String = "",
    val partyName: String,
    val partyPhone: String = "",
    val status: String = InvoiceStatus.POSTED,
    val paymentMethod: String,
    val subtotal: Double,
    val discountAmount: Double,
    val netTotal: Double,
    val paidAmount: Double,
    val remainingAmount: Double,
    val totalCost: Double,
    val notes: String = "",
    val staffId: Long,
    val staffName: String,
    val invoiceDateMs: Long,
    val dueDateMs: Long? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val extraCosts: Double = 0.0,
    val taxRate: Double = 0.0,
    val taxAmount: Double = 0.0,
    val accountId: Long = 1L,
    val sourceOcrHash: String = ""
)

@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["invoiceId"]), Index(value = ["productId"])]
)
data class InvoiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productId: Long,
    val productSyncId: String,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double,
    val discountAmount: Double = 0.0,
    val lineTotal: Double,
    val unitCostAtPosting: Double,
    val unitName: String = MeasureUnit.PIECE,
    val conversionFactor: Double = 1.0,
    val baseQuantity: Double = quantity,
    val pricingTier: String = PricingTier.RETAIL
)

@Entity(
    tableName = "stock_movements",
    indices = [Index(value = ["syncId"], unique = true), Index(value = ["productId"])]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "stk_${UUID.randomUUID().toString().replace("-", "")}",
    val productId: Long,
    val productSyncId: String,
    val productName: String,
    val movementType: String,
    val quantityDelta: Double,
    val unitCost: Double,
    val balanceAfter: Double,
    val reason: String,
    val referenceId: String = "",
    val staffName: String,
    val occurredAtMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(
    tableName = "cash_transactions",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class CashTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "cash_${UUID.randomUUID().toString().replace("-", "")}",
    val txType: String,
    val amountSigned: Double,
    val description: String,
    val referenceId: String = "",
    val staffName: String,
    val occurredAtMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val accountId: Long = 1L,
    val accountName: String = "الصندوق الرئيسي"
)

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "exp_${UUID.randomUUID().toString().replace("-", "")}",
    val category: String,
    val amount: Double,
    val description: String,
    val paymentMethod: String,
    val staffName: String,
    val expenseDateMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val receiptImageUri: String = "",
    val accountId: Long = 1L
)

@Entity(
    tableName = "debt_payments",
    indices = [Index(value = ["syncId"], unique = true), Index(value = ["partyId"])]
)
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "pay_${UUID.randomUUID().toString().replace("-", "")}",
    val partyId: Long,
    val partySyncId: String,
    val partyName: String,
    val partyType: String,
    val invoiceId: Long? = null,
    val amount: Double,
    val notes: String = "",
    val staffName: String,
    val paymentDateMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "aud_${UUID.randomUUID().toString().replace("-", "")}",
    val action: String,
    val entityType: String,
    val entityId: String,
    val details: String,
    val staffName: String,
    val occurredAtMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
