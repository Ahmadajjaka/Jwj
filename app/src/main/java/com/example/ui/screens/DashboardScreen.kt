package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.ChartPoint
import com.example.data.repository.ChartRange
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.InteractiveBarChart
import com.example.ui.components.InteractiveLineChart
import com.example.ui.components.MetricKpiCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters

@Composable
fun DashboardScreen(
    state: MamoMainUiState,
    viewModel: MamoViewModel,
    onQuickNewSale: () -> Unit,
    onQuickNewPurchase: () -> Unit,
    onQuickNewExpense: () -> Unit,
    onQuickDebtPayment: () -> Unit,
    onNavigateInventoryAlerts: () -> Unit,
    onNavigateDebts: () -> Unit
) {
    val mamoColors = LocalMamoColors.current
    val cur = state.settings.currencySymbol
    val m = state.dashboardMetrics
    val canSeeProfits = state.activeStaff?.canViewProfits == true

    val salesSeries = remember(state.invoices, state.chartRange) {
        viewModel.buildSalesChartSeries(state.chartRange)
    }
    val purchasesSeries = remember(state.invoices, state.chartRange) {
        viewModel.buildPurchasesChartSeries(state.chartRange)
    }
    val expVsRevSeries = remember(state.invoices, state.expenses, state.chartRange) {
        viewModel.buildExpensesVsRevenueSeries(state.chartRange)
    }
    val topProducts = remember(state.invoices) {
        viewModel.getTopSellingProducts(5)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Executive Hero Header
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(1.dp, mamoColors.gold.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.settings.storeName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الإدارة: ${state.settings.managerName}  •  المستخدم: ${state.activeStaff?.name ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD6E0EE)
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.syncCloudNow() },
                                enabled = !state.isSyncing,
                                border = BorderStroke(1.dp, mamoColors.gold),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = mamoColors.gold),
                                modifier = Modifier.testTag("dashboard_sync_button")
                            ) {
                                if (state.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = mamoColors.gold,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (state.settings.lastSyncAtMs > 0L) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                        contentDescription = "مزامنة سحابية",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مزامنة", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = if (m.isCashboxReconciled) "الصندوق مطابق 100%" else "تنبيه مطابقة الصندوق",
                                color = if (m.isCashboxReconciled) Color(0xFF6EE7B7) else mamoColors.warning,
                                backgroundColor = Color(0x22FFFFFF)
                            )
                            StatusPill(
                                text = "العملة: ${state.settings.currencyCode}",
                                color = mamoColors.gold,
                                backgroundColor = Color(0x22FFFFFF)
                            )
                        }
                    }
                }
            }
        }

        // Quick Action Shortcuts
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    QuickActionButton(
                        label = "بيع سريع",
                        icon = Icons.Default.PointOfSale,
                        container = mamoColors.gold,
                        content = Color(0xFF0B192C),
                        tag = "quick_sale_btn",
                        onClick = onQuickNewSale
                    )
                }
                item {
                    QuickActionButton(
                        label = "فاتورة شراء",
                        icon = Icons.Default.AddShoppingCart,
                        container = MaterialTheme.colorScheme.primary,
                        content = Color.White,
                        tag = "quick_purchase_btn",
                        onClick = onQuickNewPurchase
                    )
                }
                item {
                    QuickActionButton(
                        label = "تسجيل مصروف",
                        icon = Icons.Default.MoneyOff,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurface,
                        tag = "quick_expense_btn",
                        onClick = onQuickNewExpense
                    )
                }
                item {
                    QuickActionButton(
                        label = "دفعة دين",
                        icon = Icons.Default.Payments,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurface,
                        tag = "quick_debt_btn",
                        onClick = onQuickDebtPayment
                    )
                }
            }
        }

        // Live Alerts for Low Stock, Stale Items, Overdue Debts & Pending Sync
        if (m.lowStockCount > 0 || m.overdueDebtsCount > 0 || m.staleProductsCount > 0 || m.pendingSyncCount > 0) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.lowStockCount > 0) {
                        GlassCard(
                            highlightGold = true,
                            onClick = onNavigateInventoryAlerts,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "تنبيه مخزون",
                                    tint = mamoColors.warning
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "تنبيه المخزون: يوجد ${m.lowStockCount} أصناف قاربت على النفاد — اضغط للمراجعة",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = mamoColors.warning
                                )
                            }
                        }
                    }
                    if (m.staleProductsCount > 0) {
                        GlassCard(
                            onClick = onNavigateInventoryAlerts,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = "تنبيه أصناف راكدة",
                                    tint = mamoColors.gold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "أصناف راكدة: يوجد ${m.staleProductsCount} أصناف لم تُبع منذ ${state.settings.staleDaysThreshold} يوماً",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = mamoColors.gold
                                )
                            }
                        }
                    }
                    if (m.overdueDebtsCount > 0) {
                        GlassCard(
                            onClick = onNavigateDebts,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "تنبيه ديون",
                                    tint = mamoColors.loss
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "ديون متأخرة: يوجد ${m.overdueDebtsCount} فواتير تجاوزت تاريخ الاستحقاق",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = mamoColors.loss
                                )
                            }
                        }
                    }
                    if (m.pendingSyncCount > 0) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "طابور المزامنة السحابية: ${m.pendingSyncCount} عمليات محفوظة محلياً بانتظار الرفع",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedButton(onClick = { viewModel.syncCloudNow() }, enabled = !state.isSyncing) {
                                    Text("مزامنة")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 12 Live Dashboard KPIs in Responsive Grid
        item {
            val density = LocalDensity.current
            var columns by remember { mutableIntStateOf(2) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        val cols = if (with(density) { size.width.toDp() >= 840.dp }) 3 else 2
                        if (cols != columns) columns = cols
                    }
            ) {
                val kpis = listOf(
                    KpiSpec(
                        title = "مبيعات اليوم",
                        value = ArabicFormatters.formatMoney(m.todaySales, cur),
                        icon = Icons.Default.PointOfSale,
                        color = mamoColors.profit,
                        subtitle = "${m.todayInvoiceCount} فواتير اليوم"
                    ),
                    KpiSpec(
                        title = "مشتريات اليوم",
                        value = ArabicFormatters.formatMoney(m.todayPurchases, cur),
                        icon = Icons.Default.ShoppingBag,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    KpiSpec(
                        title = "صافي أرباح اليوم",
                        value = if (canSeeProfits) ArabicFormatters.formatMoney(m.todayNetProfit, cur) else "محجوب للصلاحية",
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        color = if (m.todayNetProfit >= 0) mamoColors.profit else mamoColors.loss,
                        subtitle = if (canSeeProfits) "بعد خصم التكلفة والمصاريف" else null
                    ),
                    KpiSpec(
                        title = "إجمالي المصاريف",
                        value = ArabicFormatters.formatMoney(m.totalExpenses, cur),
                        icon = Icons.Default.MoneyOff,
                        color = mamoColors.loss,
                        subtitle = "اليوم: ${ArabicFormatters.formatMoney(m.todayExpenses, cur)}"
                    ),
                    KpiSpec(
                        title = "رصيد الصندوق",
                        value = ArabicFormatters.formatMoney(m.cashboxBalance, cur),
                        icon = Icons.Default.AccountBalanceWallet,
                        color = if (m.isCashboxReconciled) mamoColors.profit else mamoColors.warning,
                        subtitle = if (m.isCashboxReconciled) "مطابق 100%" else "يحتاج مطابقة"
                    ),
                    KpiSpec(
                        title = "رأس المال الحالي",
                        value = if (canSeeProfits) ArabicFormatters.formatMoney(m.currentCapital, cur) else "محجوب للصلاحية",
                        icon = Icons.Default.Savings,
                        color = mamoColors.gold,
                        subtitle = "صافي حقوق الملكية والمخزون"
                    ),
                    KpiSpec(
                        title = "قيمة المخزون بالتكلفة",
                        value = ArabicFormatters.formatMoney(m.inventoryValueAtCost, cur),
                        icon = Icons.Default.Inventory2,
                        color = MaterialTheme.colorScheme.tertiary,
                        subtitle = "${state.products.size} صنف نشط"
                    ),
                    KpiSpec(
                        title = "ديون الزبائن (لنا)",
                        value = ArabicFormatters.formatMoney(m.receivablesFromCustomers, cur),
                        icon = Icons.Default.AttachMoney,
                        color = mamoColors.profit
                    ),
                    KpiSpec(
                        title = "ديون الموردين (علينا)",
                        value = ArabicFormatters.formatMoney(m.payablesToSuppliers, cur),
                        icon = Icons.Default.AccountBalanceWallet,
                        color = mamoColors.warning
                    ),
                    KpiSpec(
                        title = "إجمالي الإيرادات",
                        value = ArabicFormatters.formatMoney(m.totalRevenue, cur),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        color = mamoColors.profit,
                        subtitle = "كافة المبيعات المعتمدة"
                    ),
                    KpiSpec(
                        title = "إجمالي الفواتير",
                        value = "${m.totalInvoiceCount}",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        color = MaterialTheme.colorScheme.primary,
                        subtitle = "اليوم: ${m.todayInvoiceCount}"
                    ),
                    KpiSpec(
                        title = "المنتجات الناقصة",
                        value = "${m.lowStockCount}",
                        icon = Icons.Default.WarningAmber,
                        color = if (m.lowStockCount > 0) mamoColors.loss else mamoColors.profit,
                        subtitle = "راكدة: ${m.staleProductsCount}"
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    kpis.chunked(columns).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { kpi ->
                                MetricKpiCard(
                                    title = kpi.title,
                                    value = kpi.value,
                                    icon = kpi.icon,
                                    accentColor = kpi.color,
                                    subtitle = kpi.subtitle,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Latest Financial Transactions Feed (KPI #13)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("آخر العمليات المالية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                val latestTxs = state.cashTransactions.take(6)
                if (latestTxs.isEmpty()) {
                    Text(
                        text = "لا توجد عمليات مالية مسجلة بعد — ابدأ بفاتورة بيع أو شراء أو حركة صندوق.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        latestTxs.forEach { tx ->
                            val isIn = tx.amountSigned >= 0.0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${ArabicFormatters.cashTxTypeLabel(tx.txType)} • ${tx.accountName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${tx.description} • ${ArabicFormatters.formatDateTime(tx.occurredAtMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = (if (isIn) "+" else "") + ArabicFormatters.formatMoney(tx.amountSigned, cur),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIn) mamoColors.profit else mamoColors.loss
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chart Range Selector (Today / Week / Month / Year)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("الرسوم البيانية التفاعلية", style = MaterialTheme.typography.titleLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf(
                            ChartRange.DAILY to "اليوم",
                            ChartRange.WEEKLY to "الأسبوع",
                            ChartRange.MONTHLY to "الشهر",
                            ChartRange.YEARLY to "السنة"
                        )
                    ) { (range, label) ->
                        FilterChip(
                            selected = state.chartRange == range,
                            onClick = { viewModel.setChartRange(range) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        val rangeLabel = when (state.chartRange) {
            ChartRange.DAILY -> "اليوم"
            ChartRange.WEEKLY -> "الأسبوع"
            ChartRange.MONTHLY -> "الشهر"
            ChartRange.YEARLY -> "السنة"
        }

        // Chart 1: Sales Trend
        item {
            InteractiveLineChart(
                title = "تطور المبيعات ($rangeLabel)",
                points = salesSeries,
                currencySymbol = cur,
                lineColor = MaterialTheme.colorScheme.primary
            )
        }

        // Chart 2: Purchases Trend
        item {
            InteractiveBarChart(
                title = "المشتريات والمدفوع للموردين ($rangeLabel)",
                points = purchasesSeries,
                primaryLabel = "إجمالي الشراء",
                secondaryLabel = "المدفوع",
                currencySymbol = cur,
                primaryColor = MaterialTheme.colorScheme.tertiary,
                secondaryColor = mamoColors.gold
            )
        }

        // Chart 3: Profit & Loss (only if permitted)
        if (canSeeProfits) {
            item {
                InteractiveBarChart(
                    title = "المبيعات مقابل إجمالي الربح ($rangeLabel)",
                    points = salesSeries,
                    primaryLabel = "الإيرادات",
                    secondaryLabel = "الربح الإجمالي",
                    currencySymbol = cur,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    secondaryColor = mamoColors.profit
                )
            }
        }

        // Chart 4: Expenses vs Revenue
        item {
            InteractiveBarChart(
                title = "مقارنة الإيرادات والمصروفات ($rangeLabel)",
                points = expVsRevSeries,
                primaryLabel = "الإيرادات",
                secondaryLabel = "المصروفات",
                currencySymbol = cur,
                primaryColor = mamoColors.gold,
                secondaryColor = mamoColors.loss
            )
        }

        // Chart 4: Top Selling Products
        item {
            val topChartPoints = topProducts.map {
                ChartPoint(
                    label = it.productName.take(10),
                    primaryValue = it.totalRevenue,
                    secondaryValue = if (canSeeProfits) it.grossProfit else 0.0
                )
            }
            InteractiveBarChart(
                title = "أكثر المنتجات مبيعاً",
                points = topChartPoints,
                primaryLabel = "المبيعات",
                secondaryLabel = if (canSeeProfits) "الربح" else null,
                currencySymbol = cur,
                primaryColor = MaterialTheme.colorScheme.tertiary,
                secondaryColor = mamoColors.profit
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    tag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .height(48.dp)
            .testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

private data class KpiSpec(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val color: Color,
    val subtitle: String? = null
)
