package com.example.data.remote

import com.example.data.local.InvoiceStatus
import com.example.data.local.InvoiceType
import com.example.data.local.PartyType
import com.example.data.local.ProductEntity
import com.example.data.repository.DashboardMetrics
import com.example.data.repository.ProductProfitabilityRow
import com.example.ui.MamoMainUiState
import com.example.utils.ArabicFormatters

object MamoAiTools {

    fun findStaleProducts(
        products: List<ProductEntity>,
        thresholdDays: Int,
        nowMs: Long = System.currentTimeMillis()
    ): List<ProductEntity> {
        val thresholdMs = thresholdDays.coerceAtLeast(7) * 86_400_000L
        return products.filter { p ->
            p.quantity > 0.0 && (
                (p.lastSaleDateMs > 0L && nowMs - p.lastSaleDateMs >= thresholdMs) ||
                    (p.lastSaleDateMs == 0L && nowMs - p.createdAtMs >= thresholdMs)
                )
        }
    }

    fun buildReorderSuggestions(state: MamoMainUiState): List<String> {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 86_400_000L
        val soldByProduct = mutableMapOf<Long, Double>()
        state.invoices.filter {
            it.invoice.status == InvoiceStatus.POSTED &&
                it.invoice.invoiceType == InvoiceType.SALE &&
                it.invoice.invoiceDateMs >= thirtyDaysAgo
        }.forEach { inv ->
            inv.items.forEach { item ->
                soldByProduct[item.productId] = (soldByProduct[item.productId] ?: 0.0) + item.baseQuantity
            }
        }
        return state.products
            .filter { it.quantity <= it.minStockLevel }
            .take(8)
            .map { p ->
                val monthlySold = soldByProduct[p.id] ?: 0.0
                val suggested = maxOf((p.minStockLevel * 2.0) - p.quantity, monthlySold * 0.5, 5.0)
                "• ${p.name}: المتوفر ${ArabicFormatters.formatNumber(p.quantity)} ${p.baseUnit} (الحد الأدنى ${ArabicFormatters.formatNumber(p.minStockLevel)}) ← يُقترح طلب ${ArabicFormatters.formatNumber(suggested)} ${p.baseUnit}"
            }
    }

    fun detectAccountingAnomalies(state: MamoMainUiState, canViewProfits: Boolean): List<String> {
        val alerts = mutableListOf<String>()
        val m = state.dashboardMetrics
        if (!m.isCashboxReconciled) {
            alerts.add("تنبيه تطابق: رصيد الصندوق الدفتري لا يطابق مجموع قيود الصندوق.")
        }
        val belowCost = state.products.filter { it.sellingPrice < it.averageCost }
        if (belowCost.isNotEmpty() && canViewProfits) {
            alerts.add("أصناف سعر بيعها أقل من متوسط التكلفة (${belowCost.size}): ${belowCost.take(3).joinToString("، ") { it.name }}")
        }
        if (m.overdueDebtsCount > 0) {
            alerts.add("توجد ${m.overdueDebtsCount} فواتير آجلة متجاوزة لتاريخ الاستحقاق.")
        }
        if (m.pendingSyncCount > 0) {
            alerts.add("يوجد ${m.pendingSyncCount} سجل بانتظار المزامنة السحابية.")
        }
        return alerts
    }

