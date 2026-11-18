package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CashTxType
import com.example.data.local.InvoiceStatus
import com.example.data.local.InvoiceType
import com.example.data.local.PartyType
import com.example.data.local.ReturnEntity
import com.example.data.local.ReturnType
import com.example.data.local.StockCountEntity
import com.example.data.repository.AccountingEngine
import com.example.data.repository.PeriodFilter
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters
import java.util.Calendar

enum class AccountingReportType(val title: String) {
    SALES("تقرير المبيعات"),
    PURCHASES("تقرير المشتريات"),
    PROFIT_LOSS("الأرباح والخسائر"),
    EXPENSES("تقرير المصروفات"),
    CASHBOX("حركة الصندوق"),
    DEBTS("تقرير الديون"),
    INVENTORY("تقرير المخزون"),
    TOP_PROFIT_PRODUCTS("الأكثر ربحية"),
    CAPITAL("تقرير رأس المال"),
    CASH_FLOW("التدفق النقدي"),
    BALANCE_SHEET("الميزانية العمومية"),
    MANAGER_DRAWINGS("سحوبات المدير"),
    SUPPLIERS_REPORT("تقرير الموردين"),
    CUSTOMERS_REPORT("تقرير الزبائن"),
    STOCK_VARIANCES("فروقات الجرد"),
    PROFIT_MARGINS("تقرير هامش الربح")
}

