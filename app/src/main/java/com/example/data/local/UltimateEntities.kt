package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "product_units",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["productId"]),
        Index(value = ["barcode"])
    ]
)
data class ProductUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "unit_${UUID.randomUUID().toString().replace("-", "")}",
    val productId: Long,
    val unitName: String,
    val conversionToBase: Double,
    val sellingPrice: Double,
    val wholesalePrice: Double = sellingPrice,
    val barcode: String = ""
)

@Entity(
    tableName = "customer_special_prices",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["partyId", "productId"])
    ]
)
data class CustomerSpecialPriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "csp_${UUID.randomUUID().toString().replace("-", "")}",
    val partyId: Long,
    val partyName: String,
    val productId: Long,
    val productName: String,
    val specialPrice: Double,
    val minQty: Double = 1.0,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "price_history",
    indices = [Index(value = ["productId"])]
)
data class ProductPriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val changeType: String, // PURCHASE_COST, RETAIL_PRICE, WHOLESALE_PRICE
    val oldPrice: Double,
    val newPrice: Double,
    val referenceId: String = "",
    val staffName: String,
    val changedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cash_accounts",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class CashAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "acc_${UUID.randomUUID().toString().replace("-", "")}",
    val name: String,
    val accountType: String = CashAccountType.CASHBOX,
    val isDefault: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cashbox_closings",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class CashboxClosingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "cls_${UUID.randomUUID().toString().replace("-", "")}",
    val accountId: Long = 1L,
    val accountName: String = "الصندوق الرئيسي",
    val closingDateMs: Long,
    val periodStartMs: Long,
    val periodEndMs: Long,
    val expectedBalance: Double,
    val actualCountedBalance: Double,
    val varianceAmount: Double,
    val notes: String = "",
    val closedByStaff: String,
    val isLocked: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(
    tableName = "journal_entries",
    indices = [Index(value = ["syncId"], unique = true), Index(value = ["entryNumber"], unique = true)]
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "jrnl_${UUID.randomUUID().toString().replace("-", "")}",
    val entryNumber: String,
    val referenceType: String,
    val referenceSyncId: String,
    val description: String,
    val totalDebit: Double,
    val totalCredit: Double,
    val staffName: String,
    val entryDateMs: Long = System.currentTimeMillis(),
    val isReversed: Boolean = false,
    val isSynced: Boolean = false
)

@Entity(
    tableName = "journal_entry_lines",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["journalEntryId"]), Index(value = ["accountCode"])]
)
data class JournalEntryLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journalEntryId: Long,
    val accountCode: String,
    val accountName: String,
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val partyId: Long? = null,
    val notes: String = ""
)

@Entity(
    tableName = "stock_counts",
    indices = [Index(value = ["syncId"], unique = true), Index(value = ["productId"])]
)
data class StockCountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "scnt_${UUID.randomUUID().toString().replace("-", "")}",
    val productId: Long,
    val productName: String,
    val systemQty: Double,
    val countedQty: Double,
    val varianceQty: Double,
    val varianceValue: Double,
    val status: String = "PENDING", // PENDING, APPROVED
    val notes: String = "",
    val countedBy: String,
    val approvedBy: String = "",
    val countedAtMs: Long = System.currentTimeMillis(),
    val approvedAtMs: Long = 0L,
    val isSynced: Boolean = false
)

@Entity(
    tableName = "returns",
    indices = [Index(value = ["syncId"], unique = true), Index(value = ["originalInvoiceId"])]
)
data class ReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "ret_${UUID.randomUUID().toString().replace("-", "")}",
    val returnNumber: String,
    val returnType: String, // SALE_RETURN, PURCHASE_RETURN
    val originalInvoiceId: Long,
    val originalInvoiceNumber: String,
    val partyId: Long? = null,
    val partyName: String,
    val productId: Long,
    val productName: String,
    val unitName: String = MeasureUnit.PIECE,
    val conversionFactor: Double = 1.0,
    val returnedQty: Double,
    val baseQty: Double,
    val unitPrice: Double,
    val totalRefundAmount: Double,
    val refundedCashAmount: Double,
    val reason: String,
    val staffName: String,
    val returnDateMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(
    tableName = "accounting_periods",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class AccountingPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "per_${UUID.randomUUID().toString().replace("-", "")}",
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val isClosed: Boolean = false,
    val closedBy: String = "",
    val closedAtMs: Long = 0L
)

@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["syncId"], unique = true), Index(value = ["status"])]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "sq_${UUID.randomUUID().toString().replace("-", "")}",
    val entityType: String,
    val entitySyncId: String,
    val operation: String, // UPSERT, STATUS_UPDATE
    val payloadJson: String = "",
    val status: String = "PENDING", // PENDING, SYNCED, FAILED, CONFLICT
    val retryCount: Int = 0,
    val lastError: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "backups",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class BackupRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "bak_${UUID.randomUUID().toString().replace("-", "")}",
    val backupType: String, // MANUAL, AUTO_ENCRYPTED, JSON
    val fileName: String,
    val isEncrypted: Boolean,
    val checksumSha256: String,
    val recordCount: Int,
    val status: String, // SUCCESS, FAILED
    val notes: String = "",
    val createdBy: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_logs",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class AiLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "ailog_${UUID.randomUUID().toString().replace("-", "")}",
    val staffName: String,
    val modelId: String,
    val prompt: String,
    val responseSummary: String,
    val wasOfflineFallback: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)