    fun buildGroundedSystemPrompt(
        state: MamoMainUiState,
        canViewProfits: Boolean,
        topProducts: List<ProductProfitabilityRow>
    ): String {
        val cur = state.settings.currencySymbol
        val m = state.dashboardMetrics
        val lowStockText = buildReorderSuggestions(state).joinToString("\n").ifBlank { "لا توجد نواقص حالياً" }
        val staleList = findStaleProducts(state.products, state.settings.staleDaysThreshold)
            .take(6).joinToString("، ") { "${it.name} (${ArabicFormatters.formatNumber(it.quantity)} ${it.baseUnit})" }
            .ifBlank { "لا يوجد" }
        val topProductsText = if (canViewProfits) {
            topProducts.joinToString("، ") {
                "${it.productName} (مباع: ${ArabicFormatters.formatNumber(it.soldQuantity)} | ربح: ${ArabicFormatters.formatMoney(it.grossProfit, cur)})"
            }
        } else {
            topProducts.joinToString("، ") {
                "${it.productName} (مباع: ${ArabicFormatters.formatNumber(it.soldQuantity)})"
            }
        }.ifBlank { "لا توجد مبيعات مسجلة بعد" }

        val topCustomers = state.parties
            .filter { it.partyType == PartyType.CUSTOMER && it.totalAmount > 0.0 }
            .sortedByDescending { it.totalAmount }
            .take(5)
            .joinToString("، ") { "${it.name}: مشتريات ${ArabicFormatters.formatMoney(it.totalAmount, cur)} (عليه: ${ArabicFormatters.formatMoney(it.balanceDue, cur)})" }
            .ifBlank { "لا يوجد" }

        val anomalies = detectAccountingAnomalies(state, canViewProfits).joinToString("\n").ifBlank { "لا توجد ملاحظات تدقيق حرجة" }

        return buildString {
            appendLine("أنت المساعد المحاسبي والذكي المتكامل (MAMO AI) لمتجر «${state.settings.storeName}» بإدارة «${state.settings.managerName}».")
            appendLine("قواعد صارمة:")
            appendLine("1. أجب باللغة العربية الاحترافية والواضحة وبنقاط عملية مختصرة.")
            appendLine("2. لا تخترع أرقاماً من خارج البيانات الحقيقية التالية إطلاقاً.")
            if (!canViewProfits) {
                appendLine("3. صلاحية المستخدم الحالي لا تشمل الاطلاع على الأرباح أو التكلفة أو رأس المال. لا تفصح عن أي تكاليف أو هوامش ربح.")
            }
            appendLine("- العملة: $cur | إجمالي الفواتير: ${m.totalInvoiceCount}")
            appendLine("- مبيعات اليوم: ${ArabicFormatters.formatMoney(m.todaySales, cur)}")
            appendLine("- مشتريات اليوم: ${ArabicFormatters.formatMoney(m.todayPurchases, cur)}")
            appendLine("- مصاريف اليوم: ${ArabicFormatters.formatMoney(m.todayExpenses, cur)} | إجمالي المصاريف: ${ArabicFormatters.formatMoney(m.totalExpenses, cur)}")
            if (canViewProfits) {
                appendLine("- صافي أرباح اليوم: ${ArabicFormatters.formatMoney(m.todayNetProfit, cur)}")
                appendLine("- قيمة المخزون بسعر التكلفة: ${ArabicFormatters.formatMoney(m.inventoryValueAtCost, cur)}")
                appendLine("- رأس المال الحالي: ${ArabicFormatters.formatMoney(m.currentCapital, cur)}")
            }
            appendLine("- رصيد الصندوق الحالي: ${ArabicFormatters.formatMoney(m.cashboxBalance, cur)}")
            appendLine("- ديون الزبائن لنا: ${ArabicFormatters.formatMoney(m.receivablesFromCustomers, cur)}")
            appendLine("- ديون الموردين علينا: ${ArabicFormatters.formatMoney(m.payablesToSuppliers, cur)}")
            appendLine("- الأصناف (${state.products.size}) | الأصناف الناقصة (${m.lowStockCount}):\n$lowStockText")
            appendLine("- الأصناف الراكدة (+${state.settings.staleDaysThreshold} يوم): $staleList")
            appendLine("- أكثر الأصناف مبيعاً: $topProductsText")
            appendLine("- أبرز الزبائن: $topCustomers")
            appendLine("- تنبيهات التدقيق والمخاطر:\n$anomalies")
        }
    }