@Composable
fun ReportsScreen(
    state: MamoMainUiState,
    viewModel: MamoViewModel
) {
    val context = LocalContext.current
    val cur = state.settings.currencySymbol
    val mamoColors = LocalMamoColors.current
    val canSeeProfits = state.activeStaff?.canViewProfits == true

    val stockCounts by viewModel.stockCounts.collectAsStateWithLifecycle()
    val returns by viewModel.returns.collectAsStateWithLifecycle()

    var selectedReport by remember { mutableStateOf(AccountingReportType.PROFIT_LOSS) }
    var periodPreset by remember { mutableStateOf("الشهر") }
    var customStartMs by remember { mutableStateOf(ArabicFormatters.startOfTodayMs() - 7L * 86_400_000L) }
    var customEndMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val period = remember(periodPreset, customStartMs, customEndMs) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        when (periodPreset) {
            "فترة مخصصة" -> {
                val label = "${ArabicFormatters.formatDate(customStartMs)} - ${ArabicFormatters.formatDate(customEndMs)}"
                PeriodFilter(label = label, startMs = customStartMs, endMs = customEndMs + 86_399_000L)
            }
            "اليوم" -> PeriodFilter("اليوم", ArabicFormatters.startOfTodayMs(), now + 86_400_000L)
            "الأسبوع" -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                PeriodFilter("الأسبوع", cal.timeInMillis, now + 86_400_000L)
            }
            "الشهر" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                PeriodFilter("الشهر", cal.timeInMillis, now + 86_400_000L)
            }
            "السنة" -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                PeriodFilter("السنة", cal.timeInMillis, now + 86_400_000L)
            }
            else -> PeriodFilter("الكل", 0L, now + 86_400_000L)
        }
    }

    val openCustomRangePicker = {
        val startCal = Calendar.getInstance().apply { timeInMillis = customStartMs }
        DatePickerDialog(
            context,
            { _, sy, sm, sd ->
                val pickedStart = Calendar.getInstance().apply {
                    set(sy, sm, sd, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                customStartMs = pickedStart
                val endCal = Calendar.getInstance().apply { timeInMillis = maxOf(customEndMs, pickedStart) }
                DatePickerDialog(
                    context,
                    { _, ey, em, ed ->
                        val pickedEnd = Calendar.getInstance().apply {
                            set(ey, em, ed, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        customEndMs = maxOf(pickedEnd, pickedStart)
                        periodPreset = "فترة مخصصة"
                    },
                    endCal.get(Calendar.YEAR),
                    endCal.get(Calendar.MONTH),
                    endCal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            startCal.get(Calendar.YEAR),
            startCal.get(Calendar.MONTH),
            startCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val reportData = remember(selectedReport, period, state, stockCounts, returns, canSeeProfits) {
        buildReportRows(selectedReport, period, state, stockCounts, returns, viewModel, cur, canSeeProfits)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("مركز التقارير المحاسبية (16 تقريراً)", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("اليوم", "الأسبوع", "الشهر", "السنة", "الكل")) { preset ->
                    FilterChip(
                        selected = periodPreset == preset,
                        onClick = { periodPreset = preset },
                        label = { Text(preset) }
                    )
                }
                item {
                    FilterChip(
                        selected = periodPreset == "فترة مخصصة",
                        onClick = { openCustomRangePicker() },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        label = { Text("فترة مخصصة") },
                        modifier = Modifier.testTag("custom_period_chip")
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AccountingReportType.entries) { type ->
                    FilterChip(
                        selected = selectedReport == type,
                        onClick = { selectedReport = type },
                        label = { Text(type.title) },
                        modifier = Modifier.testTag("report_chip_${type.name}")
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        viewModel.exportPdfReport(
                            context = context,
                            title = selectedReport.title,
                            periodLabel = period.label,
                            rows = reportData.summaryRows
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("export_report_pdf_btn")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصدير PDF")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.exportCsvReport(
                            context = context,
                            prefix = selectedReport.name.lowercase(),
                            headers = reportData.csvHeaders,
                            rows = reportData.csvRows
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("export_report_excel_btn")
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصدير Excel")
                }
            }
        }

        item {
            GlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                Text("${selectedReport.title} — (${period.label})", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                reportData.summaryRows.forEachIndexed { idx, (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                label.contains("صافي الربح") || label.contains("توازن") || label.contains("مطابق") -> mamoColors.profit
                                label.contains("عجز") || label.contains("خسارة") || label.contains("معدوم") || label.contains("تالف") -> mamoColors.loss
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    if (idx < reportData.summaryRows.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private data class BuiltReport(
    val summaryRows: List<Pair<String, String>>,
    val csvHeaders: List<String>,
    val csvRows: List<List<String>>
)

private fun unauthorizedReport(): BuiltReport = BuiltReport(
    summaryRows = listOf("تنبيه الصلاحيات" to "عرض الأرباح والهوامش وحقوق الملكية متاح للمدير والمحاسب فقط"),
    csvHeaders = listOf("التنبيه"),
    csvRows = listOf(listOf("غير مصرح"))
)

private fun buildReportRows(
    type: AccountingReportType,
    period: PeriodFilter,
    state: MamoMainUiState,
    stockCounts: List<StockCountEntity>,
    returns: List<ReturnEntity>,
    viewModel: MamoViewModel,
    cur: String,
    canSeeProfits: Boolean
): BuiltReport {
    return when (type) {
        AccountingReportType.SALES -> {
            val list = state.invoices.filter {
                it.invoice.invoiceType == InvoiceType.SALE &&
                    it.invoice.status == InvoiceStatus.POSTED &&
                    it.invoice.invoiceDateMs in period.startMs..period.endMs
            }
            val periodReturns = returns.filter {
                it.returnType == ReturnType.SALE_RETURN && it.returnDateMs in period.startMs..period.endMs
            }
            val total = list.sumOf { it.invoice.netTotal }
            val paid = list.sumOf { it.invoice.paidAmount }
            val rem = list.sumOf { it.invoice.remainingAmount }
            val retTotal = periodReturns.sumOf { it.totalRefundAmount }
            val rows = listOf(
                "عدد فواتير البيع المعتمدة" to "${list.size}",
                "إجمالي المبيعات" to ArabicFormatters.formatMoney(total, cur),
                "مرتجعات المبيعات" to ArabicFormatters.formatMoney(retTotal, cur),
                "المقبوضات النقدية" to ArabicFormatters.formatMoney(paid, cur),
                "المبيعات الآجلة المتبقية" to ArabicFormatters.formatMoney(rem, cur)
            ) + list.take(25).map {
                "${it.invoice.invoiceNumber} — ${it.invoice.partyName}" to ArabicFormatters.formatMoney(it.invoice.netTotal, cur)
            }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("رقم الفاتورة", "الزبون", "التاريخ", "الصافي", "الضريبة", "المدفوع", "المتبقي"),
                csvRows = list.map {
                    listOf(
                        it.invoice.invoiceNumber,
                        it.invoice.partyName,
                        ArabicFormatters.formatDate(it.invoice.invoiceDateMs),
                        it.invoice.netTotal.toString(),
                        it.invoice.taxAmount.toString(),
                        it.invoice.paidAmount.toString(),
                        it.invoice.remainingAmount.toString()
                    )
                }
            )
        }
        AccountingReportType.PURCHASES -> {
            val list = state.invoices.filter {
                it.invoice.invoiceType == InvoiceType.PURCHASE &&
                    it.invoice.status == InvoiceStatus.POSTED &&
                    it.invoice.invoiceDateMs in period.startMs..period.endMs
            }
            val periodReturns = returns.filter {
                it.returnType == ReturnType.PURCHASE_RETURN && it.returnDateMs in period.startMs..period.endMs
            }
            val total = list.sumOf { it.invoice.netTotal }
            val rows = listOf(
                "عدد فواتير الشراء المعتمدة" to "${list.size}",
                "إجمالي المشتريات (شامل الشحن)" to ArabicFormatters.formatMoney(total, cur),
                "مرتجعات المشتريات" to ArabicFormatters.formatMoney(periodReturns.sumOf { it.totalRefundAmount }, cur),
                "المدفوع للموردين" to ArabicFormatters.formatMoney(list.sumOf { it.invoice.paidAmount }, cur),
                "المتبقي للموردين" to ArabicFormatters.formatMoney(list.sumOf { it.invoice.remainingAmount }, cur)
            ) + list.take(25).map {
                "${it.invoice.invoiceNumber} — ${it.invoice.partyName}" to ArabicFormatters.formatMoney(it.invoice.netTotal, cur)
            }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("رقم الفاتورة", "المورد", "التاريخ", "الشحن الإضافي", "الصافي", "المدفوع", "المتبقي"),
                csvRows = list.map {
                    listOf(
                        it.invoice.invoiceNumber,
                        it.invoice.partyName,
                        ArabicFormatters.formatDate(it.invoice.invoiceDateMs),
                        it.invoice.extraCosts.toString(),
                        it.invoice.netTotal.toString(),
                        it.invoice.paidAmount.toString(),
                        it.invoice.remainingAmount.toString()
                    )
                }
            )
        }
        AccountingReportType.PROFIT_LOSS -> {
            if (!canSeeProfits) return unauthorizedReport()
            val pl = viewModel.computeProfitLoss(period)
            val rows = listOf(
                "عدد فواتير المبيعات" to "${pl.invoiceCount}",
                "إجمالي المبيعات المعتمدة" to ArabicFormatters.formatMoney(pl.salesRevenue, cur),
                "مردودات المبيعات" to ArabicFormatters.formatMoney(pl.returnsTotal, cur),
                "تكلفة البضاعة المباعة (COGS)" to ArabicFormatters.formatMoney(pl.costOfGoodsSold, cur),
                "إجمالي الربح التشغيلي" to ArabicFormatters.formatMoney(pl.grossProfit, cur),
                "إيرادات أخرى" to ArabicFormatters.formatMoney(pl.otherIncome, cur),
                "المصاريف التشغيلية" to ArabicFormatters.formatMoney(pl.operatingExpenses, cur),
                "خسائر الديون المعدومة" to ArabicFormatters.formatMoney(pl.badDebtLoss, cur),
                "خسائر البضاعة التالفة" to ArabicFormatters.formatMoney(pl.damagedStockLoss, cur),
                "صافي الربح الحقيقي" to ArabicFormatters.formatMoney(pl.netProfit, cur)
            )
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("البند", "القيمة"),
                csvRows = rows.map { listOf(it.first, it.second) }
            )
        }
        AccountingReportType.EXPENSES -> {
            val list = state.expenses.filter { it.expenseDateMs in period.startMs..period.endMs }
            val byCat = list.groupBy { it.category }.mapValues { (_, items) -> items.sumOf { it.amount } }
            val rows = listOf("إجمالي المصروفات" to ArabicFormatters.formatMoney(list.sumOf { it.amount }, cur)) +
                byCat.map { (cat, sum) -> cat to ArabicFormatters.formatMoney(sum, cur) }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("التصنيف", "الوصف", "التاريخ", "المبلغ", "الدفع"),
                csvRows = list.map { listOf(it.category, it.description, ArabicFormatters.formatDate(it.expenseDateMs), it.amount.toString(), it.paymentMethod) }
            )
        }
        AccountingReportType.CASHBOX -> {
            val list = state.cashTransactions.filter { it.occurredAtMs in period.startMs..period.endMs }
            val inflows = list.filter { it.amountSigned > 0 }.sumOf { it.amountSigned }
            val outflows = list.filter { it.amountSigned < 0 }.sumOf { -it.amountSigned }
            val rows = listOf(
                "رصيد الصناديق والحسابات" to ArabicFormatters.formatMoney(state.cashboxBalance, cur),
                "مقبوضات الفترة" to ArabicFormatters.formatMoney(inflows, cur),
                "مدفوعات الفترة" to ArabicFormatters.formatMoney(outflows, cur),
                "المطابقة المحاسبية" to if (state.dashboardMetrics.isCashboxReconciled) "مطابق 100%" else "غير مطابق"
            ) + list.take(25).map {
                "${ArabicFormatters.cashTxTypeLabel(it.txType)} (${it.accountName}) — ${it.description}" to ArabicFormatters.formatMoney(it.amountSigned, cur)
            }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("النوع", "الحساب", "الوصف", "الموظف", "المبلغ", "التاريخ"),
                csvRows = list.map { listOf(ArabicFormatters.cashTxTypeLabel(it.txType), it.accountName, it.description, it.staffName, it.amountSigned.toString(), ArabicFormatters.formatDateTime(it.occurredAtMs)) }
            )
        }
        AccountingReportType.DEBTS -> {
            val custs = state.parties.filter { it.partyType == PartyType.CUSTOMER && it.balanceDue > 0 }
            val sups = state.parties.filter { it.partyType == PartyType.SUPPLIER && it.balanceDue > 0 }
            val rows = listOf(
                "إجمالي الديون المستحقة لنا" to ArabicFormatters.formatMoney(custs.sumOf { it.balanceDue }, cur),
                "إجمالي الديون المستحقة علينا" to ArabicFormatters.formatMoney(sups.sumOf { it.balanceDue }, cur)
            ) + custs.map { "زبون: ${it.name}" to ArabicFormatters.formatMoney(it.balanceDue, cur) } +
                sups.map { "مورد: ${it.name}" to ArabicFormatters.formatMoney(it.balanceDue, cur) }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("النوع", "الاسم", "الهاتف", "الإجمالي", "المدفوع", "المتبقي"),
                csvRows = (custs + sups).map { listOf(it.partyType, it.name, it.phone, it.totalAmount.toString(), it.paidAmount.toString(), it.balanceDue.toString()) }
            )
        }
        AccountingReportType.INVENTORY -> {
            val rows = listOf(
                "عدد الأصناف النشطة" to "${state.products.size}",
                "الأصناف الناقصة" to "${state.dashboardMetrics.lowStockCount}",
                "الأصناف الراكدة" to "${state.dashboardMetrics.staleProductsCount}",
                "إجمالي قيمة المخزون بالتكلفة" to ArabicFormatters.formatMoney(state.dashboardMetrics.inventoryValueAtCost, cur)
            ) + state.products.map {
                "${it.name} (${ArabicFormatters.formatNumber(it.quantity)} ${it.baseUnit})" to ArabicFormatters.formatMoney(it.quantity * it.averageCost, cur)
            }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("الصنف", "SKU", "التصنيف", "الوحدة", "الباركود", "الكمية", "متوسط التكلفة", "سعر البيع", "القيمة بالتكلفة"),
                csvRows = state.products.map { listOf(it.name, it.sku, it.category, it.baseUnit, it.barcode, it.quantity.toString(), it.averageCost.toString(), it.sellingPrice.toString(), (it.quantity * it.averageCost).toString()) }
            )
        }
        AccountingReportType.TOP_PROFIT_PRODUCTS -> {
            val list = viewModel.getTopSellingProducts(20)
            val rows = list.map {
                "${it.productName} (مبيع: ${ArabicFormatters.formatNumber(it.soldQuantity)})" to
                    if (canSeeProfits) "ربح: ${ArabicFormatters.formatMoney(it.grossProfit, cur)}"
                    else "إيراد: ${ArabicFormatters.formatMoney(it.totalRevenue, cur)}"
            }
            BuiltReport(
                summaryRows = rows.ifEmpty { listOf("لا توجد مبيعات بعد" to "0") },
                csvHeaders = listOf("الصنف", "الكمية المباعة", "الإيراد", "التكلفة", "الربح"),
                csvRows = list.map { listOf(it.productName, it.soldQuantity.toString(), it.totalRevenue.toString(), it.totalCost.toString(), it.grossProfit.toString()) }
            )
        }
        AccountingReportType.CAPITAL -> {
            if (!canSeeProfits) return unauthorizedReport()
            val cap = viewModel.computeCapitalSummary()
            val rows = listOf(
                "رأس المال المودع" to ArabicFormatters.formatMoney(cap.injectedCapital, cur),
                "المسحوبات الشخصية" to ArabicFormatters.formatMoney(cap.personalDrawings, cur),
                "صافي الأرباح المتراكمة" to ArabicFormatters.formatMoney(cap.cumulativeNetProfit, cur),
                "حقوق الملكية المحسوبة" to ArabicFormatters.formatMoney(cap.equityCapital, cur),
                "السيولة النقدية بالصندوق" to ArabicFormatters.formatMoney(cap.cashboxBalance, cur),
                "قيمة المخزون بالتكلفة" to ArabicFormatters.formatMoney(cap.inventoryValueAtCost, cur),
                "ديون الزبائن (لنا)" to ArabicFormatters.formatMoney(cap.receivables, cur),
                "ديون الموردين (علينا)" to ArabicFormatters.formatMoney(cap.payables, cur),
                "صافي المركز المالي الفعلي" to ArabicFormatters.formatMoney(cap.netWorthCheck, cur)
            )
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("البند", "القيمة"),
                csvRows = rows.map { listOf(it.first, it.second) }
            )
        }
        AccountingReportType.CASH_FLOW -> {
            val cf = viewModel.computeCashFlow(period)
            val rows = listOf(
                "التدفقات التشغيلية الداخلة" to ArabicFormatters.formatMoney(cf.operatingInflows, cur),
                "التدفقات التشغيلية الخارجة" to ArabicFormatters.formatMoney(cf.operatingOutflows, cur),
                "صافي التدفق التشغيلي" to ArabicFormatters.formatMoney(cf.netOperatingCash, cur),
                "تمويل وإيداع رأس مال" to ArabicFormatters.formatMoney(cf.financingCapitalIn, cur),
                "مسحوبات شخصية للمالك" to ArabicFormatters.formatMoney(cf.financingDrawingsOut, cur),
                "صافي التغير النقدي للفترة" to ArabicFormatters.formatMoney(cf.netCashChange, cur),
                "رصيد النقد في نهاية الفترة" to ArabicFormatters.formatMoney(cf.endingCashBalance, cur)
            )
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("بند التدفق النقدي", "القيمة"),
                csvRows = rows.map { listOf(it.first, it.second) }
            )
        }
        AccountingReportType.BALANCE_SHEET -> {
            if (!canSeeProfits) return unauthorizedReport()
            val bs = viewModel.computeBalanceSheet()
            val rows = listOf(
                "النقدية والبنوك (الأصول المتداولة)" to ArabicFormatters.formatMoney(bs.cashAndBanks, cur),
                "قيمة المخزون السلعي" to ArabicFormatters.formatMoney(bs.inventoryAsset, cur),
                "ذمم الزبائن المدينة" to ArabicFormatters.formatMoney(bs.accountsReceivable, cur),
                "إجمالي الأصول" to ArabicFormatters.formatMoney(bs.totalAssets, cur),
                "ذمم الموردين الدائنة" to ArabicFormatters.formatMoney(bs.accountsPayable, cur),
                "الضرائب المستحقة" to ArabicFormatters.formatMoney(bs.taxPayable, cur),
                "إجمالي الالتزامات" to ArabicFormatters.formatMoney(bs.totalLiabilities, cur),
                "رأس المال المدفوع" to ArabicFormatters.formatMoney(bs.paidInCapital, cur),
                "المسحوبات الشخصية (-)" to ArabicFormatters.formatMoney(bs.drawings, cur),
                "الأرباح المحتجزة والمتراكمة" to ArabicFormatters.formatMoney(bs.retainedEarnings, cur),
                "إجمالي حقوق الملكية" to ArabicFormatters.formatMoney(bs.totalEquity, cur),
                "معادلة الميزانية (الأصول = الخصوم + الملكية)" to if (bs.isBalanced) "متوازنة ومطابقة" else "يوجد فرق تسوية"
            )
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("الحساب", "الرصيد"),
                csvRows = rows.map { listOf(it.first, it.second) }
            )
        }
        AccountingReportType.MANAGER_DRAWINGS -> {
            if (!canSeeProfits) return unauthorizedReport()
            val list = state.cashTransactions.filter {
                it.txType == CashTxType.PERSONAL_DRAWING && it.occurredAtMs in period.startMs..period.endMs
            }
            val total = list.sumOf { -it.amountSigned }
            val rows = listOf(
                "عدد عمليات السحب الشخصي" to "${list.size}",
                "إجمالي سحوبات المدير خلال الفترة" to ArabicFormatters.formatMoney(total, cur)
            ) + list.map {
                "${ArabicFormatters.formatDate(it.occurredAtMs)} — ${it.description}" to ArabicFormatters.formatMoney(-it.amountSigned, cur)
            }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("التاريخ", "الحساب", "الوصف", "الموظف", "المبلغ"),
                csvRows = list.map { listOf(ArabicFormatters.formatDateTime(it.occurredAtMs), it.accountName, it.description, it.staffName, (-it.amountSigned).toString()) }
            )
        }
        AccountingReportType.SUPPLIERS_REPORT -> {
            val suppliers = state.parties.filter { it.partyType == PartyType.SUPPLIER }
            val rows = listOf(
                "عدد الموردين" to "${suppliers.size}",
                "إجمالي المشتريات" to ArabicFormatters.formatMoney(suppliers.sumOf { it.totalAmount }, cur),
                "إجمالي المسدد للموردين" to ArabicFormatters.formatMoney(suppliers.sumOf { it.paidAmount }, cur),
                "إجمالي المستحق علينا للموردين" to ArabicFormatters.formatMoney(suppliers.sumOf { it.balanceDue }, cur)
            ) + suppliers.map {
                "${it.name} (إجمالي: ${ArabicFormatters.formatNumber(it.totalAmount)})" to "متبقي: ${ArabicFormatters.formatMoney(it.balanceDue, cur)}"
            }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("المورد", "الهاتف", "العنوان", "إجمالي التعامل", "المسدد", "المتبقي علينا"),
                csvRows = suppliers.map { listOf(it.name, it.phone, it.address, it.totalAmount.toString(), it.paidAmount.toString(), it.balanceDue.toString()) }
            )
        }
        AccountingReportType.CUSTOMERS_REPORT -> {
            val customers = state.parties.filter { it.partyType == PartyType.CUSTOMER }
            val rows = listOf(
                "عدد الزبائن" to "${customers.size}",
                "إجمالي المبيعات للزبائن" to ArabicFormatters.formatMoney(customers.sumOf { it.totalAmount }, cur),
                "إجمالي التحصيل من الزبائن" to ArabicFormatters.formatMoney(customers.sumOf { it.paidAmount }, cur),
                "إجمالي الذمم المستحقة لنا" to ArabicFormatters.formatMoney(customers.sumOf { it.balanceDue }, cur)
            ) + customers.map {
                "${it.name} (إجمالي: ${ArabicFormatters.formatNumber(it.totalAmount)})" to "ذمة: ${ArabicFormatters.formatMoney(it.balanceDue, cur)}"
            }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("الزبون", "الهاتف", "العنوان", "إجمالي التعامل", "المحصل", "المتبقي لنا"),
                csvRows = customers.map { listOf(it.name, it.phone, it.address, it.totalAmount.toString(), it.paidAmount.toString(), it.balanceDue.toString()) }
            )
        }
        AccountingReportType.STOCK_VARIANCES -> {
            val list = stockCounts.filter { it.countedAtMs in period.startMs..period.endMs }
            val totalVarianceVal = list.sumOf { it.varianceValue }
            val rows = listOf(
                "عدد جلسات الجرد" to "${list.size}",
                "جلسات بانتظار الاعتماد" to "${list.count { it.status == "PENDING" }}",
                "صافي قيمة الفروقات بالتكلفة" to ArabicFormatters.formatMoney(totalVarianceVal, cur)
            ) + list.map {
                "${it.productName} (دفتري ${ArabicFormatters.formatNumber(it.systemQty)} -> فعلي ${ArabicFormatters.formatNumber(it.countedQty)})" to
                    "${if (it.varianceQty >= 0) "+" else ""}${ArabicFormatters.formatNumber(it.varianceQty)} (${ArabicFormatters.formatMoney(it.varianceValue, cur)})"
            }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("الصنف", "الدفتري", "الفعلي", "الفرق بالكمية", "قيمة الفرق", "الحالة", "بواسطة", "التاريخ"),
                csvRows = list.map {
                    listOf(
                        it.productName,
                        it.systemQty.toString(),
                        it.countedQty.toString(),
                        it.varianceQty.toString(),
                        it.varianceValue.toString(),
                        it.status,
                        it.countedBy,
                        ArabicFormatters.formatDateTime(it.countedAtMs)
                    )
                }
            )
        }
        AccountingReportType.PROFIT_MARGINS -> {
            if (!canSeeProfits) return unauthorizedReport()
            val rowsData = state.products.map { p ->
                val marginPct = AccountingEngine.calculateProfitMarginPercent(p.sellingPrice, p.averageCost)
                val unitProfit = AccountingEngine.roundMoney(p.sellingPrice - p.averageCost)
                Triple(p, unitProfit, marginPct)
            }.sortedByDescending { it.third }
            val avgMargin = if (rowsData.isNotEmpty()) AccountingEngine.roundMoney(rowsData.map { it.third }.average()) else 0.0
            val rows = listOf(
                "متوسط هامش الربح العام" to "${ArabicFormatters.formatNumber(avgMargin)}%",
                "عدد الأصناف المحللة" to "${rowsData.size}"
            ) + rowsData.map { (p, profit, pct) ->
                "${p.name} (تكلفة ${ArabicFormatters.formatNumber(p.averageCost)} | بيع ${ArabicFormatters.formatNumber(p.sellingPrice)})" to
                    "${ArabicFormatters.formatNumber(pct)}% (${ArabicFormatters.formatMoney(profit, cur)})"
            }
            BuiltReport(
                summaryRows = rows,
                csvHeaders = listOf("الصنف", "الوحدة", "متوسط التكلفة", "سعر المفرق", "سعر الجملة", "ربح الوحدة", "هامش الربح %"),
                csvRows = rowsData.map { (p, profit, pct) ->
                    listOf(p.name, p.baseUnit, p.averageCost.toString(), p.sellingPrice.toString(), p.wholesalePrice.toString(), profit.toString(), pct.toString())
                }
            )
        }
    }
}
