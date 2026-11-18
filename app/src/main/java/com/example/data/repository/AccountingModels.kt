package com.example.data.repository

import com.example.data.local.CashTransactionEntity
import com.example.data.local.DebtPaymentEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.InvoiceWithItems
import com.example.data.local.MeasureUnit
import com.example.data.local.PartyEntity
import com.example.data.local.PricingTier
import com.example.data.local.ProductEntity
import com.example.data.local.StaffUserEntity
import com.example.data.local.StockMovementEntity

data class InvoiceLineDraft(
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double,
    val lineDiscount: Double = 0.0,
    val availableStock: Double = 0.0,
    val currentCost: Double = 0.0,
    val unitName: String = MeasureUnit.PIECE,
    val conversionFactor: Double = 1.0,
    val pricingTier: String = PricingTier.RETAIL,
    val minSellingPrice: Double = 0.0
)

data class DashboardMetrics(
    val todaySales: Double = 0.0,
    val todayPurchases: Double = 0.0,
    val todayNetProfit: Double = 0.0,
    val todayExpenses: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalRevenue: Double = 0.0,
    val totalInvoiceCount: Int = 0,
    val currentCapital: Double = 0.0,
    val cashboxBalance: Double = 0.0,
    val isCashboxReconciled: Boolean = true,
    val inventoryValueAtCost: Double = 0.0,
    val receivablesFromCustomers: Double = 0.0,
    val payablesToSuppliers: Double = 0.0,
    val todayInvoiceCount: Int = 0,
    val lowStockCount: Int = 0,
    val staleProductsCount: Int = 0,
    val overdueDebtsCount: Int = 0,
    val pendingSyncCount: Int = 0
)

enum class ChartRange {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

data class ChartPoint(
    val label: String,
    val primaryValue: Double,
    val secondaryValue: Double = 0.0
)

data class ProductProfitabilityRow(
    val productId: Long,
    val productName: String,
    val soldQuantity: Double,
    val totalRevenue: Double,
    val totalCost: Double,
    val grossProfit: Double
)

data class ProfitLossSummary(
    val salesRevenue: Double = 0.0,
    val returnsTotal: Double = 0.0,
    val costOfGoodsSold: Double = 0.0,
    val grossProfit: Double = 0.0,
    val operatingExpenses: Double = 0.0,
    val otherIncome: Double = 0.0,
    val badDebtLoss: Double = 0.0,
    val damagedStockLoss: Double = 0.0,
    val netProfit: Double = 0.0,
    val invoiceCount: Int = 0
)

data class CapitalSummary(
    val injectedCapital: Double = 0.0,
    val personalDrawings: Double = 0.0,
    val cumulativeNetProfit: Double = 0.0,
    val equityCapital: Double = 0.0,
    val cashboxBalance: Double = 0.0,
    val inventoryValueAtCost: Double = 0.0,
    val receivables: Double = 0.0,
    val payables: Double = 0.0,
    val netWorthCheck: Double = 0.0
)

data class BalanceSheetSummary(
    val cashAndBanks: Double = 0.0,
    val inventoryAsset: Double = 0.0,
    val accountsReceivable: Double = 0.0,
    val totalAssets: Double = 0.0,
    val accountsPayable: Double = 0.0,
    val taxPayable: Double = 0.0,
    val totalLiabilities: Double = 0.0,
    val paidInCapital: Double = 0.0,
    val drawings: Double = 0.0,
    val retainedEarnings: Double = 0.0,
    val totalEquity: Double = 0.0,
    val isBalanced: Boolean = true
)

data class CashFlowSummary(
    val operatingInflows: Double = 0.0,
    val operatingOutflows: Double = 0.0,
    val netOperatingCash: Double = 0.0,
    val financingCapitalIn: Double = 0.0,
    val financingDrawingsOut: Double = 0.0,
    val netCashChange: Double = 0.0,
    val endingCashBalance: Double = 0.0
)

data class PartyStatement(
    val party: PartyEntity,
    val invoices: List<InvoiceWithItems>,
    val payments: List<DebtPaymentEntity>
)

data class PeriodFilter(
    val label: String,
    val startMs: Long,
    val endMs: Long
)

data class ResolvedPriceQuote(
    val unitPrice: Double,
    val pricingTier: String,
    val costForUnit: Double,
    val minPriceForUnit: Double,
    val isBelowCost: Boolean,
    val isBelowMinimum: Boolean,
    val marginPercent: Double
)

data class StoreSnapshotState(
    val products: List<ProductEntity> = emptyList(),
    val parties: List<PartyEntity> = emptyList(),
    val invoices: List<InvoiceWithItems> = emptyList(),
    val cashTransactions: List<CashTransactionEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val debtPayments: List<DebtPaymentEntity> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList()
)

data class DatabaseIntegrityReport(
    val checkedAtMs: Long = System.currentTimeMillis(),
    val cashboxQueryBalance: Double = 0.0,
    val cashboxLedgerSum: Double = 0.0,
    val isCashboxReconciled: Boolean = true,
    val assetsNetWorth: Double = 0.0,
    val equityCheck: Double = 0.0,
    val isEquityBalanced: Boolean = true,
    val negativeStockProductsCount: Int = 0,
    val archivedProductsCount: Int = 0,
    val unsyncedTotalCount: Int = 0,
    val unbalancedJournalCount: Int = 0,
    val totalRecordsCount: Int = 0
)

sealed interface LoginAuthResult {
    data class Success(val staff: StaffUserEntity) : LoginAuthResult
    data class PendingApproval(val staffName: String, val requestedRole: String) : LoginAuthResult
    data class Rejected(val staffName: String) : LoginAuthResult
    data class Inactive(val staffName: String) : LoginAuthResult
    data object InvalidCredentials : LoginAuthResult
}

data class DatabaseStorageInfo(
    val dbFileBytes: Long = 0L,
    val walFileBytes: Long = 0L,
    val shmFileBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val auditLogsCount: Int = 0,
    val syncQueueCount: Int = 0,
    val priceHistoryCount: Int = 0,
    val aiLogsCount: Int = 0,
    val measuredAtMs: Long = System.currentTimeMillis()
)

data class DatabaseCompressionReport(
    val sizeBeforeBytes: Long = 0L,
    val sizeAfterBytes: Long = 0L,
    val savedBytes: Long = 0L,
    val savedPercent: Double = 0.0,
    val deletedAuditLogs: Int = 0,
    val deletedSyncItems: Int = 0,
    val deletedPriceHistory: Int = 0,
    val deletedAiLogs: Int = 0,
    val retentionDays: Int = 60,
    val completedAtMs: Long = System.currentTimeMillis()
) {
    val totalPrunedRecords: Int
        get() = deletedAuditLogs + deletedSyncItems + deletedPriceHistory + deletedAiLogs
}

enum class InvoiceDateFilterMode(val label: String) {
    ALL("الكل"),
    TODAY("اليوم"),
    WEEK("هذا الأسبوع"),
    MONTH("هذا الشهر"),
    CUSTOM("مخصص")
}

data class InvoiceDateFilter(
    val mode: InvoiceDateFilterMode = InvoiceDateFilterMode.ALL,
    val customStartMs: Long? = null,
    val customEndMs: Long? = null
)