    fun generateOfflineAnswer(
        question: String,
        state: MamoMainUiState,
        canViewProfits: Boolean,
        topProducts: List<ProductProfitabilityRow>
    ): String {
        val q = question.trim()
        val cur = state.settings.currencySymbol
        val m: DashboardMetrics = state.dashboardMetrics

        if (q.contains("ربح") || q.contains("أرباح") || q.contains("مكسب") || q.contains("تكلفة") || q.contains("رأس المال")) {
            if (!canViewProfits) {
                return "عذراً، صلاحية حسابك الحالي لا تسمح بعرض تقارير الأرباح أو رأس المال أو أسعار التكلفة."
            }
            return buildString {
                appendLine("تحليل الأرباح والمركز المالي (وضع التحليل المحلي الفوري):")
                appendLine("• صافي أرباح اليوم: ${ArabicFormatters.formatMoney(m.todayNetProfit, cur)}")
                appendLine("• إجمالي الإيرادات: ${ArabicFormatters.formatMoney(m.totalRevenue, cur)}")
                appendLine("• قيمة المخزون بالتكلفة: ${ArabicFormatters.formatMoney(m.inventoryValueAtCost, cur)}")
                appendLine("• رأس المال الحالي: ${ArabicFormatters.formatMoney(m.currentCapital, cur)}")
                val profitTop = topProducts.take(3).joinToString("، ") { "${it.productName} (${ArabicFormatters.formatMoney(it.grossProfit, cur)})" }
                if (profitTop.isNotBlank()) appendLine("• الأعلى ربحية: $profitTop")
            }
        }

        if (q.contains("نقص") || q.contains("ناقص") || q.contains("طلب") || q.contains("شراء") || q.contains("مخزون") || q.contains("نفاد")) {
            val reorders = buildReorderSuggestions(state)
            val stale = findStaleProducts(state.products, state.settings.staleDaysThreshold)
            return buildString {
                appendLine("توصيات المخزون وإعادة الطلب (تحليل محلي):")
                if (reorders.isEmpty()) {
                    appendLine("• جميع الأصناف (${state.products.size}) ضمن الحد الآمن ولا توجد نواقص حالياً.")
                } else {
                    appendLine("• الأصناف القريبة من النفاد (${reorders.size}):")
                    reorders.forEach { appendLine(it) }
                }
                if (stale.isNotEmpty()) {
                    appendLine("• أصناف راكدة لم تتحرك منذ +${state.settings.staleDaysThreshold} يوم (${stale.size}): ${stale.take(5).joinToString("، ") { it.name }}")
                }
            }
        }

        if (q.contains("دين") || q.contains("ديون") || q.contains("زبائن") || q.contains("عملاء") || q.contains("مورد")) {
            val topDebtors = state.parties
                .filter { it.partyType == PartyType.CUSTOMER && it.balanceDue > 0.01 }
                .sortedByDescending { it.balanceDue }
                .take(5)
            return buildString {
                appendLine("ملخص الذمم والديون (تحليل محلي):")
                appendLine("• ديون الزبائن لنا: ${ArabicFormatters.formatMoney(m.receivablesFromCustomers, cur)}")
                appendLine("• ديون الموردين علينا: ${ArabicFormatters.formatMoney(m.payablesToSuppliers, cur)}")
                appendLine("• الفواتير المتأخرة عن الاستحقاق: ${m.overdueDebtsCount}")
                if (topDebtors.isNotEmpty()) {
                    appendLine("• أكبر الذمم المدينة:")
                    topDebtors.forEach { appendLine("  - ${it.name}: ${ArabicFormatters.formatMoney(it.balanceDue, cur)}") }
                }
            }
        }

        val anomalies = detectAccountingAnomalies(state, canViewProfits)
        return buildString {
            appendLine("الملخص المالي السريع لمتجر ${state.settings.storeName} (وضع التحليل المحلي بدون إنترنت):")
            appendLine("• مبيعات اليوم: ${ArabicFormatters.formatMoney(m.todaySales, cur)} | مشتريات اليوم: ${ArabicFormatters.formatMoney(m.todayPurchases, cur)}")
            appendLine("• رصيد الصندوق: ${ArabicFormatters.formatMoney(m.cashboxBalance, cur)}")
            appendLine("• مصاريف اليوم: ${ArabicFormatters.formatMoney(m.todayExpenses, cur)}")
            appendLine("• الأصناف الناقصة: ${m.lowStockCount} | ديون الزبائن: ${ArabicFormatters.formatMoney(m.receivablesFromCustomers, cur)}")
            if (canViewProfits) {
                appendLine("• صافي أرباح اليوم: ${ArabicFormatters.formatMoney(m.todayNetProfit, cur)}")
            }
            if (anomalies.isNotEmpty()) {
                appendLine("• تنبيهات مهمة:")
                anomalies.forEach { appendLine("  - $it") }
            }
        }
    }
}
