package com.example.utils

import com.example.data.local.CashAccountType
import com.example.data.local.CashTxType
import com.example.data.local.CostingMethod
import com.example.data.local.InvoiceStatus
import com.example.data.local.InvoiceType
import com.example.data.local.MovementType
import com.example.data.local.PaymentMethod
import com.example.data.local.PricingTier
import com.example.data.local.ReturnType
import com.example.data.local.StaffRole
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ArabicFormatters {

    private val numberFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

    fun formatMoney(amount: Double, currencySymbol: String = "ل.س"): String {
        val formatted = numberFormat.format(amount)
        return "$formatted $currencySymbol"
    }

    fun formatNumber(value: Double): String {
        return numberFormat.format(value)
    }

    /**
     * Converts Arabic-Indic ('٠'..'٩') and Extended Arabic-Indic ('۰'..'۹') digits
     * plus Arabic decimal separators ('٫', ',') into standard ASCII decimal text.
     */
    fun normalizeDigits(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input.trim()) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                '٫', ',' -> sb.append('.')
                '٬', ' ' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseDoubleOrNull(input: String): Double? {
        val normalized = normalizeDigits(input)
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun parseLongOrNull(input: String): Long? {
        val normalized = normalizeDigits(input)
        return normalized.toLongOrNull()
    }

    fun formatDate(epochMs: Long, pattern: String = "dd/MM/yyyy"): String {
        if (epochMs <= 0L) return "—"
        return SimpleDateFormat(pattern, Locale.US).format(Date(epochMs))
    }

    fun formatDateTime(epochMs: Long, pattern: String = "dd/MM/yyyy"): String {
        if (epochMs <= 0L) return "—"
        return SimpleDateFormat("$pattern HH:mm", Locale.US).format(Date(epochMs))
    }

    fun startOfTodayMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun paymentMethodLabel(method: String): String = when (method) {
        PaymentMethod.CASH -> "نقدي"
        PaymentMethod.CREDIT -> "آجل"
        PaymentMethod.PARTIAL -> "جزئي"
        else -> method
    }

    fun invoiceStatusLabel(status: String): String = when (status) {
        InvoiceStatus.POSTED -> "معتمدة"
        InvoiceStatus.CANCELLED -> "ملغاة (قيد عكسي)"
        else -> status
    }

    fun invoiceTypeLabel(type: String): String = when (type) {
        InvoiceType.SALE -> "فاتورة بيع"
        InvoiceType.PURCHASE -> "فاتورة شراء"
        else -> type
    }

    fun staffRoleLabel(role: String): String = when (role) {
        StaffRole.OWNER -> "المدير العام"
        StaffRole.ACCOUNTANT -> "محاسب"
        StaffRole.CASHIER -> "بائع / كاشير"
        StaffRole.WAREHOUSE_KEEPER -> "أمين مخزن"
        StaffRole.AUDITOR -> "مراقب فقط"
        else -> role
    }

    fun pricingTierLabel(tier: String): String = when (tier) {
        PricingTier.RETAIL -> "مفرق"
        PricingTier.WHOLESALE -> "جملة"
        PricingTier.SPECIAL -> "سعر خاص"
        else -> tier
    }

    fun returnTypeLabel(type: String): String = when (type) {
        ReturnType.SALE_RETURN -> "مرتجع مبيعات"
        ReturnType.PURCHASE_RETURN -> "مرتجع مشتريات"
        else -> type
    }

    fun cashAccountTypeLabel(type: String): String = when (type) {
        CashAccountType.CASHBOX -> "صندوق نقدي"
        CashAccountType.BANK -> "حساب بنكي"
        CashAccountType.SAFE -> "خزينة رئيسية"
        else -> type
    }

    fun costingMethodLabel(method: String): String = when (method) {
        CostingMethod.WEIGHTED_AVERAGE -> "المتوسط المرجح للتكلفة"
        CostingMethod.LAST_COST -> "آخر سعر شراء"
        else -> method
    }

    fun movementTypeLabel(type: String): String = when (type) {
        MovementType.PURCHASE -> "شراء / توريد"
        MovementType.SALE -> "بيع / صرف"
        MovementType.REVERSAL -> "قيد عكسي"
        MovementType.ADJUSTMENT -> "جرد / تسوية"
        MovementType.SALE_RETURN -> "مرتجع مبيعات"
        MovementType.PURCHASE_RETURN -> "مرتجع مشتريات"
        MovementType.DAMAGED -> "بضاعة تالفة"
        else -> type
    }

    fun cashTxTypeLabel(type: String): String = when (type) {
        CashTxType.CAPITAL_IN -> "إيداع رأس مال"
        CashTxType.PERSONAL_DRAWING -> "سحب شخصي"
        CashTxType.SALE_RECEIPT -> "مقبوضات مبيعات"
        CashTxType.PURCHASE_PAYMENT -> "مدفوعات مشتريات"
        CashTxType.EXPENSE_PAYMENT -> "سداد مصاريف"
        CashTxType.DEBT_COLLECTION -> "تحصيل دين زبون"
        CashTxType.SUPPLIER_PAYMENT -> "سداد دين مورد"
        CashTxType.OTHER_INCOME -> "إيرادات أخرى"
        CashTxType.OTHER_OUTFLOW -> "إخراج أموال"
        CashTxType.REVERSAL -> "قيد عكسي"
        CashTxType.SALE_RETURN_REFUND -> "رد نقدي لمرتجع بيع"
        CashTxType.PURCHASE_RETURN_REFUND -> "استرداد نقدي لمرتجع شراء"
        CashTxType.CASH_TRANSFER_IN -> "تحويل وارد"
        CashTxType.CASH_TRANSFER_OUT -> "تحويل صادر"
        CashTxType.CLOSING_VARIANCE -> "فرق إغلاق صندوق"
        CashTxType.BAD_DEBT_WRITE_OFF -> "شطب دين معدوم"
        else -> type
    }

    val expenseCategories = listOf(
        "أجور العمال",
        "الكهرباء",
        "النقل",
        "الإيجار",
        "الصيانة",
        "الضيافة",
        "مصاريف متنوعة"
    )

    val supportedCurrencies = listOf(
        "SYP" to "ل.س",
        "USD" to "$",
        "SAR" to "ر.س",
        "EUR" to "€",
        "TRY" to "₺"
    )
}
