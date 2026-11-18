package com.example.ui

import com.example.data.local.CashTxType
import com.example.data.local.InvoiceStatus
import com.example.data.local.InvoiceType
import com.example.data.local.InvoiceWithItems
import com.example.data.local.MovementType
import com.example.data.local.PartyEntity
import com.example.data.local.PartyType
import com.example.data.local.ProductEntity
import com.example.data.local.ReturnType
import com.example.data.local.StockMovementEntity
import com.example.data.remote.MamoAiTools
import com.example.data.repository.AccountingEngine
import com.example.data.repository.BalanceSheetSummary
import com.example.data.repository.CapitalSummary
import com.example.data.repository.CashFlowSummary
import com.example.data.repository.ChartPoint
import com.example.data.repository.ChartRange
import com.example.data.repository.DashboardMetrics
import com.example.data.repository.PartyStatement
import com.example.data.repository.PeriodFilter
import com.example.data.repository.ProductProfitabilityRow
import com.example.data.repository.ProfitLossSummary
import com.example.utils.ArabicFormatters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MamoReportCalculators {

    fun computeDashboardMetrics(
        products: List<ProductEntity>,
        parties: List<PartyEntity>,
        invoices: List<InvoiceWithItems>,
        cashTransactions: List<com.example.data.local.CashTransactionEntity>,
        cashboxBalance: Double,
        expenses: List<com.example.data.local.ExpenseEntity>,
        pendingSyncCount: Int,
        staleDaysThreshold: Int
    ): DashboardMetrics {
        val todayStart = ArabicFormatters.startOfTodayMs()
        val now = System.currentTimeMillis()
        val postedInvoices = invoices.filter { it.invoice.status == InvoiceStatus.POSTED }

        val todaySales = postedInvoices
            .filter { it.invoice.invoiceType == InvoiceType.SALE && it.invoice.invoiceDateMs >= todayStart }
            .sumOf { it.invoice.netTotal }

        val todayCogs = postedInvoices
            .filter { it.invoice.invoiceType == InvoiceType.SALE && it.invoice.invoiceDateMs >= todayStart }
            .sumOf { it.invoice.totalCost }

        val todayPurchases = postedInvoices
            .filter { it.invoice.invoiceType == InvoiceType.PURCHASE && it.invoice.invoiceDateMs >= todayStart }
            .sumOf { it.invoice.netTotal }

        val todayExpenses = expenses.filter { it.expenseDateMs >= todayStart }.sumOf { it.amount }
        val totalExpenses = expenses.sumOf { it.amount }
        val totalRevenue = postedInvoices
            .filter { it.invoice.invoiceType == InvoiceType.SALE }
            .sumOf { it.invoice.netTotal }
        val todayNetProfit = AccountingEngine.calculateNetProfit(todaySales, todayCogs, todayExpenses)
        val todayInvoiceCount = postedInvoices.count { it.invoice.invoiceDateMs >= todayStart }

        val inventoryValue = products.sumOf { it.quantity * it.averageCost }
        val receivables = parties.filter { it.partyType == PartyType.CUSTOMER }.sumOf { it.balanceDue }
        val payables = parties.filter { it.partyType == PartyType.SUPPLIER }.sumOf { it.balanceDue }
        val ledgerSum = AccountingEngine.calculateCashboxBalance(cashTransactions.map { it.amountSigned })
        val reconciled = AccountingEngine.isCashboxReconciled(cashboxBalance, ledgerSum)
        val currentCapital = cashboxBalance + inventoryValue + receivables - payables
        val lowStock = products.count { it.quantity <= it.minStockLevel }
        val staleCount = MamoAiTools.findStaleProducts(products, staleDaysThreshold, now).size
        val overdueDebts = postedInvoices.count {
            it.invoice.remainingAmount > 0.0 && it.invoice.dueDateMs != null && it.invoice.dueDateMs < now
        }

        return DashboardMetrics(
            todaySales = AccountingEngine.roundMoney(todaySales),
            todayPurchases = AccountingEngine.roundMoney(todayPurchases),
            todayNetProfit = todayNetProfit,
            todayExpenses = AccountingEngine.roundMoney(todayExpenses),
            totalExpenses = AccountingEngine.roundMoney(totalExpenses),
            totalRevenue = AccountingEngine.roundMoney(totalRevenue),
            totalInvoiceCount = postedInvoices.size,
            currentCapital = AccountingEngine.roundMoney(currentCapital),
            cashboxBalance = AccountingEngine.roundMoney(cashboxBalance),
            isCashboxReconciled = reconciled,
            inventoryValueAtCost = AccountingEngine.roundMoney(inventoryValue),
            receivablesFromCustomers = AccountingEngine.roundMoney(receivables),
            payablesToSuppliers = AccountingEngine.roundMoney(payables),
            todayInvoiceCount = todayInvoiceCount,
            lowStockCount = lowStock,
            staleProductsCount = staleCount,
            overdueDebtsCount = overdueDebts,
            pendingSyncCount = pendingSyncCount
        )
    }

    fun buildSalesChartSeries(state: MamoMainUiState, range: ChartRange): List<ChartPoint> {
        val postedSales = state.invoices.filter {
            it.invoice.status == InvoiceStatus.POSTED && it.invoice.invoiceType == InvoiceType.SALE
        }
        return bucketInvoices(postedSales, range) { list ->
            val rev = list.sumOf { it.invoice.netTotal }
            val profit = list.sumOf { it.invoice.netTotal - it.invoice.totalCost }
            rev to profit
        }
    }

    fun buildPurchasesChartSeries(state: MamoMainUiState, range: ChartRange): List<ChartPoint> {
        val postedPurchases = state.invoices.filter {
            it.invoice.status == InvoiceStatus.POSTED && it.invoice.invoiceType == InvoiceType.PURCHASE
        }
        return bucketInvoices(postedPurchases, range) { list ->
            val total = list.sumOf { it.invoice.netTotal }
            val paid = list.sumOf { it.invoice.paidAmount }
            total to paid
        }
    }

    fun buildExpensesVsRevenueSeries(state: MamoMainUiState, range: ChartRange): List<ChartPoint> {
        val postedSales = state.invoices.filter {
            it.invoice.status == InvoiceStatus.POSTED && it.invoice.invoiceType == InvoiceType.SALE
        }
        val expenses = state.expenses
        return buildTimeBuckets(range).map { (label, startMs, endMs) ->
            val rev = postedSales.filter { it.invoice.invoiceDateMs in startMs..endMs }.sumOf { it.invoice.netTotal }
            val exp = expenses.filter { it.expenseDateMs in startMs..endMs }.sumOf { it.amount }
            ChartPoint(label = label, primaryValue = rev, secondaryValue = exp)
        }
    }

    private fun bucketInvoices(
        invoices: List<InvoiceWithItems>,
        range: ChartRange,
        aggregator: (List<InvoiceWithItems>) -> Pair<Double, Double>
    ): List<ChartPoint> {
        return buildTimeBuckets(range).map { (label, startMs, endMs) ->
            val matching = invoices.filter { it.invoice.invoiceDateMs in startMs..endMs }
            val (p, s) = aggregator(matching)
            ChartPoint(label = label, primaryValue = p, secondaryValue = s)
        }
    }

    private fun buildTimeBuckets(range: ChartRange): List<Triple<String, Long, Long>> {
        if (range == ChartRange.DAILY) {
            val today = Calendar.getInstance()
            return listOf(0 to "صباح", 6 to "ظهراً", 12 to "عصراً", 18 to "مساءً").map { (hour, lbl) ->
                val start = (today.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }.timeInMillis
                val end = (today.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, hour + 5); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                }.timeInMillis
                Triple(lbl, start, end)
            }
        }
        val buckets = when (range) {
            ChartRange.DAILY -> 4
            ChartRange.WEEKLY -> 7
            ChartRange.MONTHLY -> 4
            ChartRange.YEARLY -> 6
        }
        val sdf = SimpleDateFormat(if (range == ChartRange.YEARLY) "MM/yy" else "dd/MM", Locale.US)
        return (buckets - 1 downTo 0).map { offset ->
            val end = Calendar.getInstance().apply {
                when (range) {
                    ChartRange.DAILY -> {}
                    ChartRange.WEEKLY -> add(Calendar.DAY_OF_YEAR, -offset)
                    ChartRange.MONTHLY -> add(Calendar.WEEK_OF_YEAR, -offset)
                    ChartRange.YEARLY -> add(Calendar.MONTH, -offset)
                }
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
            }
            val start = (end.clone() as Calendar).apply {
                when (range) {
                    ChartRange.DAILY -> {}
                    ChartRange.WEEKLY -> {}
                    ChartRange.MONTHLY -> add(Calendar.DAY_OF_YEAR, -6)
                    ChartRange.YEARLY -> set(Calendar.DAY_OF_MONTH, 1)
                }
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            Triple(sdf.format(Date(end.timeInMillis)), start.timeInMillis, end.timeInMillis)
        }
    }

    fun getTopSellingProducts(state: MamoMainUiState, limit: Int = 5): List<ProductProfitabilityRow> {
        val postedSales = state.invoices.filter {
            it.invoice.status == InvoiceStatus.POSTED && it.invoice.invoiceType == InvoiceType.SALE
        }
        val grouped = mutableMapOf<Long, ProductProfitabilityRow>()
        for (inv in postedSales) {
            for (item in inv.items) {
                val cur = grouped[item.productId]
                val qtyForCost = if (item.baseQuantity > 0.0) item.baseQuantity else item.quantity
                val itemCost = item.unitCostAtPosting * qtyForCost
                val itemProfit = item.lineTotal - itemCost
                if (cur == null) {
                    grouped[item.productId] = ProductProfitabilityRow(
                        productId = item.productId,
                        productName = item.productName,
                        soldQuantity = qtyForCost,
                        totalRevenue = item.lineTotal,
                        totalCost = itemCost,
                        grossProfit = itemProfit
                    )
                } else {
                    grouped[item.productId] = cur.copy(
                        soldQuantity = cur.soldQuantity + qtyForCost,
                        totalRevenue = cur.totalRevenue + item.lineTotal,
                        totalCost = cur.totalCost + itemCost,
                        grossProfit = cur.grossProfit + itemProfit
                    )
                }
            }
        }
        return grouped.values.sortedByDescending { it.soldQuantity }.take(limit)
    }

    fun computeProfitLoss(
        state: MamoMainUiState,
        returns: List<com.example.data.local.ReturnEntity>,
        period: PeriodFilter
    ): ProfitLossSummary {
        val postedSales = state.invoices.filter {
            it.invoice.status == InvoiceStatus.POSTED &&
                it.invoice.invoiceType == InvoiceType.SALE &&
                it.invoice.invoiceDateMs in period.startMs..period.endMs
        }
        val salesRevenue = postedSales.sumOf { it.invoice.netTotal }
        val returnsTotal = returns
            .filter { it.returnType == ReturnType.SALE_RETURN && it.returnDateMs in period.startMs..period.endMs }
            .sumOf { it.totalRefundAmount }
        val cogs = postedSales.sumOf { it.invoice.totalCost }
        val netSalesRevenue = (salesRevenue - returnsTotal).coerceAtLeast(0.0)
        val gross = AccountingEngine.calculateGrossProfit(netSalesRevenue, cogs)
        val operatingExpenses = state.expenses
            .filter { it.expenseDateMs in period.startMs..period.endMs }
            .sumOf { it.amount }
        val otherIncome = state.cashTransactions
            .filter { it.txType == CashTxType.OTHER_INCOME && it.occurredAtMs in period.startMs..period.endMs }
            .sumOf { it.amountSigned }
        val damagedLoss = state.stockMovements
            .filter { it.movementType == MovementType.DAMAGED && it.occurredAtMs in period.startMs..period.endMs }
            .sumOf { kotlin.math.abs(it.quantityDelta * it.unitCost) }
        val badDebtLoss = state.auditLogs
            .filter { it.action == "شطب دين معدوم" && it.occurredAtMs in period.startMs..period.endMs }
            .sumOf { log ->
                Regex("""المبلغ:\s*([0-9.]+)""").find(log.details)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            }
        val netProfit = AccountingEngine.calculateNetProfit(
            salesRevenue = netSalesRevenue + otherIncome,
            costOfGoodsSold = cogs,
            operatingExpenses = operatingExpenses + damagedLoss + badDebtLoss
        )
        return ProfitLossSummary(
            salesRevenue = AccountingEngine.roundMoney(salesRevenue),
            returnsTotal = AccountingEngine.roundMoney(returnsTotal),
            costOfGoodsSold = AccountingEngine.roundMoney(cogs),
            grossProfit = gross,
            operatingExpenses = AccountingEngine.roundMoney(operatingExpenses),
            otherIncome = AccountingEngine.roundMoney(otherIncome),
            badDebtLoss = AccountingEngine.roundMoney(badDebtLoss),
            damagedStockLoss = AccountingEngine.roundMoney(damagedLoss),
            netProfit = netProfit,
            invoiceCount = postedSales.size
        )
    }

    fun computeCapitalSummary(state: MamoMainUiState): CapitalSummary {
        val injected = state.cashTransactions.filter { it.txType == CashTxType.CAPITAL_IN }.sumOf { it.amountSigned }
        val drawings = state.cashTransactions.filter { it.txType == CashTxType.PERSONAL_DRAWING }.sumOf { -it.amountSigned }
        val allSales = state.invoices.filter { it.invoice.status == InvoiceStatus.POSTED && it.invoice.invoiceType == InvoiceType.SALE }
        val totalRev = allSales.sumOf { it.invoice.netTotal }
        val totalCogs = allSales.sumOf { it.invoice.totalCost }
        val totalExp = state.expenses.sumOf { it.amount }
        val otherIncome = state.cashTransactions.filter { it.txType == CashTxType.OTHER_INCOME }.sumOf { it.amountSigned }
        val otherOutflow = state.cashTransactions.filter { it.txType == CashTxType.OTHER_OUTFLOW }.sumOf { -it.amountSigned }
        val cumulativeProfit = AccountingEngine.calculateNetProfit(totalRev + otherIncome, totalCogs, totalExp + otherOutflow)
        val inventoryAdjustments = state.stockMovements
            .filter { it.movementType == MovementType.ADJUSTMENT || it.movementType == MovementType.DAMAGED }
            .sumOf { it.quantityDelta * it.unitCost }
        val equity = AccountingEngine.roundMoney(injected - drawings + cumulativeProfit + inventoryAdjustments)
        val m = state.dashboardMetrics
        val netWorth = AccountingEngine.roundMoney(m.cashboxBalance + m.inventoryValueAtCost + m.receivablesFromCustomers - m.payablesToSuppliers)
        return CapitalSummary(
            injectedCapital = AccountingEngine.roundMoney(injected),
            personalDrawings = AccountingEngine.roundMoney(drawings),
            cumulativeNetProfit = cumulativeProfit,
            equityCapital = equity,
            cashboxBalance = m.cashboxBalance,
            inventoryValueAtCost = m.inventoryValueAtCost,
            receivables = m.receivablesFromCustomers,
            payables = m.payablesToSuppliers,
            netWorthCheck = netWorth
        )
    }

    fun computeBalanceSheet(state: MamoMainUiState): BalanceSheetSummary {
        val cap = computeCapitalSummary(state)
        val cash = state.dashboardMetrics.cashboxBalance
        val inv = state.dashboardMetrics.inventoryValueAtCost
        val rec = state.dashboardMetrics.receivablesFromCustomers
        val totalAssets = AccountingEngine.roundMoney(cash + inv + rec)
        val payables = state.dashboardMetrics.payablesToSuppliers
        val taxPayable = AccountingEngine.roundMoney(
            state.invoices.filter { it.invoice.status == InvoiceStatus.POSTED && it.invoice.invoiceType == InvoiceType.SALE }
                .sumOf { it.invoice.taxAmount }
        )
        val totalLiabilities = AccountingEngine.roundMoney(payables + taxPayable)
        val retainedEarnings = AccountingEngine.roundMoney(totalAssets - totalLiabilities - cap.injectedCapital + cap.personalDrawings)
        val totalEquity = AccountingEngine.roundMoney(cap.injectedCapital - cap.personalDrawings + retainedEarnings)
        val balanced = kotlin.math.abs(totalAssets - (totalLiabilities + totalEquity)) < 1.0
        return BalanceSheetSummary(
            cashAndBanks = cash,
            inventoryAsset = inv,
            accountsReceivable = rec,
            totalAssets = totalAssets,
            accountsPayable = payables,
            taxPayable = taxPayable,
            totalLiabilities = totalLiabilities,
            paidInCapital = cap.injectedCapital,
            drawings = cap.personalDrawings,
            retainedEarnings = retainedEarnings,
            totalEquity = totalEquity,
            isBalanced = balanced
        )
    }

    fun computeCashFlow(state: MamoMainUiState, period: PeriodFilter): CashFlowSummary {
        val txs = state.cashTransactions.filter { it.occurredAtMs in period.startMs..period.endMs }
        val operatingIn = txs.filter {
            it.amountSigned > 0 && it.txType != CashTxType.CAPITAL_IN && it.txType != CashTxType.CASH_TRANSFER_IN
        }.sumOf { it.amountSigned }
        val operatingOut = txs.filter {
            it.amountSigned < 0 && it.txType != CashTxType.PERSONAL_DRAWING && it.txType != CashTxType.CASH_TRANSFER_OUT
        }.sumOf { -it.amountSigned }
        val netOperating = AccountingEngine.roundMoney(operatingIn - operatingOut)
        val capIn = txs.filter { it.txType == CashTxType.CAPITAL_IN }.sumOf { it.amountSigned }
        val drawings = txs.filter { it.txType == CashTxType.PERSONAL_DRAWING }.sumOf { -it.amountSigned }
        val netChange = AccountingEngine.roundMoney(netOperating + capIn - drawings)
        return CashFlowSummary(
            operatingInflows = AccountingEngine.roundMoney(operatingIn),
            operatingOutflows = AccountingEngine.roundMoney(operatingOut),
            netOperatingCash = netOperating,
            financingCapitalIn = AccountingEngine.roundMoney(capIn),
            financingDrawingsOut = AccountingEngine.roundMoney(drawings),
            netCashChange = netChange,
            endingCashBalance = state.dashboardMetrics.cashboxBalance
        )
    }

    fun buildPartyStatement(state: MamoMainUiState, party: PartyEntity): PartyStatement {
        val invs = state.invoices.filter { it.invoice.partyId == party.id }
        val pays = state.debtPayments.filter { it.partyId == party.id }
        return PartyStatement(party, invs, pays)
    }
}
